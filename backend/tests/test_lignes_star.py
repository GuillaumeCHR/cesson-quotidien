import json
from datetime import datetime, timezone
from pathlib import Path

import httpx
import pytest
import respx

from cesson.collecteurs.lignes_star import CollecteurLignesStar, DonneesLignesStar, cle_alphabetique, url
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
    return {"bus": sorted({d["idligne"] for d in lire("bus_dessertes")}),
            "metro": sorted({d["idligne"] for d in lire("metro_dessertes")}),
            "points_bus": lire("bus_arrets"), "points_metro": lire("metro_arrets"),
            "dessertes_bus": lire("bus_dessertes"), "dessertes_metro": lire("metro_dessertes"),
            "lignes_bus": lire("bus_lignes"), "lignes_metro": lire("metro_lignes"),
            "reseau_bus": lire("reseau_bus_dessertes"), "reseau_metro": lire("reseau_metro_dessertes")}


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


def test_arrets_regroupes_par_nom():
    d = CollecteurLignesStar().convertir(brut(), MAINTENANT)
    par_nom = {a.nom: a for a in d.arrets}
    # Les deux quais d'un même arrêt n'en font qu'un, avec les lignes des deux.
    atalante = par_nom["Atalante"]
    assert atalante.modes == [Mode.METRO, Mode.BUS] and atalante.lignes[0] == "b"
    assert par_nom["Cesson - Viasilva"].lignes[:2] == ["b", "C2"]
    assert all(a.lignes for a in d.arrets)
    noms = [a.nom for a in d.arrets]
    assert noms.index("Ménouriais") < noms.index("Muguet") and noms == sorted(noms, key=cle_alphabetique)
    assert 40 <= len(d.arrets) <= 60


def test_reseau_entier():
    d = CollecteurLignesStar().convertir(brut(), MAINTENANT)
    par_nom = {a.nom: a for a in d.reseau}
    # Hors de Cesson aussi, avec métro et bus réunis sous un même nom.
    assert len(d.reseau) > 500
    assert par_nom["République"].modes[0] == Mode.METRO and "a" in par_nom["République"].lignes
    assert par_nom["Gares"].lignes[:2] == ["a", "b"]
    noms = [a.nom for a in d.reseau]
    assert noms == sorted(noms, key=cle_alphabetique)


@pytest.mark.parametrize("modif", [
    lambda b: b.update(reseau_bus=[]),
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


@pytest.mark.parametrize("avec_cle", [True, False])
def test_collecte_complete(tmp_path, monkeypatch, avec_cle):
    # Avec la clé du compte STAR, chaque appel la porte ; sans fichier de clé, appels anonymes.
    cle = tmp_path / "star.env"
    if avec_cle:
        cle.write_text("cle_api=0123abcd\n")
    monkeypatch.setenv("CESSON_STAR_IDENTIFIANTS", str(cle))
    publieur, sante = Publieur(tmp_path / "public"), Sante(tmp_path / "etat", url_alerte="")
    with respx.mock() as mock:
        # Les dessertes servent deux fois : arrêts de Cesson (filtrées par idarret), puis réseau entier.
        for jeu, cesson, reseau in [("tco-bus-topologie-dessertes-td", "bus_dessertes", "reseau_bus_dessertes"),
                                    ("tco-metro-topologie-dessertes-td", "metro_dessertes", "reseau_metro_dessertes")]:
            mock.get(url(jeu)).mock(side_effect=lambda req, c=cesson, r=reseau: httpx.Response(
                200, json=lire(c if "where" in req.url.params else r)))
        for jeu, nom in [("tco-bus-topologie-pointsarret-td", "bus_arrets"),
                         ("tco-metro-topologie-pointsarret-td", "metro_arrets"),
                         ("tco-bus-topologie-lignes-td", "bus_lignes"),
                         ("tco-metro-topologie-lignes-td", "metro_lignes")]:
            mock.get(url(jeu)).mock(return_value=httpx.Response(200, json=lire(nom)))
        with httpx.Client() as client:
            r = CollecteurLignesStar().executer(publieur, sante, client, MAINTENANT)
        autorisations = {c.request.headers.get("Authorization") for c in mock.calls}
    assert r.etat == Etat.OK, r.erreur
    assert autorisations == ({"Apikey 0123abcd"} if avec_cle else {None})
    f = FichierModule[DonneesLignesStar].model_validate_json(publieur.chemin("lignes_star").read_text())
    assert f.meta.licence == "ODbL" and len(f.donnees.lignes) > 10 and len(f.donnees.reseau) > 500
