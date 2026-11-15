"""Séances du cinéma Le Sévigné, lues dans la grille mensuelle que la salle publie (PDF tiré d'un tableur).

La grille est un tableau : une ligne d'en-tête par semaine (« du 4 au 10 Novembre », puis « M 4 », « J 5 »…),
puis une ligne par film, ses horaires dans la colonne de leur jour. On lit la position de chaque mot sur la
page (pdfminer) :

- chaque horaire va au jour de la colonne la plus proche, et au film dont le titre est verticalement le plus
  proche (un titre sur deux lignes est centré sur sa ligne d'horaires ; une case sur deux lignes, « 14h30 »
  puis « 4€50 », est centrée sur la ligne de son film) ;
- « VO » et « Sortie nationale » deviennent des étiquettes ; une ligne commençant par « * » est la note des
  séances marquées d'un astérisque ; un prix dans une case (« 4€50 ») va à la séance juste au-dessus ;
- les annonces (lignes qui traversent le tableau) et la ligne des tarifs sont reprises telles quelles.

Si la mise en page change au point qu'aucune séance ne se lit, la collecte échoue : la version précédente
reste publiée et l'alerte de santé prévient.
"""

from __future__ import annotations

import io
import re
import unicodedata
from datetime import date, datetime, timedelta
from pathlib import Path
from typing import Any

import httpx
import yaml
from pdfminer.high_level import extract_pages
from pdfminer.layout import LTTextContainer, LTTextLine

from ..commun.collecteur import Collecteur, ErreurSource, ErreurValidation, obtenir
from ..commun.horaires import PARIS
from ..commun.modele import Modele

SAISIE = Path(__file__).resolve().parents[2] / "saisie" / "cinema.yaml"
# Le site de la salle publie chaque mois un nouveau fichier Drive, titré « grille novembre 26 » : on y lit le lien
# du mois. Le lien saisi dans cinema.yaml sert de secours si le site est injoignable ou change.
URL_SITE = "https://www.cinesevigne.fr/"
MOIS_GRILLE = ["janvier", "fevrier", "mars", "avril", "mai", "juin", "juillet", "aout", "septembre", "octobre",
               "novembre", "decembre"]
COLONNE_TITRES = 270  # au-delà, on est dans les colonnes des jours
TOLERANCE_LIGNE = 2.5

MOIS = {"janvier": 1, "février": 2, "fevrier": 2, "mars": 3, "avril": 4, "mai": 5, "juin": 6, "juillet": 7,
        "août": 8, "aout": 8, "septembre": 9, "octobre": 10, "novembre": 11, "décembre": 12, "decembre": 12}
ABREGES = {"janv": 1, "févr": 2, "fevr": 2, "fév": 2, "avr": 4, "juil": 7, "sept": 9, "oct": 10, "nov": 11, "déc": 12, "dec": 12}
_HEURE = re.compile(r"^(\d{1,2})h(\d{2})(\*?)$")
_PRIX = re.compile(r"^(\d{1,2})\s?€\s?(\d{0,2})$")
_JOUR = re.compile(r"\b([LMJVSD])\s*(\d{1,2})\b")


class Seance(Modele):
    debut: datetime
    tarif: str | None = None  # tarif particulier de la séance (« 4,50 € »)
    note: str | None = None  # « Présenté par… »


class Film(Modele):
    titre: str
    vo: bool = False
    sortie_nationale: bool = False
    seances: list[Seance]


class DonneesCinema(Modele):
    salle: str
    adresse: str | None = None
    films: list[Film]  # séances à venir seulement, films triés par prochaine séance
    annonces: list[str] = []
    tarifs: str | None = None
    grille: str  # lien vers la grille d'origine


class Mot:
    def __init__(self, y: float, x0: float, x1: float, texte: str) -> None:
        self.y, self.x0, self.x1, self.texte = y, x0, x1, texte

    @property
    def x(self) -> float:
        return (self.x0 + self.x1) / 2


def mots_du_pdf(pdf: bytes) -> list[Mot]:
    mots = []
    for page in extract_pages(io.BytesIO(pdf)):
        for bloc in page:
            if isinstance(bloc, LTTextContainer):
                for ligne in bloc:
                    if isinstance(ligne, LTTextLine) and (t := " ".join(ligne.get_text().split())):
                        mots.append(Mot(page.height - ligne.y1, ligne.x0, ligne.x1, t))
        break  # la grille tient sur une page
    return sorted(mots, key=lambda m: (m.y, m.x0))


def _mois_de(texte: str) -> list[int]:
    t = texte.lower()
    trouves = []
    for m in re.finditer(r"[a-zéèûô]+", t):
        mot = m.group(0)
        if mot in MOIS:
            trouves.append(MOIS[mot])
        elif mot in ABREGES:
            trouves.append(ABREGES[mot])
    return trouves


def lire_grille(pdf: bytes, aujourdhui: date) -> dict:
    """Films et séances de la grille, avec leurs dates complètes."""
    mots = mots_du_pdf(pdf)
    entetes = [m for m in mots if m.texte.lower().startswith("du ") and m.x0 < COLONNE_TITRES and _mois_de(m.texte)]
    if not entetes:
        raise ErreurValidation("aucune semaine dans la grille (mise en page changée ?)")
    # Colonnes des jours : celles d'un en-tête aux sept jours bien séparés.
    colonnes = None
    for e in entetes:
        jours = [m for m in mots if abs(m.y - e.y) < TOLERANCE_LIGNE and m.x0 >= COLONNE_TITRES and _JOUR.fullmatch(m.texte)]
        if len(jours) == 7:
            colonnes = [j.x for j in sorted(jours, key=lambda j: j.x)]
            break
    if colonnes is None:
        raise ErreurValidation("colonnes des jours introuvables")

    films: dict[str, dict] = {}
    annonces: list[str] = []
    tarifs = next((m.texte for m in mots if m.texte.lower().startswith("tarifs")), None)
    for i, e in enumerate(entetes):
        y_fin = entetes[i + 1].y if i + 1 < len(entetes) else float("inf")
        # Dates des sept colonnes : numéros de l'en-tête, mois du libellé, passage au mois suivant si le numéro baisse.
        texte_jours = " ".join(m.texte for m in mots if abs(m.y - e.y) < TOLERANCE_LIGNE and m.x0 >= COLONNE_TITRES)
        numeros = [int(n) for _, n in _JOUR.findall(texte_jours)]
        if len(numeros) != 7:
            raise ErreurValidation(f"semaine « {e.texte} » : {len(numeros)} jours lus au lieu de 7")
        mois = _mois_de(e.texte)[0]
        annee = aujourdhui.year + (1 if mois < aujourdhui.month - 6 else -1 if mois > aujourdhui.month + 6 else 0)
        dates = []
        for n in numeros:
            if dates and n < dates[-1].day:
                mois, annee = (1, annee + 1) if mois == 12 else (mois + 1, annee)
            dates.append(date(annee, mois, n))

        corps = [m for m in mots if e.y + TOLERANCE_LIGNE < m.y < y_fin - TOLERANCE_LIGNE]
        heures = [m for m in corps if m.x0 >= COLONNE_TITRES and _HEURE.match(m.texte)]
        prix = [m for m in corps if m.x0 >= COLONNE_TITRES and _PRIX.match(m.texte)]
        lignes_titres = []
        for m in corps:
            if m.x0 >= COLONNE_TITRES or m.texte.lower().startswith("tarifs"):
                continue
            if m.x1 > COLONNE_TITRES + 40:  # traverse le tableau : annonce
                annonces.append(m.texte)
            else:
                lignes_titres.append(m)

        # Blocs de film : une ligne continue la précédente si une ligne d'horaires passe exactement entre les deux
        # (titre sur deux lignes), ou si c'est une note (« * ») et sa suite.
        rangs = sorted({round(h.y, 1) for h in heures})
        blocs: list[list[Mot]] = []
        for m in lignes_titres:
            if blocs:
                prec = blocs[-1][-1]
                milieu = (prec.y + m.y) / 2
                dans_la_note = any(l.texte.startswith("*") for l in blocs[-1])
                if m.texte.startswith("*") or dans_la_note or any(abs(r - milieu) < TOLERANCE_LIGNE for r in rangs):
                    blocs[-1].append(m)
                    continue
            blocs.append([m])
        # Un bloc sans aucun horaire à sa hauteur est la suite d'une note : on le rattache au précédent.
        fusionnes: list[list[Mot]] = []
        for b in blocs:
            centre = (b[0].y + b[-1].y) / 2
            if fusionnes and not any(abs(h.y - centre) < 10 for h in heures) and \
                    any(l.texte.startswith("*") for l in fusionnes[-1]):
                fusionnes[-1].extend(b)
            else:
                fusionnes.append(b)
        if not fusionnes:
            continue
        centres = [(b[0].y + b[-1].y) / 2 for b in fusionnes]

        for h in heures:
            k = min(range(len(fusionnes)), key=lambda j: abs(centres[j] - h.y))
            b = fusionnes[k]
            titre_brut = " ".join(l.texte for l in b if not l.texte.startswith("*") and not any(
                x.texte.startswith("*") and x.y < l.y for x in b))
            note = " ".join(l.texte for l in b if l.texte.startswith("*") or any(
                x.texte.startswith("*") and x.y < l.y for x in b)).lstrip("* ").strip() or None
            jour = dates[min(range(7), key=lambda c: abs(colonnes[c] - h.x))]
            hh, mm, etoile = _HEURE.match(h.texte).groups()
            tarif = None
            for p in prix:
                if abs(p.x - h.x) < 20 and 0 < p.y - h.y < 16:
                    euros, cents = _PRIX.match(p.texte).groups()
                    tarif = f"{euros},{(cents or '00').ljust(2, '0')} €"
            f = films.setdefault(cle(titre_brut), {"titre": titre_brut, "seances": []})
            f["seances"].append(Seance(
                debut=datetime(jour.year, jour.month, jour.day, int(hh), int(mm), tzinfo=PARIS),
                tarif=tarif, note=note if etoile else None,
            ))
    # Une annonce sur deux lignes : la seconde continue la première tant que celle-ci ne finit pas une phrase.
    recollees: list[str] = []
    for a in annonces:
        if recollees and not recollees[-1].endswith((".", "!", "?")):
            recollees[-1] = f"{recollees[-1]} {a}"
        else:
            recollees.append(a)
    return {"films": list(films.values()), "annonces": recollees, "tarifs": tarifs}


def cle(t: str) -> str:
    t = "".join(c for c in unicodedata.normalize("NFD", t.lower()) if not unicodedata.combining(c))
    return re.sub(r"[^a-z0-9]", "", t)


def nettoyer_titre(brut: str) -> tuple[str, bool, bool]:
    """« L'affaire Marie-Claire Sortie Nationale » → (« L'affaire Marie-Claire », VO non, sortie nationale oui)."""
    t = brut
    sortie = bool(re.search(r"sortie\s+nationale", t, re.I))
    t = re.sub(r"\s*sortie\s+nationale", "", t, flags=re.I)
    vo = bool(re.search(r"[–-]\s*VO\b", t))
    t = re.sub(r"\s*[–-]\s*VO\b", "", t)
    return re.sub(r"\s+", " ", t).strip(" –-"), vo, sortie


def grilles_du_site(page: str) -> list[tuple[date, str]]:
    """Liens « grille <mois> <aa> » du site de la salle : (premier jour du mois, lien Drive)."""
    res = []
    for lien, titre in re.findall(r'href="(https://drive\.google\.com/[^"]+)"[^>]*title="([^"]+)"', page):
        t = "".join(c for c in unicodedata.normalize("NFD", titre.lower()) if not unicodedata.combining(c))
        m = re.match(r"grille\s+([a-z]+)\s+(\d{2,4})", t)
        if m and m.group(1) in MOIS_GRILLE:
            annee = int(m.group(2)) + (2000 if len(m.group(2)) == 2 else 0)
            res.append((date(annee, MOIS_GRILLE.index(m.group(1)) + 1, 1), lien))
    return res


def grille_du_mois(grilles: list[tuple[date, str]], aujourdhui: date) -> str | None:
    """La grille du mois en cours ; à défaut, la plus récente déjà commencée, puis la prochaine."""
    mois = aujourdhui.replace(day=1)
    passees = sorted((g for g in grilles if g[0] <= mois), reverse=True)
    futures = sorted(g for g in grilles if g[0] > mois)
    choix = (passees or futures or [None])[0]
    return choix[1] if choix else None


def id_drive(url: str) -> str | None:
    m = re.search(r"/d/([A-Za-z0-9_-]{10,})", url) or re.search(r"[?&]id=([A-Za-z0-9_-]{10,})", url)
    return m.group(1) if m else None


class CollecteurCinema(Collecteur[DonneesCinema]):
    module = "cinema"
    source = "Cinéma Le Sévigné (grille mensuelle des séances)"
    licence = "Informations publiques du cinéma Le Sévigné"
    url_source = None
    fraicheur = timedelta(hours=12)
    modele_donnees = DonneesCinema

    def __init__(self, chemin: Path = SAISIE) -> None:
        self.config = (yaml.safe_load(chemin.read_text(encoding="utf-8")) or {}).get("cinema") or {}

    def telecharger(self, client: httpx.Client) -> Any:
        lien = self.config.get("grille", "")
        try:
            lien = grille_du_mois(grilles_du_site(obtenir(client, URL_SITE).text), datetime.now(PARIS).date()) or lien
        except ErreurSource:
            pass  # site injoignable : le lien saisi prend le relais
        ident = id_drive(lien)
        if not ident:
            raise ErreurValidation("lien de la grille illisible (site de la salle et saisie/cinema.yaml)")
        pdf = obtenir(client, "https://drive.google.com/uc", params={"export": "download", "id": ident}).content
        if not pdf.startswith(b"%PDF"):
            raise ErreurValidation("la grille téléchargée n'est pas un PDF (lien privé ou déplacé ?)")
        return {"pdf": pdf, "lien": lien}

    def valider(self, brut: Any) -> None:
        pass  # la lecture elle-même valide : voir convertir()

    def convertir(self, brut: Any, maintenant: datetime) -> DonneesCinema:
        aujourdhui = maintenant.astimezone(PARIS).date()
        g = lire_grille(brut["pdf"], aujourdhui)
        if not any(f["seances"] for f in g["films"]):
            raise ErreurValidation("aucune séance lue dans la grille")
        debut_journee = datetime.combine(aujourdhui, datetime.min.time(), PARIS)
        films = []
        for f in g["films"]:
            seances = sorted((s for s in f["seances"] if s.debut >= debut_journee), key=lambda s: s.debut)
            if not seances:
                continue
            titre, vo, sortie = nettoyer_titre(f["titre"])
            films.append(Film(titre=titre, vo=vo, sortie_nationale=sortie, seances=seances))
        films.sort(key=lambda f: f.seances[0].debut)
        return DonneesCinema(salle=self.config.get("nom", "Cinéma Le Sévigné"), adresse=self.config.get("adresse"),
                             films=films, annonces=g["annonces"], tarifs=g["tarifs"], grille=brut["lien"])

    def valide_jusqu_a(self, donnees: DonneesCinema, maintenant: datetime) -> datetime:
        return maintenant + timedelta(days=2)
