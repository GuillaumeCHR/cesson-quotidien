from datetime import datetime, timezone
from pathlib import Path

import pytest

from cesson.collecteurs.handball import CollecteurHandball, lire_matchs
from cesson.commun.collecteur import ErreurValidation

# Page « Saison 26/27 » du site du club, enregistrée le 22 nov. 2026.
PAGE = (Path(__file__).parent / "fixtures" / "handball_saison.html").read_text()
MAINTENANT = datetime(2026, 11, 22, 10, 0, tzinfo=timezone.utc)


def donnees(maintenant=MAINTENANT):
    c = CollecteurHandball()
    c.valider(PAGE)
    return c.convertir(PAGE, maintenant)


def test_toute_la_saison_avec_l_annee_deduite():
    ms = lire_matchs(PAGE)
    assert len(ms) == 34
    assert ms[0]["debut"].year == 2026 and ms[-1]["debut"].year == 2027
    assert ms[0]["competition"] == "Coupe de France" and ms[0]["tour"] == "1er tour"
    assert ms[1]["tour"] == "1re journée"


def test_seulement_les_matchs_a_la_glaz_arena():
    d = donnees()
    assert d.saison == "2026-2027"
    assert [m.adversaire for m in d.a_venir] == [
        "Saran", "Paris", "Saint-Raphaël", "Dunkerque", "Chambéry", "Montpellier", "Caen", "Tremblay", "Chartres"]
    assert all(m.salle == "Glaz Arena" for m in d.a_venir)
    saran = d.a_venir[0]
    assert saran.id == "liqui-moly-starligue-13e-journee-saran" and saran.tour == "13e journée"
    assert saran.debut.isoformat() == "2026-12-03T20:30:00+01:00"
    assert saran.billetterie == "https://billetterie.cesson-handball.com/content"
    assert d.a_venir[1].billetterie is None  # « A venir » : places pas encore en vente


def test_dernier_match_a_domicile_avec_son_score():
    d = donnees()
    assert d.dernier.adversaire == "Toulouse" and (d.dernier.score_cesson, d.dernier.score_adversaire) == (29, 37)


def test_match_commence_reste_a_venir_trois_heures():
    pendant = donnees(datetime(2026, 12, 3, 20, 0, tzinfo=timezone.utc))  # 21h à Paris, score pas encore publié
    assert pendant.a_venir[0].adversaire == "Saran"
    apres = donnees(datetime(2026, 12, 4, 0, 0, tzinfo=timezone.utc))
    assert apres.a_venir[0].adversaire == "Paris" and apres.dernier.adversaire == "Toulouse"


def test_page_sans_match_refusee():
    with pytest.raises(ErreurValidation):
        CollecteurHandball().valider("<html><h3>Calendrier <span>26/27</span></h3></html>")
