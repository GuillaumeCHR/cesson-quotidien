"""Vacances scolaires de Cesson-Sévigné (académie de Rennes, zone B).

Source : calendrier scolaire de l'Éducation nationale, publié en données ouvertes. Chaque période y a une date
de début (premier jour sans classe) et une date de fin (jour de la reprise), à minuit heure de Paris. Un pont
(Ascension) a la même date de début et de fin : c'est un jour sans classe. Pour l'été, on garde les dates des
élèves, pas celles des enseignants.
"""

from __future__ import annotations

from datetime import date, datetime, timedelta
from typing import Any

import httpx

from ..commun.collecteur import Collecteur, ErreurValidation, obtenir
from ..commun.horaires import PARIS
from ..commun.modele import Modele

URL = "https://data.education.gouv.fr/api/explore/v2.1/catalog/datasets/fr-en-calendrier-scolaire/records"
ACADEMIE = "Rennes"
NOMBRE = 8  # périodes publiées : de quoi couvrir l'année scolaire en cours et le début de la suivante


class Periode(Modele):
    nom: str  # « Vacances de Noël », « Pont de l'Ascension »
    du: date  # premier jour sans classe
    reprise: date | None = None  # jour de la reprise ; absent quand la fin n'est pas encore publiée
    pont: bool = False  # un seul jour sans classe (du)
    annee_scolaire: str


class DonneesVacances(Modele):
    zone: str
    academie: str
    periodes: list[Periode]


def jour_paris(iso: str) -> date:
    return datetime.fromisoformat(iso).astimezone(PARIS).date()


class CollecteurVacancesScolaires(Collecteur[DonneesVacances]):
    module = "vacances_scolaires"
    source = "Calendrier scolaire (ministère de l'Éducation nationale)"
    licence = "Licence Ouverte 2.0"
    url_source = "https://data.education.gouv.fr/explore/dataset/fr-en-calendrier-scolaire/"
    fraicheur = timedelta(days=7)
    modele_donnees = DonneesVacances

    def telecharger(self, client: httpx.Client) -> Any:
        debut = (datetime.now(PARIS).date() - timedelta(days=120)).isoformat()
        r = obtenir(client, URL, params={
            "where": f'location="{ACADEMIE}" and end_date>="{debut}"', "order_by": "start_date", "limit": 50,
            "select": "description,start_date,end_date,zones,annee_scolaire,population,location",
        })
        try:
            return r.json()
        except ValueError as e:
            raise ErreurValidation(f"réponse non JSON : {e}") from e

    def valider(self, brut: Any) -> None:
        lignes = brut.get("results") if isinstance(brut, dict) else None
        if not isinstance(lignes, list) or not lignes:
            raise ErreurValidation("aucune période de vacances pour l'académie de Rennes")
        for l in lignes:
            if not l.get("description") or not l.get("start_date"):
                raise ErreurValidation(f"période incomplète : {l}")

    def convertir(self, brut: Any, maintenant: datetime) -> DonneesVacances:
        aujourdhui = maintenant.astimezone(PARIS).date()
        periodes: dict[tuple, Periode] = {}
        zone = "Zone B"
        for l in brut["results"]:
            if l.get("population") == "Enseignants":
                continue
            zone = l.get("zones") or zone
            nom = l["description"].strip()
            du = jour_paris(l["start_date"])
            fin = jour_paris(l["end_date"]) if l.get("end_date") else None
            debut_seul = nom.lower().startswith("début des ")
            if debut_seul:
                nom = nom[len("Début des "):].strip()
                nom = nom[0].upper() + nom[1:]
            pont = fin == du and not debut_seul
            reprise = None if debut_seul or pont else fin
            if (reprise or du) < aujourdhui:
                continue  # déjà passée
            periodes[(nom, du)] = Periode(nom=nom, du=du, reprise=reprise, pont=pont,
                                          annee_scolaire=l.get("annee_scolaire") or "")
        liste = sorted(periodes.values(), key=lambda p: p.du)[:NOMBRE]
        return DonneesVacances(zone=zone, academie=ACADEMIE, periodes=liste)

    def valide_jusqu_a(self, donnees: DonneesVacances, maintenant: datetime) -> datetime:
        return maintenant + timedelta(days=14)
