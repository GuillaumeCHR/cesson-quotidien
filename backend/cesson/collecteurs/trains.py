"""Prochains trains à la halte de Cesson-Sévigné (code 87334490), directions Rennes et Vitré.

Deux sources SNCF, sous Licence Ouverte (transport.data.gouv.fr) :

- le GTFS national, pour les horaires prévus. Il pèse 5 à 6 Mo compressé (84 Mo de stop_times) :
  on n'en extrait qu'une fois par jour les seuls trains qui desservent Cesson, gardés dans un
  petit fichier d'état (`trains_horaires.json`) ;
- le GTFS-RT national (trip updates), pour le temps réel : retard, suppression, arrêt non
  desservi. Il ne couvre que les trains en circulation ou partant dans l'heure, et la SNCF ne
  garantit pas sa couverture pour les TER : un train absent du flux est publié comme « horaire
  théorique », jamais comme « à l'heure ».

Cadence : toutes les 2 min en journée. Le téléphone ne télécharge jamais le flux national.
"""

from __future__ import annotations

import csv
import io
import json
import re
import zipfile
from datetime import date, datetime, timedelta, timezone
from pathlib import Path
from typing import Any
from zoneinfo import ZoneInfo

import httpx
from google.protobuf.message import DecodeError
from google.transit import gtfs_realtime_pb2 as rt

from ..commun.collecteur import Collecteur, ErreurValidation, Resultat, obtenir
from ..commun.modele import Coord, Depart, Lieu, Mode, Modele, StatutDepart
from ..commun.publication import Publieur, ecrire_atomique
from ..commun.sante import Sante

URL_GTFS = "https://eu.ftp.opendatasoft.com/sncf/plandata/Export_OpenData_SNCF_GTFS_NewTripId.zip"
URL_RT = "https://proxy.transport.data.gouv.fr/resource/sncf-gtfs-rt-trip-updates"
ARRET = "StopPoint:OCETrain TER-87334490"
PARIS = ZoneInfo("Europe/Paris")
FICHIER_HORAIRES = "trains_horaires.json"
JOURS_GARDES = 8  # le fichier d'état couvre une semaine : une panne du GTFS ne prive pas de trains
AGE_MAX_HORAIRES = timedelta(hours=20)
PAR_DIRECTION = 4
HORIZON = timedelta(hours=24)
# Un départ affiché jusqu'à 2 min après son heure : le train peut être encore à quai.
TOLERANCE_PASSE = timedelta(minutes=2)

HALTE = Lieu(
    id="sncf:87334490",
    nom="Halte de Cesson-Sévigné",
    type="gare",
    coord=Coord(lat=48.11281, lon=-1.60211),
    adresse="Chemin du Bois de la Justice, Cesson-Sévigné",
    source="SNCF",
    url="https://www.ter.sncf.com/bretagne/se-deplacer/gares/cesson-sevigne-87334490",
)


class Direction(Modele):
    id: str  # rennes, est
    libelle: str


DIRECTIONS = {"rennes": Direction(id="rennes", libelle="Vers Rennes"),
              "est": Direction(id="est", libelle="Vers Vitré, Laval, Le Mans")}


class DepartTrain(Depart):
    numero: str
    direction: str  # id de Direction
    retard_min: int | None = None


class DonneesTrains(Modele):
    halte: Lieu
    directions: list[Direction]
    departs: list[DepartTrain]
    # Faux si le flux temps réel est injoignable, ou si aucun train de l'heure à venir n'y figure :
    # l'app prévient alors que les horaires affichés sont ceux prévus.
    temps_reel_disponible: bool


# --- Extraction des horaires prévus depuis le GTFS national ---------------------------------------

def numero_train(trip_id: str) -> str | None:
    m = re.match(r"OCESN(\d+)", trip_id)
    return m.group(1) if m else None


def extraire_horaires(zip_gtfs: bytes, aujourdhui: date) -> dict:
    """Trains qui desservent la halte, avec leurs dates de circulation sur [aujourdhui, +8 j]."""
    try:
        z = zipfile.ZipFile(io.BytesIO(zip_gtfs))
    except zipfile.BadZipFile as e:
        raise ErreurValidation(f"GTFS illisible : {e}") from e

    def lignes(nom: str):
        with z.open(nom) as f:
            yield from csv.DictReader(io.TextIOWrapper(f, encoding="utf-8-sig"))

    # Deux passages sur stop_times : d'abord les trains concernés, puis leurs arrêts (dans l'ordre).
    trains = {r["trip_id"]: r for r in lignes("stop_times.txt") if r["stop_id"] == ARRET}
    if not trains:
        raise ErreurValidation("aucun train ne dessert la halte de Cesson dans le GTFS")
    arrets: dict[str, list[dict]] = {t: [] for t in trains}
    for r in lignes("stop_times.txt"):
        if r["trip_id"] in arrets:
            arrets[r["trip_id"]].append(r)
    voyages = {r["trip_id"]: r for r in lignes("trips.txt") if r["trip_id"] in trains}
    lignes_tc = {r["route_id"]: r for r in lignes("routes.txt")}
    noms = {r["stop_id"]: r["stop_name"] for r in lignes("stops.txt")}
    fin = aujourdhui + timedelta(days=JOURS_GARDES)
    services = {v["service_id"] for v in voyages.values()}
    dates: dict[str, list[str]] = {}
    for r in lignes("calendar_dates.txt"):
        if r["service_id"] in services:
            d = datetime.strptime(r["date"], "%Y%m%d").date()
            if aujourdhui - timedelta(days=1) <= d <= fin:  # la veille : trains après minuit
                jours = dates.setdefault(r["service_id"], [])
                if r["exception_type"] == "1":
                    jours.append(r["date"])
                elif r["date"] in jours:
                    jours.remove(r["date"])

    resultat = []
    for trip_id, ici in trains.items():
        v = voyages.get(trip_id)
        if v is None or not dates.get(v["service_id"]) or ici.get("pickup_type") == "1":
            continue  # ne circule pas sur la période, ou ne prend pas de voyageurs ici
        suite = sorted(arrets[trip_id], key=lambda r: int(r["stop_sequence"]))
        k = next(i for i, r in enumerate(suite) if r["stop_id"] == ARRET)
        if k == len(suite) - 1:
            continue  # terminus : personne n'y monte
        destination = noms.get(suite[-1]["stop_id"], "")
        resultat.append({
            "trip_id": trip_id,
            "numero": numero_train(trip_id) or v.get("trip_headsign", ""),
            "ligne": lignes_tc.get(v["route_id"], {}).get("route_short_name") or "TER",
            "destination": destination,
            "direction": "rennes" if destination.startswith("Rennes") else "est",
            "depart": ici["departure_time"],
            "arrets_avant": [r["stop_id"] for r in suite[:k]],
            "dates": sorted(dates[v["service_id"]]),
        })
    if not resultat:
        raise ErreurValidation("aucun départ de Cesson sur les 8 prochains jours dans le GTFS")
    return {"genere_le": datetime.now(timezone.utc).isoformat(), "trains": resultat}


def heure_gtfs(jour: str, hms: str) -> datetime:
    """Heure GTFS (peut dépasser 24:00) : relative à « midi moins 12 h » du jour de service."""
    h, m, s = (int(x) for x in hms.split(":"))
    d = datetime.strptime(jour, "%Y%m%d")
    midi = datetime(d.year, d.month, d.day, 12, tzinfo=PARIS)
    return (midi - timedelta(hours=12) + timedelta(hours=h, minutes=m, seconds=s)).astimezone(timezone.utc)


# --- Temps réel ---------------------------------------------------------------------------------

def decoder_rt(contenu: bytes) -> rt.FeedMessage:
    flux = rt.FeedMessage()
    try:
        flux.ParseFromString(contenu)
    except DecodeError as e:
        raise ErreurValidation(f"GTFS-RT illisible : {e}") from e
    return flux


def index_rt(flux: rt.FeedMessage) -> dict[tuple[str, str], rt.TripUpdate]:
    """Mises à jour par (numéro de train, date de départ) : plus robuste que le trip_id, dont la
    partie version change d'un export du GTFS à l'autre."""
    index = {}
    for e in flux.entity:
        if e.HasField("trip_update"):
            tu = e.trip_update
            n = numero_train(tu.trip.trip_id)
            if n:
                index[(n, tu.trip.start_date)] = tu
    return index


def appliquer_rt(theorique: datetime, train: dict, tu: rt.TripUpdate | None
                 ) -> tuple[StatutDepart, datetime | None, int | None]:
    if tu is None:
        return StatutDepart.THEORIQUE, None, None
    if tu.trip.schedule_relationship == rt.TripDescriptor.CANCELED:
        return StatutDepart.SUPPRIME, None, None
    # Le retard vaut celui de la dernière mise à jour à Cesson ou avant (propagation GTFS-RT).
    rang = {s: i for i, s in enumerate(train["arrets_avant"] + [ARRET])}
    retenue = None
    for s in tu.stop_time_update:
        i = rang.get(s.stop_id)
        if i is not None and (retenue is None or i >= rang[retenue.stop_id]):
            retenue = s
    if retenue is None:
        return StatutDepart.THEORIQUE, None, None
    if retenue.stop_id == ARRET and retenue.schedule_relationship == rt.TripUpdate.StopTimeUpdate.SKIPPED:
        return StatutDepart.SUPPRIME, None, None
    evt = retenue.departure if retenue.HasField("departure") else retenue.arrival
    if retenue.stop_id == ARRET and evt.time:
        reelle = datetime.fromtimestamp(evt.time, timezone.utc)
    else:
        reelle = theorique + timedelta(seconds=evt.delay)
    retard = max(0, round((reelle - theorique).total_seconds() / 60))
    return (StatutDepart.RETARD if retard >= 1 else StatutDepart.A_L_HEURE), reelle, retard


# --- Collecteur ---------------------------------------------------------------------------------

class CollecteurTrains(Collecteur[DonneesTrains]):
    module = "trains"
    source = "SNCF (GTFS et GTFS-RT, transport.data.gouv.fr)"
    licence = "Licence Ouverte 2.0"
    url_source = "https://transport.data.gouv.fr/datasets/horaires-sncf"
    fraicheur = timedelta(minutes=2)
    modele_donnees = DonneesTrains

    def __init__(self, dossier_etat: Path | None = None) -> None:
        self.dossier_etat = dossier_etat
        self._maintenant = datetime.now(timezone.utc)

    def _horaires(self, client: httpx.Client, maintenant: datetime) -> dict:
        chemin = (self.dossier_etat or Path("etat")) / FICHIER_HORAIRES
        actuel = None
        if chemin.exists():
            actuel = json.loads(chemin.read_text(encoding="utf-8"))
            if maintenant - datetime.fromisoformat(actuel["genere_le"]) < AGE_MAX_HORAIRES:
                return actuel
        try:
            neuf = extraire_horaires(obtenir(client, URL_GTFS).content, maintenant.astimezone(PARIS).date())
        except Exception:
            if actuel is not None:
                return actuel  # GTFS indisponible : on garde la semaine déjà extraite
            raise
        neuf["genere_le"] = maintenant.isoformat()
        ecrire_atomique(chemin, json.dumps(neuf, ensure_ascii=False) + "\n")
        return neuf

    def executer(self, publieur: Publieur, sante: Sante, client: httpx.Client | None = None,
                 maintenant: datetime | None = None) -> Resultat:
        # Le fichier d'horaires extrait vit avec l'état de santé, hors du dossier publié.
        if self.dossier_etat is None:
            self.dossier_etat = sante.chemin.parent
        self._maintenant = maintenant or datetime.now(timezone.utc)
        return super().executer(publieur, sante, client, self._maintenant)

    def telecharger(self, client: httpx.Client) -> Any:
        horaires = self._horaires(client, self._maintenant)
        try:
            flux = decoder_rt(obtenir(client, URL_RT).content)
        except Exception as e:  # le temps réel tombe souvent : on publie alors l'horaire prévu
            flux = None
            erreur_rt = str(e)
        else:
            erreur_rt = None
        return {"horaires": horaires, "flux": flux, "erreur_rt": erreur_rt}

    def valider(self, brut: Any) -> None:
        trains = brut["horaires"].get("trains")
        if not isinstance(trains, list) or not trains:
            raise ErreurValidation("horaires de Cesson vides")

    def convertir(self, brut: Any, maintenant: datetime) -> DonneesTrains:
        index = index_rt(brut["flux"]) if brut["flux"] is not None else {}
        departs: list[DepartTrain] = []
        dans_l_heure = trouves = 0
        for t in brut["horaires"]["trains"]:
            for jour in t["dates"]:
                theorique = heure_gtfs(jour, t["depart"])
                if not maintenant - TOLERANCE_PASSE - timedelta(hours=1) <= theorique <= maintenant + HORIZON:
                    continue
                tu = index.get((t["numero"], jour))
                statut, reelle, retard = appliquer_rt(theorique, t, tu)
                if (reelle or theorique) < maintenant - TOLERANCE_PASSE:
                    continue  # parti
                if theorique <= maintenant + timedelta(hours=1):
                    dans_l_heure += 1
                    trouves += tu is not None
                departs.append(DepartTrain(
                    arret=HALTE.id, ligne=t["ligne"], mode=Mode.TRAIN, destination=t["destination"],
                    heure_theorique=theorique, heure_reelle=reelle, statut=statut,
                    numero=t["numero"], direction=t["direction"], retard_min=retard,
                ))
        departs.sort(key=lambda d: d.heure_theorique)
        gardes, vus = [], {}
        for d in departs:
            if vus.get(d.direction, 0) < PAR_DIRECTION:
                gardes.append(d)
                vus[d.direction] = vus.get(d.direction, 0) + 1
        return DonneesTrains(halte=HALTE, directions=list(DIRECTIONS.values()), departs=gardes,
                             temps_reel_disponible=brut["flux"] is not None and (dans_l_heure == 0 or trouves > 0))

    def valide_jusqu_a(self, donnees: DonneesTrains, maintenant: datetime) -> datetime:
        # Un retard vieux de 10 min ne vaut plus rien : la carte passe alors en « données anciennes ».
        return maintenant + timedelta(minutes=10)
