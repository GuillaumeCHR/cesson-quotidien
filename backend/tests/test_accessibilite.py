import json
from datetime import date, datetime, timezone
from pathlib import Path

import httpx
import pytest
import respx

from cesson.collecteurs.accessibilite import API, CollecteurAccessibilite, DonneesAccessibilite, accessibilite, adresse_courte
from cesson.commun.collecteur import ErreurValidation
from cesson.commun.modele import Etat, FichierModule
from cesson.commun.publication import Publieur
from cesson.commun.sante import Sante

# Réponse réelle d'acceslibre, 17 nov. 2026 (commune=Cesson-Sévigné) : 120 des 310 fiches, champs vides retirés.
BRUT = json.loads((Path(__file__).parent / "fixtures" / "acceslibre_cesson.json").read_text())
MAINTENANT = datetime(2026, 11, 17, 9, 0, tzinfo=timezone.utc)


def etablissements():
    c = CollecteurAccessibilite()
    c.valider(BRUT["results"])
    return c.convertir(BRUT["results"], MAINTENANT).etablissements


def par_nom(nom):
    return next(e for e in etablissements() if e.lieu.nom == nom)


def test_fiches_et_familles():
    e = etablissements()
    assert len(e) >= 110 and all(x.lieu.coord and x.lieu.url.startswith("https://acceslibre.beta.gouv.fr/") for x in e)
    # Services publics d'abord, commerces en dernier.
    assert e[0].famille == "services_publics" and e[-1].famille == "commerces"
    assert par_nom("Médiathèque").famille == "loisirs"
    assert par_nom("La Poste").famille == "services_publics"
    assert par_nom("Pharmacie Le Brun").famille == "sante"


def test_entree_avec_marches():
    p = par_nom("Pharmacie Le Brun")
    assert p.lieu.adresse == "49a Rue de Rennes" and p.mis_a_jour <= date(2026, 11, 17)
    a = p.acces
    assert a.entree_plain_pied is False and a.entree_marches == 1 and a.entree_rampe == "fixe"
    assert a.stationnement_pmr is False and a.stationnement_pmr_proche is True
    assert a.personnel_forme is False and a.entree_largeur_cm == 100


def test_entree_de_plain_pied_et_malentendants():
    a = par_nom("La Poste").acces
    assert a.entree_plain_pied is True and a.entree_marches is None and a.entree_rampe is None
    assert a.malentendants == ["boucle à induction magnétique"]


def test_non_renseigne_reste_none():
    a = accessibilite({"transport": {}, "entree": {"entree_plain_pied": True, "entree_marches": 3}})
    assert a.entree_plain_pied is True and a.entree_marches is None  # marches ignorées si plain-pied
    assert a.stationnement_pmr is None and a.sanitaires is None and a.sanitaires_adaptes is None
    assert a.personnel_forme is None and a.entree_appel is None
    a = accessibilite({"sanitaires": {"sanitaires_presence": False, "sanitaires_adaptes": True},
                       "transport": {"stationnement_presence": False}})
    assert a.sanitaires is False and a.sanitaires_adaptes is None and a.stationnement_pmr is False


def test_adresse_courte():
    assert adresse_courte("1 Place de l'Eglise 35510 Cesson-Sévigné") == "1 Place de l'Eglise"
    assert adresse_courte("Rue de Paris 35510") == "Rue de Paris"
    assert adresse_courte(None) is None


def test_trop_peu_de_fiches():
    with pytest.raises(ErreurValidation):
        CollecteurAccessibilite().valider(BRUT["results"][:20])


def test_collecte_complete_avec_cle_et_pagination(tmp_path, monkeypatch):
    cle = tmp_path / "acceslibre.env"
    cle.write_text('cle_api="abc.123"\n')
    monkeypatch.setenv("CESSON_ACCESLIBRE_IDENTIFIANTS", str(cle))
    publieur, sante = Publieur(tmp_path / "public"), Sante(tmp_path / "etat", url_alerte="")
    res = BRUT["results"]
    suite = f"{API}?commune=Cesson-S%C3%A9vign%C3%A9&page=2&page_size=200"
    with respx.mock() as mock:
        mock.get(API, params={"page": "2"}).mock(return_value=httpx.Response(200, json={"next": None, "results": res[60:]}))
        mock.get(API).mock(return_value=httpx.Response(200, json={"next": suite, "results": res[:60]}))
        with httpx.Client() as client:
            r = CollecteurAccessibilite().executer(publieur, sante, client, MAINTENANT)
        autorisations = {c.request.headers.get("Authorization") for c in mock.calls}
    assert r.etat == Etat.OK, r.erreur
    assert autorisations == {"Api-Key abc.123"}
    f = FichierModule[DonneesAccessibilite].model_validate_json(publieur.chemin("accessibilite").read_text())
    assert len(f.donnees.etablissements) == len(etablissements())


def test_sans_cle_pas_de_fuite(tmp_path, monkeypatch):
    monkeypatch.setenv("CESSON_ACCESLIBRE_IDENTIFIANTS", str(tmp_path / "absent.env"))
    publieur, sante = Publieur(tmp_path / "public"), Sante(tmp_path / "etat", url_alerte="")
    with respx.mock():
        r = CollecteurAccessibilite().executer(publieur, sante, httpx.Client(), MAINTENANT)
    assert r.etat == Etat.ERREUR and "illisibles" in r.erreur
