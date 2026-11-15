import json
from datetime import datetime, timedelta, timezone
from pathlib import Path

import httpx
import pytest
import respx

from cesson.collecteurs.carburants import URL, CollecteurCarburants, DonneesCarburants, lire_saisie
from cesson.commun.collecteur import ErreurValidation
from cesson.commun.modele import Etat, FichierModule
from cesson.commun.publication import Publieur
from cesson.commun.sante import Sante

# Réponse réelle du 15 nov. 2026 pour le code postal 35510 (3 stations).
FIXTURE = json.loads((Path(__file__).parent / "fixtures" / "carburants_cesson.json").read_text())
MAINTENANT = datetime(2026, 11, 15, 8, 0, tzinfo=timezone.utc)


def copie() -> dict:
    return json.loads(json.dumps(FIXTURE))


def test_saisie_versionnee_lisible():
    s = lire_saisie()
    assert s[35510001]["nom"] == "Carrefour Rigourdière"


def test_conversion_fixture_reelle():
    c = CollecteurCarburants()
    c.valider(FIXTURE)
    d = c.convertir(FIXTURE, MAINTENANT)
    assert [s.lieu.nom for s in d.stations] == ["Carrefour Rigourdière", "Carrefour Market Bourgchevreuil", "Total route de Rennes"]
    carrefour = d.stations[0]
    assert [p.libelle for p in carrefour.prix] == ["Gazole", "SP95-E10", "SP95", "SP98", "E85", "GPLc"]
    assert carrefour.prix[0].prix == 2.025 and carrefour.automate_24_24
    assert carrefour.lieu.url == "https://www.prix-carburants.gouv.fr/station/35510001"
    total = d.stations[2]
    # SP98 en rupture temporaire : pas de prix, une rupture datée.
    assert "sp98" not in {p.carburant for p in total.prix}
    assert [(r.libelle, r.debut.isoformat()) for r in total.ruptures] == [("SP98", "2026-11-13T19:54:21+00:00")]
    # Les ruptures définitives (E85, GPLc, E10 au Carrefour Market) ne sont pas listées.
    assert d.stations[1].ruptures == []


def test_station_hors_saisie_garde_son_adresse():
    d = CollecteurCarburants(saisie={}).convertir(FIXTURE, MAINTENANT)
    assert {s.lieu.nom for s in d.stations} >= {"Rue de la Rigourdière", "27 RTE DE RENNES"}


@pytest.mark.parametrize("modif", [
    lambda b: b.pop("results"),
    lambda b: b.update(results=[]),
    lambda b: b["results"][0].update(gazole_prix=12.5),
    lambda b: b["results"][0].update(gazole_maj=None),
    lambda b: b["results"][0].update(id="x"),
])
def test_validation_rejette(modif):
    brut = copie()
    modif(brut)
    with pytest.raises(ErreurValidation):
        CollecteurCarburants().valider(brut)


def test_requete_cible_cesson_et_la_saisie(tmp_path):
    publieur, sante = Publieur(tmp_path / "public"), Sante(tmp_path / "etat", url_alerte="")
    with respx.mock() as mock:
        route = mock.get(URL).mock(return_value=httpx.Response(200, json=FIXTURE))
        with httpx.Client() as client:
            r = CollecteurCarburants(saisie={35000020: {"nom": "Total Access"}}).executer(
                publieur, sante, client, MAINTENANT)
    assert r.etat == Etat.OK
    assert route.calls[0].request.url.params["where"] == "cp='35510' or id=35000020"
    f = FichierModule[DonneesCarburants].model_validate_json(publieur.chemin("carburants").read_text())
    assert f.meta.valide_jusqu_a == MAINTENANT + timedelta(hours=1)
