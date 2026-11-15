from datetime import date, datetime, timezone
from pathlib import Path

import pytest
import yaml

from cesson.collecteurs.equipements import CollecteurEquipements, DonneesEquipements
from cesson.commun.collecteur import ErreurValidation
from cesson.commun.modele import Etat, FichierModule
from cesson.commun.publication import Publieur
from cesson.commun.sante import Sante

SAISIE = Path(__file__).parents[1] / "saisie" / "equipements.yaml"
# Lundi 16 nov. 2026, 9h à Paris.
LUNDI = datetime(2026, 11, 16, 8, 0, tzinfo=timezone.utc)


def donnees(maintenant=LUNDI, brut=None) -> DonneesEquipements:
    c = CollecteurEquipements()
    b = brut if brut is not None else c.telecharger(None)
    c.valider(b)
    return c.convertir(b, maintenant)


def espace(d: DonneesEquipements, equipement: str, espace: str):
    e = next(e for e in d.equipements if e.lieu.id == f"equipement:{equipement}")
    return next(s for s in e.espaces if s.id == espace)


def heures(jour) -> list[str]:
    return [f"{p.debut:%H:%M}-{p.fin:%H:%M}" for p in jour.plages]


def test_saisie_versionnee_valide():
    d = donnees()
    assert {e.lieu.nom for e in d.equipements} == {"Piscine municipale", "Médiathèque", "Golf municipal",
                                                     "Base Sports Nature", "Déchèterie"}
    assert all(len(s.jours) == 14 for e in d.equipements for s in e.espaces)


def test_periode_scolaire_de_la_piscine():
    lundi = espace(donnees(), "piscine", "bassin_ludique").jours[0]
    assert lundi.date == date(2026, 11, 16) and lundi.periode == "Période scolaire"
    assert heures(lundi) == ["16:00-19:45"]
    assert lundi.plages[0].debut.isoformat() == "2026-11-16T16:00:00+01:00"


def test_vacances_l_emportent_sur_la_periode_scolaire():
    d = donnees(datetime(2027, 2, 22, 8, 0, tzinfo=timezone.utc))
    jour = espace(d, "piscine", "bassin_sportif").jours[0]
    assert jour.periode == "Vacances d'hiver" and heures(jour) == ["10:30-18:30"]


def test_fermeture_pour_vidange():
    d = donnees(datetime(2026, 12, 20, 8, 0, tzinfo=timezone.utc))
    jour = espace(d, "piscine", "bassin_ludique").jours[0]
    assert jour.connu and jour.plages == [] and jour.motif == "Fermeture pour vidange"


def test_hors_saison_n_est_pas_ferme():
    # Aucune période publiée pour la Base Sports Nature en novembre : « non communiqué », pas « fermé ».
    jour = espace(donnees(), "base_sports_nature", "eau_calme").jours[0]
    assert not jour.connu and jour.plages == []


def test_golf_hiver_et_remarque_de_periode():
    s = espace(donnees(), "golf", "parcours")
    assert heures(s.jours[0]) == ["09:30-18:00"] and "en ligne" in s.remarque
    samedi = espace(donnees(), "golf", "club_house").jours[5]
    assert samedi.date.weekday() == 5 and heures(samedi) == ["09:30-12:00", "13:00-16:45"]


def test_mediatheque_fermee_le_lundi():
    s = espace(donnees(), "mediatheque", "mediatheque")
    assert s.jours[0].connu and s.jours[0].plages == []
    assert heures(s.jours[2]) == ["10:00-12:30", "14:30-19:00"]


def test_decheterie_fermee_jeudi_apres_midi_et_jours_feries():
    s = espace(donnees(datetime(2026, 11, 9, 8, 0, tzinfo=timezone.utc)), "decheterie", "decheterie")
    assert heures(s.jours[3]) == ["09:00-12:00"]  # jeudi 12 nov.
    mercredi_11 = s.jours[2]
    assert mercredi_11.plages == [] and mercredi_11.motif == "Jour férié (Armistice 1918)"


@pytest.mark.parametrize("modif", [
    lambda b: b.pop("equipements"),
    lambda b: b["equipements"]["piscine"].pop("verifie_le"),
    lambda b: b["equipements"]["mediatheque"]["espaces"]["mediatheque"]["periodes"][0].update(horaires="Ma 9h-12h"),
    lambda b: b["equipements"]["piscine"]["exceptions"][0].update(au=date(2026, 11, 1)),
    lambda b: b["equipements"]["golf"]["espaces"]["parcours"]["periodes"][0].update(du="novembre"),
])
def test_saisie_fautive_refusee(modif):
    brut = yaml.safe_load(SAISIE.read_text(encoding="utf-8"))
    modif(brut)
    with pytest.raises(ErreurValidation):
        donnees(brut=brut)


def test_publication(tmp_path):
    publieur, sante = Publieur(tmp_path / "public"), Sante(tmp_path / "etat", url_alerte="")
    r = CollecteurEquipements().executer(publieur, sante, None, LUNDI)
    assert r.etat == Etat.OK, r.erreur
    f = FichierModule[DonneesEquipements].model_validate_json(publieur.chemin("equipements").read_text())
    assert f.donnees.equipements[0].horaires[0].opening_hours.startswith("Mo 16:00-19:45")
