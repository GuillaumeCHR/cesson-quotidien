import json
from datetime import datetime, timezone
from pathlib import Path

import httpx
import pytest
import respx

from cesson.collecteurs.circulation import (
    GRAVITE, URL, CollecteurCirculation, DonneesCirculation, entre_portes, etat_global, sens_horaire, simplifier,
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
    # Dimanche midi : cadence de 10 min, la carte reste à jour 25 min après la mesure (11h51).
    assert f.meta.valide_jusqu_a.isoformat() == "2026-11-15T12:16:00+01:00"


def test_cadence_selon_l_heure():
    from cesson.collecteurs.circulation import pas_de_collecte
    assert pas_de_collecte(datetime(2026, 11, 16, 7, 30, tzinfo=timezone.utc)).seconds == 180   # lundi 8h30
    assert pas_de_collecte(datetime(2026, 11, 16, 12, 0, tzinfo=timezone.utc)).seconds == 600   # lundi 13h
    assert pas_de_collecte(datetime(2026, 11, 16, 22, 0, tzinfo=timezone.utc)).seconds == 1800  # lundi 23h
    assert pas_de_collecte(datetime(2026, 11, 15, 7, 30, tzinfo=timezone.utc)).seconds == 600   # dimanche 8h30


def test_traces_pour_le_plan():
    d = CollecteurCirculation().convertir(FIXTURE, MAINTENANT)
    assert len(d.troncons) == 316  # rocade est (deux sens) et axes de Cesson
    assert 0 < sum(t.rocade for t in d.troncons) < 316
    # Du moins au plus chargé : le plus grave se dessine par-dessus.
    gravites = [GRAVITE.index(t.etat) for t in d.troncons]
    assert gravites == sorted(gravites)
    for t in d.troncons:
        assert len(t.points) >= 2
        assert all(-1.64 < lon < -1.56 and 48.08 < lat < 48.16 for lon, lat in t.points)
    # Simplifié mais fidèle : moins d'un tiers des points d'origine.
    assert sum(len(t.points) for t in d.troncons) < 2343 / 3


def test_simplifier_garde_les_extremites_et_les_virages():
    droite = [[-1.6, 48.1], [-1.59995, 48.1], [-1.5999, 48.1]]
    assert simplifier(droite) == [[-1.6, 48.1], [-1.5999, 48.1]]
    coude = [[-1.6, 48.1], [-1.599, 48.1], [-1.599, 48.101]]
    assert simplifier(coude) == coude
