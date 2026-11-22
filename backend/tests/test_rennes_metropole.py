import httpx
import pytest
import respx

from cesson.commun.collecteur import ErreurSource
from cesson.commun.rennes_metropole import obtenir_rennes

URL = "https://data.rennesmetropole.fr/api/explore/v2.1/catalog/datasets/x/exports/json"


@pytest.fixture
def cle(tmp_path, monkeypatch):
    f = tmp_path / "rennes.env"
    f.write_text("cle_api=0123abcd\n")
    monkeypatch.setenv("CESSON_RENNES_IDENTIFIANTS", str(f))
    monkeypatch.setattr("cesson.commun.collecteur.time.sleep", lambda s: None)


def appels(mock):
    return [c.request.headers.get("Authorization") for c in mock.calls]


def test_avec_cle(cle):
    with respx.mock() as mock, httpx.Client() as client:
        mock.get(URL).mock(return_value=httpx.Response(200, json=[]))
        obtenir_rennes(client, URL)
        assert appels(mock) == ["Apikey 0123abcd"]


def test_sans_fichier_appel_anonyme(tmp_path, monkeypatch):
    monkeypatch.setenv("CESSON_RENNES_IDENTIFIANTS", str(tmp_path / "absent.env"))
    with respx.mock() as mock, httpx.Client() as client:
        mock.get(URL).mock(return_value=httpx.Response(200, json=[]))
        obtenir_rennes(client, URL)
        assert appels(mock) == [None]


@pytest.mark.parametrize("statut", [401, 429])
def test_cle_refusee_ou_epuisee_repli_anonyme(cle, statut):
    with respx.mock() as mock, httpx.Client() as client:
        mock.get(URL).mock(side_effect=lambda req: httpx.Response(
            statut if "Authorization" in req.headers else 200, json=[]))
        assert obtenir_rennes(client, URL).status_code == 200
        assert appels(mock)[-1] is None


def test_panne_du_portail_sans_second_essai_anonyme(cle):
    with respx.mock() as mock, httpx.Client() as client:
        mock.get(URL).mock(return_value=httpx.Response(503))
        with pytest.raises(ErreurSource):
            obtenir_rennes(client, URL)
        assert set(appels(mock)) == {"Apikey 0123abcd"}
