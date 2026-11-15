from datetime import date, datetime, timezone
from pathlib import Path

import httpx
import pytest
import respx

from cesson.collecteurs.cinema import (URL_SITE, CollecteurCinema, DonneesCinema, grille_du_mois, grilles_du_site, id_drive,
                                      lire_grille, nettoyer_titre)
from cesson.commun.collecteur import ErreurValidation
from cesson.commun.modele import Etat, FichierModule
from cesson.commun.publication import Publieur
from cesson.commun.sante import Sante

# Grille réelle publiée par le cinéma Le Sévigné pour novembre 2026 (PDF tiré d'un tableur).
PDF = (Path(__file__).parent / "fixtures" / "cinema" / "grille_novembre_2026.pdf").read_bytes()
MAINTENANT = datetime(2026, 11, 15, 10, 0, tzinfo=timezone.utc)


def films():
    g = lire_grille(PDF, date(2026, 11, 15))
    return {nettoyer_titre(f["titre"])[0]: f for f in g["films"]}, g


def heures(f):
    return sorted(s.debut.strftime("%d/%m %Hh%M") for s in f["seances"])


def test_toutes_les_seances_au_bon_jour():
    f, _ = films()
    assert len(f) == 17
    assert heures(f["L’affaire Marie-Claire"]) == ["04/11 20h30", "06/11 20h30", "07/11 15h00", "08/11 17h30", "10/11 20h30"]
    assert heures(f["La gradiva"]) == ["12/11 20h30", "14/11 20h30", "15/11 20h30"]
    # Semaine à cheval sur deux mois : le « M 1 » est le 1er décembre.
    assert "01/12 20h30" in heures(f["Un bon petit soldat"])


def test_titres_sur_deux_lignes_et_etiquettes():
    f, _ = films()
    assert "Shaun le mouton : la bêêête d’Halloween" in f
    assert "La bataille de Gaulle – Partie 2 : J’écris ton nom" in f
    assert nettoyer_titre("L’affaire Marie-Claire Sortie Nationale") == ("L’affaire Marie-Claire", False, True)
    assert nettoyer_titre("Hope – VO") == ("Hope", True, False)


def test_tarif_particulier_et_note():
    f, g = films()
    moulin = {s.debut.strftime("%d/%m %Hh%M"): s for s in f["Moulin"]["seances"]}
    assert moulin["21/11 14h30"].tarif == "4,50 €" and moulin["20/11 20h30"].tarif is None
    assert f["Le monde à l’envers"]["seances"][0].tarif == "4,50 €"
    taxi = f["Taxi driver (1976)"]["seances"][0]
    assert taxi.note == "Présenté par Eddy Francheteau, programmateur du ciné-club"
    assert g["annonces"] == ["Cycle « Mon p’tit Ciné » en partenariat avec l’association « CinéMA 35 » : un dimanche matin "
                             "par mois, rendez-vous ciné des tout-p’tits brétilliens"]
    assert g["tarifs"].startswith("tarifs : plein 8€")


SITE = (Path(__file__).parent / "fixtures" / "cinema" / "site_extrait.html").read_text()


def test_grille_du_mois_lue_sur_le_site():
    grilles = grilles_du_site(SITE)
    assert grilles == [(date(2026, 11, 1), "https://drive.google.com/file/d/1-g688Vqf7EgjO3ImJRD43F3Bb1mLaZU3/view?usp=sharing")]
    decembre = grilles + [(date(2026, 12, 1), "https://drive.google.com/file/d/DEC/view")]
    assert grille_du_mois(decembre, date(2026, 11, 28)).endswith("1-g688Vqf7EgjO3ImJRD43F3Bb1mLaZU3/view?usp=sharing")
    assert grille_du_mois(decembre, date(2026, 12, 1)) == "https://drive.google.com/file/d/DEC/view"
    assert grille_du_mois([], date(2026, 12, 1)) is None


def test_lien_drive():
    assert id_drive("https://drive.google.com/file/d/1-g688Vqf7EgjO3ImJRD43F3Bb1mLaZU3/view") == "1-g688Vqf7EgjO3ImJRD43F3Bb1mLaZU3"


def test_publication_seances_a_venir(tmp_path):
    publieur, sante = Publieur(tmp_path / "public"), Sante(tmp_path / "etat", url_alerte="")
    with respx.mock() as mock:
        mock.get(URL_SITE).mock(return_value=httpx.Response(200, text=SITE))
        mock.get("https://drive.google.com/uc").mock(return_value=httpx.Response(200, content=PDF))
        with httpx.Client() as client:
            r = CollecteurCinema().executer(publieur, sante, client, MAINTENANT)
    assert r.etat == Etat.OK, r.erreur
    d = FichierModule[DonneesCinema].model_validate_json(publieur.chemin("cinema").read_text()).donnees
    assert d.salle == "Cinéma Le Sévigné"
    # Seulement les films qui ont encore une séance, le premier étant celui de la séance la plus proche.
    assert "Karma" not in {x.titre for x in d.films}
    assert all(s.debut.date() >= date(2026, 11, 15) for x in d.films for s in x.seances)
    debuts = [x.seances[0].debut for x in d.films]
    assert debuts == sorted(debuts)


def test_fichier_qui_n_est_pas_un_pdf(tmp_path):
    publieur, sante = Publieur(tmp_path / "public"), Sante(tmp_path / "etat", url_alerte="")
    with respx.mock() as mock:
        mock.get(URL_SITE).mock(return_value=httpx.Response(503))
        mock.get("https://drive.google.com/uc").mock(return_value=httpx.Response(200, text="<html>Connexion requise</html>"))
        with httpx.Client() as client:
            r = CollecteurCinema().executer(publieur, sante, client, MAINTENANT)
    assert r.etat == Etat.ERREUR
