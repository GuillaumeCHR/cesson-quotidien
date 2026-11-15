"""Défibrillateurs (Géo'DAE) et bornes de recharge (base nationale IRVE) à Cesson-Sévigné.

Les deux bases nationales sont publiées sur data.gouv.fr ; on les interroge par l'API tabulaire, filtrées sur
la commune, plutôt que de télécharger les fichiers entiers (80 et 120 Mo). Données statiques : collecte
hebdomadaire. Pas de disponibilité en temps réel des bornes (non publiée en open data).
"""

from __future__ import annotations

import re
from datetime import datetime, timedelta
from typing import Any

import httpx

from ..commun.collecteur import Collecteur, ErreurValidation, obtenir
from ..commun.modele import Coord, Lieu, Modele

TABULAIRE = "https://tabular-api.data.gouv.fr/api/resources"
RESSOURCE_DAE = "edb6a9e1-2f16-4bbf-99e7-c3eb6b90794c"
RESSOURCE_IRVE = "eb76d20a-8501-400e-b336-d85724de5435"
INSEE = "35051"
# « Mo-Su off » est la valeur par défaut de la base Géo'DAE, saisie quand l'exploitant n'a rien précisé :
# on ne la publie pas, pour ne pas faire croire qu'un défibrillateur n'est jamais accessible.
HORAIRES_PAR_DEFAUT = {"Mo-Su off", ""}


def lignes(client: httpx.Client, ressource: str, filtre: str) -> list[dict]:
    """Toutes les lignes d'une ressource pour la commune (pages de 200)."""
    url, params, res = f"{TABULAIRE}/{ressource}/data/", {filtre: INSEE, "page_size": 200}, []
    while url:
        r = obtenir(client, url, params=params)
        try:
            page = r.json()
        except ValueError as e:
            raise ErreurValidation(f"réponse non JSON : {e}") from e
        if not isinstance(page.get("data"), list):
            raise ErreurValidation("champ « data » absent")
        res += page["data"]
        url, params = (page.get("links") or {}).get("next"), None
    return res


# --- Défibrillateurs ------------------------------------------------------------------------------

class Defibrillateur(Modele):
    lieu: Lieu
    exterieur: bool
    libre_acces: bool  # accessible au public
    horaires: str | None = None  # format OSM, seulement s'il a été renseigné
    precision: str | None = None  # complément sur l'emplacement ou la disponibilité


class DonneesDefibrillateurs(Modele):
    defibrillateurs: list[Defibrillateur]


def nom_lisible(nom: str | None) -> str | None:
    """Écarte les noms qui ne sont qu'un code interne (« Cla224707853 », « 1959 »)."""
    n = (nom or "").strip()
    if not re.search(r"[A-Za-zÀ-ÿ]{3}", n) or re.fullmatch(r"[A-Za-z]{0,4}\d{4,}", n.replace(" ", "")):
        return None
    return n.title() if n.isupper() or n.islower() else n


def _vrai(v: Any) -> bool:
    return str(v).lower() in ("t", "true", "1", "oui")


class CollecteurDefibrillateurs(Collecteur[DonneesDefibrillateurs]):
    module = "defibrillateurs"
    source = "Géo'DAE, base nationale des défibrillateurs (ministère de la Santé)"
    licence = "Licence Ouverte 2.0"
    url_source = "https://geodae.atlasante.fr/"
    fraicheur = timedelta(days=7)
    modele_donnees = DonneesDefibrillateurs

    def telecharger(self, client: httpx.Client) -> Any:
        return lignes(client, RESSOURCE_DAE, "c_com_insee__exact")

    def valider(self, brut: Any) -> None:
        if len(brut) < 10:
            raise ErreurValidation(f"seulement {len(brut)} défibrillateur(s) à Cesson")
        for d in brut:
            if not isinstance(d.get("c_lat_coor1"), (int, float)) or not isinstance(d.get("c_long_coor1"), (int, float)):
                raise ErreurValidation(f"défibrillateur {d.get('gid')} sans coordonnées")

    def convertir(self, brut: Any, maintenant: datetime) -> DonneesDefibrillateurs:
        liste = []
        for d in brut:
            if d.get("c_etat_fonct") != "En fonctionnement" or d.get("c_etat_valid") != "validées" or _vrai(d.get("c_doublon")):
                continue
            horaires = (d.get("c_dispo_horaires") or "").strip()
            adresse = (d.get("c_adr_voie") or "").strip()
            liste.append(Defibrillateur(
                lieu=Lieu(id=f"dae:{d['gid']}", nom=nom_lisible(d.get("c_nom")) or adresse or "Défibrillateur", type="defibrillateur",
                          coord=Coord(lat=d["c_lat_coor1"], lon=d["c_long_coor1"]),
                          adresse=(adresse if "cesson" in adresse.lower() else f"{adresse}, Cesson-Sévigné") if adresse else None,
                          source="Géo'DAE"),
                exterieur=d.get("c_acc") == "Extérieur",
                libre_acces=_vrai(d.get("c_acc_lib")),
                horaires=None if horaires in HORAIRES_PAR_DEFAUT else horaires,
                precision=(d.get("c_disp_complt") or d.get("c_acc_complt") or None),
            ))
        # Accessibles à tout moment d'abord : dehors et en libre accès.
        liste.sort(key=lambda x: (not (x.exterieur and x.libre_acces), not x.libre_acces, x.lieu.nom))
        return DonneesDefibrillateurs(defibrillateurs=liste)

    def valide_jusqu_a(self, donnees: DonneesDefibrillateurs, maintenant: datetime) -> datetime:
        return maintenant + timedelta(days=14)


# --- Bornes de recharge ---------------------------------------------------------------------------

class StationRecharge(Modele):
    lieu: Lieu
    enseigne: str | None = None
    operateur: str | None = None
    h24: bool = False
    points: int
    puissance_max: float  # kW
    prises: list[str]  # « Type 2 », « Combo CCS », « CHAdeMO », « Domestique (E/F) »
    gratuit: bool
    horaires: str | None = None
    acces: str | None = None


class DonneesBornes(Modele):
    stations: list[StationRecharge]


H24 = {"24/7", "Mo-Su 00:00-23:59", "Mo-Su 00:00-23:57", "Mo-Su 00:00-24:00"}


def nom_station(brut: str | None) -> str | None:
    """« CESSON SEVIGNE - Rue de bray », « Volvo - Rennes (PDL1) - powered by DRIVECO » → « Rue de bray », « Volvo - Rennes (PDL1) »."""
    n = re.sub(r"\s*-\s*powered by .*$", "", (brut or "").strip(), flags=re.I)
    n = re.sub(r"\s*-\s*\d+(\.\d+)?\s*kW.*$", "", n, flags=re.I)
    n = re.sub(r"^CESSON[ -]S[EÉ]VIGN[EÉ]\s*-\s*", "", n, flags=re.I).strip(" -")
    if not n:
        return None
    if n.isupper():
        n = n.title()
    return n[0].upper() + n[1:]


PRISES = {"prise_type_2": "Type 2", "prise_type_combo_ccs": "Combo CCS", "prise_type_chademo": "CHAdeMO",
          "prise_type_ef": "Domestique (E/F)"}


class CollecteurBornes(Collecteur[DonneesBornes]):
    module = "bornes"
    source = "Base nationale des IRVE (data.gouv.fr)"
    licence = "Licence Ouverte 2.0"
    url_source = "https://transport.data.gouv.fr/datasets/fichier-consolide-des-bornes-de-recharge-pour-vehicules-electriques"
    fraicheur = timedelta(days=7)
    modele_donnees = DonneesBornes

    def telecharger(self, client: httpx.Client) -> Any:
        return lignes(client, RESSOURCE_IRVE, "code_insee_commune__exact")

    def valider(self, brut: Any) -> None:
        if not brut:
            raise ErreurValidation("aucun point de recharge à Cesson")
        for p in brut:
            if not p.get("id_station_itinerance") or not isinstance(p.get("consolidated_latitude"), (int, float)):
                raise ErreurValidation("point de recharge sans station ou sans coordonnées")

    def convertir(self, brut: Any, maintenant: datetime) -> DonneesBornes:
        par_station: dict[str, list[dict]] = {}
        for p in brut:
            par_station.setdefault(p["id_station_itinerance"], []).append(p)
        stations = []
        for sid, pts in par_station.items():
            p0 = pts[0]
            prises = [lib for cle, lib in PRISES.items() if any(_vrai(p.get(cle)) for p in pts)]
            nom = nom_station(p0.get("nom_station")) or (p0.get("nom_enseigne") or "Borne de recharge").strip()
            horaires = (p0.get("horaires") or "").strip()
            stations.append(StationRecharge(
                lieu=Lieu(id=f"irve:{sid}", nom=nom, type="station_recharge",
                          coord=Coord(lat=p0["consolidated_latitude"], lon=p0["consolidated_longitude"]),
                          adresse=(p0.get("adresse_station") or None), source="IRVE"),
                enseigne=(p0.get("nom_enseigne") or "").strip() or None,
                operateur=p0.get("nom_operateur"),
                h24=horaires in H24,
                points=len(pts),
                puissance_max=max(float(p.get("puissance_nominale") or 0) for p in pts),
                prises=prises,
                gratuit=all(_vrai(p.get("gratuit")) for p in pts),
                horaires=None if horaires in H24 or not horaires else horaires,
                acces=(p0.get("condition_acces") or "").replace("Accčs", "Accès") or None,
            ))
        stations.sort(key=lambda s: (-s.puissance_max, s.lieu.nom))
        return DonneesBornes(stations=stations)

    def valide_jusqu_a(self, donnees: DonneesBornes, maintenant: datetime) -> datetime:
        return maintenant + timedelta(days=14)
