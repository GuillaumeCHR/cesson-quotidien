import json
from datetime import date, datetime, timedelta, timezone
from pathlib import Path

import httpx
import respx
from google.transit import gtfs_realtime_pb2 as rt

from cesson.collecteurs.trains import URL_GTFS, URL_RT, heure_gtfs
from cesson.collecteurs.trains_retour import (
    FICHIER_HORAIRES, UIC_CESSON, UIC_RENNES, URL_GARES, CollecteurTrainsRetour, DonneesTrainsRetour, extraire,
    lire_gares, type_de_train,
)
from cesson.commun.modele import Etat, FichierModule, StatutDepart
from cesson.commun.publication import Publieur
from cesson.commun.sante import Sante

# Extrait réel du GTFS SNCF du 22 nov. 2026 : 70 trains qui vont à Rennes ou à Cesson le lundi 23, et un car TER.
DOSSIER = Path(__file__).parent / "fixtures"
GTFS = (DOSSIER / "sncf_gtfs_retour.zip").read_bytes()
GARES_BRUT = json.loads((DOSSIER / "sncf_gares.json").read_text())
GARES = lire_gares(GARES_BRUT)
# Lundi 23 nov. 2026, 7h à Paris.
MAINTENANT = datetime(2026, 11, 23, 6, 0, tzinfo=timezone.utc)
HORAIRES = extraire(GTFS, GARES, date(2026, 11, 23))
MONTPARNASSE = "87391003"


def test_gares_des_departements_retenus():
    assert {g["departement"] for g in GARES.values()} == {"35", "44", "56", "22", "53", "50", "75"}
    assert GARES[MONTPARNASSE]["nom"] == "Paris Montparnasse"


def test_extraction_trains_vers_rennes_ou_cesson():
    departs = HORAIRES["departs"]
    gares = {g["id"] for g in HORAIRES["gares"]}
    assert UIC_CESSON not in gares  # l'aller, c'est le module trains
    assert all(d["arrivee"] in (UIC_CESSON, UIC_RENNES) for d in departs)
    # Depuis Rennes, seuls les trains qui s'arrêtent ensuite à Cesson.
    assert all(d["arrivee"] == UIC_CESSON for d in departs if d["gare"] == UIC_RENNES)
    assert all("OCECar" not in d["stop_id"] for d in departs)
    paris = [d for d in departs if d["gare"] == MONTPARNASSE]
    assert paris and all(d["ligne"] in ("TGV INOUI", "OUIGO") for d in paris)


def test_type_de_train():
    assert type_de_train("StopPoint:OCETGV INOUI-87391003") == "TGV INOUI"
    assert type_de_train("StopPoint:OCETrain TER-87471003") == "TER"


def flux(*entites: rt.TripUpdate) -> bytes:
    f = rt.FeedMessage()
    f.header.gtfs_realtime_version = "2.0"
    for i, tu in enumerate(entites):
        e = f.entity.add()
        e.id = str(i)
        e.trip_update.CopyFrom(tu)
    return f.SerializeToString()


def test_collecte_avec_retard(tmp_path):
    prochain = min((d for d in HORAIRES["departs"] if "20261123" in d["dates"]
                    and heure_gtfs("20261123", d["depart"]) > MAINTENANT),
                   key=lambda d: heure_gtfs("20261123", d["depart"]))
    tu = rt.TripUpdate()
    tu.trip.trip_id = f"OCESN{prochain['numero']}F:autre-version"
    tu.trip.start_date = "20261123"
    s = tu.stop_time_update.add()
    s.stop_id = prochain["stop_id"]
    s.departure.delay = 600
    publieur, sante = Publieur(tmp_path / "public"), Sante(tmp_path / "etat", url_alerte="")
    with respx.mock() as mock:
        gtfs = mock.get(URL_GTFS).mock(return_value=httpx.Response(200, content=GTFS))
        mock.get(URL_GARES).mock(return_value=httpx.Response(200, json=GARES_BRUT))
        mock.get(URL_RT).mock(return_value=httpx.Response(200, content=flux(tu)))
        with httpx.Client() as client:
            r1 = CollecteurTrainsRetour().executer(publieur, sante, client, MAINTENANT)
            r2 = CollecteurTrainsRetour().executer(publieur, sante, client, MAINTENANT + timedelta(minutes=5))
    assert r1.etat == r2.etat == Etat.OK, (r1.erreur, r2.erreur)
    assert gtfs.call_count == 1 and (tmp_path / "etat" / FICHIER_HORAIRES).exists()
    f = FichierModule[DonneesTrainsRetour].model_validate_json(publieur.chemin("trains_retour").read_text())
    d = f.donnees
    assert d.temps_reel_disponible and len(d.gares) >= 20
    assert all(x.arrivee in ("Rennes", "Cesson-Sévigné") for x in d.departs)
    assert all(x.heure_arrivee > x.heure_theorique for x in d.departs)
    retarde = next(x for x in d.departs if x.numero == prochain["numero"] and x.arret == prochain["gare"])
    assert retarde.statut == StatutDepart.RETARD and retarde.retard_min == 10
    assert retarde.heure_arrivee == heure_gtfs("20261123", prochain["heure_arrivee"]) + timedelta(minutes=10)
    noms = [g.nom for g in d.gares]
    assert "Paris Montparnasse" in noms and noms.index("Laval") < noms.index("Vitré")


def test_retour_supprime_garde_jusqu_au_suivant(tmp_path):
    """Comme à l'aller : un train supprimé reste affiché jusqu'au départ du suivant depuis la même gare."""
    jour = [d for d in HORAIRES["departs"] if "20261123" in d["dates"] and heure_gtfs("20261123", d["depart"]) > MAINTENANT]
    gare = max({d["gare"] for d in jour}, key=lambda g: sum(d["gare"] == g for d in jour))  # la gare la plus desservie
    a, b = sorted((d for d in jour if d["gare"] == gare), key=lambda d: heure_gtfs("20261123", d["depart"]))[:2]
    tu = rt.TripUpdate()
    tu.trip.trip_id = f"OCESN{a['numero']}F:autre-version"
    tu.trip.start_date = "20261123"
    tu.trip.schedule_relationship = rt.TripDescriptor.CANCELED
    vide = rt.FeedMessage()
    vide.header.gtfs_realtime_version = "2.0"
    plein = rt.FeedMessage()
    plein.CopyFrom(vide)
    plein.entity.add(id="0").trip_update.CopyFrom(tu)
    depart_a, depart_b = heure_gtfs("20261123", a["depart"]), heure_gtfs("20261123", b["depart"])
    c = CollecteurTrainsRetour(dossier_etat=tmp_path)
    c.convertir({"horaires": HORAIRES, "flux": plein}, MAINTENANT)
    apres = depart_a + timedelta(minutes=3)
    if apres < min(depart_b, depart_a + timedelta(hours=1)):
        d = c.convertir({"horaires": HORAIRES, "flux": vide}, apres)
        x = next(x for x in d.departs if x.numero == a["numero"] and x.arret == gare)
        assert x.statut == StatutDepart.SUPPRIME
    fin = min(depart_b + timedelta(minutes=2), depart_a + timedelta(hours=1))
    d = c.convertir({"horaires": HORAIRES, "flux": vide}, fin + timedelta(seconds=1))
    assert not any(x.numero == a["numero"] and x.arret == gare for x in d.departs)
