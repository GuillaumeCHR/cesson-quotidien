import json
from datetime import date, datetime, timezone
from pathlib import Path

import pytest

from cesson.collecteurs.pharmacies import CollecteurPharmacies, semaine_type, telephone_lisible
from cesson.commun.collecteur import ErreurValidation
from cesson.commun.horaires import PARIS

# Réponse réelle d'Overpass, 17 nov. 2026 : les pharmacies de Cesson-Sévigné (code INSEE 35051).
OSM = json.loads((Path(__file__).parent / "fixtures" / "overpass_pharmacies_cesson.json").read_text())
MAINTENANT = datetime(2026, 11, 17, 9, 0, tzinfo=timezone.utc)  # mardi


def test_pharmacies_et_horaires_sur_14_jours():
    c = CollecteurPharmacies()
    c.valider(OSM)
    p = c.convertir(OSM, MAINTENANT).pharmacies
    assert len(p) == 5 and all(x.lieu.coord for x in p)
    monniais = next(x for x in p if x.lieu.nom == "Pharmacie de la Monniais")
    assert monniais.telephone == "02 99 83 23 18" and monniais.lieu.adresse == "48 Rue de Bray"
    assert monniais.horaires_verifies_le == date(2025, 11, 22)
    assert len(monniais.jours) == 14 and monniais.jours[0].date == date(2026, 11, 17)
    mardi = monniais.jours[0]
    assert mardi.connu and [(a.debut.astimezone(PARIS).hour, a.fin.astimezone(PARIS).hour) for a in mardi.plages] == [(9, 13), (14, 20)]
    # Dimanche 22 : rien dans OSM, donc fermé (connu).
    dimanche = monniais.jours[5]
    assert dimanche.date == date(2026, 11, 22) and dimanche.connu and dimanche.plages == []
    # Le Brun : numéro sans espaces remis en forme ; Bouriel et Salmon : un chemin OSM (way), centre publié.
    assert next(x for x in p if x.lieu.nom == "Pharmacie Le Brun").telephone == "02 99 83 22 21"
    assert next(x for x in p if x.lieu.nom == "Pharmacie Bouriel et Salmon").lieu.id.startswith("osm:way/")


def test_jour_ferie_ferme_sauf_garde():
    c = CollecteurPharmacies()
    p = c.convertir(OSM, datetime(2026, 11, 10, 9, 0, tzinfo=timezone.utc)).pharmacies[0]
    onze = next(j for j in p.jours if j.date == date(2026, 11, 11))
    assert onze.connu and onze.plages == [] and "Armistice" in onze.motif


def test_horaires_illisibles_inconnus():
    assert semaine_type("Mo-Fr 09:00-19:00; PH off") is not None
    assert semaine_type("Mo-Fr 09:00-19:00; Sa 09:00-12:00 \"sur rendez-vous\"") is None
    assert semaine_type(None) is None
    brut = {"elements": [{"type": "node", "id": i, "lat": 48.1, "lon": -1.6,
                          "tags": {"name": f"P{i}", "opening_hours": "sunrise-sunset"}} for i in range(3)]}
    p = CollecteurPharmacies().convertir(brut, MAINTENANT).pharmacies
    assert p[0].lieu.nom == "Pharmacie P0" and all(not j.connu for j in p[0].jours)


def test_telephones():
    assert telephone_lisible("+33 2 99 83 27 76") == "02 99 83 27 76"
    assert telephone_lisible("02 99 83 97 80;+33 6 00 00 00 00") == "02 99 83 97 80"
    assert telephone_lisible(None) is None


def test_trop_peu_de_pharmacies():
    with pytest.raises(ErreurValidation):
        CollecteurPharmacies().valider({"elements": []})
