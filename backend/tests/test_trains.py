from datetime import date, datetime, timedelta, timezone
from pathlib import Path

import httpx
import pytest
import respx
from google.transit import gtfs_realtime_pb2 as rt

from cesson.collecteurs.trains import (
    ARRET, FICHIER_HORAIRES, URL_GTFS, URL_RT, CollecteurTrains, DonneesTrains, extraire_horaires, heure_gtfs,
)
from cesson.commun.collecteur import ErreurValidation
from cesson.commun.modele import Etat, FichierModule, StatutDepart
from cesson.commun.publication import Publieur
from cesson.commun.sante import Sante

# Extrait réel du GTFS SNCF du 14 nov. 2026, limité aux 63 trains qui desservent Cesson.
GTFS = (Path(__file__).parent / "fixtures" / "sncf_gtfs_cesson.zip").read_bytes()
# Lundi 16 nov. 2026, 7h à Paris.
MAINTENANT = datetime(2026, 11, 16, 6, 0, tzinfo=timezone.utc)
HORAIRES = extraire_horaires(GTFS, date(2026, 11, 16))


def prochains(n: int) -> list[dict]:
    """Les n prochains trains du lundi matin, tirés des vrais horaires."""
    lundi = [(heure_gtfs("20261116", t["depart"]), t) for t in HORAIRES["trains"] if "20261116" in t["dates"]]
    return [t for h, t in sorted(lundi, key=lambda x: x[0]) if h >= MAINTENANT][:n]


def flux(*entites: rt.TripUpdate) -> rt.FeedMessage:
    f = rt.FeedMessage()
    f.header.gtfs_realtime_version = "2.0"
    f.header.timestamp = int(MAINTENANT.timestamp())
    for i, tu in enumerate(entites):
        e = f.entity.add()
        e.id = str(i)
        e.trip_update.CopyFrom(tu)
    return f


def mise_a_jour(train: dict, *, retard_s: int | None = None, supprime=False, saute=False) -> rt.TripUpdate:
    tu = rt.TripUpdate()
    # Identifiant d'un autre export du GTFS : seul le numéro de train et la date doivent compter.
    tu.trip.trip_id = f"OCESN{train['numero']}F1187_F:TER:autre-version"
    tu.trip.start_date = "20261116"
    if supprime:
        tu.trip.schedule_relationship = rt.TripDescriptor.CANCELED
    if retard_s is not None:
        s = tu.stop_time_update.add()
        s.stop_id = train["arrets_avant"][-1]  # gare précédente : le retard se propage jusqu'à Cesson
        s.departure.delay = retard_s
    if saute:
        s = tu.stop_time_update.add()
        s.stop_id = ARRET
        s.schedule_relationship = rt.TripUpdate.StopTimeUpdate.SKIPPED
    return tu


def test_extraction_du_gtfs_reel():
    trains = HORAIRES["trains"]
    assert {t["direction"] for t in trains} == {"rennes", "est"}
    assert {t["ligne"] for t in trains} <= {"C4", "P4", "P41"}
    assert all(t["arrets_avant"] and t["dates"] for t in trains)
    assert all(t["destination"].startswith("Rennes") == (t["direction"] == "rennes") for t in trains)


def test_gtfs_sans_cesson_rejete():
    with pytest.raises(ErreurValidation):
        extraire_horaires(b"pas un zip", date(2026, 11, 16))


def test_heure_gtfs_apres_minuit():
    assert heure_gtfs("20261116", "07:05:00") == datetime(2026, 11, 16, 6, 5, tzinfo=timezone.utc)
    assert heure_gtfs("20261116", "24:30:00") == datetime(2026, 11, 16, 23, 30, tzinfo=timezone.utc)


def test_sans_temps_reel_tout_est_theorique():
    d = CollecteurTrains().convertir({"horaires": HORAIRES, "flux": None}, MAINTENANT)
    assert d.departs and all(x.statut == StatutDepart.THEORIQUE for x in d.departs)
    assert not d.temps_reel_disponible
    for direction in ("rennes", "est"):
        assert 1 <= sum(x.direction == direction for x in d.departs) <= 8
    heures = [x.heure_theorique for x in d.departs]
    assert heures == sorted(heures) and heures[0] >= MAINTENANT


def test_horaires_reguliers_pour_les_favoris():
    from cesson.commun.horaires import PARIS
    d = CollecteurTrains().convertir({"horaires": HORAIRES, "flux": None}, MAINTENANT)
    assert d.horaires and {h.direction for h in d.horaires} == {"rennes", "est"}
    cles = [(h.heure, h.direction) for h in d.horaires]
    assert cles == sorted(cles) and len(cles) == len(set(cles))
    jour = MAINTENANT.astimezone(PARIS).date()
    for h in d.horaires:
        assert len(h.heure) == 5 and h.dates == sorted(h.dates)
        assert all(jour <= x < jour + timedelta(days=7) for x in h.dates)
    # Chaque départ publié correspond à un horaire régulier, à la même heure de Paris.
    for x in d.departs:
        local = x.heure_theorique.astimezone(PARIS)
        assert any(h.heure == local.strftime("%H:%M") and h.direction == x.direction and local.date() in h.dates
                   for h in d.horaires)


def test_retard_suppression_et_arret_saute():
    a, b, c = prochains(3)
    f = flux(mise_a_jour(a, retard_s=7 * 60), mise_a_jour(b, supprime=True), mise_a_jour(c, saute=True))
    d = CollecteurTrains().convertir({"horaires": HORAIRES, "flux": f}, MAINTENANT)
    par_numero = {x.numero: x for x in d.departs}
    assert par_numero[a["numero"]].statut == StatutDepart.RETARD and par_numero[a["numero"]].retard_min == 7
    assert par_numero[a["numero"]].heure_reelle - par_numero[a["numero"]].heure_theorique == timedelta(minutes=7)
    assert par_numero[b["numero"]].statut == StatutDepart.SUPPRIME
    assert par_numero[c["numero"]].statut == StatutDepart.SUPPRIME
    assert d.temps_reel_disponible


def test_a_l_heure():
    (a,) = prochains(1)
    d = CollecteurTrains().convertir({"horaires": HORAIRES, "flux": flux(mise_a_jour(a, retard_s=20))}, MAINTENANT)
    x = next(x for x in d.departs if x.numero == a["numero"])
    assert x.statut == StatutDepart.A_L_HEURE and x.retard_min == 0


def test_collecte_garde_les_horaires_un_jour(tmp_path):
    publieur, sante = Publieur(tmp_path / "public"), Sante(tmp_path / "etat", url_alerte="")
    (a,) = prochains(1)
    with respx.mock() as mock:
        gtfs = mock.get(URL_GTFS).mock(return_value=httpx.Response(200, content=GTFS))
        mock.get(URL_RT).mock(return_value=httpx.Response(200, content=flux(mise_a_jour(a, retard_s=300))
                                                          .SerializeToString()))
        with httpx.Client() as client:
            r1 = CollecteurTrains().executer(publieur, sante, client, MAINTENANT)
            r2 = CollecteurTrains().executer(publieur, sante, client, MAINTENANT + timedelta(minutes=2))
    assert r1.etat == r2.etat == Etat.OK, (r1.erreur, r2.erreur)
    assert gtfs.call_count == 1  # le GTFS national n'est téléchargé qu'une fois par jour
    assert (tmp_path / "etat" / FICHIER_HORAIRES).exists()
    f = FichierModule[DonneesTrains].model_validate_json(publieur.chemin("trains").read_text())
    assert f.donnees.halte.nom == "Halte de Cesson-Sévigné"
    assert any(x.statut == StatutDepart.RETARD for x in f.donnees.departs)


def test_temps_reel_en_panne_publie_les_horaires_prevus(tmp_path, monkeypatch):
    monkeypatch.setattr("cesson.commun.collecteur.time.sleep", lambda s: None)  # pas d'attente entre essais
    publieur, sante = Publieur(tmp_path / "public"), Sante(tmp_path / "etat", url_alerte="")
    with respx.mock() as mock:
        mock.get(URL_GTFS).mock(return_value=httpx.Response(200, content=GTFS))
        mock.get(URL_RT).mock(return_value=httpx.Response(503))
        with httpx.Client() as client:
            r = CollecteurTrains().executer(publieur, sante, client, MAINTENANT)
    assert r.etat == Etat.OK
    f = FichierModule[DonneesTrains].model_validate_json(publieur.chemin("trains").read_text())
    assert not f.donnees.temps_reel_disponible


# --- Rémanence des retards et des suppressions ----------------------------------------------------

def test_train_supprime_garde_jusqu_au_suivant(tmp_path):
    """Un train supprimé reste affiché après son heure prévue, jusqu'au départ du train suivant vers la même
    direction (au plus une heure)."""
    a = prochains(1)[0]
    meme_direction = [t for t in prochains(20) if t["direction"] == a["direction"] and t is not a]
    suivant = heure_gtfs("20261116", meme_direction[0]["depart"])
    depart_a = heure_gtfs("20261116", a["depart"])
    f = flux(mise_a_jour(a, supprime=True))
    c = CollecteurTrains(dossier_etat=tmp_path)
    x = next(x for x in c.convertir({"horaires": HORAIRES, "flux": f}, MAINTENANT).departs if x.numero == a["numero"])
    attendu = min(suivant + timedelta(minutes=2), depart_a + timedelta(hours=1))
    assert x.statut == StatutDepart.SUPPRIME and x.visible_jusqu_a == attendu
    # 5 min après son horaire, le flux ne parle plus du train : il reste affiché, toujours supprimé.
    plus_tard = depart_a + timedelta(minutes=5)
    if plus_tard < attendu:
        d = c.convertir({"horaires": HORAIRES, "flux": flux()}, plus_tard)
        x = next(x for x in d.departs if x.numero == a["numero"])
        assert x.statut == StatutDepart.SUPPRIME
    # Après le départ du train suivant, il disparaît.
    d = c.convertir({"horaires": HORAIRES, "flux": flux()}, attendu + timedelta(seconds=1))
    assert all(x.numero != a["numero"] for x in d.departs)


def test_retard_garde_quand_le_flux_n_en_parle_plus(tmp_path):
    (a,) = prochains(1)
    depart_a = heure_gtfs("20261116", a["depart"])
    c = CollecteurTrains(dossier_etat=tmp_path)
    c.convertir({"horaires": HORAIRES, "flux": flux(mise_a_jour(a, retard_s=20 * 60))}, MAINTENANT)
    # 5 min après l'heure prévue, le flux a « oublié » le train, qui n'est pas parti : le retard reste.
    d = c.convertir({"horaires": HORAIRES, "flux": flux()}, depart_a + timedelta(minutes=5))
    x = next(x for x in d.departs if x.numero == a["numero"])
    assert x.statut == StatutDepart.RETARD and x.retard_min == 20
    assert x.visible_jusqu_a == depart_a + timedelta(minutes=22)
    # Parti (heure réelle + 2 min dépassée) : il disparaît.
    d = c.convertir({"horaires": HORAIRES, "flux": flux()}, depart_a + timedelta(minutes=23))
    assert all(x.numero != a["numero"] for x in d.departs)


def test_memoire_perimee_ignoree(tmp_path):
    (a,) = prochains(1)
    c = CollecteurTrains(dossier_etat=tmp_path)
    c.convertir({"horaires": HORAIRES, "flux": flux(mise_a_jour(a, retard_s=3 * 3600))}, MAINTENANT)
    assert (tmp_path / "trains_memoire.json").exists()
    # Plus de 90 min sans confirmation du flux : le retard n'est plus repris.
    d = c.convertir({"horaires": HORAIRES, "flux": flux()}, MAINTENANT + timedelta(minutes=91))
    assert all(x.statut == StatutDepart.THEORIQUE for x in d.departs if x.numero == a["numero"])


def test_les_trains_passes_ne_prennent_pas_la_place_des_prochains(tmp_path):
    a = prochains(1)[0]
    depart_a = heure_gtfs("20261116", a["depart"])
    c = CollecteurTrains(dossier_etat=tmp_path)
    c.convertir({"horaires": HORAIRES, "flux": flux(mise_a_jour(a, retard_s=40 * 60))}, MAINTENANT)
    d = c.convertir({"horaires": HORAIRES, "flux": flux()}, depart_a + timedelta(minutes=10))
    a_venir = [x for x in d.departs if x.direction == a["direction"] and x.heure_theorique >= depart_a + timedelta(minutes=10)]
    assert any(x.numero == a["numero"] for x in d.departs)
    assert len(a_venir) == 8  # les 8 prochains, en plus du train en retard
