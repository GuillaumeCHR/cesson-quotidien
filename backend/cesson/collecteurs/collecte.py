"""Prochaines collectes des déchets (bac gris, bac jaune) pour les maisons de Cesson-Sévigné.

Source : `saisie/collecte.yaml`, règle recopiée du calendrier de Rennes Métropole. Le backend
publie les dates des 8 prochaines semaines, décalages des jours fériés appliqués. Aucun appel réseau.
"""

from __future__ import annotations

from datetime import date, datetime, timedelta
from pathlib import Path
from typing import Any

import yaml

from ..commun.collecteur import Collecteur, ErreurValidation
from ..commun.feries import ferie
from ..commun.horaires import JOURS, PARIS
from ..commun.modele import Modele

SAISIE = Path(__file__).resolve().parents[2] / "saisie" / "collecte.yaml"
HORIZON = timedelta(weeks=8)


class Flux(Modele):
    id: str
    nom: str
    bac: str  # gris, jaune
    regle: str  # « le lundi des semaines paires », en clair pour l'app


class Passage(Modele):
    flux: str
    date: date
    decale_par: str | None = None  # jour férié qui a décalé le passage


class Avis(Modele):
    du: date
    au: date
    texte: str


class DonneesCollecte(Modele):
    public: str
    flux: list[Flux]
    passages: list[Passage]
    avis: list[Avis] = []
    conseil: str | None = None
    info: str | None = None
    url: str | None = None
    verifie_le: date
    valable_au: date


NOMS_JOURS = ["lundi", "mardi", "mercredi", "jeudi", "vendredi", "samedi", "dimanche"]


def date_de_passage(jour_normal: date, decalage: bool) -> tuple[date, str | None]:
    """Décalage d'un jour si un férié tombe dans la semaine, ce jour-là ou avant."""
    if not decalage:
        return jour_normal, None
    lundi = jour_normal - timedelta(days=jour_normal.weekday())
    for i in range(jour_normal.weekday() + 1):
        nom = ferie(lundi + timedelta(days=i))
        if nom:
            return jour_normal + timedelta(days=1), nom
    return jour_normal, None


def passages(flux: list[dict], debut: date, fin: date, decalage: bool) -> list[Passage]:
    resultat = []
    lundi = debut - timedelta(days=debut.weekday() + 7)  # une semaine avant : un décalage peut tomber après « debut »
    while lundi <= fin:
        semaine = lundi.isocalendar().week
        for f in flux:
            if f["semaines"] != "toutes" and (semaine % 2 == 0) != (f["semaines"] == "paires"):
                continue
            d, motif = date_de_passage(lundi + timedelta(days=JOURS.index(f["jour"])), decalage)
            if debut <= d <= fin:
                resultat.append(Passage(flux=f["id"], date=d, decale_par=motif))
        lundi += timedelta(weeks=1)
    return sorted(resultat, key=lambda p: (p.date, p.flux))


class CollecteurCollecte(Collecteur[DonneesCollecte]):
    module = "collecte"
    source = "Rennes Métropole (calendrier de collecte), via le site de la ville ; saisie manuelle"
    licence = "Informations publiques de Rennes Métropole"
    url_source = "https://www.ville-cesson-sevigne.fr/gestion-des-dechets/"
    fraicheur = timedelta(hours=24)
    modele_donnees = DonneesCollecte

    def __init__(self, chemin: Path = SAISIE) -> None:
        self.chemin = chemin

    def telecharger(self, client: Any) -> Any:
        try:
            return yaml.safe_load(self.chemin.read_text(encoding="utf-8"))
        except (OSError, yaml.YAMLError) as e:
            raise ErreurValidation(f"{self.chemin.name} illisible : {e}") from e

    def valider(self, brut: Any) -> None:
        c = brut.get("collecte") if isinstance(brut, dict) else None
        if not isinstance(c, dict):
            raise ErreurValidation("clé « collecte » absente")
        for champ in ("verifie_le", "valable_du", "valable_au"):
            if not isinstance(c.get(champ), date):
                raise ErreurValidation(f"{champ} : date attendue (AAAA-MM-JJ)")
        if c["valable_au"] < c["valable_du"]:
            raise ErreurValidation("période de validité à l'envers")
        if not c.get("flux"):
            raise ErreurValidation("aucun flux de collecte")
        for f in c["flux"]:
            if not all(f.get(k) for k in ("id", "nom", "bac", "jour", "semaines")):
                raise ErreurValidation(f"flux incomplet : {f!r}")
            if f["jour"] not in JOURS or f["semaines"] not in ("paires", "impaires", "toutes"):
                raise ErreurValidation(f"{f['id']} : jour ou semaines inconnus")
        for a in c.get("avis") or []:
            if not isinstance(a.get("du"), date) or not isinstance(a.get("au"), date) or not a.get("texte"):
                raise ErreurValidation(f"avis incomplet : {a!r}")

    def convertir(self, brut: Any, maintenant: datetime) -> DonneesCollecte:
        c = brut["collecte"]
        aujourdhui = maintenant.astimezone(PARIS).date()
        debut, fin = max(aujourdhui, c["valable_du"]), min(aujourdhui + HORIZON, c["valable_au"])
        liste = passages(c["flux"], debut, fin, bool(c.get("decalage_jours_feries")))
        regle = {"paires": "des semaines paires", "impaires": "des semaines impaires", "toutes": "de chaque semaine"}
        return DonneesCollecte(
            public=c["public"],
            flux=[Flux(id=f["id"], nom=f["nom"], bac=f["bac"],
                       regle=f"le {NOMS_JOURS[JOURS.index(f['jour'])]} {regle[f['semaines']]}") for f in c["flux"]],
            passages=liste,
            avis=[Avis(**a) for a in c.get("avis") or [] if a["au"] >= aujourdhui],
            conseil=c.get("conseil"), info=c.get("info"), url=c.get("url"),
            verifie_le=c["verifie_le"], valable_au=c["valable_au"],
        )

    def valide_jusqu_a(self, donnees: DonneesCollecte, maintenant: datetime) -> datetime:
        return maintenant + timedelta(hours=36)
