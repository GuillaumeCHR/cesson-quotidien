"""Lignes STAR (métro et bus) qui desservent Cesson-Sévigné, avec leurs couleurs officielles.

La liste n'est pas saisie : elle est déduite chaque nuit de la topologie publiée par STAR
(data.explore.star.fr). Une ligne est retenue dès qu'un de ses arrêts est sur la commune
(code INSEE 35051). Une refonte du réseau est ainsi prise en compte sans intervention.

Les prochains passages, eux, ne passent pas par ce backend : l'app les demande directement à STAR.
Ce module lui fournit la liste des lignes et leurs couleurs pour les afficher.
"""

from __future__ import annotations

import re
from datetime import datetime, timedelta
from typing import Any

import httpx

from ..commun.collecteur import Collecteur, ErreurValidation, obtenir
from ..commun.modele import Mode, Modele

BASE = "https://data.explore.star.fr/api/explore/v2.1/catalog/datasets"
CODE_INSEE = "35051"
COULEUR = re.compile(r"^#[0-9a-fA-F]{6}$")
MIN_LIGNES = 5


def url(jeu: str) -> str:
    return f"{BASE}/{jeu}/exports/json"


class LigneStar(Modele):
    id: str
    nom_court: str
    nom_long: str
    mode: Mode
    famille: str | None = None  # Métro, Chronostar, Majeure…
    couleur: str  # #rrggbb
    couleur_texte: str


class DonneesLignesStar(Modele):
    lignes: list[LigneStar]


def _ids(liste: Any, champ: str) -> list[str]:
    if not isinstance(liste, list):
        raise ErreurValidation("réponse STAR inattendue : une liste était attendue")
    return [str(x[champ]) for x in liste if isinstance(x, dict) and x.get(champ) is not None]


def _cle_tri(l: LigneStar) -> tuple:
    # Métro d'abord, puis Chronostar (C1…C7), puis les lignes par numéro.
    rang = 0 if l.mode == Mode.METRO else 1 if (l.famille or "").lower() == "chronostar" else 2
    nombre = re.match(r"\d+", l.nom_court)
    return rang, int(nombre.group()) if nombre else 10_000, l.nom_court


class CollecteurLignesStar(Collecteur[DonneesLignesStar]):
    module = "lignes_star"
    source = "STAR (Rennes Métropole, Keolis Rennes)"
    licence = "ODbL"
    url_source = "https://data.explore.star.fr/"
    fraicheur = timedelta(hours=24)
    modele_donnees = DonneesLignesStar

    def telecharger(self, client: httpx.Client) -> Any:
        def lire(jeu: str, **params: str) -> Any:
            r = obtenir(client, url(jeu), params=params)
            try:
                return r.json()
            except ValueError as e:
                raise ErreurValidation(f"{jeu} : réponse non JSON : {e}") from e

        dans_cesson = f"codeinseecommune='{CODE_INSEE}'"
        arrets_bus = _ids(lire("tco-bus-topologie-pointsarret-td", where=f"{dans_cesson} and estversionactive='Oui'",
                               select="id"), "id")
        arrets_metro = _ids(lire("tco-metro-topologie-pointsarret-td", where=dans_cesson, select="id"), "id")
        if not arrets_bus:
            raise ErreurValidation("aucun arrêt de bus STAR à Cesson")

        def lignes_desservant(jeu: str, arrets: list[str]) -> list[str]:
            if not arrets:
                return []
            liste = ",".join(f"'{a}'" for a in arrets)
            return _ids(lire(jeu, where=f"idarret in ({liste})", select="idligne", group_by="idligne"), "idligne")

        return {
            "bus": lignes_desservant("tco-bus-topologie-dessertes-td", arrets_bus),
            "metro": lignes_desservant("tco-metro-topologie-dessertes-td", arrets_metro),
            "lignes_bus": lire("tco-bus-topologie-lignes-td",
                               where="estversionactive='Oui' and visibilite='Grand public'",
                               select="id,nomcourt,nomlong,nomfamillecommerciale,couleurligne,couleurtexteligne"),
            "lignes_metro": lire("tco-metro-topologie-lignes-td"),
        }

    def valider(self, brut: Any) -> None:
        for cle in ("lignes_bus", "lignes_metro"):
            if not isinstance(brut.get(cle), list):
                raise ErreurValidation(f"{cle} : liste attendue")
            for l in brut[cle]:
                for champ in ("couleurligne", "couleurtexteligne"):
                    if l.get(champ) is not None and not COULEUR.match(str(l[champ])):
                        raise ErreurValidation(f"ligne {l.get('nomcourt')} : couleur inattendue {l[champ]!r}")
        if len(brut["bus"]) + len(brut["metro"]) < MIN_LIGNES:
            raise ErreurValidation(f"seulement {len(brut['bus']) + len(brut['metro'])} ligne(s) à Cesson")
        if not brut["metro"]:
            raise ErreurValidation("le métro b ne dessert plus Cesson d'après la topologie : à vérifier")

    def convertir(self, brut: Any, maintenant: datetime) -> DonneesLignesStar:
        lignes = []
        for mode, servies, topo in ((Mode.METRO, brut["metro"], brut["lignes_metro"]),
                                    (Mode.BUS, brut["bus"], brut["lignes_bus"])):
            par_id = {str(l["id"]): l for l in topo}
            for i in servies:
                l = par_id.get(i)
                if l is None:
                    continue  # ligne non grand public (scolaire, service interne…)
                lignes.append(LigneStar(
                    id=i,
                    nom_court=l["nomcourt"],
                    nom_long=l.get("nomlong") or l["nomcourt"],
                    mode=mode,
                    famille=l.get("nomfamillecommerciale"),
                    couleur=(l.get("couleurligne") or "#5f6b66").lower(),
                    couleur_texte=(l.get("couleurtexteligne") or "#ffffff").lower(),
                ))
        return DonneesLignesStar(lignes=sorted(lignes, key=_cle_tri))

    def valide_jusqu_a(self, donnees: DonneesLignesStar, maintenant: datetime) -> datetime:
        return maintenant + 2 * self.fraicheur
