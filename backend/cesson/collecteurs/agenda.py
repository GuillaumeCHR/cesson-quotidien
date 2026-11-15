"""Agenda de Cesson-Sévigné : saison culturelle (OpenAgenda) et autres rendez-vous publiés par la ville.

Deux sources, fusionnées sans doublon :

- OpenAgenda, agenda de la ville n° 31553419 : la saison du Carré Sévigné et du Pont des Arts, l'École des
  arts. Structuré (séances, lieu, conditions). On lit l'export public sans clé ; OpenAgenda recommande son
  API, qui demande une clé : à prendre au nom de Cesson'App le jour où l'export disparaîtrait.
- Le site de la ville, pour les rendez-vous qui ne sont pas sur OpenAgenda (associations, solidarité…) :
  son point d'accès interne « postsQuery » donne les cartes de l'agenda page par page, puis la page de
  chaque événement donne ses dates complètes, ses horaires et son lieu. C'est une extraction, donc fragile :
  si moins de 3 événements se lisent au total, la collecte échoue, la version précédente reste publiée et
  l'alerte de santé prévient.

On ne reprend que le titre, la catégorie, les dates, les horaires, le lieu, les conditions, une phrase de
présentation et le lien : l'événement se lit chez son éditeur. Pas d'image.
"""

from __future__ import annotations

import html
import math
import re
import time
import unicodedata
from datetime import date, datetime, timedelta
from typing import Any

import httpx

from ..commun.collecteur import Collecteur, ErreurSource, ErreurValidation, obtenir
from ..commun.horaires import PARIS
from ..commun.modele import Modele

URL_OPENAGENDA = "https://openagenda.com/agendas/31553419/events.v2.json"
URL_VILLE = "https://www.ville-cesson-sevigne.fr/wp-json/creasit/postsQuery"
PAGES_MAX = 8
MIN_EVENEMENTS = 3
PAUSE = 0.4  # entre deux pages du site de la ville, pour rester discret
LONGUEUR_RESUME = 200

MOIS = {m: i for i, m in enumerate(["janvier", "février", "mars", "avril", "mai", "juin", "juillet", "août",
                                    "septembre", "octobre", "novembre", "décembre"], start=1)}
_DATE = r"(\d{1,2}) (" + "|".join(MOIS) + r") (\d{4})"


class Evenement(Modele):
    id: str
    titre: str
    categorie: str | None = None
    du: date  # prochaine date (ou début d'une période en cours)
    au: date
    horaires: str | None = None  # « 14h00 – 17h00 », « 20h00 »
    lieu: str | None = None
    conditions: str | None = None  # « Entrée libre », « Payant, sur réservation »
    resume: str | None = None
    lien: str
    theme: str | None = None  # carre_sevigne, mediatheque (lieu) ; gardé pour les versions précédentes de l'app
    saison: bool = False  # spectacle de la saison culturelle : Carré Sévigné ou Pont des Arts, payant ou sur réservation
    famille: str = "autre"  # filtres de l'agenda : associations, culture, sport, mediatheque, autre


class DonneesAgenda(Modele):
    evenements: list[Evenement]


def texte(fragment: str) -> str:
    t = re.sub(r"<(script|style|svg)\b.*?</\1>", " ", fragment, flags=re.S)
    return re.sub(r"\s+", " ", html.unescape(re.sub(r"<[^>]+>", " ", t))).strip()


def lignes(fragment: str) -> list[str]:
    t = re.sub(r"<(script|style|svg)\b.*?</\1>", "", fragment, flags=re.S)
    t = html.unescape(re.sub(r"<[^>]+>", "\n", t))
    return [l.strip() for l in t.split("\n") if l.strip()]


def court(t: str | None) -> str | None:
    if not t:
        return None
    t = re.sub(r"\s+", " ", t).strip()
    return t if len(t) <= LONGUEUR_RESUME else t[:LONGUEUR_RESUME].rsplit(" ", 1)[0].rstrip(" ,;:") + "…"


def cle_titre(t: str) -> str:
    """Pour repérer un même événement dans les deux sources (accents, casse, ponctuation ignorés)."""
    t = "".join(c for c in unicodedata.normalize("NFD", t.lower()) if not unicodedata.combining(c))
    return re.sub(r"[^a-z0-9]", "", t)


# --- Site de la ville ------------------------------------------------------------------------------

def lire_liste(page: str) -> list[dict]:
    """Cartes de l'agenda (HTML) : lien, titre, catégorie, résumé."""
    cartes = []
    for m in re.finditer(r'<article\s+class="card[^"]*card--event">(.*?)</article>', page, re.S):
        a = m.group(1)
        lien = re.search(r'class="card__link" href="([^"]+)"', a)
        titre = re.search(r'card__title">(.*?)</', a, re.S)
        if not lien or not titre:
            continue
        cat = re.search(r'card__category">(.*?)</', a, re.S)
        desc = re.search(r'card__description">(.*?)</div>', a, re.S)
        cartes.append({"lien": lien.group(1), "titre": texte(titre.group(1)),
                       "categorie": texte(cat.group(1)) if cat else None,
                       "resume": court(texte(desc.group(1))) if desc else None})
    return cartes


def lire_evenement(page: str) -> dict:
    """Dates (avec l'année), horaires et lieu, depuis la page d'un événement du site."""
    i = page.find('class="date-event"')
    if i < 0:
        raise ErreurValidation("page d'événement sans bloc de dates (mise en page changée ?)")
    bloc = lignes(page[i:i + 20_000])
    dates = re.findall(_DATE, bloc[1] if len(bloc) > 1 else "")
    if not dates:
        raise ErreurValidation(f"dates illisibles : {bloc[1:2]}")
    du = date(int(dates[0][2]), MOIS[dates[0][1]], int(dates[0][0]))
    au = date(int(dates[1][2]), MOIS[dates[1][1]], int(dates[1][0])) if len(dates) > 1 else du
    horaires = None
    if "Horaires" in bloc and bloc.index("Horaires") + 1 < len(bloc):
        h = bloc[bloc.index("Horaires") + 1]
        if (m := re.fullmatch(r"De (\d{1,2}h\d{2}) à (\d{1,2}h\d{2})", h)):
            horaires = f"{m.group(1)} – {m.group(2)}"
        elif (m := re.fullmatch(r"À (\d{1,2}h\d{2})", h)):
            horaires = m.group(1)
    lieu = None
    j = page.find('class="listing__location"')
    if j >= 0:
        l = lignes(page[j:j + 2000])
        lieu = l[1] if len(l) > 1 and not l[1].startswith("Contact") else None
    elif "Localisation" in bloc and bloc.index("Localisation") + 1 < len(bloc):
        lieu = bloc[bloc.index("Localisation") + 1]
    return {"du": du, "au": au, "horaires": horaires, "lieu": lieu if lieu and len(lieu) >= 3 else None}


# --- OpenAgenda ----------------------------------------------------------------------------------

def _fr(v: Any) -> str | None:
    if isinstance(v, dict):
        return v.get("fr") or next(iter(v.values()), None)
    return v or None


def _heure(iso: str) -> str:
    d = datetime.fromisoformat(iso).astimezone(PARIS)
    return f"{d.hour}h{d.minute:02d}"


def depuis_openagenda(e: dict, aujourdhui: date) -> Evenement | None:
    seances = sorted((datetime.fromisoformat(t["begin"]).astimezone(PARIS), datetime.fromisoformat(t["end"]).astimezone(PARIS))
                     for t in e.get("timings") or [] if t.get("begin") and t.get("end"))
    a_venir = [s for s in seances if s[1].date() >= aujourdhui]
    if not a_venir:
        return None
    prochaine, derniere = a_venir[0], a_venir[-1]
    meme_jour = prochaine[0].date() == prochaine[1].date()
    horaires = f"{_heure(prochaine[0].isoformat())} – {_heure(prochaine[1].isoformat())}" if meme_jour else None
    lieu = (e.get("location") or {}).get("name")
    titre = (_fr(e.get("title")) or "").strip()
    mots = _fr(e.get("keywords")) or []
    categorie = mots[0].capitalize() if mots else None
    conditions = court(_fr(e.get("conditions")))
    th = theme(lieu, titre, None)
    return Evenement(
        id=f"openagenda:{e['uid']}", titre=titre,
        categorie=categorie,
        du=prochaine[0].date(), au=derniere[1].date(),
        horaires=horaires, lieu=lieu, conditions=conditions,
        resume=court(_fr(e.get("description"))),
        lien=e.get("canonicalUrl") or f"https://openagenda.com/agendas/31553419/events/{e['uid']}",
        theme=th, saison=est_saison(lieu, conditions), famille=famille(titre, categorie, lieu, th),
    )


SALLES_SAISON = ("carré sévigné", "carre sevigne", "pont des arts")
MOTS_CULTURE = ("exposition", "conférence", "conference", "concert", "théâtre", "theatre", "lecture", "spectacle",
                "danse", "cinéma", "école des arts", "galerie")


def est_saison(lieu: str | None, conditions: str | None) -> bool:
    """Spectacle de la saison culturelle : dans une des deux salles, et payant ou sur réservation."""
    l, c = (lieu or "").lower(), (conditions or "").lower()
    return any(s in l for s in SALLES_SAISON) and ("payant" in c or "réserv" in c or "reserv" in c)


def famille(titre: str, categorie: str | None, lieu: str | None, th: str | None) -> str:
    """Famille de l'agenda (hors saison culturelle), pour ses filtres."""
    t, cat, l = titre.lower(), (categorie or "").lower(), (lieu or "").lower()
    if th == "mediatheque":
        return "mediatheque"
    if cat == "associations":
        return "associations"
    if cat.startswith("sport") or "glaz arena" in t or "glaz arena" in l:
        return "sport"
    if any(m in t or m in cat for m in MOTS_CULTURE) or any(s in l for s in SALLES_SAISON + ("galerie",)):
        return "culture"
    return "autre"


def theme(lieu: str | None, titre: str, categorie: str | None) -> str | None:
    t = f"{lieu or ''} {titre} {categorie or ''}".lower()
    if "médiathèque" in t or "mediatheque" in t:
        return "mediatheque"
    if any(m in t for m in ("carré sévigné", "carre sevigne", "pont des arts", "galerie pictura")):
        return "carre_sevigne"
    return None


# --- Collecteur ------------------------------------------------------------------------------------

class CollecteurAgenda(Collecteur[DonneesAgenda]):
    module = "agenda"
    source = "OpenAgenda (Ville de Cesson-Sévigné) et site de la ville (extraction)"
    licence = "Informations publiques de la Ville ; l'événement se lit chez son éditeur"
    url_source = "https://openagenda.com/agendas/31553419"
    fraicheur = timedelta(hours=6)
    modele_donnees = DonneesAgenda

    def __init__(self, pause: float = PAUSE) -> None:
        self.pause = pause

    def telecharger(self, client: httpx.Client) -> Any:
        try:
            oa = obtenir(client, URL_OPENAGENDA, params={"limit": 100}).json().get("events", [])
        except (ErreurSource, ValueError):
            oa = []  # le site de la ville prend le relais
        deja = {cle_titre(_fr(e.get("title")) or "") for e in oa}
        cartes, n, pages = [], 1, 1
        while n <= min(pages, PAGES_MAX):
            r = obtenir(client, URL_VILLE, params={"pages": n, "cpt": "events", "numberPosts": 10, "orderBy": "post_date",
                                                   "order": "DESC", "post_status[]": "publish"})
            try:
                page = r.json()
            except ValueError as e:
                raise ErreurValidation(f"agenda de la ville : réponse non JSON : {e}") from e
            infos = page.get("pagination_infos") or {}
            pages = math.ceil((infos.get("foundPosts") or 0) / max(infos.get("postsPerPage") or 10, 1))
            cartes += lire_liste("".join(page.get("posts") or []))
            n += 1
            time.sleep(self.pause)
        details = {}
        for c in cartes:
            if cle_titre(c["titre"]) in deja:
                continue  # déjà sur OpenAgenda, plus complet
            try:
                details[c["lien"]] = lire_evenement(obtenir(client, c["lien"]).text)
            except (ErreurValidation, ErreurSource):
                continue  # un événement illisible n'empêche pas les autres
            time.sleep(self.pause)
        return {"openagenda": oa, "cartes": cartes, "details": details}

    def valider(self, brut: Any) -> None:
        total = len(brut["openagenda"]) + len(brut["details"])
        if total < MIN_EVENEMENTS:
            raise ErreurValidation(f"seulement {total} événement(s) lisible(s) : une source a-t-elle changé ?")

    def convertir(self, brut: Any, maintenant: datetime) -> DonneesAgenda:
        aujourdhui = maintenant.astimezone(PARIS).date()
        evenements = [e for e in (depuis_openagenda(x, aujourdhui) for x in brut["openagenda"]) if e]
        # Même spectacle publié aussi par le site de la ville (titre un peu différent) : même jour, même salle.
        saison_le = {(e.du, s) for e in evenements if e.saison for s in SALLES_SAISON if s in (e.lieu or "").lower()}
        for c in brut["cartes"]:
            d = brut["details"].get(c["lien"])
            if d is None or d["au"] < aujourdhui:
                continue
            if any((d["du"], s) in saison_le for s in SALLES_SAISON if s in (d["lieu"] or "").lower()):
                continue
            slug = c["lien"].rstrip("/").rsplit("/", 1)[-1]
            evenements.append(Evenement(
                id=f"ville:{slug}", titre=c["titre"], categorie=c["categorie"], du=max(d["du"], aujourdhui), au=d["au"],
                horaires=d["horaires"], lieu=d["lieu"], resume=c["resume"], lien=c["lien"],
                theme=(th := theme(d["lieu"], c["titre"], c["categorie"])),
                famille=famille(c["titre"], c["categorie"], d["lieu"], th),
            ))
        evenements.sort(key=lambda e: (e.du, e.au, e.titre))
        return DonneesAgenda(evenements=evenements)

    def valide_jusqu_a(self, donnees: DonneesAgenda, maintenant: datetime) -> datetime:
        return maintenant + timedelta(hours=24)
