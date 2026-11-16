from datetime import datetime, timedelta, timezone

from cesson.collecteurs.atmo import DonneesEpisodes
from cesson.commun.publication import empreinte

DONNEES = DonneesEpisodes(zone="Ille-et-Vilaine", mis_a_jour=datetime(2026, 11, 16, 14, 21, tzinfo=timezone.utc), jours=[])


def test_meme_contenu_meme_version():
    assert empreinte(DONNEES, "Atmo France / AASQA", "ODbL") == empreinte(DONNEES.model_copy(), "Atmo France / AASQA", "ODbL")


def test_un_changement_de_source_change_la_version():
    # Le 16/11, la mention « Atmo France / AASQA » ne parvenait pas aux téléphones : les données n'avaient pas bougé,
    # la version non plus, et l'app gardait son fichier avec l'ancienne source.
    assert empreinte(DONNEES, "Atmo France / Air Breizh", "ODbL") != empreinte(DONNEES, "Atmo France / AASQA", "ODbL")
    assert empreinte(DONNEES, "Météo-France", "Licence Ouverte 2.0") != empreinte(DONNEES, "Météo-France", "ODbL")


def test_les_donnees_changent_la_version():
    autre = DONNEES.model_copy(update={"mis_a_jour": DONNEES.mis_a_jour + timedelta(hours=1)})
    assert empreinte(DONNEES, "Atmo France / AASQA") != empreinte(autre, "Atmo France / AASQA")
