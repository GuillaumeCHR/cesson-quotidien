import json
from datetime import date, datetime, timezone
from pathlib import Path

import pytest

from cesson.collecteurs.vacances_scolaires import CollecteurVacancesScolaires
from cesson.commun.collecteur import ErreurValidation

# Réponse réelle du 15 nov. 2026, académie de Rennes.
BRUT = json.loads((Path(__file__).parent / "fixtures" / "calendrier_scolaire_rennes.json").read_text())
MAINTENANT = datetime(2026, 11, 15, 10, 0, tzinfo=timezone.utc)


def donnees(maintenant=MAINTENANT):
    c = CollecteurVacancesScolaires()
    c.valider(BRUT)
    return c.convertir(BRUT, maintenant)


def test_prochaines_vacances_en_heure_de_paris():
    d = donnees()
    assert d.zone == "Zone B"
    noel = d.periodes[0]
    assert noel.nom == "Vacances de Noël" and noel.du == date(2026, 12, 19) and noel.reprise == date(2027, 1, 4)
    assert all(p.du >= date(2026, 11, 15) for p in d.periodes)  # la Toussaint passée n'y est plus


def test_ete_des_eleves_et_pont():
    d = donnees()
    ete = [p for p in d.periodes if p.nom == "Vacances d'Été"]
    assert len(ete) == 1 and ete[0].reprise == date(2027, 9, 2)
    pont = next(p for p in d.periodes if p.nom == "Pont de l'Ascension")
    assert pont.pont and pont.du == date(2027, 5, 7) and pont.reprise is None


def test_periode_en_cours_gardee():
    d = donnees(datetime(2026, 12, 25, 10, 0, tzinfo=timezone.utc))
    assert d.periodes[0].nom == "Vacances de Noël"


def test_reponse_vide_refusee():
    with pytest.raises(ErreurValidation):
        CollecteurVacancesScolaires().valider({"results": []})
