"""Trains du retour : depuis une gare d'Ille-et-Vilaine, des départements voisins (44, 56, 22, 53, 50) ou de Paris,
les prochains trains qui s'arrêtent ensuite à Cesson-Sévigné ou à Rennes.

Sert la carte « Mon trajet en train » : l'utilisateur choisit sa gare de destination, l'aller part de la halte de
Cesson (module trains), le retour de cette gare. L'app reçoit toutes les gares et n'affiche que la sienne : le
serveur ne sait pas laquelle.

Mêmes sources que le module trains (GTFS national et GTFS-RT SNCF, Licence Ouverte), plus la liste des gares de
voyageurs (SNCF, Licence Ouverte) pour situer chaque gare dans son département. Extraction une fois par jour, gardée
dans l'état (`trains_retour_horaires.json`) ; temps réel à chaque collecte.
"""

from __future__ import annotations

import csv
import io
import json
import zipfile
from datetime import date, datetime, timedelta, timezone
from pathlib import Path
from typing import Any

import httpx

from ..commun.collecteur import Collecteur, ErreurValidation, Resultat, obtenir
from ..commun.modele import Depart, Mode, Modele, StatutDepart
from ..commun.publication import Publieur, ecrire_atomique
from ..commun.sante import Sante
from .lignes_star import cle_alphabetique
from .trains import (
    AGE_MAX_HORAIRES, JOURS_GARDES, PARIS, TOLERANCE_PASSE, URL_GTFS, URL_RT, appliquer_rt, decoder_rt, heure_gtfs,
    index_rt, numero_train,
)

URL_GARES = "https://ressources.data.sncf.com/api/explore/v2.1/catalog/datasets/gares-de-voyageurs/exports/json"
DEPARTEMENTS = ("35", "44", "56", "22", "53", "50", "75")
UIC_CESSON, UIC_RENNES = "87334490", "87471003"
ARRIVEES = {UIC_CESSON: "Cesson-Sévigné", UIC_RENNES: "Rennes"}
FICHIER_HORAIRES = "trains_retour_horaires.json"
HORIZON = timedelta(hours=12)
PAR_GARE = 6
MIN_GARES = 20


def uic(stop_id: str) -> str:
    """« StopPoint:OCETGV INOUI-87391003 » → « 87391003 »."""
    return stop_id.rsplit("-", 1)[-1]


def est_car(stop_id: str) -> bool:
    """Les cars TER partagent les gares : le retour n'affiche que des trains."""
    return "OCECar" in stop_id


def type_de_train(stop_id: str) -> str:
    """« StopPoint:OCETGV INOUI-87391003 » → « TGV INOUI » ; « Train TER » → « TER ». Plus parlant que le code de
    ligne du GTFS (« 401C »)."""
    t = stop_id.removeprefix("StopPoint:OCE").rsplit("-", 1)[0]
    return "TER" if t == "Train TER" else t or "Train"


class GareRetour(Modele):
    id: str  # code UIC
    nom: str
    departement: str


class DepartRetour(Depart):
    numero: str
    retard_min: int | None = None
    arrivee: str  # « Cesson-Sévigné » ou « Rennes » : le premier des deux où le train s'arrête ensuite
    heure_arrivee: datetime  # prévue, décalée du retard connu au départ


class DonneesTrainsRetour(Modele):
    gares: list[GareRetour]
    departs: list[DepartRetour]
    temps_reel_disponible: bool


def lire_gares(brut: Any) -> dict[str, dict]:
    """Code UIC → nom et département, pour les seules gares des départements retenus."""
    if not isinstance(brut, list):
        raise ErreurValidation("liste des gares SNCF : liste attendue")
    gares = {}
    for g in brut:
        insee = str(g.get("codeinsee") or "")
        if insee[:2] not in DEPARTEMENTS or not g.get("nom"):
            continue
        for code in str(g.get("codes_uic") or "").split(";"):
            if code.strip():
                gares[code.strip()] = {"nom": g["nom"], "departement": insee[:2]}
    return gares


def extraire(zip_gtfs: bytes, gares: dict[str, dict], aujourdhui: date) -> dict:
    """Trains qui, après une gare retenue, s'arrêtent à Cesson ou à Rennes ; dates sur [aujourdhui, +8 j]."""
    try:
        z = zipfile.ZipFile(io.BytesIO(zip_gtfs))
    except zipfile.BadZipFile as e:
        raise ErreurValidation(f"GTFS illisible : {e}") from e

    def lignes(nom: str):
        with z.open(nom) as f:
            yield from csv.DictReader(io.TextIOWrapper(f, encoding="utf-8-sig"))

    cibles = {r["trip_id"] for r in lignes("stop_times.txt") if uic(r["stop_id"]) in ARRIVEES and not est_car(r["stop_id"])}
    arrets: dict[str, list[dict]] = {t: [] for t in cibles}
    for r in lignes("stop_times.txt"):
        if r["trip_id"] in arrets:
            arrets[r["trip_id"]].append(r)
    voyages = {r["trip_id"]: r for r in lignes("trips.txt") if r["trip_id"] in cibles}
    noms = {r["stop_id"]: r["stop_name"] for r in lignes("stops.txt")}
    fin = aujourdhui + timedelta(days=JOURS_GARDES)
    services = {v["service_id"] for v in voyages.values()}
    dates: dict[str, list[str]] = {}
    for r in lignes("calendar_dates.txt"):
        if r["service_id"] in services:
            d = datetime.strptime(r["date"], "%Y%m%d").date()
            if aujourdhui - timedelta(days=1) <= d <= fin:
                jours = dates.setdefault(r["service_id"], [])
                if r["exception_type"] == "1":
                    jours.append(r["date"])
                elif r["date"] in jours:
                    jours.remove(r["date"])

    departs = []
    for trip_id, suite in arrets.items():
        v = voyages.get(trip_id)
        if v is None or not dates.get(v["service_id"]):
            continue
        suite.sort(key=lambda r: int(r["stop_sequence"]))
        codes = [uic(r["stop_id"]) for r in suite]
        for j, r in enumerate(suite):
            g = codes[j]
            if g not in gares or g == UIC_CESSON or r.get("pickup_type") == "1" or est_car(r["stop_id"]):
                continue
            # Première arrivée utile ensuite : Cesson ou Rennes ; depuis Rennes, seulement Cesson.
            k = next((i for i in range(j + 1, len(suite)) if codes[i] in ARRIVEES and codes[i] != g
                      and suite[i].get("drop_off_type") != "1"), None)
            if k is None:
                continue
            departs.append({
                "numero": numero_train(trip_id) or v.get("trip_headsign", ""),
                "ligne": type_de_train(r["stop_id"]),
                "destination": noms.get(suite[-1]["stop_id"], ""),
                "gare": g,
                "depart": r["departure_time"],
                "arrivee": codes[k],
                "heure_arrivee": suite[k]["arrival_time"],
                "arrets_avant": [x["stop_id"] for x in suite[:j]],
                "stop_id": r["stop_id"],
                "dates": sorted(dates[v["service_id"]]),
            })
    if not departs:
        raise ErreurValidation("aucun train vers Rennes ou Cesson depuis les gares retenues")
    utilisees = {d["gare"] for d in departs}
    return {
        "gares": [{"id": u, **gares[u]} for u in sorted(utilisees)],
        "departs": departs,
    }


class CollecteurTrainsRetour(Collecteur[DonneesTrainsRetour]):
    module = "trains_retour"
    source = "SNCF (GTFS, GTFS-RT et gares de voyageurs)"
    licence = "Licence Ouverte 2.0"
    url_source = "https://transport.data.gouv.fr/datasets/horaires-sncf"
    fraicheur = timedelta(minutes=10)
    modele_donnees = DonneesTrainsRetour

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
            gares = lire_gares(obtenir(client, URL_GARES, params={"select": "nom,codeinsee,codes_uic"}).json())
            neuf = extraire(obtenir(client, URL_GTFS).content, gares, maintenant.astimezone(PARIS).date())
        except Exception:
            if actuel is not None:
                return actuel  # source indisponible : on garde la semaine déjà extraite
            raise
        neuf["genere_le"] = maintenant.isoformat()
        ecrire_atomique(chemin, json.dumps(neuf, ensure_ascii=False) + "\n")
        return neuf

    def executer(self, publieur: Publieur, sante: Sante, client: httpx.Client | None = None,
                 maintenant: datetime | None = None) -> Resultat:
        if self.dossier_etat is None:
            self.dossier_etat = sante.chemin.parent
        self._maintenant = maintenant or datetime.now(timezone.utc)
        return super().executer(publieur, sante, client, self._maintenant)

    def telecharger(self, client: httpx.Client) -> Any:
        horaires = self._horaires(client, self._maintenant)
        try:
            flux = decoder_rt(obtenir(client, URL_RT).content)
        except Exception:  # le temps réel tombe souvent : horaires prévus
            flux = None
        return {"horaires": horaires, "flux": flux}

    def valider(self, brut: Any) -> None:
        h = brut["horaires"]
        if not isinstance(h.get("departs"), list) or not h["departs"]:
            raise ErreurValidation("aucun départ vers Rennes ou Cesson")
        if len(h.get("gares") or []) < MIN_GARES:
            raise ErreurValidation(f"seulement {len(h.get('gares') or [])} gare(s) : liste des gares à vérifier")

    def convertir(self, brut: Any, maintenant: datetime) -> DonneesTrainsRetour:
        index = index_rt(brut["flux"]) if brut["flux"] is not None else {}
        departs: list[DepartRetour] = []
        for t in brut["horaires"]["departs"]:
            for jour in t["dates"]:
                theorique = heure_gtfs(jour, t["depart"])
                if not maintenant - TOLERANCE_PASSE - timedelta(hours=1) <= theorique <= maintenant + HORIZON:
                    continue
                statut, reelle, retard = appliquer_rt(theorique, t, index.get((t["numero"], jour)), t["stop_id"])
                if (reelle or theorique) < maintenant - TOLERANCE_PASSE:
                    continue
                arrivee = heure_gtfs(jour, t["heure_arrivee"]) + timedelta(minutes=retard or 0)
                departs.append(DepartRetour(
                    arret=t["gare"], ligne=t["ligne"], mode=Mode.TRAIN, destination=t["destination"],
                    heure_theorique=theorique, heure_reelle=reelle, statut=statut, numero=t["numero"],
                    retard_min=retard, arrivee=ARRIVEES[t["arrivee"]], heure_arrivee=arrivee,
                ))
        departs.sort(key=lambda d: (d.arret, d.heure_theorique))
        gardes, vus = [], {}
        for d in departs:
            if vus.get(d.arret, 0) < PAR_GARE:
                gardes.append(d)
                vus[d.arret] = vus.get(d.arret, 0) + 1
        gares = sorted((GareRetour(**g) for g in brut["horaires"]["gares"]), key=lambda g: cle_alphabetique(g.nom))
        return DonneesTrainsRetour(gares=gares, departs=gardes, temps_reel_disponible=brut["flux"] is not None)

    def valide_jusqu_a(self, donnees: DonneesTrainsRetour, maintenant: datetime) -> datetime:
        return maintenant + timedelta(minutes=15)
