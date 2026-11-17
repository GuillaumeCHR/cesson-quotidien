"""Déchets acceptés à la déchèterie de Cesson-Sévigné (jeu de données de Rennes Métropole).

Les horaires restent dans `saisie/equipements.yaml` (module « equipements ») : le jeu de données n'en publie pas.
Il dit en revanche, champ par champ (1 = oui, 0 = non), quels déchets la déchèterie accepte, et s'il y a un
espace réemploi. On publie la liste lisible des déchets acceptés, et les refus qui méritent d'être connus avant
de charger la voiture. Données qui bougent peu : collecte quotidienne, pour suivre une fermeture exceptionnelle.
"""

from __future__ import annotations

from datetime import datetime, timedelta
from typing import Any

import httpx

from ..commun.collecteur import Collecteur, ErreurValidation, obtenir
from ..commun.modele import Modele

URL = "https://data.rennesmetropole.fr/api/explore/v2.1/catalog/datasets/decheteries_plateformes_vegetaux/records"
INSEE = "35051"

# Champ du jeu de données -> libellé, dans l'ordre d'affichage (les apports les plus courants d'abord).
# Les libellés reprennent la description officielle des champs.
ACCEPTES = {
    "dv": "Végétaux",
    "f_souches": "Souches",
    "f_bois": "Bois",
    "f_mobilier": "Meubles",
    "f_dee": "Appareils électriques et électroniques",
    "f_ferraille": "Ferraille",
    "f_gravats": "Gravats",
    "f_platre": "Plâtre",
    "f_carton": "Cartons",
    "f_jm": "Journaux et magazines",
    "f_textile": "Textiles (conteneur)",
    "f_incinerables": "Objets incinérables",
    "f_tt_vt": "Tout-venant",
    "f_plastrigides": "Plastiques rigides",
    "f_toxique": "Déchets dangereux ou toxiques",
    "f_huile_f": "Huile alimentaire",
    "f_huile_v": "Huile de vidange",
    "f_batteries": "Batteries",
    "f_piles": "Piles",
    "f_cart_impr": "Cartouches d'imprimante vides",
    "f_pneus": "Pneus",
    "f_amiante": "Amiante",
    "f_verre": "Verre",
}
# Refus utiles à connaître : on les publie seulement s'ils le sont (le tout-venant, remplacé par les
# « incinérables », n'est pas listé : ce serait plus déroutant qu'utile).
REFUS_SIGNALES = {"f_verre": "Verre (colonnes à verre)", "f_plastrigides": "Plastiques rigides"}


class DonneesDecheterie(Modele):
    nom: str
    acceptes: list[str]
    refuses: list[str]
    reemploi: bool  # objets en bon état déposés pour une seconde vie
    fermeture_exceptionnelle: str | None = None


def _oui(v: Any) -> bool:
    return v in (1, "1", True)


class CollecteurDecheterie(Collecteur[DonneesDecheterie]):
    module = "decheterie"
    source = "Rennes Métropole (déchèteries et plateformes végétaux)"
    licence = "ODbL"
    url_source = "https://metropole.rennes.fr/pratique/infos-demarches/dechets-proprete/les-decheteries-de-rennes-metropole/"
    fraicheur = timedelta(days=1)
    modele_donnees = DonneesDecheterie

    def telecharger(self, client: httpx.Client) -> Any:
        r = obtenir(client, URL, params={"where": f'code_insee="{INSEE}"', "limit": 20})
        try:
            return r.json()
        except ValueError as e:
            raise ErreurValidation(f"réponse non JSON : {e}") from e

    @staticmethod
    def _fiche(brut: Any) -> dict:
        fiches = [r for r in (brut.get("results") or []) if r.get("type") == "Déchèterie" and r.get("etat") == "actif"]
        if len(fiches) != 1:
            raise ErreurValidation(f"{len(fiches)} déchèterie(s) active(s) à Cesson au lieu d'une")
        return fiches[0]

    def valider(self, brut: Any) -> None:
        if not isinstance(brut, dict):
            raise ErreurValidation("réponse inattendue")
        f = self._fiche(brut)
        manquants = [c for c in ACCEPTES if c != "dv" and c not in f]
        if manquants:
            raise ErreurValidation(f"champs absents : {', '.join(manquants)}")

    def convertir(self, brut: Any, maintenant: datetime) -> DonneesDecheterie:
        f = self._fiche(brut)
        oui = {c for c in ACCEPTES if c != "dv" and _oui(f.get(c))}
        if _oui(f.get("dv_pf")) or _oui(f.get("dv_caisson")):
            oui.add("dv")
        if len(oui) < 5:
            raise ErreurValidation(f"seulement {len(oui)} type(s) de déchets acceptés")
        fermeture = (f.get("fermeture_except") or "").strip()
        return DonneesDecheterie(
            nom=f"Déchèterie de {f.get('nom_court') or 'Cesson-Sévigné'}",
            acceptes=[lib for c, lib in ACCEPTES.items() if c in oui],
            refuses=[lib for c, lib in REFUS_SIGNALES.items() if c not in oui],
            reemploi=_oui(f.get("reemploi")),
            fermeture_exceptionnelle=fermeture or None,
        )

    def valide_jusqu_a(self, donnees: DonneesDecheterie, maintenant: datetime) -> datetime:
        return maintenant + timedelta(days=7)
