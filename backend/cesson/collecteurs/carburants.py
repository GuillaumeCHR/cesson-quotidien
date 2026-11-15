"""Prix des carburants dans les stations de Cesson-Sévigné (flux instantané v2, data.economie.gouv.fr).

Le flux national est mis à jour toutes les 10 min. On interroge l'API Opendatasoft sur le code
postal 35510, plus les stations voisines listées dans `saisie/carburants.yaml`, qui donne aussi les
noms (le flux n'en a pas).

Chaque prix garde sa propre date de relevé : une station peut ne pas avoir mis à jour un prix
depuis des jours, et l'app le dit plutôt que de le présenter comme actuel.
"""

from __future__ import annotations

from datetime import datetime, timedelta
from pathlib import Path
from typing import Any

import httpx
import yaml

from ..commun.collecteur import Collecteur, ErreurValidation, obtenir
from ..commun.modele import Coord, Lieu, Modele

URL = ("https://data.economie.gouv.fr/api/explore/v2.1/catalog/datasets/"
       "prix-des-carburants-en-france-flux-instantane-v2/records")
CODE_POSTAL = "35510"
SAISIE = Path(__file__).resolve().parents[2] / "saisie" / "carburants.yaml"

# Code du flux -> libellé affiché, dans l'ordre d'affichage.
CARBURANTS = {
    "gazole": "Gazole",
    "e10": "SP95-E10",
    "sp95": "SP95",
    "sp98": "SP98",
    "e85": "E85",
    "gplc": "GPLc",
}
# Bornes larges de plausibilité, en €/L (l'E85 et le GPLc descendent sous 1 €).
PRIX_MIN, PRIX_MAX = 0.3, 5.0


class PrixCarburant(Modele):
    carburant: str  # code : gazole, e10, sp95, sp98, e85, gplc
    libelle: str
    prix: float  # €/L
    maj: datetime


class RuptureCarburant(Modele):
    carburant: str
    libelle: str
    debut: datetime | None = None


class StationCarburant(Modele):
    lieu: Lieu
    automate_24_24: bool
    prix: list[PrixCarburant]
    # Ruptures temporaires seulement : un carburant en rupture définitive n'est simplement pas vendu.
    ruptures: list[RuptureCarburant] = []


class DonneesCarburants(Modele):
    stations: list[StationCarburant]


def lire_saisie(chemin: Path = SAISIE) -> dict[int, dict[str, str]]:
    brut = yaml.safe_load(chemin.read_text(encoding="utf-8")) or {}
    return {int(k): v or {} for k, v in (brut.get("stations") or {}).items()}


def _date(s: str | None) -> datetime | None:
    return datetime.fromisoformat(s) if s else None


class CollecteurCarburants(Collecteur[DonneesCarburants]):
    module = "carburants"
    source = "Prix des carburants (ministère de l'Économie) ; noms des stations : OpenStreetMap"
    licence = "Licence Ouverte 2.0 ; ODbL pour les noms des stations"
    url_source = "https://www.prix-carburants.gouv.fr/"
    fraicheur = timedelta(minutes=30)
    modele_donnees = DonneesCarburants

    def __init__(self, saisie: dict[int, dict[str, str]] | None = None) -> None:
        self.saisie = lire_saisie() if saisie is None else saisie

    def telecharger(self, client: httpx.Client) -> Any:
        filtre = " or ".join([f"cp='{CODE_POSTAL}'", *(f"id={i}" for i in sorted(self.saisie))])
        champs = ["id", "adresse", "ville", "cp", "geom", "horaires_automate_24_24"]
        for c in CARBURANTS:
            champs += [f"{c}_prix", f"{c}_maj", f"{c}_rupture_type", f"{c}_rupture_debut"]
        r = obtenir(client, URL, params={"where": filtre, "select": ",".join(champs), "limit": 100})
        try:
            return r.json()
        except ValueError as e:
            raise ErreurValidation(f"réponse non JSON : {e}") from e

    def valider(self, brut: Any) -> None:
        if not isinstance(brut, dict) or not isinstance(brut.get("results"), list):
            raise ErreurValidation("champ « results » absent")
        stations = brut["results"]
        if not any(str(s.get("cp")) == CODE_POSTAL for s in stations):
            raise ErreurValidation(f"aucune station au code postal {CODE_POSTAL}")
        for s in stations:
            if not isinstance(s.get("id"), int):
                raise ErreurValidation(f"identifiant de station inattendu : {s.get('id')!r}")
            for c in CARBURANTS:
                p = s.get(f"{c}_prix")
                if p is None:
                    continue
                if not isinstance(p, (int, float)) or not PRIX_MIN <= p <= PRIX_MAX:
                    raise ErreurValidation(f"station {s['id']} : prix {c} implausible : {p!r}")
                if not isinstance(s.get(f"{c}_maj"), str):
                    raise ErreurValidation(f"station {s['id']} : prix {c} sans date de relevé")

    def convertir(self, brut: Any, maintenant: datetime) -> DonneesCarburants:
        stations = []
        for s in brut["results"]:
            saisie = self.saisie.get(s["id"], {})
            geom = s.get("geom") or {}
            lieu = Lieu(
                id=f"carburant:{s['id']}",
                nom=saisie.get("nom") or s.get("adresse") or f"Station {s['id']}",
                type="station_carburant",
                coord=Coord(lat=geom["lat"], lon=geom["lon"]) if "lat" in geom else None,
                adresse=saisie.get("adresse") or ", ".join(x for x in (s.get("adresse"), s.get("ville")) if x),
                source="Prix des carburants",
                url=f"https://www.prix-carburants.gouv.fr/station/{s['id']}",
            )
            prix = [PrixCarburant(carburant=c, libelle=l, prix=s[f"{c}_prix"], maj=_date(s[f"{c}_maj"]))
                    for c, l in CARBURANTS.items() if s.get(f"{c}_prix") is not None]
            ruptures = [RuptureCarburant(carburant=c, libelle=l, debut=_date(s.get(f"{c}_rupture_debut")))
                        for c, l in CARBURANTS.items() if s.get(f"{c}_rupture_type") == "temporaire"]
            stations.append(StationCarburant(lieu=lieu, automate_24_24=s.get("horaires_automate_24_24") == "Oui",
                                             prix=prix, ruptures=ruptures))
        # Ordre stable : stations de la saisie d'abord, dans l'ordre du fichier, puis les autres.
        rang = {f"carburant:{i}": n for n, i in enumerate(self.saisie)}
        stations.sort(key=lambda st: (rang.get(st.lieu.id, len(rang)), st.lieu.id))
        return DonneesCarburants(stations=stations)

    def valide_jusqu_a(self, donnees: DonneesCarburants, maintenant: datetime) -> datetime:
        # Les prix bougent peu d'une heure à l'autre : une heure sans collecte reste acceptable.
        return maintenant + timedelta(hours=1)
