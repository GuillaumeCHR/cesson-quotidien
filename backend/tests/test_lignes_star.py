import json
from datetime import datetime, timezone
from pathlib import Path

import httpx
import pytest
import respx

from cesson.collecteurs.lignes_star import CollecteurLignesStar, DonneesLignesStar, url
from cesson.commun.collecteur import ErreurValidation
from cesson.commun.modele import Etat, FichierModule, Mode
from cesson.commun.publication import Publieur
from cesson.commun.sante import Sante

# Réponses réelles de data.explore.star.fr du 15 nov. 2026.
DOSSIER = Path(__file__).parent / "fixtures" / "star"
MAINTENANT = datetime(2026, 11, 15, 0, 40, tzinfo=timezone.utc)


def lire(nom: str):
    return json.loads((DOSSIER / f"{nom}.json").read_text())


def brut() -> dict:
    return {"bus": [d["idligne"] for d in lire("bus_dessertes")],
            "metro": [d["idligne"] for d in lire("metro_dessertes")],
            "lignes_bus": lire("bus_lignes"), "lignes_metro": lire("metro_lignes")}


def test_conversion_fixture_reelle():
    c = CollecteurLignesStar()
    b = brut()
    c.valider(b)
    d = c.convertir(b, MAINTENANT)
    noms = [l.nom_court for l in d.lignes]
    assert noms[0] == "b" and d.lignes[0].mode == Mode.METRO and d.lignes[0].couleur == "#00893e"
    # Chronostar juste après le métro, puis les lignes par numéro.
    assert noms[1:4] == ["C2", "C4", "C6"]
    assert noms.index("11") < noms.index("64") < noms.index("164ex")
    assert "a" not in noms


@pytest.mark.parametrize("modif", [
    lambda b: b.update(metro=[]),
    lambda b: b.update(bus=["0002"], metro=["1002"]),
    lambda b: b["lignes_bus"][0].update(couleurligne="rouge"),
    lambda b: b.update(lignes_metro=None),
])
def test_validation_rejette(modif):
    b = brut()
    modif(b)
    with pytest.raises(ErreurValidation):
        CollecteurLignesStar().valider(b)


def test_collecte_complete(tmp_path):
    publieur, sante = Publieur(tmp_path / "public"), Sante(tmp_path / "etat", url_alerte="")
    with respx.mock() as mock:
        for jeu, nom in [("tco-bus-topologie-pointsarret-td", "bus_arrets"),
                         ("tco-metro-topologie-pointsarret-td", "metro_arrets"),
                         ("tco-bus-topologie-dessertes-td", "bus_dessertes"),
                         ("tco-metro-topologie-dessertes-td", "metro_dessertes"),
                         ("tco-bus-topologie-lignes-td", "bus_lignes"),
                         ("tco-metro-topologie-lignes-td", "metro_lignes")]:
            mock.get(url(jeu)).mock(return_value=httpx.Response(200, json=lire(nom)))
        with httpx.Client() as client:
            r = CollecteurLignesStar().executer(publieur, sante, client, MAINTENANT)
    assert r.etat == Etat.OK, r.erreur
    f = FichierModule[DonneesLignesStar].model_validate_json(publieur.chemin("lignes_star").read_text())
    assert f.meta.licence == "ODbL" and len(f.donnees.lignes) > 10
