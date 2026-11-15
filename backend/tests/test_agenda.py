import json
import re
from datetime import date, datetime, timezone
from pathlib import Path

import httpx
import pytest
import respx

from cesson.collecteurs.agenda import (
    URL_OPENAGENDA, URL_VILLE, CollecteurAgenda, DonneesAgenda, depuis_openagenda, lire_evenement, lire_liste, theme,
)
from cesson.commun.collecteur import ErreurValidation
from cesson.commun.modele import Etat, FichierModule
from cesson.commun.publication import Publieur
from cesson.commun.sante import Sante

# Pages réelles du site de la ville, 15 nov. 2026 (réduites aux blocs utiles).
D = Path(__file__).parent / "fixtures" / "agenda"
LISTE = (D / "liste_page1.html").read_text()
EVENEMENTS = {re.search(r"<!-- (\S+) -->", p.read_text()).group(1): p.read_text() for p in sorted(D.glob("evenement_*.html"))}
MAINTENANT = datetime(2026, 11, 15, 10, 0, tzinfo=timezone.utc)


def test_liste():
    c = lire_liste(LISTE)
    assert len(c) == 10
    assert c[0]["titre"] == "Permanence Vestiaire Solidaire" and c[0]["categorie"] == "Associations"
    assert c[5]["titre"] == "Chœur d’or : concert avec les Funky Frogs"  # entités HTML décodées


def test_pages_d_evenement():
    p = {u.rstrip("/").rsplit("/", 1)[-1]: lire_evenement(t) for u, t in EVENEMENTS.items()}
    vestiaire = p["reouverture-du-vestiaire-solidaire"]
    assert vestiaire["du"] == date(2026, 10, 23) and vestiaire["au"] == date(2026, 12, 18)
    assert vestiaire["horaires"] == "14h00 – 17h00" and vestiaire["lieu"] == "Stade Roger Belliard"
    concert = next(v for k, v in p.items() if "funky" in k)
    assert concert["du"] == concert["au"] == date(2026, 11, 15) and concert["horaires"] == "17h00"
    assert concert["lieu"] == "Pont des arts"


def test_page_changee():
    with pytest.raises(ErreurValidation):
        lire_evenement("<html><p>Nouvelle mise en page</p></html>")


def test_themes():
    assert theme("Galerie Pictura / Centre culturel du Pont des Arts", "Exposition", None) == "carre_sevigne"
    assert theme("Médiathèque", "Pouce on lit", None) == "mediatheque"
    assert theme("Stade Roger Belliard", "Vestiaire", "Associations") is None


OA = json.loads((D / "openagenda.json").read_text())
PQ = json.loads((D / "postsquery_page1.json").read_text())


def test_openagenda():
    e = [x for x in (depuis_openagenda(v, date(2026, 11, 15)) for v in OA["events"]) if x]
    suzane = next(x for x in e if x.titre == "Suzane")
    assert suzane.du == date(2026, 11, 19) and suzane.horaires.startswith("20h00")
    assert suzane.lieu == "Carré Sévigné" and suzane.theme == "carre_sevigne"
    assert suzane.conditions == "Payant, sur réservation"
    expo = next(x for x in e if x.titre == "Exposition Clémentine Carsberg")
    assert expo.du == date(2026, 11, 17) and expo.au == date(2026, 12, 16)  # prochaine séance, dernière séance


def test_collecte(tmp_path):
    publieur, sante = Publieur(tmp_path / "public"), Sante(tmp_path / "etat", url_alerte="")
    # Les pages d'événements déjà sur OpenAgenda ne sont pas relues : toutes les routes ne sont pas appelées.
    with respx.mock(assert_all_called=False) as mock:
        mock.get(URL_OPENAGENDA).mock(return_value=httpx.Response(200, json=OA))
        mock.get(URL_VILLE).mock(side_effect=lambda req: httpx.Response(200, json=PQ if req.url.params["pages"] == "1"
                                                                         else {"pagination_infos": PQ["pagination_infos"], "posts": []}))
        for url, page in EVENEMENTS.items():
            mock.get(url).mock(return_value=httpx.Response(200, text=page))
        with httpx.Client() as client:
            r = CollecteurAgenda(pause=0).executer(publieur, sante, client, MAINTENANT)
    assert r.etat == Etat.OK, r.erreur
    e = FichierModule[DonneesAgenda].model_validate_json(publieur.chemin("agenda").read_text()).donnees.evenements
    titres = [x.titre for x in e]
    # Suzane et l'exposition sont sur OpenAgenda et sur le site : une seule fois, la version OpenAgenda.
    assert titres.count("Suzane") == 1 and next(x for x in e if x.titre == "Suzane").id.startswith("openagenda:")
    assert "Permanence Vestiaire Solidaire" in titres and "Thomas Fersen" in titres
    assert [x.du for x in e] == sorted(x.du for x in e) and all(x.au >= date(2026, 11, 15) for x in e)


def test_sources_cassees_garde_la_version_precedente(tmp_path):
    publieur, sante = Publieur(tmp_path / "public"), Sante(tmp_path / "etat", url_alerte="")
    with respx.mock() as mock:
        mock.get(URL_OPENAGENDA).mock(return_value=httpx.Response(200, json={"events": []}))
        mock.get(URL_VILLE).mock(return_value=httpx.Response(200, json={"pagination_infos": {}, "posts": []}))
        with httpx.Client() as client:
            r = CollecteurAgenda(pause=0).executer(publieur, sante, client, MAINTENANT)
    assert r.etat == Etat.ERREUR


def test_saison_culturelle_et_familles():
    from cesson.collecteurs.agenda import est_saison, famille
    assert est_saison("Carré Sévigné", "Payant, sur réservation")
    assert est_saison("Auditorium du Pont des arts", "payant, sur inscriptions")
    assert not est_saison("Auditorium du Pont des arts", "Entrée libre")  # concert gratuit : pas la saison
    assert not est_saison("Glaz Arena", "Payant, sur réservation")
    assert famille("Rotary Club : concours de pétanque", "Associations", "Boulodrome", None) == "associations"
    assert famille("Glaz Arena : Glorious", "Divers", "Glaz Arena", None) == "sport"
    assert famille("Exposition Clémentine Carsberg", "Exposition", "Galerie Pictura", "carre_sevigne") == "culture"
    assert famille("Lecture pour les tout-petits", None, "Médiathèque", "mediatheque") == "mediatheque"
    assert famille("Banque alimentaire : collecte", "Divers", None, None) == "autre"
