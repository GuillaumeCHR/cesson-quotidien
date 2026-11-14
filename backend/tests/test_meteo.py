import json
from datetime import datetime, timezone
from pathlib import Path

import httpx
import pytest
import respx

from cesson.collecteurs.meteo import URL, CollecteurMeteo, DonneesMeteo, libelle
from cesson.commun.collecteur import ErreurValidation
from cesson.commun.modele import Etat, FichierModule
from cesson.commun.publication import Publieur
from cesson.commun.sante import Sante

FIXTURE = json.loads((Path(__file__).parent / "fixtures" / "openmeteo_cesson.json").read_text())
# 15 nov. 2026, 1h40 à Paris.
MAINTENANT = datetime(2026, 11, 15, 0, 40, tzinfo=timezone.utc)


def test_conversion_fixture_reelle():
    c = CollecteurMeteo()
    c.valider(FIXTURE)
    d = c.convertir(FIXTURE, MAINTENANT)
    assert d.actuelle.temperature == 4.0 and d.actuelle.libelle == "Couvert" and not d.actuelle.jour
    # Heures rattachées au fuseau de Paris, à partir de l'heure en cours.
    assert d.heures[0].horodatage.isoformat() == "2026-11-15T01:00:00+01:00"
    assert len(d.heures) == 24
    assert [j.date.isoformat() for j in d.jours] == ["2026-11-15", "2026-11-16"]
    assert d.jours[0].min == 3.2 and d.jours[0].max == 13.9 and d.jours[0].libelle == "Pluie"
    assert d.jours[0].lever.isoformat() == "2026-11-15T08:13:00+01:00"


def test_code_inconnu_ne_bloque_pas():
    assert libelle(42) == "Conditions non précisées"


@pytest.mark.parametrize("modif", [
    lambda b: b.pop("current"),
    lambda b: b["current"].update(temperature_2m=99),
    lambda b: b["hourly"].update(time=b["hourly"]["time"][:5]),
    lambda b: b["hourly"].update(precipitation=b["hourly"]["precipitation"][:-1]),
    lambda b: b["daily"].update(time=b["daily"]["time"][:1]),
])
def test_validation_rejette(modif):
    brut = json.loads(json.dumps(FIXTURE))
    modif(brut)
    with pytest.raises(ErreurValidation):
        CollecteurMeteo().valider(brut)


def test_publication(tmp_path):
    publieur, sante = Publieur(tmp_path / "public"), Sante(tmp_path / "etat", url_alerte="")
    with respx.mock() as mock:
        mock.get(URL).mock(return_value=httpx.Response(200, json=FIXTURE))
        with httpx.Client() as client:
            r = CollecteurMeteo().executer(publieur, sante, client, MAINTENANT)
    assert r.etat == Etat.OK
    f = FichierModule[DonneesMeteo].model_validate_json(publieur.chemin("meteo").read_text())
    assert f.meta.licence == "CC BY 4.0"
    assert f.meta.valide_jusqu_a == datetime(2026, 11, 15, 3, 40, tzinfo=timezone.utc)
