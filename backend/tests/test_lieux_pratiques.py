import json
from datetime import datetime, timezone
from pathlib import Path

from cesson.collecteurs.lieux_pratiques import CollecteurBornes, CollecteurDefibrillateurs

# Réponses réelles de l'API tabulaire de data.gouv.fr, 15 nov. 2026, filtrées sur Cesson-Sévigné.
DAE = json.loads((Path(__file__).parent / "fixtures" / "geodae_cesson.json").read_text())["data"]
IRVE = json.loads((Path(__file__).parent / "fixtures" / "irve_cesson.json").read_text())["data"]
MAINTENANT = datetime(2026, 11, 15, 10, 0, tzinfo=timezone.utc)


def test_defibrillateurs():
    c = CollecteurDefibrillateurs()
    c.valider(DAE)
    d = c.convertir(DAE, MAINTENANT).defibrillateurs
    # 110 fiches, dont celles en attente de validation et les doublons écartées.
    assert 95 <= len(d) <= 105
    # Les accessibles à tout moment (dehors, libre accès) en tête.
    assert d[0].exterieur and d[0].libre_acces
    # La valeur par défaut « Mo-Su off » n'est jamais publiée.
    assert all(x.horaires != "Mo-Su off" for x in d)
    assert all(x.lieu.coord for x in d)
    # Les noms réduits à un code interne sont remplacés par l'adresse.
    assert not any(x.lieu.nom in ("Cla224707853", "1959") for x in d)


def test_bornes_regroupees_par_station():
    c = CollecteurBornes()
    c.valider(IRVE)
    s = c.convertir(IRVE, MAINTENANT).stations
    assert sum(x.points for x in s) == 63 and len(s) == 14
    carrefour = next(x for x in s if x.lieu.id == "irve:FRALLPEVCARSRECES")
    assert carrefour.points == 12 and carrefour.puissance_max == 300 and "Combo CCS" in carrefour.prises
    assert carrefour.acces == "Accès libre" and carrefour.h24 and carrefour.horaires is None
    assert {x.lieu.nom for x in s} >= {"Rennes Cesson", "Rue de bray", "Volvo - Rennes (PDL1)", "Mazda - Rennes"}
    assert [x.puissance_max for x in s] == sorted((x.puissance_max for x in s), reverse=True)
