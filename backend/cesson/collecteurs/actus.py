"""Actualités de la Ville de Cesson-Sévigné (flux RSS de son site).

On ne publie de chaque article que le titre, la date, un court extrait et le lien : l'article se lit
sur le site de son éditeur. Pas d'image (rien à charger depuis un serveur tiers), pas de tri ni de
commentaire : l'app relaie, sans choisir (principe de neutralité).

Eau du Bassin Rennais n'est pas repris ici : seules ses consignes sur l'usage de l'eau le sont, dans
le module usages_eau. `lire_flux` sert aux deux.
"""

from __future__ import annotations

import html
import re
import xml.etree.ElementTree as ET
from datetime import datetime, timedelta
from email.utils import parsedate_to_datetime
from typing import Any

import httpx

from ..commun.collecteur import Collecteur, ErreurValidation, obtenir
from ..commun.modele import Modele

SOURCES = {
    "ville": ("Ville de Cesson-Sévigné", "https://www.ville-cesson-sevigne.fr/feed/", 15),
}
LONGUEUR_EXTRAIT = 220
CONTENU = "{http://purl.org/rss/1.0/modules/content/}encoded"


class Article(Modele):
    source: str  # ville, eau
    titre: str
    date: datetime
    lien: str
    extrait: str | None = None
    concerne_cesson: bool = False


class Flux(Modele):
    id: str
    nom: str
    articles: list[Article]


class DonneesActus(Modele):
    flux: list[Flux]


def texte_brut(fragment: str | None) -> str:
    if not fragment:
        return ""
    t = re.sub(r"<[^>]+>", " ", fragment)
    return re.sub(r"\s+", " ", html.unescape(t)).strip()


def extrait(item: ET.Element) -> str | None:
    """Premier texte utile : la description, sauf la phrase automatique « L'article … est apparu en premier sur … »."""
    for brut in (item.findtext("description"), item.findtext(CONTENU)):
        t = texte_brut(brut)
        if t and not t.startswith("L’article") and not t.startswith("L'article"):
            if len(t) > LONGUEUR_EXTRAIT:
                t = t[:LONGUEUR_EXTRAIT].rsplit(" ", 1)[0].rstrip(" ,;:") + "…"
            return t
    return None


def lire_flux(xml: bytes, source: str, maximum: int) -> list[Article]:
    try:
        racine = ET.fromstring(xml)
    except ET.ParseError as e:
        raise ErreurValidation(f"flux {source} illisible : {e}") from e
    articles = []
    for item in racine.findall("./channel/item"):
        titre, lien, date = item.findtext("title"), item.findtext("link"), item.findtext("pubDate")
        if not titre or not lien or not date:
            continue
        texte = extrait(item)
        tout = f"{titre} {texte_brut(item.findtext(CONTENU))}"
        articles.append(Article(
            source=source, titre=texte_brut(titre), date=parsedate_to_datetime(date), lien=lien.strip(),
            extrait=texte, concerne_cesson=source != "ville" and "cesson" in tout.lower(),
        ))
    return sorted(articles, key=lambda a: a.date, reverse=True)[:maximum]


class CollecteurActus(Collecteur[DonneesActus]):
    module = "actus"
    source = "Ville de Cesson-Sévigné (flux RSS)"
    licence = "Titres et extraits © leurs éditeurs ; l'article se lit sur leur site"
    url_source = "https://www.ville-cesson-sevigne.fr/"
    fraicheur = timedelta(hours=1)
    modele_donnees = DonneesActus

    def telecharger(self, client: httpx.Client) -> Any:
        return {cle: obtenir(client, url).content for cle, (_, url, _) in SOURCES.items()}

    def valider(self, brut: Any) -> None:
        for cle, xml in brut.items():
            if not lire_flux(xml, cle, 1):
                raise ErreurValidation(f"flux {cle} sans article")

    def convertir(self, brut: Any, maintenant: datetime) -> DonneesActus:
        return DonneesActus(flux=[Flux(id=cle, nom=SOURCES[cle][0], articles=lire_flux(brut[cle], cle, SOURCES[cle][2]))
                                  for cle in SOURCES])

    def valide_jusqu_a(self, donnees: DonneesActus, maintenant: datetime) -> datetime:
        return maintenant + timedelta(hours=6)
