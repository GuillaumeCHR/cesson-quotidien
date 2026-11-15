from datetime import datetime, timezone
from pathlib import Path

import httpx
import pytest
import respx

from cesson.collecteurs.actus import SOURCES, CollecteurActus, DonneesActus, lire_flux
from cesson.commun.collecteur import ErreurValidation
from cesson.commun.modele import Etat, FichierModule
from cesson.commun.publication import Publieur
from cesson.commun.sante import Sante

# Flux RSS réels du 15 nov. 2026.
VILLE = (Path(__file__).parent / "fixtures" / "rss_ville.xml").read_bytes()
EAU = (Path(__file__).parent / "fixtures" / "rss_ebr.xml").read_bytes()
MAINTENANT = datetime(2026, 11, 15, 10, 0, tzinfo=timezone.utc)


def test_flux_de_la_ville():
    a = lire_flux(VILLE, "ville", 15)
    assert len(a) == 11 and a[0].titre == "Ordre du jour du Conseil municipal du 18 novembre"
    assert a[0].lien.startswith("https://www.ville-cesson-sevigne.fr/actualites/")
    assert a[0].extrait.startswith("Le Conseil municipal se réunira")
    assert [x.date for x in a] == sorted((x.date for x in a), reverse=True)


def test_flux_eau_sans_phrase_automatique():
    a = lire_flux(EAU, "eau", 10)
    assert len(a) == 10
    assert all(x.extrait is None or not x.extrait.startswith("L’article") for x in a)
    assert all(x.extrait is None or len(x.extrait) <= 221 for x in a)


def test_flux_illisible():
    with pytest.raises(ErreurValidation):
        lire_flux(b"<html>pas un flux", "ville", 5)


def test_publication(tmp_path):
    publieur, sante = Publieur(tmp_path / "public"), Sante(tmp_path / "etat", url_alerte="")
    with respx.mock() as mock:
        mock.get(SOURCES["ville"][1]).mock(return_value=httpx.Response(200, content=VILLE))
        with httpx.Client() as client:
            r = CollecteurActus().executer(publieur, sante, client, MAINTENANT)
    assert r.etat == Etat.OK, r.erreur
    f = FichierModule[DonneesActus].model_validate_json(publieur.chemin("actus").read_text())
    assert [x.nom for x in f.donnees.flux] == ["Ville de Cesson-Sévigné"]
