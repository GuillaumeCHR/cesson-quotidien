from datetime import date, datetime, timezone

import pytest
import yaml

from cesson.collecteurs.collecte import SAISIE, CollecteurCollecte, DonneesCollecte, passages
from cesson.commun.collecteur import ErreurValidation
from cesson.commun.feries import feries, paques
from cesson.commun.modele import Etat, FichierModule
from cesson.commun.publication import Publieur
from cesson.commun.sante import Sante

# Toutes les dates lues sur le calendrier Rennes Métropole 2026/2027 de Cesson-Sévigné (PDF scanné).
GRIS_PDF = [
    "2026-10-12", "2026-10-26", "2026-11-09", "2026-11-23", "2026-12-07", "2026-12-21",
    "2027-01-11", "2027-01-25", "2027-02-08", "2027-02-22", "2027-03-08", "2027-03-22",
    "2027-04-05", "2027-04-19", "2027-05-03", "2027-05-18", "2027-05-31", "2027-06-14", "2027-06-28",
    "2027-07-12", "2027-07-26", "2027-08-09", "2027-08-23", "2027-09-06", "2027-09-20",
]
JAUNE_PDF = [
    "2026-10-14", "2026-10-28", "2026-11-12", "2026-11-25", "2026-12-09", "2026-12-23",
    "2027-01-13", "2027-01-27", "2027-02-10", "2027-02-24", "2027-03-10", "2027-03-24",
    "2027-04-07", "2027-04-21", "2027-05-05", "2027-05-20", "2027-06-02", "2027-06-16", "2027-06-30",
    "2027-07-15", "2027-07-28", "2027-08-11", "2027-08-25", "2027-09-08", "2027-09-22",
]


def saisie() -> dict:
    return yaml.safe_load(SAISIE.read_text(encoding="utf-8"))


def test_regle_redonne_tout_le_calendrier_papier():
    c = saisie()["collecte"]
    p = passages(c["flux"], c["valable_du"], c["valable_au"], c["decalage_jours_feries"])
    assert [x.date.isoformat() for x in p if x.flux == "ordures_menageres"] == GRIS_PDF
    assert [x.date.isoformat() for x in p if x.flux == "recyclables"] == JAUNE_PDF


def test_decalages_et_leur_motif():
    c = saisie()["collecte"]
    p = {(x.flux, x.date): x for x in passages(c["flux"], c["valable_du"], c["valable_au"], True)}
    assert p[("recyclables", date(2026, 11, 12))].decale_par == "Armistice 1918"
    assert p[("ordures_menageres", date(2027, 5, 18))].decale_par == "Lundi de Pentecôte"
    assert p[("recyclables", date(2027, 7, 15))].decale_par == "Fête nationale"
    assert p[("ordures_menageres", date(2027, 5, 3))].decale_par is None  # 1er mai un samedi : sans effet


def test_feries():
    assert paques(2027) == date(2027, 3, 28) and paques(2026) == date(2026, 4, 5)
    f = feries(2027)
    assert f[date(2027, 5, 17)] == "Lundi de Pentecôte" and f[date(2027, 5, 6)] == "Ascension"


def test_publication_huit_semaines(tmp_path):
    publieur, sante = Publieur(tmp_path / "public"), Sante(tmp_path / "etat", url_alerte="")
    maintenant = datetime(2026, 11, 15, 9, 0, tzinfo=timezone.utc)
    r = CollecteurCollecte().executer(publieur, sante, None, maintenant)
    assert r.etat == Etat.OK, r.erreur
    d = FichierModule[DonneesCollecte].model_validate_json(publieur.chemin("collecte").read_text()).donnees
    assert d.passages[0].date == date(2026, 11, 23) and d.passages[0].flux == "ordures_menageres"
    assert d.flux[0].regle == "le lundi des semaines paires"
    assert d.avis and "semaines 53 et 1" in d.avis[0].texte


def test_fin_de_validite_borne_les_passages(tmp_path):
    d = CollecteurCollecte().convertir(saisie(), datetime(2027, 9, 25, 9, 0, tzinfo=timezone.utc))
    assert d.passages == []  # calendrier 2027/2028 pas encore saisi : rien d'inventé


@pytest.mark.parametrize("modif", [
    lambda b: b.pop("collecte"),
    lambda b: b["collecte"].update(valable_au=date(2026, 1, 1)),
    lambda b: b["collecte"]["flux"][0].update(jour="Lundi"),
    lambda b: b["collecte"]["flux"][1].update(semaines="une sur deux"),
    lambda b: b["collecte"].update(verifie_le="hier"),
])
def test_saisie_fautive_refusee(modif):
    b = saisie()
    modif(b)
    with pytest.raises(ErreurValidation):
        CollecteurCollecte().valider(b)
