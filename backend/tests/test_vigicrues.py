import json
from datetime import datetime, timedelta, timezone
from pathlib import Path

import httpx
import pytest
import respx

from cesson.collecteurs.vigicrues import URL, CollecteurVigicrues, DonneesVigicrues, Vigilance
from cesson.commun.collecteur import ErreurValidation
from cesson.commun.modele import Etat, FichierModule, NiveauAlerte
from cesson.commun.publication import Publieur
from cesson.commun.sante import Sante

# Flux national réel du 15 nov. 2026 (géométries retirées) : Vilaine amont en vert.
FIXTURE = json.loads((Path(__file__).parent / "fixtures" / "vigicrues_france.geojson").read_text())
MAINTENANT = datetime(2026, 11, 15, 7, 15, tzinfo=timezone.utc)


def avec_niveau(niveau: int) -> dict:
    brut = json.loads(json.dumps(FIXTURE))
    for f in brut["features"]:
        if f["properties"]["CdEntCru"] == "BT9":
            f["properties"]["NivInfViCr"] = niveau
    return brut


def collecter(tmp_path, brut, maintenant):
    publieur, sante = Publieur(tmp_path / "public"), Sante(tmp_path / "etat", url_alerte="")
    with respx.mock() as mock:
        mock.get(URL).mock(return_value=httpx.Response(200, json=brut))
        with httpx.Client() as client:
            r = CollecteurVigicrues().executer(publieur, sante, client, maintenant)
    f = FichierModule[DonneesVigicrues].model_validate_json(publieur.chemin("vigicrues").read_text())
    return r, f


def test_fixture_reelle_vert():
    c = CollecteurVigicrues()
    c.valider(FIXTURE)
    d = c.convertir(FIXTURE, MAINTENANT)
    assert d.vigilance == Vigilance.VERT and d.alerte is None
    assert d.troncon.nom == "Vilaine amont" and d.depuis == MAINTENANT


def test_jaune_produit_une_alerte():
    d = CollecteurVigicrues().convertir(avec_niveau(2), MAINTENANT)
    assert d.vigilance == Vigilance.JAUNE
    assert d.alerte.niveau == NiveauAlerte.JAUNE
    assert d.alerte.titre == "Vigilance crues jaune sur la Vilaine amont"


@pytest.mark.parametrize("modif", [
    lambda b: b.pop("features"),
    lambda b: b.update(features=b["features"][:20]),
    lambda b: b.update(features=[f for f in b["features"] if f["properties"]["CdEntCru"] != "BT9"]),
])
def test_validation_rejette(modif):
    brut = json.loads(json.dumps(FIXTURE))
    modif(brut)
    with pytest.raises(ErreurValidation):
        CollecteurVigicrues().valider(brut)


@pytest.mark.parametrize("niveau", [0, 5, None, "2"])
def test_niveau_inconnu_rejete(niveau):
    with pytest.raises(ErreurValidation):
        CollecteurVigicrues().valider(avec_niveau(niveau))


def test_depuis_conserve_tant_que_le_niveau_ne_change_pas(tmp_path):
    t1, t2, t3 = MAINTENANT, MAINTENANT + timedelta(minutes=15), MAINTENANT + timedelta(minutes=30)
    _, f1 = collecter(tmp_path, FIXTURE, t1)
    r2, f2 = collecter(tmp_path, FIXTURE, t2)
    assert f2.donnees.depuis == t1 and r2.changements == []
    r3, f3 = collecter(tmp_path, avec_niveau(3), t3)
    assert f3.donnees.vigilance == Vigilance.ORANGE and f3.donnees.depuis == t3
    assert f3.donnees.alerte.debut == t3
    assert [(c.type, c.donnees["sens"]) for c in r3.changements] == [("vigilance", "hausse")]


def test_publication(tmp_path):
    r, f = collecter(tmp_path, FIXTURE, MAINTENANT)
    assert r.etat == Etat.OK
    assert f.meta.licence == "Licence Ouverte 2.0"
    assert f.meta.valide_jusqu_a == MAINTENANT + timedelta(minutes=45)
