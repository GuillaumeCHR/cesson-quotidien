import json
from datetime import datetime, timezone
from pathlib import Path

import httpx
import pytest
import respx

from cesson.collecteurs.circulation import (
    URL, CollecteurCirculation, DonneesCirculation, entre_portes, etat_global, sens_horaire,
)
from cesson.commun.collecteur import ErreurValidation
from cesson.commun.modele import Etat, FichierModule
from cesson.commun.publication import Publieur
from cesson.commun.sante import Sante

# Instantané réel de Rennes Métropole, dimanche 15 nov. 2026 à 11h51 (rocade et axes de Cesson).
FIXTURE = json.loads((Path(__file__).parent / "fixtures" / "trafic_rennes.json").read_text())
MAINTENANT = datetime(2026, 11, 15, 10, 52, tzinfo=timezone.utc)


def copie():
    return json.loads(json.dumps(FIXTURE))


def test_instantane_reel():
    d = CollecteurCirculation().convertir(FIXTURE, MAINTENANT)
    assert [s.libelle for s in d.rocade_est] == ["Rocade intérieure", "Rocade extérieure"]
    assert all(s.etat == "fluide" and 60 <= s.vitesse <= 110 for s in d.rocade_est)
    assert d.axes_cesson and not any(a.nom.startswith("Rond-point") for a in d.axes_cesson)
    assert d.mesure_le.isoformat() == "2026-11-15T11:51:00+01:00"


def test_sens_de_rotation():
    # À l'est de Rennes, aller vers le sud, c'est tourner dans le sens des aiguilles d'une montre (rocade intérieure).
    assert sens_horaire([[-1.59, 48.13], [-1.59, 48.12]])
    assert not sens_horaire([[-1.59, 48.12], [-1.59, 48.13]])


def test_situer_entre_deux_portes():
    assert entre_portes(-1.588, 48.125) == "Porte de Tizé et Porte de la Valette"


def test_etat_global_pondere_par_la_longueur():
    assert etat_global([("fluide", 900), ("sature", 100)]) == "dense"
    assert etat_global([("fluide", 800), ("sature", 200)]) == "sature"
    assert etat_global([("fluide", 950), ("dense", 50)]) == "fluide"
    assert etat_global([("inconnu", 100)]) == "inconnu"


def test_ralentissement_localise():
    brut = copie()
    for t in brut:
        c = t["geo_shape"]["geometry"]["coordinates"]
        lon, lat = c[len(c) // 2]
        if t["denomination"] == "Route nationale 136" and lon > -1.60 and 48.115 < lat < 48.14 and sens_horaire(c):
            t["trafficstatus"], t["averagevehiclespeed"], t["traveltimereliability"] = "congested", 18, 100
    d = CollecteurCirculation().convertir(brut, MAINTENANT)
    interieure = d.rocade_est[0]
    assert interieure.ralentissements[0].entre == "Porte de Tizé et Porte de la Valette"
    assert interieure.ralentissements[0].etat == "sature"
    assert d.rocade_est[1].ralentissements == []


def test_ralentissement_peu_fiable_ignore():
    from cesson.collecteurs.circulation import etat
    assert etat({"trafficstatus": "heavy", "traveltimereliability": 42}) == "inconnu"
    assert etat({"trafficstatus": "heavy", "traveltimereliability": 100}) == "dense"
    assert etat({"trafficstatus": "freeFlow", "traveltimereliability": 0}) == "fluide"


def test_validation():
    with pytest.raises(ErreurValidation):
        CollecteurCirculation().valider([t for t in FIXTURE if t["denomination"] != "Route nationale 136"])


def test_publication(tmp_path):
    publieur, sante = Publieur(tmp_path / "public"), Sante(tmp_path / "etat", url_alerte="")
    with respx.mock() as mock:
        mock.get(URL).mock(return_value=httpx.Response(200, json=FIXTURE))
        with httpx.Client() as client:
            r = CollecteurCirculation().executer(publieur, sante, client, MAINTENANT)
    assert r.etat == Etat.OK, r.erreur
    f = FichierModule[DonneesCirculation].model_validate_json(publieur.chemin("circulation").read_text())
    assert f.meta.valide_jusqu_a.isoformat() == "2026-11-15T12:06:00+01:00"
