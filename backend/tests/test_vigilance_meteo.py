import json
from datetime import datetime, timezone
from pathlib import Path

import httpx
import pytest
import respx

from cesson.collecteurs.vigilance_meteo import (BASE, URL_JETON, CollecteurVigilanceMeteo, Couleur,
                                                DonneesVigilanceMeteo)
from cesson.commun.collecteur import ErreurValidation
from cesson.commun.modele import Etat, FichierModule, NiveauAlerte
from cesson.commun.publication import Publieur
from cesson.commun.sante import Sante

# Diffusion réelle de Météo-France du 16 nov. 2026 à 16h (départements 14, 29 et 35, zone côtière 3510) :
# l'Ille-et-Vilaine passe en jaune le 17 pour le vent, la pluie-inondation et les orages.
FIXTURES = Path(__file__).parent / "fixtures" / "meteofrance"
CARTE = json.loads((FIXTURES / "carte_vigilance.json").read_text())
TEXTES = json.loads((FIXTURES / "textes_vigilance.json").read_text())
SOIR_DU_16 = datetime(2026, 11, 16, 21, 0, tzinfo=timezone.utc)


def brut(carte=None, textes=None) -> dict:
    return {"cartevigilance": json.loads(json.dumps(carte or CARTE)), "textesvigilance": json.loads(json.dumps(textes or TEXTES))}


def domaine(b: dict, echeance: str, dep: str = "35") -> dict:
    periode = next(p for p in b["cartevigilance"]["product"]["periods"] if p["echeance"] == echeance)
    return next(d for d in periode["timelaps"]["domain_ids"] if d["domain_id"] == dep)


def colorer(b: dict, echeance: str, phenomene: str, couleur: int) -> dict:
    ph = next(p for p in domaine(b, echeance)["phenomenon_items"] if p["phenomenon_id"] == phenomene)
    ph["phenomenon_max_color_id"] = couleur
    for t in ph["timelaps_items"]:
        t["color_id"] = couleur
    return b


def test_diffusion_reelle_du_16():
    c = CollecteurVigilanceMeteo()
    c.valider(brut())
    d = c.convertir(brut(), SOIR_DU_16)
    assert d.departement == "Ille-et-Vilaine"
    aujourdhui, demain = d.jours
    assert (aujourdhui.echeance, aujourdhui.couleur, aujourdhui.phenomenes) == ("J", Couleur.VERT, [])
    assert demain.couleur == Couleur.JAUNE
    assert [p.nom for p in demain.phenomenes] == ["vent violent", "pluie-inondation", "orages"]
    vent = demain.phenomenes[0]
    # Vent en jaune jusqu'à 8h, puis de 15h à 20h (heure de Paris) ; les plages vertes ne sont pas gardées.
    assert [(p.debut.hour, p.fin.hour) for p in vent.plages] == [(23, 7), (14, 19)]


def test_alerte_jaune_pour_demain():
    a = CollecteurVigilanceMeteo().convertir(brut(), SOIR_DU_16).alerte
    assert a.niveau == NiveauAlerte.JAUNE
    assert a.titre == "Vigilance jaune vent violent, pluie-inondation, orages"
    assert a.id == "vigilance:35:2026-11-17:jaune:1-2-3"
    assert (a.debut, a.fin) == (datetime(2026, 11, 16, 23, tzinfo=timezone.utc), datetime(2026, 11, 17, 19, tzinfo=timezone.utc))


def test_seul_le_bulletin_d_ille_et_vilaine_est_garde():
    textes = CollecteurVigilanceMeteo().convertir(brut(), SOIR_DU_16).textes
    assert [t.titre for t in textes] == ["Aggravation possible du niveau de Vigilance"]
    assert "aggravation vers le niveau orange ne peut être exclue" in textes[0].texte
    assert "Bretagne" in textes[0].texte  # pas le texte du Calvados


def test_l_orange_l_emporte_sur_le_jaune():
    d = CollecteurVigilanceMeteo().convertir(colorer(brut(), "J1", "3", 3), SOIR_DU_16)
    assert d.jours[1].couleur == Couleur.ORANGE
    assert d.jours[1].phenomenes[0].nom == "orages"
    assert (d.alerte.niveau, d.alerte.titre) == (NiveauAlerte.ORANGE, "Vigilance orange orages")


def test_les_crues_ne_declenchent_pas_l_alerte():
    b = brut()
    for ph in domaine(b, "J1")["phenomenon_items"]:
        if ph["phenomenon_id"] != "4":
            ph["phenomenon_max_color_id"] = 1
            for t in ph["timelaps_items"]:
                t["color_id"] = 1
    crues = next(p for p in domaine(b, "J1")["phenomenon_items"] if p["phenomenon_id"] == "4")
    crues.update(phenomenon_max_color_id=2, timelaps_items=[
        {"begin_time": "2026-11-16T23:00:00Z", "end_time": "2026-11-17T23:00:00Z", "color_id": 2}])
    d = CollecteurVigilanceMeteo().convertir(b, SOIR_DU_16)
    assert [p.nom for p in d.jours[1].phenomenes] == ["crues"]  # affichée dans la carte
    assert d.alerte is None  # Vigicrues porte l'alerte crue


def test_une_echeance_passee_ne_declenche_plus_l_alerte():
    b = colorer(brut(), "J", "1", 2)
    for p in domaine(b, "J1")["phenomenon_items"]:
        p["phenomenon_max_color_id"] = 1
        for t in p["timelaps_items"]:
            t["color_id"] = 1
    assert CollecteurVigilanceMeteo().convertir(b, SOIR_DU_16).alerte is not None
    assert CollecteurVigilanceMeteo().convertir(b, datetime(2026, 11, 16, 23, 30, tzinfo=timezone.utc)).alerte is None


@pytest.mark.parametrize("modif", [
    lambda b: b["cartevigilance"].pop("product"),
    lambda b: b["cartevigilance"]["product"]["periods"].pop(),
    lambda b: domaine(b, "J1")["phenomenon_items"][0].update(phenomenon_id="42"),
    lambda b: domaine(b, "J1")["phenomenon_items"][0].update(phenomenon_max_color_id=5),
    lambda b: domaine(b, "J1")["phenomenon_items"][0]["timelaps_items"][0].update(color_id=0),
    lambda b: domaine(b, "J", "35").update(domain_id="350"),
])
def test_validation_rejette(modif):
    b = brut()
    modif(b)
    with pytest.raises(ErreurValidation):
        CollecteurVigilanceMeteo().valider(b)


def test_collecte_avec_jeton_et_identifiant_entre_guillemets(tmp_path, monkeypatch):
    f = tmp_path / "meteofrance.env"
    f.write_text('identifiants_application="aWRlbnRpZmlhbnQ6c2VjcmV0"\n')  # guillemets copiés depuis le portail
    monkeypatch.setenv("CESSON_METEOFRANCE_IDENTIFIANTS", str(f))
    publieur, sante = Publieur(tmp_path / "public"), Sante(tmp_path / "etat", url_alerte="")
    with respx.mock() as mock:
        jeton = mock.post(URL_JETON).mock(return_value=httpx.Response(200, json={"access_token": "jwt-de-test", "expires_in": 3600}))
        carte = mock.get(f"{BASE}/cartevigilance/encours").mock(return_value=httpx.Response(200, json=CARTE))
        mock.get(f"{BASE}/textesvigilance/encours").mock(return_value=httpx.Response(200, json=TEXTES))
        with httpx.Client() as client:
            r = CollecteurVigilanceMeteo().executer(publieur, sante, client, SOIR_DU_16)
    assert r.etat == Etat.OK
    assert jeton.calls[0].request.headers["Authorization"] == "Basic aWRlbnRpZmlhbnQ6c2VjcmV0"
    assert carte.calls[0].request.headers["Authorization"] == "Bearer jwt-de-test"
    f = FichierModule[DonneesVigilanceMeteo].model_validate_json(publieur.chemin("vigilance_meteo").read_text())
    assert f.meta.source == "Météo-France" and f.donnees.alerte.niveau == NiveauAlerte.JAUNE


def test_identifiant_refuse_sans_le_divulguer(tmp_path, monkeypatch):
    f = tmp_path / "meteofrance.env"
    f.write_text("identifiants_application=c2VjcmV0LWRlLXRlc3Q=\n")
    monkeypatch.setenv("CESSON_METEOFRANCE_IDENTIFIANTS", str(f))
    publieur, sante = Publieur(tmp_path / "public"), Sante(tmp_path / "etat", url_alerte="")
    with respx.mock() as mock:
        # Le pare-feu de Météo-France répond 200 avec une page HTML quand la requête est mal formée.
        mock.post(URL_JETON).mock(return_value=httpx.Response(200, html="<title>Request Rejected</title>"))
        with httpx.Client() as client:
            r = CollecteurVigilanceMeteo().executer(publieur, sante, client, SOIR_DU_16)
    assert r.etat == Etat.ERREUR and not r.publie
    assert "c2VjcmV0LWRlLXRlc3Q" not in r.erreur and "text/html" in r.erreur
