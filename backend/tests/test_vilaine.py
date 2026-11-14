import json
from datetime import datetime, timedelta, timezone
from pathlib import Path

import httpx
import pytest
import respx

from cesson.collecteurs.vilaine import (URL, CollecteurVilaine, DonneesVilaine, PointSerie,
                                        calculer_tendance)
from cesson.commun.collecteur import ErreurValidation
from cesson.commun.modele import Etat, FichierModule, Tendance
from cesson.commun.publication import Publieur
from cesson.commun.sante import Sante

FIXTURE = json.loads((Path(__file__).parent / "fixtures" / "hubeau_pont_briand.json").read_text())
MAINTENANT = datetime(2026, 11, 14, 22, 40, tzinfo=timezone.utc)


@pytest.fixture
def env(tmp_path):
    return Publieur(tmp_path / "public"), Sante(tmp_path / "etat", url_alerte="")


def executer(env, reponse):
    publieur, sante = env
    with respx.mock(assert_all_called=False) as mock:
        mock.get(URL).mock(return_value=reponse)
        with httpx.Client() as client:
            return CollecteurVilaine().executer(publieur, sante, client, MAINTENANT)


def test_conversion_fixture_reelle():
    c = CollecteurVilaine()
    c.valider(FIXTURE)
    d = c.convertir(FIXTURE, MAINTENANT)
    assert d.mesure.valeur == 215.0 and d.mesure.unite == "mm"
    assert d.mesure.horodatage == datetime(2026, 11, 14, 22, 30, tzinfo=timezone.utc)
    assert d.serie_24h[0].horodatage < d.serie_24h[-1].horodatage
    assert d.mesure.tendance == Tendance.STABLE


@pytest.mark.parametrize("brut", [
    {},
    {"data": []},
    {"data": [{"code_station": "J709063002", "date_obs": "2026-11-14T22:30:00Z", "resultat_obs": 99999}] * 10},
    {"data": [{"code_station": "X", "date_obs": "2026-11-14T22:30:00Z", "resultat_obs": 200}] * 10},
])
def test_validation_rejette(brut):
    with pytest.raises(ErreurValidation):
        CollecteurVilaine().valider(brut)


def serie(valeurs_mm):
    t0 = datetime(2026, 11, 14, 12, tzinfo=timezone.utc)
    pts = [PointSerie(horodatage=t0 + timedelta(minutes=5 * i), valeur=v) for i, v in enumerate(valeurs_mm)]
    return list(reversed(pts))


def test_tendance():
    assert calculer_tendance(serie([200] * 13)) == Tendance.STABLE
    assert calculer_tendance(serie([200 + 5 * i for i in range(13)])) == Tendance.HAUSSE
    assert calculer_tendance(serie([400 - 5 * i for i in range(13)])) == Tendance.BAISSE
    assert calculer_tendance(serie([200, 210])) == Tendance.INCONNUE


def test_publication_et_manifest(env):
    publieur, sante = env
    r = executer(env, httpx.Response(200, json=FIXTURE))
    assert r.etat == Etat.OK and r.publie
    f = FichierModule[DonneesVilaine].model_validate_json(publieur.chemin("vilaine").read_text())
    assert f.meta.etat == Etat.OK and f.meta.licence == "Licence Ouverte 2.0"
    m = publieur.lire_manifest()
    assert m.modules["vilaine"].etat == Etat.OK
    assert sante.lire()["vilaine"].etat == Etat.OK


def test_version_stable_si_donnees_identiques(env):
    publieur, _ = env
    executer(env, httpx.Response(200, json=FIXTURE))
    v1 = publieur.lire_manifest().modules["vilaine"].version
    executer(env, httpx.Response(200, json=FIXTURE))
    assert publieur.lire_manifest().modules["vilaine"].version == v1


def test_echec_conserve_la_derniere_version_marquee_perimee(env, monkeypatch):
    monkeypatch.setattr("cesson.commun.collecteur.time.sleep", lambda s: None)
    publieur, sante = env
    executer(env, httpx.Response(200, json=FIXTURE))
    avant = json.loads(publieur.chemin("vilaine").read_text())

    r = executer(env, httpx.Response(503))
    assert r.etat == Etat.PERIME and r.publie
    apres = json.loads(publieur.chemin("vilaine").read_text())
    assert apres["meta"]["etat"] == "perime"
    assert apres["donnees"] == avant["donnees"]
    assert apres["meta"]["recupere_le"] == avant["meta"]["recupere_le"]
    assert publieur.lire_manifest().modules["vilaine"].etat == Etat.PERIME
    assert sante.lire()["vilaine"].echecs_consecutifs == 1


def test_echec_sans_version_precedente_ne_publie_rien(env):
    publieur, sante = env
    r = executer(env, httpx.Response(200, json={"data": []}))
    assert r.etat == Etat.ERREUR and not r.publie
    assert not publieur.chemin("vilaine").exists()
    assert publieur.lire_manifest() is None
    assert sante.lire()["vilaine"].etat == Etat.ERREUR


def test_nouvelles_tentatives_puis_succes(env, monkeypatch):
    monkeypatch.setattr("cesson.commun.collecteur.time.sleep", lambda s: None)
    publieur, sante = env
    with respx.mock() as mock:
        route = mock.get(URL).mock(side_effect=[httpx.Response(502), httpx.ConnectTimeout("t"),
                                                httpx.Response(200, json=FIXTURE)])
        with httpx.Client() as client:
            r = CollecteurVilaine().executer(publieur, sante, client, MAINTENANT)
    assert route.call_count == 3 and r.etat == Etat.OK


def test_alerte_envoyee_une_seule_fois_par_incident(tmp_path, monkeypatch):
    monkeypatch.setattr("cesson.commun.collecteur.time.sleep", lambda s: None)
    messages = []
    with respx.mock(assert_all_called=False) as mock:
        mock.post("https://ntfy.example/x").mock(
            side_effect=lambda req: messages.append(req.content.decode()) or httpx.Response(200))
        mock.get(URL).mock(side_effect=[httpx.Response(200, json=FIXTURE), httpx.Response(500),
                                        httpx.Response(500), httpx.Response(500)] * 2
                           + [httpx.Response(200, json=FIXTURE)])
        with httpx.Client() as client:
            publieur = Publieur(tmp_path / "public")
            sante = Sante(tmp_path / "etat", url_alerte="https://ntfy.example/x", client=client)
            for _ in range(3):
                CollecteurVilaine().executer(publieur, sante, client, MAINTENANT)
    assert len(messages) == 2
    assert "décroche" in messages[0] and "rétabli" in messages[1]
