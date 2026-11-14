"""Météo à Cesson-Sévigné : conditions actuelles, prochaines heures, aujourd'hui et demain.

Source : Open-Meteo, avec les modèles de Météo-France (AROME et ARPEGE, « meteofrance_seamless »),
sans clé, sous licence CC BY 4.0. Ce module est informatif : la vigilance météo officielle est un
module distinct (API Météo-France), et seule elle peut déclencher une alerte.

Les heures renvoyées par Open-Meteo sont locales (fuseau Europe/Paris) et sans décalage : on les
rattache explicitement au fuseau avant de publier.
"""

from __future__ import annotations

import datetime as dt
from datetime import datetime, timedelta
from typing import Any
from zoneinfo import ZoneInfo

import httpx

from ..commun.collecteur import Collecteur, ErreurValidation, obtenir
from ..commun.modele import Coord, Lieu, Modele

URL = "https://api.open-meteo.com/v1/forecast"
PARIS = ZoneInfo("Europe/Paris")

LIEU = Lieu(
    id="meteo:cesson-sevigne",
    nom="Cesson-Sévigné",
    type="commune",
    coord=Coord(lat=48.1213, lon=-1.6036),
    source="Open-Meteo",
)

PARAMS = {
    "latitude": LIEU.coord.lat,
    "longitude": LIEU.coord.lon,
    "models": "meteofrance_seamless",
    "timezone": "Europe/Paris",
    "forecast_days": 2,
    "current": "temperature_2m,apparent_temperature,weather_code,is_day,wind_speed_10m,wind_gusts_10m,precipitation",
    "hourly": "temperature_2m,weather_code,precipitation,is_day",
    "daily": "temperature_2m_min,temperature_2m_max,weather_code,precipitation_sum,sunrise,sunset,wind_gusts_10m_max",
}

# Codes météo WMO utilisés par Open-Meteo.
LIBELLES = {
    0: "Ciel dégagé", 1: "Plutôt dégagé", 2: "Partiellement nuageux", 3: "Couvert",
    45: "Brouillard", 48: "Brouillard givrant",
    51: "Bruine faible", 53: "Bruine", 55: "Bruine dense",
    56: "Bruine verglaçante", 57: "Bruine verglaçante dense",
    61: "Pluie faible", 63: "Pluie", 65: "Pluie forte",
    66: "Pluie verglaçante", 67: "Pluie verglaçante forte",
    71: "Neige faible", 73: "Neige", 75: "Neige forte", 77: "Grains de neige",
    80: "Averses faibles", 81: "Averses", 82: "Averses violentes",
    85: "Averses de neige", 86: "Averses de neige fortes",
    95: "Orage", 96: "Orage avec grêle", 99: "Orage avec forte grêle",
}
INCONNU = "Conditions non précisées"
HEURES_PUBLIEES = 24


def libelle(code: int) -> str:
    return LIBELLES.get(code, INCONNU)


class MeteoActuelle(Modele):
    horodatage: datetime
    temperature: float
    ressentie: float
    code: int
    libelle: str
    jour: bool
    vent_kmh: float
    rafales_kmh: float
    precipitation_mm: float


class MeteoHeure(Modele):
    horodatage: datetime
    temperature: float
    code: int
    libelle: str
    jour: bool
    precipitation_mm: float


class MeteoJour(Modele):
    date: dt.date
    min: float
    max: float
    code: int
    libelle: str
    precipitation_mm: float
    rafales_max_kmh: float
    lever: datetime
    coucher: datetime


class DonneesMeteo(Modele):
    lieu: Lieu
    actuelle: MeteoActuelle
    jours: list[MeteoJour]  # aujourd'hui, puis demain
    heures: list[MeteoHeure]  # à partir de l'heure en cours


def _local(s: str) -> datetime:
    return datetime.fromisoformat(s).replace(tzinfo=PARIS)


class CollecteurMeteo(Collecteur[DonneesMeteo]):
    module = "meteo"
    source = "Open-Meteo (modèles Météo-France)"
    licence = "CC BY 4.0"
    url_source = "https://open-meteo.com/"
    fraicheur = timedelta(hours=1)
    modele_donnees = DonneesMeteo

    def telecharger(self, client: httpx.Client) -> Any:
        r = obtenir(client, URL, params=PARAMS)
        try:
            return r.json()
        except ValueError as e:
            raise ErreurValidation(f"réponse non JSON : {e}") from e

    def valider(self, brut: Any) -> None:
        if not isinstance(brut, dict):
            raise ErreurValidation("réponse inattendue")
        for bloc in ("current", "hourly", "daily"):
            if not isinstance(brut.get(bloc), dict):
                raise ErreurValidation(f"bloc « {bloc} » absent")
        c = brut["current"]
        if not isinstance(c.get("temperature_2m"), (int, float)) or not -40 <= c["temperature_2m"] <= 50:
            raise ErreurValidation(f"température implausible : {c.get('temperature_2m')!r}")
        h, d = brut["hourly"], brut["daily"]
        if len(h.get("time") or []) < 24:
            raise ErreurValidation("moins de 24 heures de prévision")
        if len(d.get("time") or []) < 2:
            raise ErreurValidation("moins de 2 jours de prévision")
        for cle in ("temperature_2m", "weather_code", "precipitation", "is_day"):
            if len(h.get(cle) or []) != len(h["time"]):
                raise ErreurValidation(f"série horaire « {cle} » incomplète")

    def convertir(self, brut: Any, maintenant: datetime) -> DonneesMeteo:
        c, h, d = brut["current"], brut["hourly"], brut["daily"]
        actuelle = MeteoActuelle(
            horodatage=_local(c["time"]),
            temperature=c["temperature_2m"],
            ressentie=c["apparent_temperature"],
            code=c["weather_code"],
            libelle=libelle(c["weather_code"]),
            jour=bool(c["is_day"]),
            vent_kmh=c["wind_speed_10m"],
            rafales_kmh=c["wind_gusts_10m"],
            precipitation_mm=c["precipitation"],
        )
        debut = maintenant.astimezone(PARIS).replace(minute=0, second=0, microsecond=0)
        heures = [
            MeteoHeure(horodatage=t, temperature=h["temperature_2m"][i], code=h["weather_code"][i],
                       libelle=libelle(h["weather_code"][i]), jour=bool(h["is_day"][i]), precipitation_mm=h["precipitation"][i] or 0.0)
            for i, t in enumerate(_local(s) for s in h["time"])
            if t >= debut and h["temperature_2m"][i] is not None and h["weather_code"][i] is not None
        ][:HEURES_PUBLIEES]
        jours = [
            MeteoJour(
                date=dt.date.fromisoformat(d["time"][i]),
                min=d["temperature_2m_min"][i],
                max=d["temperature_2m_max"][i],
                code=d["weather_code"][i],
                libelle=libelle(d["weather_code"][i]),
                precipitation_mm=d["precipitation_sum"][i] or 0.0,
                rafales_max_kmh=d["wind_gusts_10m_max"][i] or 0.0,
                lever=_local(d["sunrise"][i]),
                coucher=_local(d["sunset"][i]),
            )
            for i in range(len(d["time"]))
        ]
        return DonneesMeteo(lieu=LIEU, actuelle=actuelle, jours=jours, heures=heures)

    def valide_jusqu_a(self, donnees: DonneesMeteo, maintenant: datetime) -> datetime:
        # Une prévision horaire reste utile quelques heures ; au-delà, la carte se dit ancienne.
        return maintenant + timedelta(hours=3)
