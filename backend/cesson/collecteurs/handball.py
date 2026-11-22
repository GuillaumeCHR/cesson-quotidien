"""Matchs à domicile du Cesson Rennes Métropole Handball, à la Glaz Arena.

Source : la page « Saison » du site du club (www.cesson-handball.com/pro/saison), qui liste tous les matchs de
l'équipe pro (championnat et coupes) avec la salle, l'heure, le score une fois joué et le bouton de billetterie
quand les places sont en vente. Ni le club ni la LNH ne publient de flux (iCal, API) : on lit la page HTML,
une extraction donc fragile. Si aucun match ne se lit, la collecte échoue et la version précédente reste publiée.

Les dates n'ont pas d'année (« Friday 05/02 ») : on la déduit de la saison affichée (« Calendrier 26/27 ») :
l'année de début d'août à décembre, la suivante de janvier à juillet. On ne garde que les matchs joués à
Cesson-Sévigné : ceux à venir, et le dernier joué pour son score.
"""

from __future__ import annotations

import html
import re
import unicodedata
from datetime import date, datetime, time, timedelta
from typing import Any

import httpx

from ..commun.collecteur import Collecteur, ErreurValidation, obtenir
from ..commun.horaires import PARIS
from ..commun.modele import Modele

URL = "https://www.cesson-handball.com/pro/saison"
CLUB = "CRMHB"
HEURE_PLAUSIBLE = 14  # avant 14h, l'heure affichée est provisoire (« 11H40 » le temps que la LNH la fixe)


class Match(Modele):
    id: str  # « liqui-moly-starligue-16e-journee-paris » : stable si le match est déplacé
    debut: datetime  # heure de Paris
    horaire_a_confirmer: bool = False
    adversaire: str  # « Paris », « Saint-Raphaël »
    competition: str | None = None  # « Liqui Moly Starligue », « Coupe de France »
    tour: str  # « 16e journée », « 1/4 de finale »
    salle: str  # « Glaz Arena »
    score_cesson: int | None = None
    score_adversaire: int | None = None
    billetterie: str | None = None  # lien quand les places sont en vente


class DonneesHandball(Modele):
    saison: str  # « 2026-2027 »
    a_venir: list[Match]
    dernier: Match | None = None  # dernier match joué à domicile


def _texte(fragment: str) -> str:
    return re.sub(r"\s+", " ", html.unescape(re.sub(r"<[^>]+>", " ", fragment))).strip()


def _classe(bloc: str, nom: str) -> list[str]:
    """Contenu des <div class="calendar-match__nom …"> du bloc (jusqu'au prochain div de même niveau)."""
    return re.findall(rf'<div class="calendar-match__{nom}[^"]*">(.*?)</div>', bloc, flags=re.S)


def saison(page: str) -> tuple[int, int]:
    m = re.search(r"Calendrier\s*<span[^>]*>\s*(\d{2})/(\d{2})\s*<", page)
    if not m:
        raise ErreurValidation("saison introuvable (« Calendrier 26/27 »)")
    return 2000 + int(m.group(1)), 2000 + int(m.group(2))


def lire_matchs(page: str) -> list[dict[str, Any]]:
    """Tous les matchs de la page, dans leur version grand écran (la page les répète pour le mobile)."""
    debut, fin = saison(page)
    matchs = []
    for bloc in re.split(r'<div class="calendar-match justify-content-between[^"]*">', page)[1:]:
        bloc = re.split(r'<div class="calendar-match d-sm-block', bloc)[0]
        ligue = re.search(r'calendar-match__league">\s*<img[^>]*alt="([^"]*)"', bloc)
        jour = _classe(bloc, "day")
        equipes = [_texte(e) for e in re.findall(r'<p class="versus-team__body-title">(.*?)</p>', bloc, flags=re.S)]
        quand = re.search(r'calendar-match__date[^"]*">\s*<p[^>]*>\s*\w+\s+(\d{2})/(\d{2})\s*</p>\s*'
                          r'<p[^>]*>\s*à\s*(\d{1,2})H(\d{2})', bloc)
        lieux = re.findall(r'<div class="calendar-match__place">(.*?)</div>', bloc, flags=re.S)
        if not jour or len(equipes) != 2 or not quand or len(lieux) < 2:
            continue
        j, mois, h, mn = (int(x) for x in quand.groups())
        annee = debut if mois >= 8 else fin
        score = re.search(r'calendar-match__score[^"]*">\s*<span[^>]*>\s*(\d+)\s*</span>\s*-\s*(\d+)', bloc)
        reserver = re.search(r'<a href="([^"]+)"[^>]*>\s*Réserver', bloc, flags=re.S)
        salle = re.findall(r"<p[^>]*>(.*?)</p>", lieux[1], flags=re.S)
        matchs.append({
            "competition": html.unescape(ligue.group(1)).strip() if ligue and ligue.group(1).strip() else None,
            "tour": re.sub(r"(\d+)\s*(?:ème|er)\b", r"\1e", _texte(jour[0])).replace("1e tour", "1er tour").replace("1e journée", "1re journée"),
            "domicile": equipes[0], "exterieur": equipes[1],
            "debut": datetime.combine(date(annee, mois, j), time(h, mn), PARIS),
            "ville": _texte(lieux[0]), "salle": _texte(salle[0]) if salle else "",
            "score": (int(score.group(1)), int(score.group(2))) if score else None,
            "billetterie": reserver.group(1).split("#")[0] if reserver else None,
        })
    return matchs


def a_cesson(m: dict[str, Any]) -> bool:
    return "glaz" in m["salle"].lower() or "cesson" in m["ville"].lower()


def _slug(s: str) -> str:
    s = unicodedata.normalize("NFKD", s).encode("ascii", "ignore").decode().lower()
    return re.sub(r"[^a-z0-9]+", "-", s).strip("-")


def vers_match(m: dict[str, Any]) -> Match:
    cesson_recoit = m["domicile"] == CLUB
    adversaire = m["exterieur"] if cesson_recoit else m["domicile"]
    sc = m["score"]
    if sc and not cesson_recoit:
        sc = (sc[1], sc[0])
    return Match(
        id=_slug(f"{m['competition'] or 'match'} {m['tour']} {adversaire}"), debut=m["debut"],
        horaire_a_confirmer=m["debut"].hour < HEURE_PLAUSIBLE, adversaire=adversaire,
        competition=m["competition"], tour=m["tour"], salle=m["salle"],
        score_cesson=sc[0] if sc else None, score_adversaire=sc[1] if sc else None,
        billetterie=m["billetterie"],
    )


class CollecteurHandball(Collecteur[DonneesHandball]):
    module = "handball"
    source = "Site du Cesson Rennes Métropole Handball (extraction)"
    licence = "Informations publiques du club ; le match se réserve chez le club"
    url_source = URL
    fraicheur = timedelta(days=2)
    modele_donnees = DonneesHandball

    def telecharger(self, client: httpx.Client) -> Any:
        return obtenir(client, URL).text

    def valider(self, brut: Any) -> None:
        if not isinstance(brut, str) or not lire_matchs(brut):
            raise ErreurValidation("aucun match lisible sur la page Saison du club : la page a-t-elle changé ?")

    def convertir(self, brut: Any, maintenant: datetime) -> DonneesHandball:
        debut, fin = saison(brut)
        maison = sorted((m for m in lire_matchs(brut) if a_cesson(m)), key=lambda m: m["debut"])
        # Un match reste « à venir » jusqu'à son score, et au plus 3 h après le coup d'envoi.
        joues = [m for m in maison if m["score"] or m["debut"] + timedelta(hours=3) < maintenant]
        a_venir = [m for m in maison if m not in joues]
        avec_score = [m for m in joues if m["score"]]  # le score arrive parfois le lendemain
        return DonneesHandball(
            saison=f"{debut}-{fin}", a_venir=[vers_match(m) for m in a_venir],
            dernier=vers_match(avec_score[-1]) if avec_score else None,
        )

    def valide_jusqu_a(self, donnees: DonneesHandball, maintenant: datetime) -> datetime:
        return maintenant + timedelta(days=2)
