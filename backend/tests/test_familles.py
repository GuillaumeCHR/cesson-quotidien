import json
from datetime import date, datetime, timezone
from pathlib import Path

import pytest

from cesson.collecteurs.familles import CollecteurFamilles, annee_proche, lire_menus
from cesson.commun.collecteur import ErreurValidation

# Réponse réelle du portail familles (15 nov. 2026), réduite aux actualités publiques, plus une actualité privée
# fictive ; menu de novembre 2026 des restaurants scolaires de Bourgchevreuil et Beausoleil.
F = Path(__file__).parent / "fixtures" / "familles"
ACTUS = json.loads((F / "actualites.json").read_text())
PDF = (F / "menu_novembre_2026.pdf").read_bytes()
LUNDI = datetime(2026, 11, 16, 8, 0, tzinfo=timezone.utc)


def donnees(maintenant=LUNDI, pdfs=None):
    c = CollecteurFamilles()
    brut = {"actualites": ACTUS, "pdfs": {"133": PDF} if pdfs is None else pdfs}
    c.valider(brut)
    return c.convertir(brut, maintenant)


def test_menu_du_jour_range_par_rubrique():
    d = donnees()
    assert d.restaurants == "Bourgchevreuil et Beausoleil"
    lundi = d.menus[0]
    assert lundi.jour == date(2026, 11, 16)
    assert lundi.hors_doeuvre == ["Carottes râpées au citron"]
    assert lundi.plat == ["Reblochonnade", "Salade verte"]
    assert lundi.dessert == ["Fruit frais"] and lundi.gouter == ["Lait", "Céréales"]
    mercredi = d.menus[2]
    assert mercredi.plat == ["Tortellinis à la Ricotta et aux épinard sauce tomate et basilic"]


def test_semaines_du_mois_et_suivante():
    jours, _ = lire_menus(PDF, date(2026, 11, 10))
    assert jours[0].jour == date(2026, 10, 26) and jours[-1].jour == date(2026, 12, 4)
    assert all(j.jour.weekday() < 5 for j in jours)
    assert all(j.laitier == ["Fromage"] for j in jours if not j.note)  # rien de la légende ne s'y glisse
    ferie = next(j for j in jours if j.note)
    assert ferie.jour == date(2026, 11, 11) and ferie.note == "11 novembre, Armistice 1918" and not ferie.plat
    assert d_menus_futurs()[0] == date(2026, 11, 16)


def d_menus_futurs():
    return [m.jour for m in donnees().menus]


def test_actualites_publiques_seulement():
    titres = [a.titre for a in donnees().actualites]
    assert "Actualité réservée aux familles" not in titres
    assert not any(t.lower().startswith("menu") for t in titres)  # le menu a sa propre carte
    assert titres[0] == "PAI - 2026-2027"  # datée et en cours : en tête
    pai = donnees().actualites[0]
    assert pai.lien.endswith("/news/108") and pai.resume.startswith("Si votre enfant")


def test_annee_au_tournant_de_l_annee():
    assert annee_proche(4, 1, date(2026, 12, 20)) == date(2027, 1, 4)
    assert annee_proche(28, 12, date(2027, 1, 3)) == date(2026, 12, 28)


def test_pdf_illisible_refuse():
    with pytest.raises(ErreurValidation):
        donnees(pdfs={"133": b"%PDF-1.4 vide"})
