"""Usage de l'eau à Cesson-Sévigné : restrictions sécheresse en vigueur et consignes d'Eau du Bassin Rennais.

- Restrictions : API VigiEau (ministère de la Transition écologique), qui publie pour chaque commune le niveau
  de gravité (vigilance, alerte, alerte renforcée, crise) et la liste des usages limités ou interdits, d'après
  les arrêtés préfectoraux. On retient l'eau du robinet (zone AEP) et les usages qui concernent les particuliers.
- Consignes : parmi les articles d'Eau du Bassin Rennais, seuls ceux qui portent sur l'usage de l'eau
  (sécheresse, restrictions, économies d'eau) ; le reste de leur actualité n'est pas repris.
"""

from __future__ import annotations

import re
from datetime import datetime, timedelta
from typing import Any

import httpx

from ..commun.collecteur import Collecteur, ErreurSource, ErreurValidation, obtenir
from ..commun.modele import Modele
from .actus import lire_flux

URL_VIGIEAU = "https://api.vigieau.gouv.fr/api/zones"
URL_EBR = "https://www.eaudubassinrennais-collectivite.fr/feed/"
INSEE = "35051"
NIVEAUX = ["aucune", "vigilance", "alerte", "alerte_renforcee", "crise"]
# Mots qui désignent un article sur l'usage de l'eau (titre en minuscules, sans accents sur « economi »).
MOTS_CONSIGNES = ("sécheresse", "secheresse", "restriction", "économi", "economi", "usage de l", "consigne", "arrosage")
AGE_MAX_CONSIGNE = timedelta(days=150)
LONGUEUR_DESCRIPTION = 280


class Usage(Modele):
    thematique: str  # Arroser, Nettoyer, Remplir ou vidanger…
    nom: str
    regle: str  # « Interdit. », « Interdiction de 8h à 20h. »…


class Consigne(Modele):
    titre: str
    date: datetime
    lien: str


class DonneesUsagesEau(Modele):
    niveau: str  # aucune, vigilance, alerte, alerte_renforcee, crise
    zone: str | None = None
    depuis: datetime | None = None
    jusqu_au: datetime | None = None
    arrete: str | None = None  # lien vers l'arrêté préfectoral (PDF)
    usages: list[Usage] = []
    consignes: list[Consigne] = []


def _propre(texte: str, limite: int) -> str:
    t = re.sub(r"\s+", " ", texte).strip()
    return t if len(t) <= limite else t[:limite].rsplit(" ", 1)[0].rstrip(" ,;:") + "…"


def niveau_vigieau(valeur: str | None) -> str:
    v = (valeur or "").lower().replace(" ", "_").replace("é", "e")
    return v if v in NIVEAUX else "aucune"


class CollecteurUsagesEau(Collecteur[DonneesUsagesEau]):
    module = "usages_eau"
    source = "VigiEau (arrêtés sécheresse) et Eau du Bassin Rennais"
    licence = "Licence Ouverte 2.0 (VigiEau) ; titres © Eau du Bassin Rennais"
    url_source = "https://vigieau.gouv.fr/"
    fraicheur = timedelta(hours=1)
    modele_donnees = DonneesUsagesEau

    def telecharger(self, client: httpx.Client) -> Any:
        try:
            r = obtenir(client, URL_VIGIEAU, params={"commune": INSEE, "profil": "particulier", "zoneType": "AEP"})
            zones = r.json()
        except ErreurSource as e:
            # VigiEau répond 404 quand aucune restriction ne s'applique à la commune.
            if "404" not in str(e):
                raise
            zones = []
        except ValueError as e:
            raise ErreurValidation(f"VigiEau : réponse non JSON : {e}") from e
        try:
            ebr = obtenir(client, URL_EBR).content
        except ErreurSource:
            ebr = None  # les consignes sont un plus : sans elles, les restrictions restent publiées
        return {"zones": zones, "ebr": ebr}

    def valider(self, brut: Any) -> None:
        zones = brut["zones"]
        if isinstance(zones, dict):
            zones = [zones]
        if not isinstance(zones, list):
            raise ErreurValidation("VigiEau : liste de zones attendue")
        for z in zones:
            if not isinstance(z, dict) or not isinstance(z.get("usages", []), list):
                raise ErreurValidation("VigiEau : zone sans liste d'usages")
        brut["zones"] = zones

    def convertir(self, brut: Any, maintenant: datetime) -> DonneesUsagesEau:
        zones = brut["zones"]
        # Plusieurs zones possibles : la plus grave s'applique.
        zone = max(zones, key=lambda z: NIVEAUX.index(niveau_vigieau(z.get("niveauGravite"))), default=None)
        consignes = []
        if brut.get("ebr"):
            for a in lire_flux(brut["ebr"], "eau", 30):
                if any(m in a.titre.lower() for m in MOTS_CONSIGNES) and maintenant - a.date <= AGE_MAX_CONSIGNE:
                    consignes.append(Consigne(titre=a.titre, date=a.date, lien=a.lien))
        if zone is None:
            return DonneesUsagesEau(niveau="aucune", consignes=consignes[:3])
        arrete = zone.get("arrete") or {}

        def date(cle):
            return datetime.fromisoformat(arrete[cle].replace("Z", "+00:00")) if arrete.get(cle) else None

        usages = [Usage(thematique=u.get("thematique") or "Autres usages", nom=_propre(u["nom"], 160),
                        regle=_propre(u.get("description") or "", LONGUEUR_DESCRIPTION))
                  for u in zone.get("usages", []) if u.get("concerneParticulier") and u.get("nom")]
        return DonneesUsagesEau(
            niveau=niveau_vigieau(zone.get("niveauGravite")),
            zone=zone.get("nom"),
            depuis=date("dateDebutValidite"),
            jusqu_au=date("dateFinValidite"),
            arrete=arrete.get("cheminFichier"),
            usages=usages,
            consignes=consignes[:3],
        )

    def valide_jusqu_a(self, donnees: DonneesUsagesEau, maintenant: datetime) -> datetime:
        return maintenant + timedelta(hours=12)
