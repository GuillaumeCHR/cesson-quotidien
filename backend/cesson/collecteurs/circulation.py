"""Conditions de circulation : rocade de Rennes à l'est (RN136, le long de Cesson) et axes de Cesson-Sévigné.

Source : « État du trafic en temps réel » de Rennes Métropole (données FCD, Licence Ouverte), mis à jour
chaque minute, un enregistrement par tronçon et par sens : vitesse moyenne et état (fluide, dense, saturé).

Le jeu ne nomme pas les échangeurs de la rocade : chaque tronçon est situé entre les deux portes les plus
proches (positions relevées dans OpenStreetMap). Le sens est déduit de la géométrie : en circulation à droite,
la rocade intérieure tourne dans le sens des aiguilles d'une montre.

Pour le plan de la carte, chaque tronçon garde aussi son tracé, simplifié (à quelques mètres près) et arrondi.
"""

from __future__ import annotations

import math
from datetime import datetime, timedelta
from typing import Any

import httpx

from ..commun.collecteur import Collecteur, ErreurValidation, obtenir
from ..commun.horaires import PARIS, heure_de_pointe
from ..commun.modele import Modele

URL = "https://data.rennesmetropole.fr/api/explore/v2.1/catalog/datasets/etat-du-trafic-en-temps-reel/exports/json"
ROCADE = "Route nationale 136"
INSEE_CESSON = 35051
CENTRE_RENNES = (48.1113, -1.6800)
# La rocade est : les tronçons à l'est de cette longitude (Porte des Longs Champs à Porte des Loges).
LONGITUDE_EST = -1.632

# Portes de la rocade est, dans le sens des aiguilles d'une montre (OpenStreetMap, highway=motorway_junction).
PORTES = [
    ("Porte des Longs Champs", 48.14118, -1.62782),
    ("Porte de Normandie", 48.14851, -1.60394),
    ("Porte de Tizé", 48.14200, -1.58561),
    ("Porte de la Valette", 48.11046, -1.58950),
    ("Porte de Beaulieu", 48.10206, -1.60857),
    ("Porte des Loges", 48.09204, -1.62684),
]

ETATS = {"freeFlow": "fluide", "heavy": "dense", "congested": "sature", "impossible": "bloque"}
# En ville, un tronçon est souvent jugé sur 1 ou 2 véhicules : une voiture arrêtée au feu suffit à le dire « dense ».
# Un ralentissement n'est retenu que si la fiabilité annoncée par la source atteint ce seuil (%).
FIABILITE_MIN = 70


def etat(t: dict) -> str:
    e = ETATS.get(t.get("trafficstatus"), "inconnu")
    if e in ("dense", "sature", "bloque") and (t.get("traveltimereliability") or 0) < FIABILITE_MIN:
        return "inconnu"
    return e
GRAVITE = ["inconnu", "fluide", "dense", "sature", "bloque"]


class Ralentissement(Modele):
    entre: str  # « Porte de Tizé et Porte de la Valette »
    etat: str
    vitesse: int | None = None


class Sens(Modele):
    id: str  # interieure, exterieure
    libelle: str
    etat: str
    vitesse: int | None = None  # moyenne pondérée par la longueur
    ralentissements: list[Ralentissement] = []


class Axe(Modele):
    nom: str
    etat: str
    vitesse: int | None = None


class Troncon(Modele):
    etat: str
    rocade: bool = False  # tracé plus épais sur le plan
    points: list[list[float]]  # [lon, lat], 5 décimales


class DonneesCirculation(Modele):
    mesure_le: datetime
    rocade_est: list[Sens]
    axes_cesson: list[Axe]  # du plus chargé au plus fluide
    # Tracés de la rocade est et des axes de Cesson, du moins au plus chargé : le plus grave se dessine par-dessus.
    troncons: list[Troncon] = []


TOLERANCE_TRACE = 0.00008  # degrés, de 6 à 9 m : invisible à l'échelle de la commune


def simplifier(coords: list[list[float]], tol: float = TOLERANCE_TRACE) -> list[list[float]]:
    """Douglas-Peucker sur les degrés (longitude corrigée de la latitude), puis arrondi à 5 décimales (~1 m)."""
    k = math.cos(math.radians(coords[0][1]))

    def garder(debut: int, fin: int, gardes: set[int]) -> None:
        (xa, ya), (xb, yb) = coords[debut], coords[fin]
        dx, dy = (xb - xa) * k, yb - ya
        n = math.hypot(dx, dy)
        pire, ip = 0.0, -1
        for i in range(debut + 1, fin):
            px, py = (coords[i][0] - xa) * k, coords[i][1] - ya
            d = abs(dx * py - dy * px) / n if n else math.hypot(px, py)
            if d > pire:
                pire, ip = d, i
        if pire > tol:
            gardes.add(ip)
            garder(debut, ip, gardes)
            garder(ip, fin, gardes)

    gardes = {0, len(coords) - 1}
    garder(0, len(coords) - 1, gardes)
    return [[round(coords[i][0], 5), round(coords[i][1], 5)] for i in sorted(gardes)]


def _longueur(coords: list[list[float]]) -> float:
    total = 0.0
    for (lo1, la1), (lo2, la2) in zip(coords, coords[1:]):
        total += math.hypot((lo2 - lo1) * 111_320 * math.cos(math.radians(la1)), (la2 - la1) * 110_540)
    return total


def _milieu(coords):
    return coords[len(coords) // 2]


def sens_horaire(coords: list[list[float]]) -> bool:
    """Vrai si le tronçon tourne autour de Rennes dans le sens des aiguilles d'une montre."""
    (lo_a, la_a), (lo_b, la_b), (lo_m, la_m) = coords[0], coords[-1], _milieu(coords)
    k = math.cos(math.radians(CENTRE_RENNES[0]))
    rx, ry = (lo_m - CENTRE_RENNES[1]) * k, la_m - CENTRE_RENNES[0]
    vx, vy = (lo_b - lo_a) * k, la_b - la_a
    return rx * vy - ry * vx < 0


def entre_portes(lon: float, lat: float) -> str:
    """Les deux portes consécutives qui encadrent ce point."""
    k = math.cos(math.radians(lat))
    d = [math.hypot((lon - plo) * k, lat - pla) for _, pla, plo in PORTES]
    i = min(range(len(PORTES)), key=d.__getitem__)
    voisins = [j for j in (i - 1, i + 1) if 0 <= j < len(PORTES)]
    j = min(voisins, key=d.__getitem__)
    a, b = sorted((i, j))
    return f"{PORTES[a][0]} et {PORTES[b][0]}"


def etat_global(troncons: list[tuple[str, float]]) -> str:
    """État d'un ensemble pondéré par la longueur : saturé dès 15 % saturés ; dense dès 20 % denses ou 5 % saturés."""
    connus = [(e, l) for e, l in troncons if e != "inconnu"]
    total = sum(l for _, l in connus)
    if not total:
        return "inconnu"
    part = lambda etats: sum(l for e, l in connus if e in etats) / total  # noqa: E731
    if part({"bloque"}) > 0.05:
        return "bloque"
    if part({"sature", "bloque"}) >= 0.15:
        return "sature"
    # Un bouchon court reste un bouchon : 5 % de tronçons saturés suffisent à dire « dense ».
    if part({"dense", "sature", "bloque"}) >= 0.20 or part({"sature", "bloque"}) >= 0.05:
        return "dense"
    return "fluide"


def vitesse_moyenne(troncons: list[tuple[int | None, float]]) -> int | None:
    v = [(s, l) for s, l in troncons if s is not None and s > 0]
    total = sum(l for _, l in v)
    return round(sum(s * l for s, l in v) / total) if total else None


class CollecteurCirculation(Collecteur[DonneesCirculation]):
    module = "circulation"
    source = "Rennes Métropole, état du trafic en temps réel"
    licence = "Licence Ouverte 2.0 ; positions des portes : OpenStreetMap (ODbL)"
    url_source = "https://data.rennesmetropole.fr/explore/dataset/etat-du-trafic-en-temps-reel/"
    fraicheur = timedelta(minutes=30)  # la plus longue des cadences (la nuit) ; voir deploy/crontab
    modele_donnees = DonneesCirculation

    def telecharger(self, client: httpx.Client) -> Any:
        r = obtenir(client, URL, params={
            "where": f'denomination="{ROCADE}" or insee={INSEE_CESSON}',
            "select": "datetime,averagevehiclespeed,trafficstatus,traveltimereliability,denomination,insee,geo_shape",
        })
        try:
            return r.json()
        except ValueError as e:
            raise ErreurValidation(f"réponse non JSON : {e}") from e

    def valider(self, brut: Any) -> None:
        if not isinstance(brut, list):
            raise ErreurValidation("liste de tronçons attendue")
        rocade = [t for t in brut if t.get("denomination") == ROCADE]
        if len(rocade) < 30:
            raise ErreurValidation(f"seulement {len(rocade)} tronçon(s) de rocade")
        for t in brut:
            coords = ((t.get("geo_shape") or {}).get("geometry") or {}).get("coordinates")
            if not isinstance(coords, list) or len(coords) < 2 or not isinstance(t.get("datetime"), str):
                raise ErreurValidation("tronçon sans géométrie ou sans date")

    def convertir(self, brut: Any, maintenant: datetime) -> DonneesCirculation:
        mesure = max(datetime.fromisoformat(t["datetime"]) for t in brut)
        sens = {"interieure": [], "exterieure": []}
        for t in brut:
            if t["denomination"] != ROCADE:
                continue
            coords = t["geo_shape"]["geometry"]["coordinates"]
            lon, lat = _milieu(coords)
            if lon <= LONGITUDE_EST:
                continue
            sens["interieure" if sens_horaire(coords) else "exterieure"].append((t, coords, lon, lat))

        rocade = []
        for sid, libelle in (("interieure", "Rocade intérieure"), ("exterieure", "Rocade extérieure")):
            troncons = sens[sid]
            etats = [(etat(t), _longueur(c)) for t, c, _, _ in troncons]
            ralentis: dict[str, list] = {}
            for t, c, lon, lat in troncons:
                e = etat(t)
                if e in ("dense", "sature", "bloque"):
                    ralentis.setdefault(entre_portes(lon, lat), []).append((e, t.get("averagevehiclespeed"), _longueur(c)))
            rocade.append(Sens(
                id=sid, libelle=libelle, etat=etat_global(etats),
                vitesse=vitesse_moyenne([(t.get("averagevehiclespeed"), _longueur(c)) for t, c, _, _ in troncons]),
                ralentissements=sorted(
                    (Ralentissement(entre=lieu, etat=max((e for e, _, _ in v), key=GRAVITE.index),
                                    vitesse=vitesse_moyenne([(s, l) for _, s, l in v])) for lieu, v in ralentis.items()),
                    key=lambda r: -GRAVITE.index(r.etat)),
            ))

        traces = [Troncon(etat=etat(t), rocade=True, points=simplifier(c)) for sid in sens for t, c, _, _ in sens[sid]]
        axes: dict[str, list] = {}
        for t in brut:
            if t.get("insee") == INSEE_CESSON and t["denomination"] != ROCADE:
                c = t["geo_shape"]["geometry"]["coordinates"]
                traces.append(Troncon(etat=etat(t), points=simplifier(c)))
                axes.setdefault(t["denomination"], []).append((etat(t),
                                                               t.get("averagevehiclespeed"), _longueur(c)))
        liste = [Axe(nom=nom, etat=etat_global([(e, l) for e, _, l in v]), vitesse=vitesse_moyenne([(s, l) for _, s, l in v]))
                 for nom, v in axes.items() if not nom.startswith("Rond-point")]
        liste.sort(key=lambda a: (-GRAVITE.index(a.etat), a.nom))
        traces.sort(key=lambda t: GRAVITE.index(t.etat))
        return DonneesCirculation(mesure_le=mesure, rocade_est=rocade, axes_cesson=liste, troncons=traces)

    def valide_jusqu_a(self, donnees: DonneesCirculation, maintenant: datetime) -> datetime:
        # Le trafic change vite : la carte passe en « données anciennes » après deux collectes manquées.
        return donnees.mesure_le + max(timedelta(minutes=15), 2 * pas_de_collecte(maintenant) + timedelta(minutes=5))


def pas_de_collecte(t: datetime) -> timedelta:
    """Cadence de deploy/crontab : 3 min en pointe, 30 min la nuit (21h-6h), 10 min le reste du temps."""
    if heure_de_pointe(t):
        return timedelta(minutes=3)
    heure = t.astimezone(PARIS).hour
    return timedelta(minutes=30) if heure < 6 or heure >= 21 else timedelta(minutes=10)
