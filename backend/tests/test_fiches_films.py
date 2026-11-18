import json
from datetime import date, datetime, timedelta, timezone

import httpx
import respx

from cesson.collecteurs.fiches_films import API, IMAGES, WIKIDATA, FichesFilms, choisir, liens_wikidata, titre_et_annee

AUJOURDHUI = date(2026, 11, 18)
MAINTENANT = datetime(2026, 11, 18, 18, 0, tzinfo=timezone.utc)


def r(ident, titre, sortie, popularite=1.0, original=None):
    return {"id": ident, "title": titre, "original_title": original or titre, "release_date": sortie,
            "popularity": popularite}


def test_annee_dans_le_titre():
    assert titre_et_annee("Taxi driver (1976)") == ("Taxi driver", 1976)
    assert titre_et_annee("15/18") == ("15/18", None)
    resultats = [r(1, "Taxi Driver", "2025-03-01", 50), r(103, "Taxi Driver", "1976-02-08", 10)]
    assert choisir(resultats, "Taxi driver", 1976, False, AUJOURDHUI) == 103


def test_sortie_nationale_prend_le_plus_recent():
    resultats = [r(10, "Moulin", "1998-05-01", 30), r(11, "Moulin", "2026-11-12", 2)]
    assert choisir(resultats, "Moulin", None, True, AUJOURDHUI) == 11
    # Sans sortie nationale aussi : le seul sorti depuis moins de six mois.
    assert choisir(resultats, "Moulin", None, False, AUJOURDHUI) == 11
    # Deux homonymes anciens : le nettement plus connu l'emporte.
    anciens = [r(10, "Moulin", "1998-05-01", 30), r(12, "Moulin", "2005-05-01", 2)]
    assert choisir(anciens, "Moulin", None, False, AUJOURDHUI) == 10


def test_doute_pas_de_fiche():
    resultats = [r(1, "Hope", "2019-01-01", 5), r(2, "Hope", "2024-01-01", 4)]
    assert choisir(resultats, "Hope", None, False, AUJOURDHUI) is None
    assert choisir([r(3, "Autre chose", "2020-01-01")], "Hope", None, False, AUJOURDHUI) is None


def test_homonymes_le_seul_recent():
    # « Le monde à l'envers » (Mon p'tit Ciné) : courts métrages de 2026, homonymes de 1998 et 2012 plus connus.
    resultats = [r(1711490, "Le Monde à l'envers", "2026-10-07", 0.36), r(572216, "Le monde à l'envers", "1998-08-14", 0.66),
                 r(162140, "Le monde à l'envers", "2012-01-01", 0.65)]
    assert choisir(resultats, "Le monde à l’envers", None, False, AUJOURDHUI) == 1711490


def test_titre_original_et_typographie():
    resultats = [r(5, "Shaun le Mouton : La Bêêête d'Halloween", "2026-10-15"), r(6, "Shaun le mouton", "2015-02-01", 40)]
    assert choisir(resultats, "Shaun le mouton : la bêêête d’Halloween", None, False, AUJOURDHUI) == 5
    assert choisir([r(7, "Le Jardin", "2020-01-01", original="Gradiva")], "Gradiva", None, False, AUJOURDHUI) == 7


def test_liens_wikidata():
    entite = {"sitelinks": {"frwiki": {"title": "Taxi Driver"}},
              "claims": {"P1265": [{"mainsnak": {"datavalue": {"value": "1405"}}}]}}
    assert liens_wikidata(entite) == {"wikipedia": "https://fr.wikipedia.org/wiki/Taxi_Driver",
                                      "allocine": "https://www.allocine.fr/film/fichefilm_gen_cfilm=1405.html"}


DETAIL = {
    "id": 103, "title": "Taxi Driver", "original_title": "Taxi Driver", "release_date": "1976-02-08", "runtime": 114,
    "overview": "Vétéran du Vietnam, Travis Bickle est chauffeur de taxi la nuit à New York.",
    "poster_path": "/affiche.jpg", "genres": [{"name": "Crime"}, {"name": "Drame"}],
    "credits": {"crew": [{"job": "Director", "name": "Martin Scorsese"}, {"job": "Writer", "name": "Paul Schrader"}],
                "cast": [{"name": n, "order": i} for i, n in enumerate(
                    ["Robert De Niro", "Jodie Foster", "Cybill Shepherd", "Harvey Keitel", "Peter Boyle"])]},
    "release_dates": {"results": [{"iso_3166_1": "US", "release_dates": [{"certification": "R"}]},
                                  {"iso_3166_1": "FR", "release_dates": [{"certification": ""}, {"certification": "12"}]}]},
    "external_ids": {"wikidata_id": "Q47221"},
}
WD = {"entities": {"Q47221": {"sitelinks": {"frwiki": {"title": "Taxi Driver"}},
                              "claims": {"P1265": [{"mainsnak": {"datavalue": {"value": "1405"}}}]}}}}


def test_fiche_complete_puis_cache(tmp_path, monkeypatch):
    jeton = tmp_path / "tmdb.env"
    jeton.write_text("jeton=eyJessai\n")
    monkeypatch.setenv("CESSON_TMDB_IDENTIFIANTS", str(jeton))
    public, etat = tmp_path / "public", tmp_path / "etat"
    films = [{"titre": "Taxi driver (1976)", "sortie_nationale": False}, {"titre": "Inconnu", "sortie_nationale": False}]
    with respx.mock() as mock:
        recherche = mock.get(f"{API}/search/movie").mock(side_effect=lambda req: httpx.Response(200, json={
            "results": [r(103, "Taxi Driver", "1976-02-08")] if "Taxi" in req.url.params["query"] else []}))
        detail = mock.get(f"{API}/movie/103").mock(return_value=httpx.Response(200, json=DETAIL))
        mock.get(WIKIDATA).mock(return_value=httpx.Response(200, json=WD))
        mock.get(IMAGES + "/affiche.jpg").mock(return_value=httpx.Response(200, content=b"\xff\xd8\xff jpeg"))
        with httpx.Client() as client:
            fiches = FichesFilms(public, etat).completer(client, films, MAINTENANT)
            assert recherche.calls[0].request.headers["Authorization"] == "Bearer eyJessai"
            assert recherche.calls[0].request.url.params["year"] == "1976"
            # Seconde collecte le même jour : tout vient du cache, aucun appel.
            FichesFilms(public, etat).completer(client, films, MAINTENANT + timedelta(hours=12))
            assert recherche.call_count == 2 and detail.call_count == 1
    f = fiches["Taxi driver (1976)"]
    assert "Inconnu" not in fiches
    assert (f.realisateurs, f.duree_min, f.genres, f.classification, f.annee) == (["Martin Scorsese"], 114, ["Crime", "Drame"], "12", 1976)
    assert f.distribution == ["Robert De Niro", "Jodie Foster", "Cybill Shepherd", "Harvey Keitel"]
    assert f.titre_original is None and f.affiche == "affiches/103.jpg"
    assert (public / "affiches" / "103.jpg").read_bytes().startswith(b"\xff\xd8")
    assert f.allocine.endswith("cfilm=1405.html") and f.wikipedia.endswith("/Taxi_Driver")
    assert json.loads((etat / "fiches_films.json").read_text())["titres"]["inconnu"]["tmdb"] is None


def test_sans_jeton_ni_reseau_les_fiches_en_cache_restent(tmp_path, monkeypatch):
    monkeypatch.setenv("CESSON_TMDB_IDENTIFIANTS", str(tmp_path / "absent.env"))
    etat = tmp_path / "etat"
    etat.mkdir()
    (etat / "fiches_films.json").write_text(json.dumps({
        "titres": {"taxidriver1976": {"tmdb": 103, "le": "2026-11-01T00:00:00+00:00"}},
        "fiches": {"103": {"fiche": {"tmdb": 103, "titre": "Taxi Driver", "page_tmdb": "https://www.themoviedb.org/movie/103"},
                           "le": "2026-11-01T00:00:00+00:00"}}}))
    with respx.mock(), httpx.Client() as client:  # aucun appel permis
        fiches = FichesFilms(tmp_path / "public", etat).completer(
            client, [{"titre": "Taxi driver (1976)"}, {"titre": "Autre"}], MAINTENANT)
    assert list(fiches) == ["Taxi driver (1976)"]


def test_fiche_forcee_ou_refusee(tmp_path, monkeypatch):
    monkeypatch.setenv("CESSON_TMDB_IDENTIFIANTS", str(tmp_path / "absent.env"))
    f = FichesFilms(tmp_path / "public", tmp_path / "etat", {"Hope": 0, "La gradiva": 42})
    assert f._identifiant(None, None, {"titre": "Hope"}, AUJOURDHUI, MAINTENANT) is None
    assert f._identifiant(None, None, {"titre": "La Gradiva"}, AUJOURDHUI, MAINTENANT) == 42
