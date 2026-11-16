import csv
import io
import json
from datetime import date, datetime, timezone
from pathlib import Path

import httpx
import pytest
import respx

from cesson.collecteurs.atmo import (BASE, CollecteurEpisodes, CollecteurPollens, CollecteurQualiteAir,
                                     DonneesEpisodes, DonneesQualiteAir, EtatEpisode, lire_identifiants)
from cesson.commun.collecteur import ErreurSource, ErreurValidation
from cesson.commun.modele import Etat, FichierModule, NiveauAlerte
from cesson.commun.publication import Publieur
from cesson.commun.sante import Sante

# Réponses réelles d'Atmo Data du 16 nov. 2026 (indice de Rennes Métropole, pollens de Cesson,
# épisodes d'Ille-et-Vilaine en CSV).
FIXTURES = Path(__file__).parent / "fixtures" / "atmo"
INDICES = json.loads((FIXTURES / "indices_rennes_metropole.json").read_text())
POLLENS = json.loads((FIXTURES / "pollens_cesson.json").read_text())
EPISODES_CSV = (FIXTURES / "episodes_ille_et_vilaine.csv").read_text()
MAINTENANT = datetime(2026, 11, 16, 16, 30, tzinfo=timezone.utc)


def proprietes(geojson: dict) -> list[dict]:
    return [f["properties"] for f in geojson["features"]]


def episodes(remplacer: dict[tuple[str, str], str] | None = None) -> list[dict]:
    lignes = list(csv.DictReader(io.StringIO(EPISODES_CSV)))
    for l in lignes:
        etat = (remplacer or {}).get((l["date_ech"], l["code_pol"]))
        if etat:
            l["etat"] = etat
    return lignes


@pytest.fixture
def identifiants(tmp_path, monkeypatch):
    f = tmp_path / "atmo.env"
    f.write_text("utilisateur=essai\nmot_de_passe=secret-de-test\n")
    monkeypatch.setenv("CESSON_ATMO_IDENTIFIANTS", str(f))
    return f


# --- Indice ATMO ---------------------------------------------------------------------------------

def test_indice_reel_de_rennes_metropole():
    c = CollecteurQualiteAir()
    c.valider(proprietes(INDICES))
    d = c.convertir(proprietes(INDICES), MAINTENANT)
    assert d.zone == "Rennes Métropole"
    assert [(j.date, j.libelle, j.couleur) for j in d.jours] == [
        (date(2026, 11, 16), "Moyen", "#50ccaa"), (date(2026, 11, 17), "Moyen", "#50ccaa")]
    # L'ozone (sous-indice 2) fait l'indice, les autres sont à 1.
    assert d.jours[0].responsables == ["o3"]
    assert {s.nom: s.code for s in d.jours[0].sous_indices} == {"NO₂": 1, "O₃": 2, "PM10": 1, "PM2,5": 1, "SO₂": 1}


def test_le_lendemain_seul_le_jour_suivant_reste():
    d = CollecteurQualiteAir().convertir(proprietes(INDICES), datetime(2026, 11, 17, 8, tzinfo=timezone.utc))
    assert [j.date for j in d.jours] == [date(2026, 11, 17)]


@pytest.mark.parametrize("modif", [
    lambda p: p.update(code_qual=9),
    lambda p: p.update(coul_qual="vert"),
    lambda p: p.update(code_o3=None),
    lambda p: p.update(code_zone="35051"),
])
def test_indice_rejete(modif):
    brut = proprietes(json.loads(json.dumps(INDICES)))
    modif(brut[0])
    with pytest.raises(ErreurValidation):
        CollecteurQualiteAir().valider(brut)


def test_indice_vide_rejete():
    with pytest.raises(ErreurValidation):
        CollecteurQualiteAir().valider([])


# --- Pollens -------------------------------------------------------------------------------------

def test_pollens_reels_de_cesson():
    c = CollecteurPollens()
    c.valider(proprietes(POLLENS))
    d = c.convertir(proprietes(POLLENS), MAINTENANT)
    assert d.commune == "Cesson-Sévigné"
    assert [(j.date, j.libelle, j.alerte) for j in d.jours] == [
        (date(2026, 11, 16), "Très faible", False), (date(2026, 11, 17), "Très faible", False),
        (date(2026, 11, 18), "Très faible", False)]
    assert [e.nom for e in d.jours[0].especes] == ["Aulne", "Bouleau", "Olivier", "Graminées", "Armoise", "Ambroisie"]
    # À « très faible », l'API cite toutes les espèces : aucune n'est mise en avant.
    assert d.jours[0].responsables == []


def test_pollens_especes_responsables():
    brut = proprietes(json.loads(json.dumps(POLLENS)))
    brut[0].update(code_qual=4, code_gram=4.0, pollen_resp="GRAMINEE", alerte=True)
    d = CollecteurPollens().convertir(brut, MAINTENANT)
    assert (d.jours[0].libelle, d.jours[0].alerte, d.jours[0].responsables) == ("Élevé", True, ["gram"])


@pytest.mark.parametrize("modif", [
    lambda p: p.update(code_qual=8),
    lambda p: p.update(code_boul=1.5),
    lambda p: p.update(alerte="non"),
    lambda p: p.update(code_zone="35238"),
])
def test_pollens_rejetes(modif):
    brut = proprietes(json.loads(json.dumps(POLLENS)))
    modif(brut[0])
    with pytest.raises(ErreurValidation):
        CollecteurPollens().valider(brut)


# --- Épisodes de pollution -----------------------------------------------------------------------

def test_episodes_reels_sans_depassement():
    c = CollecteurEpisodes()
    c.valider(episodes())
    d = c.convertir(episodes(), MAINTENANT)
    assert d.zone == "Ille-et-Vilaine" and d.alerte is None
    assert [(j.date, j.etat) for j in d.jours] == [
        (date(2026, 11, 15), EtatEpisode.AUCUN), (date(2026, 11, 16), EtatEpisode.AUCUN),
        (date(2026, 11, 17), EtatEpisode.AUCUN)]
    assert [p.polluant for p in d.jours[1].polluants] == ["NO₂", "O₃", "PM10", "PM2,5"]
    assert d.mis_a_jour == datetime(2026, 11, 16, 14, 21, 40, 674000, tzinfo=timezone.utc)


def test_episode_demain_produit_une_alerte():
    d = CollecteurEpisodes().convertir(episodes({("2026-11-17", "5"): "INFORMATION ET RECOMMANDATION"}), MAINTENANT)
    assert d.jours[2].etat == EtatEpisode.INFORMATION
    assert d.alerte.niveau == NiveauAlerte.JAUNE
    assert d.alerte.titre == "Pollution PM10 demain : procédure d'information et de recommandation"


def test_episode_d_hier_ne_produit_pas_d_alerte():
    d = CollecteurEpisodes().convertir(episodes({("2026-11-15", "7"): "ALERTE"}), MAINTENANT)
    assert d.jours[0].etat == EtatEpisode.ALERTE and d.alerte is None


def test_la_procedure_la_plus_grave_l_emporte():
    d = CollecteurEpisodes().convertir(episodes({
        ("2026-11-16", "5"): "INFORMATION ET RECOMMANDATION",
        ("2026-11-17", "5"): "ALERTE SUR PERSISTANCE", ("2026-11-17", "6001"): "ALERTE SUR PERSISTANCE",
    }), MAINTENANT)
    assert d.alerte.niveau == NiveauAlerte.ORANGE
    assert d.alerte.titre == "Pollution PM10, PM2,5 demain : procédure d'alerte sur persistance"


@pytest.mark.parametrize("etat", ["VIGILANCE", "", None])
def test_etat_d_episode_inconnu_rejete(etat):
    brut = episodes()
    brut[0]["etat"] = etat
    with pytest.raises(ErreurValidation):
        CollecteurEpisodes().valider(brut)


# --- Connexion et identifiants -----------------------------------------------------------------------

def test_identifiants_absents(tmp_path, monkeypatch):
    monkeypatch.setenv("CESSON_ATMO_IDENTIFIANTS", str(tmp_path / "absent.env"))
    with pytest.raises(ErreurSource, match="illisibles"):
        lire_identifiants()


def test_identifiants_incomplets(tmp_path, monkeypatch):
    f = tmp_path / "atmo.env"
    f.write_text("utilisateur=essai\n")
    monkeypatch.setenv("CESSON_ATMO_IDENTIFIANTS", str(f))
    with pytest.raises(ErreurSource, match="incomplets"):
        lire_identifiants()


def executer(collecteur, publieur, sante, reponses):
    with respx.mock() as mock:
        login = mock.post(f"{BASE}/api/login").mock(return_value=reponses.pop("login"))
        for chemin, rep in reponses.items():
            mock.get(BASE + chemin).mock(return_value=rep)
        with httpx.Client() as client:
            r = collecteur.executer(publieur, sante, client, MAINTENANT)
    return r, login


def test_collecte_complete_avec_jeton(tmp_path, identifiants):
    publieur, sante = Publieur(tmp_path / "public"), Sante(tmp_path / "etat", url_alerte="")
    r, login = executer(CollecteurQualiteAir(), publieur, sante, {
        "login": httpx.Response(200, json={"token": "jwt-de-test"}),
        "/api/v2/data/indices/atmo": httpx.Response(200, json=INDICES),
    })
    assert r.etat == Etat.OK
    assert json.loads(login.calls[0].request.content) == {"username": "essai", "password": "secret-de-test"}
    f = FichierModule[DonneesQualiteAir].model_validate_json(publieur.chemin("qualite_air").read_text())
    assert f.meta.licence == "ODbL" and f.donnees.jours[0].libelle == "Moyen"


def test_episodes_lus_en_csv_avec_le_jeton(tmp_path, identifiants):
    publieur, sante = Publieur(tmp_path / "public"), Sante(tmp_path / "etat", url_alerte="")
    with respx.mock() as mock:
        mock.post(f"{BASE}/api/login").mock(return_value=httpx.Response(200, json={"token": "jwt-de-test"}))
        route = mock.get(f"{BASE}/api/v2/data/episodes/3jours").mock(
            return_value=httpx.Response(200, content=EPISODES_CSV.encode()))
        with httpx.Client() as client:
            r = CollecteurEpisodes().executer(publieur, sante, client, MAINTENANT)
    assert r.etat == Etat.OK
    requete = route.calls[0].request
    assert requete.headers["Authorization"] == "Bearer jwt-de-test"
    assert requete.url.params["format"] == "csv" and requete.url.params["code_zone"] == "35"
    f = FichierModule[DonneesEpisodes].model_validate_json(publieur.chemin("episodes_pollution").read_text())
    assert len(f.donnees.jours) == 3


def test_identifiants_refuses_sans_les_divulguer(tmp_path, identifiants):
    publieur, sante = Publieur(tmp_path / "public"), Sante(tmp_path / "etat", url_alerte="")
    r, _ = executer(CollecteurPollens(), publieur, sante, {
        "login": httpx.Response(401, json={"code": 401, "message": "Identifiants invalides."}),
    })
    assert r.etat == Etat.ERREUR and not r.publie
    assert "identifiants invalides" in r.erreur and "secret-de-test" not in r.erreur
