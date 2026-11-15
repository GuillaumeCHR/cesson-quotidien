"""Familles : actualités publiques du portail familles de la ville et menus des restaurants scolaires.

Le portail familles (application de la ville) publie sans connexion une liste d'actualités. Certaines y
figurent marquées « privées » : elles sont destinées aux familles connectées, on ne les reprend pas.

Le menu du mois est une de ces actualités (« Menu novembre 2026 »), avec le PDF du prestataire en pièce jointe :
une page par semaine, une colonne par jour (lundi à vendredi), une ligne par rubrique (hors-d'œuvre, plat et
garnitures, produit laitier, dessert, goûter). On lit la position de chaque texte pour le ranger dans sa case.
Les pictogrammes (bio, végétarien, produit de saison) sont des images : ils ne sont pas repris.
"""

from __future__ import annotations

import html
import io
import re
from datetime import date, datetime, timedelta
from typing import Any

import httpx
from pdfminer.high_level import extract_pages
from pdfminer.layout import LTTextBox

from ..commun.collecteur import Collecteur, ErreurSource, ErreurValidation, obtenir
from ..commun.horaires import PARIS
from ..commun.modele import Modele

PORTAIL = "https://portail-familles.ville-cesson-sevigne.fr"
URL_ACTUALITES = f"{PORTAIL}/api/news-list/public"
MENUS_MAX = 2  # le mois en cours et, s'il est déjà publié, le suivant
LONGUEUR_RESUME = 220
JOURS = ["LUNDI", "MARDI", "MERCREDI", "JEUDI", "VENDREDI"]
RUBRIQUES = {  # libellé dans la grille -> champ du modèle
    "HORS D'ŒUVRE": "hors_doeuvre", "PLAT CHAUD": "plat", "PRODUIT LAITIER": "laitier", "DESSERT": "dessert",
    "GOUTERS": "gouter",
}


class ActuFamille(Modele):
    id: str
    titre: str
    resume: str | None = None
    categories: list[str] = []
    lien: str
    du: date | None = None
    au: date | None = None


class MenuDuJour(Modele):
    jour: date
    hors_doeuvre: list[str] = []
    plat: list[str] = []  # plat puis garnitures
    laitier: list[str] = []
    dessert: list[str] = []
    gouter: list[str] = []
    note: str | None = None  # jour sans repas : « 11 novembre, Armistice 1918 »


class DonneesFamilles(Modele):
    actualites: list[ActuFamille]
    menus: list[MenuDuJour]
    restaurants: str | None = None  # « Bourgchevreuil et Beausoleil »
    menu_pdf: str | None = None  # le PDF du mois en cours, pour tout voir


# --- Actualités ------------------------------------------------------------------------------------

def texte(fragment: str) -> str:
    t = re.sub(r"<!--.*?-->", " ", fragment or "", flags=re.S)
    t = re.sub(r"<(script|style)\b.*?</\1>", " ", t, flags=re.S)
    return re.sub(r"\s+", " ", html.unescape(re.sub(r"<[^>]+>", " ", t))).strip()


def court(t: str) -> str | None:
    t = re.sub(r"\s+", " ", t or "").strip()
    if not t:
        return None
    return t if len(t) <= LONGUEUR_RESUME else t[:LONGUEUR_RESUME].rsplit(" ", 1)[0].rstrip(" ,;:") + "…"


def jour(valeur: Any) -> date | None:
    v = str(valeur or "")
    return None if not v or v.startswith("0000") else date.fromisoformat(v[:10])


def est_menu(a: dict) -> bool:
    return bool(re.match(r"\s*menus?\b", str(a.get("titre") or ""), re.I))


def publiques(brut: list[dict], aujourdhui: date) -> list[dict]:
    """Actualités visibles sans connexion, en cours de diffusion."""
    res = []
    for a in brut:
        if str(a.get("estPrive")) == "True" or str(a.get("estVisible")) != "True":
            continue
        du, au = jour(a.get("dateDebut")), jour(a.get("dateFin"))
        if (du and du > aujourdhui) or (au and au < aujourdhui):
            continue
        res.append(a)
    return res


def pdf_joint(a: dict) -> str | None:
    m = re.search(r'href="(/api/files/\d+)"', a.get("contenu") or "")
    return PORTAIL + m.group(1) if m else None


# --- Menus ---------------------------------------------------------------------------------------

def lire_menus(pdf: bytes, reference: date) -> tuple[list[MenuDuJour], str | None]:
    """Une page par semaine. [reference] fixe l'année des dates « 16/11 » (la plus proche)."""
    menus, restaurants = [], None
    try:
        pages = list(extract_pages(io.BytesIO(pdf)))
    except Exception as e:  # pdfminer lève plusieurs types d'erreurs selon le défaut du fichier
        raise ErreurValidation(f"menu : PDF illisible ({e})") from e
    for page in pages:
        boites = [(o.x0, o.x1, o.y0, o.y1, re.sub(r"\s+", " ", o.get_text()).strip())
                  for o in page if isinstance(o, LTTextBox)]
        semaine = next((re.search(r"Semaine du (\d{2})/(\d{2})", t) for *_, t in boites if t.startswith("Semaine du")), None)
        colonnes = {t: (x0 + x1) / 2 for x0, x1, _, _, t in boites if t in JOURS}
        lignes = {RUBRIQUES[k]: (y0 + y1) / 2 for _, _, y0, y1, t in boites for k in RUBRIQUES if t.startswith(k)}
        if not semaine or len(colonnes) < 3 or len(lignes) < 4:
            continue  # page sans grille (légende, page de garde)
        restaurants = restaurants or next(
            (re.sub(r"(?i)^restaurants scolaires de ", "", t).title().replace(" Et ", " et ")
             for *_, t in boites if t.lower().startswith("restaurants scolaires")), None)
        lundi = annee_proche(int(semaine.group(1)), int(semaine.group(2)), reference)
        ordre = sorted(lignes.items(), key=lambda kv: -kv[1])  # du haut vers le bas
        bornes = {}  # rubrique -> (y haut, y bas)
        haut = min(y0 for _, _, y0, _, t in boites if t in JOURS)
        for i, (rub, y) in enumerate(ordre):
            bas = (y + ordre[i + 1][1]) / 2 if i + 1 < len(ordre) else y - (ordre[i - 1][1] - y) / 2
            bornes[rub] = (haut, bas)
            haut = bas
        cases: dict[tuple[str, str], list[tuple[float, str]]] = {}
        largeur = min(abs(a - b) for a in colonnes.values() for b in colonnes.values() if a != b)
        for x0, x1, y0, y1, t in boites:
            if x1 - x0 > largeur * 1.3 or t in JOURS or any(t.startswith(k) for k in RUBRIQUES):
                continue  # titres, légende sur plusieurs colonnes
            cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
            jour_ = min(colonnes, key=lambda j: abs(colonnes[j] - cx))
            if abs(colonnes[jour_] - cx) > largeur * 0.6:
                continue
            rub = next((r for r, (h, b) in bornes.items() if b <= cy < h), None)
            if rub:
                cases.setdefault((jour_, rub), []).append((-cy, t))
        for i, j in enumerate(JOURS):
            champs = {rub: [t for _, t in sorted(cases.get((j, rub), []))] for rub in RUBRIQUES.values()}
            textes = [t for v in champs.values() for t in v]
            if len(textes) == 1 and sum(1 for v in champs.values() if v) == 1 and not champs["laitier"]:
                # Une seule mention dans toute la colonne : jour férié ou sans service, pas un menu.
                note = re.sub(r"^(\d{1,2}) ([A-ZÉÛ]+)\b", lambda m: f"{m[1]} {m[2].lower()},", textes[0])
                menus.append(MenuDuJour(jour=lundi + timedelta(days=i), note=note))
            elif textes:
                menus.append(MenuDuJour(jour=lundi + timedelta(days=i), **champs))
    return menus, restaurants


def annee_proche(j: int, m: int, reference: date) -> date:
    return min((date(reference.year + d, m, j) for d in (-1, 0, 1)), key=lambda x: abs((x - reference).days))


# --- Collecteur ------------------------------------------------------------------------------------

class CollecteurFamilles(Collecteur[DonneesFamilles]):
    module = "familles"
    source = "Portail familles de la Ville de Cesson-Sévigné (actualités publiques, menus du prestataire)"
    licence = "Informations publiques de la Ville ; l'actualité se lit sur le portail"
    url_source = f"{PORTAIL}/news"
    fraicheur = timedelta(hours=12)
    modele_donnees = DonneesFamilles

    def telecharger(self, client: httpx.Client) -> Any:
        r = obtenir(client, URL_ACTUALITES)
        try:
            actus = r.json()
        except ValueError as e:
            raise ErreurValidation(f"portail familles : réponse non JSON : {e}") from e
        if not isinstance(actus, list):
            raise ErreurValidation("portail familles : liste d'actualités attendue")
        aujourdhui = datetime.now(PARIS).date()
        menus = sorted((a for a in publiques(actus, aujourdhui) if est_menu(a) and pdf_joint(a)),
                       key=lambda a: str(a.get("dateDebut")))[-MENUS_MAX:]
        pdfs = {}
        for a in menus:
            try:
                contenu = obtenir(client, pdf_joint(a)).content
            except ErreurSource:
                continue  # le menu manque, les actualités restent publiées
            if contenu.startswith(b"%PDF"):
                pdfs[str(a["idActualite"])] = contenu
        return {"actualites": actus, "pdfs": pdfs}

    def valider(self, brut: Any) -> None:
        for a in brut["actualites"]:
            if not isinstance(a, dict) or not a.get("titre") or a.get("idActualite") in (None, ""):
                raise ErreurValidation("actualité sans titre ou sans identifiant")

    def convertir(self, brut: Any, maintenant: datetime) -> DonneesFamilles:
        aujourdhui = maintenant.astimezone(PARIS).date()
        visibles = publiques(brut["actualites"], aujourdhui)
        menus, restaurants, menu_pdf = {}, None, None
        for a in sorted((a for a in visibles if est_menu(a)), key=lambda a: str(a.get("dateDebut"))):
            pdf = brut["pdfs"].get(str(a["idActualite"]))
            if not pdf:
                continue
            reference = jour(a.get("dateDebut")) or aujourdhui
            jours, r = lire_menus(pdf, reference + timedelta(days=10))
            if jours and not menu_pdf or (jours and jours[0].jour <= aujourdhui):
                menu_pdf = pdf_joint(a)
            restaurants = restaurants or r
            menus.update({m.jour: m for m in jours})
        if brut["pdfs"] and not menus:
            raise ErreurValidation("menu du mois illisible : la mise en page du PDF a-t-elle changé ?")
        debut_semaine = aujourdhui - timedelta(days=aujourdhui.weekday())
        actualites = [ActuFamille(
            id=str(a["idActualite"]), titre=a["titre"].strip(),
            resume=court(a.get("resume") or texte(a.get("contenu") or "")),
            categories=[str(c.get("titre") or "").strip().capitalize() for c in a.get("_categories") or [] if c.get("titre")],
            lien=f"{PORTAIL}/news/{a['idActualite']}", du=jour(a.get("dateDebut")), au=jour(a.get("dateFin")),
        ) for a in visibles if not est_menu(a)]
        # Datées (en cours) d'abord, les plus récentes en tête ; puis les pages permanentes.
        actualites.sort(key=lambda a: (a.du is None, -(a.du.toordinal() if a.du else 0), a.titre))
        return DonneesFamilles(
            actualites=actualites,
            menus=sorted((m for j, m in menus.items() if j >= debut_semaine), key=lambda m: m.jour),
            restaurants=restaurants, menu_pdf=menu_pdf,
        )

    def valide_jusqu_a(self, donnees: DonneesFamilles, maintenant: datetime) -> datetime:
        return maintenant + timedelta(hours=36)
