"""Fiches des films du cinéma : affiche, synopsis, réalisateur, durée… (TMDB), liens Wikipédia et Allociné (Wikidata).

Chaque titre lu sur la grille est cherché dans TMDB, en français et sans limite de date (la salle passe aussi des
classiques). Le bon film :

- une année dans le titre (« Taxi driver (1976) ») limite la recherche à cette année ;
- « Sortie nationale » : le film du titre sorti le plus récemment, s'il est sorti depuis moins de trois mois ;
- sinon, le titre exact (français ou original) ; entre plusieurs titres exacts, le seul sorti depuis moins de six mois
  (la salle passe surtout des films récents, et la grille donne l'année des classiques), à défaut le plus connu s'il
  l'est nettement plus que le suivant. En cas de doute, pas de fiche plutôt qu'une fausse ; `fiches` dans saisie/cinema.yaml
  impose un film (identifiant TMDB) ou aucun (0).

Le Pi télécharge les affiches (185 px) dans public/affiches/ : les téléphones ne contactent jamais TMDB.
Correspondances et fiches restent en cache dans etat/ (fiches revues chaque semaine, titres sans film chaque jour).
La fiche est facultative : sans jeton, ou si TMDB ne répond pas, les séances sont publiées sans elle.

Résumé d'une ligne : la première phrase du synopsis officiel, coupée sur un mot au-delà de 120 caractères.

Jeton d'accès en lecture TMDB (« jeton=eyJ… ») dans /srv/cesson/secrets/tmdb.env, ou le fichier que désigne
CESSON_TMDB_IDENTIFIANTS.
"""

from __future__ import annotations

import json
import logging
import re
import unicodedata
from datetime import date, datetime, timedelta
from difflib import SequenceMatcher
from pathlib import Path
from typing import Any

import httpx

from ..commun.collecteur import ErreurSource, obtenir
from ..commun.modele import Modele
from ..commun.publication import ecrire_atomique
from ..commun.secrets import lire_secrets

log = logging.getLogger(__name__)

API = "https://api.themoviedb.org/3"
IMAGES = "https://image.tmdb.org/t/p/w185"
WIKIDATA = "https://www.wikidata.org/w/api.php"
DOSSIER_AFFICHES = "affiches"
CACHE = "fiches_films.json"
REVOIR_FICHE = timedelta(days=7)
REVOIR_SANS_FILM = timedelta(days=1)
GARDER_AFFICHE = timedelta(days=60)  # après la dernière séance
RECENTE = timedelta(days=92)
SEMESTRE = timedelta(days=183)
NETTEMENT_PLUS_CONNU = 3.0
TITRE_PROCHE = 0.92


class Fiche(Modele):
    tmdb: int
    titre: str  # titre français de TMDB
    titre_original: str | None = None  # s'il diffère
    annee: int | None = None
    realisateurs: list[str] = []
    distribution: list[str] = []  # quatre premiers rôles
    duree_min: int | None = None
    genres: list[str] = []
    synopsis: str | None = None
    resume: str | None = None  # première phrase du synopsis, raccourcie
    classification: str | None = None  # visa français : « 12 », « 16 »… (rien = tous publics ou inconnu)
    affiche: str | None = None  # chemin relatif à la racine des données : affiches/103.jpg
    wikipedia: str | None = None
    allocine: str | None = None
    page_tmdb: str


def cle(t: str) -> str:
    t = "".join(c for c in unicodedata.normalize("NFD", t.lower()) if not unicodedata.combining(c))
    return re.sub(r"[^a-z0-9]", "", t)


def titre_et_annee(titre: str) -> tuple[str, int | None]:
    """« Taxi driver (1976) » → (« Taxi driver », 1976)."""
    m = re.match(r"^(.*?)\s*\((\d{4})\)\s*$", titre)
    return (m.group(1), int(m.group(2))) if m else (titre, None)


def _date(s: str | None) -> date | None:
    try:
        return date.fromisoformat(s) if s else None
    except ValueError:
        return None


def choisir(resultats: list[dict], titre: str, annee: int | None, sortie_nationale: bool,
            aujourdhui: date) -> int | None:
    """Identifiant TMDB du film de la grille parmi les résultats de recherche, ou None en cas de doute."""
    c = cle(titre)

    def proximite(r: dict) -> float:
        return max(SequenceMatcher(None, c, cle(r.get(k) or "")).ratio() for k in ("title", "original_title"))

    if annee is not None:
        resultats = [r for r in resultats if (d := _date(r.get("release_date"))) and abs(d.year - annee) <= 1]
    exacts = [r for r in resultats if proximite(r) == 1.0]
    candidats = exacts or [r for r in resultats if proximite(r) >= TITRE_PROCHE]
    if not candidats:
        return None
    if sortie_nationale:
        recents = [r for r in candidats if (d := _date(r.get("release_date")))
                   and aujourdhui - RECENTE <= d <= aujourdhui + timedelta(days=31)]
        if recents:
            return max(recents, key=lambda r: r["release_date"])["id"]
    if len(candidats) == 1:
        return candidats[0]["id"]
    if annee is None:
        du_semestre = [r for r in candidats if (d := _date(r.get("release_date")))
                       and aujourdhui - SEMESTRE <= d <= aujourdhui + timedelta(days=31)]
        if len(du_semestre) == 1:
            return du_semestre[0]["id"]
    tries = sorted(candidats, key=lambda r: r.get("popularity") or 0, reverse=True)
    premier, second = (tries[0].get("popularity") or 0), (tries[1].get("popularity") or 0)
    return tries[0]["id"] if premier >= NETTEMENT_PLUS_CONNU * max(second, 0.1) else None


def fiche_de(detail: dict, liens: dict[str, str], affiche: str | None = None) -> Fiche:
    credits = detail.get("credits") or {}
    visas = next((p.get("release_dates") or [] for p in (detail.get("release_dates") or {}).get("results", [])
                  if p.get("iso_3166_1") == "FR"), [])
    classification = next((v["certification"].strip() for v in visas if (v.get("certification") or "").strip()), None)
    if classification in ("U", "TP", "Tous publics"):
        classification = None
    titre = detail.get("title") or detail.get("original_title") or ""
    original = detail.get("original_title")
    annee = _date(detail.get("release_date"))
    return Fiche(
        tmdb=detail["id"],
        titre=titre,
        titre_original=original if original and cle(original) != cle(titre) else None,
        annee=annee.year if annee else None,
        realisateurs=[p["name"] for p in credits.get("crew", []) if p.get("job") == "Director"][:2],
        distribution=[p["name"] for p in sorted(credits.get("cast", []), key=lambda p: p.get("order", 99))][:4],
        duree_min=detail.get("runtime") or None,
        genres=[g["name"] for g in detail.get("genres", [])][:3],
        synopsis=(detail.get("overview") or "").strip() or None,
        resume=premiere_phrase(detail.get("overview")),
        classification=classification,
        affiche=affiche,
        wikipedia=liens.get("wikipedia"),
        allocine=liens.get("allocine"),
        page_tmdb=f"https://www.themoviedb.org/movie/{detail['id']}",
    )


def liens_wikidata(entite: dict) -> dict[str, str]:
    liens = {}
    if (page := (entite.get("sitelinks") or {}).get("frwiki", {}).get("title")):
        liens["wikipedia"] = "https://fr.wikipedia.org/wiki/" + page.replace(" ", "_")
    for a in (entite.get("claims") or {}).get("P1265", []):  # identifiant AlloCiné film
        if (v := a.get("mainsnak", {}).get("datavalue", {}).get("value")) and str(v).isdigit():
            liens["allocine"] = f"https://www.allocine.fr/film/fichefilm_gen_cfilm={v}.html"
            break
    return liens


RESUME_MAX = 120  # caractères


def premiere_phrase(synopsis: str | None) -> str | None:
    """Première phrase du synopsis ; au-delà de RESUME_MAX caractères, coupée sur un mot et suivie de « … »."""
    if not synopsis:
        return None
    t = " ".join(synopsis.split())
    phrase = re.split(r"(?<=[.!?…])\s+(?=[A-ZÀ-Ý«])", t, maxsplit=1)[0]
    if len(phrase) <= RESUME_MAX:
        return phrase
    coupe = phrase[:RESUME_MAX - 1].rsplit(" ", 1)[0]
    coupe = re.sub(r"[\s,;:–-]+$", "", coupe)
    coupe = re.sub(r"\s+(et|ou|de|du|des|la|le|les|un|une|à|au|aux|en|par|pour|sur|qui|que|dont|d’|l’)$", "", coupe, flags=re.I)
    return coupe + "…"


class FichesFilms:
    def __init__(self, public: Path, etat: Path, forcees: dict[str, int] | None = None) -> None:
        self.public, self.etat = Path(public), Path(etat)
        self.forcees = {cle(k): int(v) for k, v in (forcees or {}).items()}
        try:
            self.cache = json.loads((self.etat / CACHE).read_text(encoding="utf-8"))
        except (OSError, ValueError):
            self.cache = {}
        self.cache.setdefault("titres", {})
        self.cache.setdefault("fiches", {})

    def _jeton(self) -> str:
        return lire_secrets("TMDB", "CESSON_TMDB_IDENTIFIANTS", "tmdb.env", ("jeton",))["jeton"]

    def completer(self, client: httpx.Client, films: list[dict], maintenant: datetime) -> dict[str, Fiche]:
        """Fiche de chaque titre {titre: Fiche} ; un titre sans fiche n'y figure pas. N'échoue jamais."""
        try:
            entetes = {"Authorization": f"Bearer {self._jeton()}", "Accept": "application/json"}
        except ErreurSource as e:
            log.warning("fiches des films : %s", e)
            entetes = None
        aujourdhui = maintenant.date()
        resultat: dict[str, Fiche] = {}
        for f in films:
            try:
                ident = self._identifiant(client, entetes, f, aujourdhui, maintenant)
                if ident and (fiche := self._fiche(client, entetes, ident, maintenant)):
                    resultat[f["titre"]] = fiche
            except ErreurSource as e:  # une source muette : ce film reste sans fiche
                log.warning("fiche de « %s » : %s", f["titre"], e)
            except (KeyError, TypeError, ValueError) as e:
                log.warning("fiche de « %s » illisible : %s", f["titre"], e)
        self._ranger_affiches({x.tmdb for x in resultat.values()}, maintenant)
        ecrire_atomique(self.etat / CACHE, json.dumps(self.cache, indent=1, ensure_ascii=False) + "\n")
        return resultat

    def _identifiant(self, client, entetes, f: dict, aujourdhui: date, maintenant: datetime) -> int | None:
        k = cle(f["titre"]) + ("+sn" if f.get("sortie_nationale") else "")
        if cle(f["titre"]) in self.forcees:
            return self.forcees[cle(f["titre"])] or None
        connu = self.cache["titres"].get(k)
        if connu and (connu["tmdb"] or maintenant - datetime.fromisoformat(connu["le"]) < REVOIR_SANS_FILM):
            return connu["tmdb"]
        if entetes is None:
            return None
        titre, annee = titre_et_annee(f["titre"])
        params = {"query": titre, "language": "fr-FR", "include_adult": "false"}
        if annee:
            params["year"] = str(annee)
        resultats = obtenir(client, f"{API}/search/movie", params=params, headers=entetes).json().get("results", [])
        ident = choisir(resultats, titre, annee, bool(f.get("sortie_nationale")), aujourdhui)
        if ident is None:
            log.info("fiche de « %s » : aucun film certain parmi %d résultat(s)", f["titre"], len(resultats))
        self.cache["titres"][k] = {"tmdb": ident, "le": maintenant.isoformat()}
        return ident

    def _fiche(self, client, entetes, ident: int, maintenant: datetime) -> Fiche | None:
        connue = self.cache["fiches"].get(str(ident))
        if connue and (entetes is None or maintenant - datetime.fromisoformat(connue["le"]) < REVOIR_FICHE):
            connue["vue"] = maintenant.isoformat()
            fiche = Fiche.model_validate(connue["fiche"])
            return fiche if fiche.resume or not fiche.synopsis else fiche.model_copy(update={"resume": premiere_phrase(fiche.synopsis)})
        if entetes is None:
            return None
        detail = obtenir(client, f"{API}/movie/{ident}", headers=entetes, params={
            "language": "fr-FR", "append_to_response": "credits,release_dates,external_ids"}).json()
        liens: dict[str, str] = {}
        if (qid := (detail.get("external_ids") or {}).get("wikidata_id")):
            try:
                entites = obtenir(client, WIKIDATA, params={
                    "action": "wbgetentities", "ids": qid, "props": "claims|sitelinks", "sitefilter": "frwiki",
                    "format": "json"}).json().get("entities", {})
                liens = liens_wikidata(entites.get(qid, {}))
            except ErreurSource as e:
                log.warning("Wikidata %s : %s", qid, e)
                ancienne = (connue or {}).get("fiche") or {}
                liens = {k: ancienne[k] for k in ("wikipedia", "allocine") if ancienne.get(k)}
        affiche = self._affiche(client, ident, chemin) if (chemin := detail.get("poster_path")) else None
        fiche = fiche_de(detail, liens, affiche)
        self.cache["fiches"][str(ident)] = {"fiche": fiche.model_dump(mode="json", exclude_none=True),
                                            "le": maintenant.isoformat(), "vue": maintenant.isoformat()}
        return fiche

    def _affiche(self, client, ident: int, chemin: str) -> str | None:
        relatif = f"{DOSSIER_AFFICHES}/{ident}.jpg"
        cible = self.public / relatif
        if cible.exists():
            return relatif
        try:
            r = obtenir(client, IMAGES + chemin)
        except ErreurSource as e:
            log.warning("affiche %s : %s", ident, e)
            return None
        if not r.content.startswith(b"\xff\xd8"):
            return None
        cible.parent.mkdir(parents=True, exist_ok=True)
        tmp = cible.with_name(f".{cible.name}.tmp")
        tmp.write_bytes(r.content)
        tmp.chmod(0o644)
        tmp.replace(cible)
        return relatif

    def _ranger_affiches(self, utiles: set[int], maintenant: datetime) -> None:
        """Oublie les fiches et affiches des films qui ne passent plus depuis deux mois."""
        for ident, c in list(self.cache["fiches"].items()):
            if int(ident) in utiles:
                c["vue"] = maintenant.isoformat()
            elif maintenant - datetime.fromisoformat(c.get("vue", c["le"])) > GARDER_AFFICHE:
                del self.cache["fiches"][ident]
                (self.public / DOSSIER_AFFICHES / f"{ident}.jpg").unlink(missing_ok=True)
