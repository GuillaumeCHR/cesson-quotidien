"""Lignes STAR (métro et bus) qui desservent Cesson-Sévigné, avec leurs couleurs officielles.

La liste n'est pas saisie : elle est déduite chaque nuit de la topologie publiée par STAR
(data.explore.star.fr). Une ligne est retenue dès qu'un de ses arrêts est sur la commune
(code INSEE 35051). Une refonte du réseau est ainsi prise en compte sans intervention.

Les prochains passages, eux, ne passent pas par ce backend : l'app les demande directement à STAR.
Ce module lui fournit la liste des lignes et leurs couleurs pour les afficher, et la liste des arrêts
de la commune (regroupés par nom, les deux quais ensemble) pour la carte « Mon arrêt ».
"""

from __future__ import annotations

import re
import unicodedata
from datetime import datetime, timedelta
from typing import Any

import httpx

from ..commun.collecteur import Collecteur, ErreurValidation, obtenir
from ..commun.modele import Coord, Mode, Modele

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


class ArretStar(Modele):
    nom: str  # tel qu'il figure dans les jeux temps réel (champ nomarret)
    coord: Coord  # centre des quais
    modes: list[Mode]
    lignes: list[str]  # noms courts, dans l'ordre des lignes


class DonneesLignesStar(Modele):
    lignes: list[LigneStar]
    arrets: list[ArretStar] = []


def cle_alphabetique(nom: str) -> str:
    """Ordre de l'annuaire : sans accents ni casse (« Ménouriais » avant « Muguet »)."""
    sans_accents = "".join(c for c in unicodedata.normalize("NFD", nom) if not unicodedata.combining(c))
    return sans_accents.casefold()


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
        points_bus = lire("tco-bus-topologie-pointsarret-td", where=f"{dans_cesson} and estversionactive='Oui'",
                          select="id,nom,coordonnees")
        points_metro = lire("tco-metro-topologie-pointsarret-td", where=dans_cesson, select="id,nom,coordonnees")
        arrets_bus, arrets_metro = _ids(points_bus, "id"), _ids(points_metro, "id")
        if not arrets_bus:
            raise ErreurValidation("aucun arrêt de bus STAR à Cesson")

        def dessertes(jeu: str, arrets: list[str]) -> list[dict]:
            if not arrets:
                return []
            liste = ",".join(f"'{a}'" for a in arrets)
            r = lire(jeu, where=f"idarret in ({liste})", select="idarret,idligne", group_by="idarret,idligne")
            _ids(r, "idligne")  # contrôle de forme
            return r

        d_bus = dessertes("tco-bus-topologie-dessertes-td", arrets_bus)
        d_metro = dessertes("tco-metro-topologie-dessertes-td", arrets_metro)
        return {
            "bus": sorted({str(d["idligne"]) for d in d_bus}),
            "metro": sorted({str(d["idligne"]) for d in d_metro}),
            "points_bus": points_bus, "points_metro": points_metro,
            "dessertes_bus": d_bus, "dessertes_metro": d_metro,
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
        lignes.sort(key=_cle_tri)
        return DonneesLignesStar(lignes=lignes, arrets=self._arrets(brut, lignes))

    @staticmethod
    def _arrets(brut: Any, lignes: list[LigneStar]) -> list[ArretStar]:
        rang = {(l.mode, l.id): i for i, l in enumerate(lignes)}
        par_nom: dict[str, dict] = {}
        for mode, points, dessertes in ((Mode.METRO, brut.get("points_metro", []), brut.get("dessertes_metro", [])),
                                        (Mode.BUS, brut.get("points_bus", []), brut.get("dessertes_bus", []))):
            lignes_du_point: dict[str, set[str]] = {}
            for d in dessertes:
                lignes_du_point.setdefault(str(d["idarret"]), set()).add(str(d["idligne"]))
            for p in points:
                servies = [i for i in lignes_du_point.get(str(p["id"]), ()) if (mode, i) in rang]
                if not servies or not p.get("coordonnees"):
                    continue  # quai sans ligne grand public
                a = par_nom.setdefault(p["nom"], {"coords": [], "modes": set(), "lignes": set()})
                a["coords"].append(p["coordonnees"])
                a["modes"].add(mode)
                a["lignes"].update((mode, i) for i in servies)
        arrets = []
        for nom, a in par_nom.items():
            c = a["coords"]
            arrets.append(ArretStar(
                nom=nom,
                coord=Coord(lat=round(sum(x["lat"] for x in c) / len(c), 6), lon=round(sum(x["lon"] for x in c) / len(c), 6)),
                modes=sorted(a["modes"], key=lambda m: m != Mode.METRO),
                lignes=[lignes[rang[k]].nom_court for k in sorted(a["lignes"], key=rang.__getitem__)],
            ))
        return sorted(arrets, key=lambda a: cle_alphabetique(a.nom))

    def valide_jusqu_a(self, donnees: DonneesLignesStar, maintenant: datetime) -> datetime:
        return maintenant + 2 * self.fraicheur
