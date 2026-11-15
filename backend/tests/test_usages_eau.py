import json
from datetime import datetime, timezone
from pathlib import Path

import httpx
import respx

from cesson.collecteurs.usages_eau import URL_EBR, URL_VIGIEAU, CollecteurUsagesEau, DonneesUsagesEau, niveau_vigieau
from cesson.commun.modele import Etat, FichierModule
from cesson.commun.publication import Publieur
from cesson.commun.sante import Sante

# Réponses réelles du 15 nov. 2026 : Cesson en « crise » sécheresse, flux RSS d'Eau du Bassin Rennais.
VIGIEAU = json.loads((Path(__file__).parent / "fixtures" / "vigieau_cesson.json").read_text())
EBR = (Path(__file__).parent / "fixtures" / "rss_ebr.xml").read_bytes()
MAINTENANT = datetime(2026, 11, 15, 10, 0, tzinfo=timezone.utc)


def convertir(zones, ebr=EBR):
    c = CollecteurUsagesEau()
    brut = {"zones": zones, "ebr": ebr}
    c.valider(brut)
    return c.convertir(brut, MAINTENANT)


def test_restrictions_en_vigueur():
    d = convertir(VIGIEAU)
    assert d.niveau == "crise" and "Vilaine en amont de Rennes" in d.zone
    assert d.depuis.date().isoformat() == "2026-08-15" and d.jusqu_au is None
    assert d.arrete.endswith(".pdf")
    assert {u.thematique for u in d.usages} >= {"Arroser", "Nettoyer", "Remplir ou vidanger"}
    assert all("\r" not in u.regle and len(u.regle) <= 281 for u in d.usages)


def test_seules_les_consignes_sur_l_usage_de_l_eau():
    titres = [c.titre for c in convertir(VIGIEAU).consignes]
    assert titres and all("sécheresse" in t.lower() for t in titres)
    assert not any("Tout Rennes Court" in t or "Rophémel" in t for t in titres)


def test_sans_restriction():
    d = convertir([])
    assert d.niveau == "aucune" and d.usages == []


def test_niveaux():
    assert niveau_vigieau("alerte_renforcee") == "alerte_renforcee"
    assert niveau_vigieau("Alerte renforcée") == "alerte_renforcee"
    assert niveau_vigieau("inconnu") == "aucune"


def test_publication_et_vigieau_sans_restriction(tmp_path):
    publieur, sante = Publieur(tmp_path / "public"), Sante(tmp_path / "etat", url_alerte="")
    with respx.mock() as mock:
        mock.get(URL_VIGIEAU).mock(return_value=httpx.Response(404))
        mock.get(URL_EBR).mock(return_value=httpx.Response(200, content=EBR))
        with httpx.Client() as client:
            r = CollecteurUsagesEau().executer(publieur, sante, client, MAINTENANT)
    assert r.etat == Etat.OK, r.erreur
    f = FichierModule[DonneesUsagesEau].model_validate_json(publieur.chemin("usages_eau").read_text())
    assert f.donnees.niveau == "aucune"
