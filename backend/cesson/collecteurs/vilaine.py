"""Niveau de la Vilaine à la station de Pont Briand (Hub'Eau Hydrométrie, temps réel).

Hub'Eau donne la hauteur H en millimètres, horodatée en UTC, toutes les 5 minutes environ.
On publie la dernière mesure, sa tendance sur une heure et la série des dernières 24 h.

Cadence : une collecte par jour suffit pour cette carte informative. La sécurité repose sur la
vigilance crues officielle (module Vigicrues, collecté toutes les 10 à 15 min), jamais sur ce module.
"""

from __future__ import annotations

from datetime import datetime, timedelta
from typing import Any

import httpx

from ..commun.collecteur import Changement, Collecteur, ErreurValidation, obtenir
from ..commun.modele import Coord, Lieu, Mesure, Modele, Tendance

URL = "https://hubeau.eaufrance.fr/api/v2/hydrometrie/observations_tr"
CODE_STATION = "J709063002"

STATION = Lieu(
    id=f"hubeau:{CODE_STATION}",
    nom="La Vilaine à Cesson-Sévigné – Pont Briand",
    type="station_hydrometrique",
    coord=Coord(lat=48.123664694, lon=-1.56367843),
    source="Hub'Eau",
    url=f"https://www.vigicrues.gouv.fr/station/{CODE_STATION}",
)

# Une variation de plus de 2 cm en une heure est considérée comme une tendance.
SEUIL_TENDANCE_MM = 20
# Bornes larges de plausibilité (le marnage de la station est de 0 à 5 m).
H_MIN_MM, H_MAX_MM = -1000, 8000
MIN_POINTS = 6


class PointSerie(Modele):
    horodatage: datetime
    valeur: float


class DonneesVilaine(Modele):
    station: Lieu
    mesure: Mesure
    serie_24h: list[PointSerie]


def _parse_date(s: str) -> datetime:
    return datetime.fromisoformat(s.replace("Z", "+00:00"))


def calculer_tendance(points: list[PointSerie]) -> Tendance:
    """Compare la dernière valeur à celle d'il y a environ une heure (points triés du plus récent)."""
    if not points:
        return Tendance.INCONNUE
    dernier = points[0]
    cible = dernier.horodatage - timedelta(hours=1)
    anterieurs = [p for p in points if p.horodatage <= cible + timedelta(minutes=10)]
    if not anterieurs or dernier.horodatage - anterieurs[0].horodatage > timedelta(hours=2):
        return Tendance.INCONNUE
    delta = dernier.valeur - anterieurs[0].valeur
    if delta > SEUIL_TENDANCE_MM:
        return Tendance.HAUSSE
    if delta < -SEUIL_TENDANCE_MM:
        return Tendance.BAISSE
    return Tendance.STABLE


class CollecteurVilaine(Collecteur[DonneesVilaine]):
    module = "vilaine"
    source = "Hub'Eau Hydrométrie (SCHAPI, Vigicrues)"
    licence = "Licence Ouverte 2.0"
    url_source = "https://hubeau.eaufrance.fr/page/api-hydrometrie"
    fraicheur = timedelta(hours=24)
    modele_donnees = DonneesVilaine

    def telecharger(self, client: httpx.Client) -> Any:
        debut = datetime.now().astimezone() - timedelta(hours=24)
        r = obtenir(client, URL, params={
            "code_entite": CODE_STATION,
            "grandeur_hydro": "H",
            "date_debut_obs": debut.strftime("%Y-%m-%dT%H:%M:%S%z"),
            "size": 1000,
            "sort": "desc",
            "fields": "code_station,date_obs,resultat_obs",
        })
        try:
            return r.json()
        except ValueError as e:
            raise ErreurValidation(f"réponse non JSON : {e}") from e

    def valider(self, brut: Any) -> None:
        if not isinstance(brut, dict) or not isinstance(brut.get("data"), list):
            raise ErreurValidation("champ « data » absent")
        data = brut["data"]
        if len(data) < MIN_POINTS:
            raise ErreurValidation(f"{len(data)} observation(s) sur 24 h, moins que les {MIN_POINTS} attendues")
        for obs in data:
            if obs.get("code_station") not in (None, CODE_STATION):
                raise ErreurValidation(f"station inattendue : {obs.get('code_station')}")
            v = obs.get("resultat_obs")
            if not isinstance(v, (int, float)) or not H_MIN_MM <= v <= H_MAX_MM:
                raise ErreurValidation(f"hauteur implausible : {v!r}")
            if not isinstance(obs.get("date_obs"), str):
                raise ErreurValidation("date_obs absente")

    def convertir(self, brut: Any, maintenant: datetime) -> DonneesVilaine:
        points = sorted(
            (PointSerie(horodatage=_parse_date(o["date_obs"]), valeur=float(o["resultat_obs"]))
             for o in brut["data"]),
            key=lambda p: p.horodatage, reverse=True,
        )
        dernier = points[0]
        mesure = Mesure(station=STATION.id, grandeur="H", valeur=dernier.valeur, unite="mm",
                        horodatage=dernier.horodatage, tendance=calculer_tendance(points))
        # Série publiée dans l'ordre chronologique, prête à tracer.
        return DonneesVilaine(station=STATION, mesure=mesure, serie_24h=list(reversed(points)))

    def valide_jusqu_a(self, donnees: DonneesVilaine, maintenant: datetime) -> datetime:
        # La donnée vaut par l'heure de la mesure, pas par l'heure de collecte : collecte quotidienne,
        # plus une heure de marge avant d'afficher la carte comme périmée.
        return donnees.mesure.horodatage + self.fraicheur + timedelta(hours=1)

    def comparer(self, ancien: DonneesVilaine | None, nouveau: DonneesVilaine) -> list[Changement]:
        if ancien is None or ancien.mesure.tendance == nouveau.mesure.tendance:
            return []
        return [Changement(self.module, "tendance",
                           f"{ancien.mesure.tendance} → {nouveau.mesure.tendance}",
                           {"valeur_mm": nouveau.mesure.valeur})]
