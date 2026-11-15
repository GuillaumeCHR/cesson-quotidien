"""Vigilance crues de la Vilaine à Cesson-Sévigné (Vigicrues, SCHAPI).

Source : le flux national des tronçons (`InfoVigiCru.geojson`), un niveau par tronçon :
1 vert, 2 jaune, 3 orange, 4 rouge. La station de Pont Briand (J709063002) est sur le tronçon
BT9 « Vilaine amont », territoire 8 « Vilaine-Côtiers Bretons ».

Le flux ne donne ni l'heure ni le texte du bulletin : on publie le niveau, et depuis quand ce
collecteur l'observe. Le bulletin officiel reste sur vigicrues.gouv.fr, vers lequel la carte renvoie.

Cadence : toutes les 15 min. C'est ce module, et lui seul, qui portera l'alerte crue.
"""

from __future__ import annotations

from datetime import datetime, timedelta
from enum import StrEnum
from typing import Any

import httpx

from ..commun.collecteur import Changement, Collecteur, ErreurValidation, Resultat, obtenir
from ..commun.modele import Alerte, Lieu, Modele, NiveauAlerte
from ..commun.publication import Publieur
from ..commun.sante import Sante

URL = "https://www.vigicrues.gouv.fr/services/InfoVigiCru.geojson"
CODE_TRONCON = "BT9"
NOM_TRONCON = "Vilaine amont"
# Le flux national compte environ 340 tronçons ; moins de 150 signale une réponse tronquée.
MIN_TRONCONS = 150

TRONCON = Lieu(
    id=f"vigicrues:{CODE_TRONCON}",
    nom=NOM_TRONCON,
    type="troncon_vigicrues",
    source="Vigicrues",
    # Les pages de tronçon n'ont pas d'adresse stable ; celle de la station affiche la vigilance du tronçon.
    url="https://www.vigicrues.gouv.fr/station/J709063002",
)


class Vigilance(StrEnum):
    VERT = "vert"
    JAUNE = "jaune"
    ORANGE = "orange"
    ROUGE = "rouge"


PAR_CODE = {1: Vigilance.VERT, 2: Vigilance.JAUNE, 3: Vigilance.ORANGE, 4: Vigilance.ROUGE}
ORDRE = list(PAR_CODE.values())
ALERTE = {Vigilance.JAUNE: NiveauAlerte.JAUNE, Vigilance.ORANGE: NiveauAlerte.ORANGE,
          Vigilance.ROUGE: NiveauAlerte.ROUGE}


class DonneesVigicrues(Modele):
    troncon: Lieu
    vigilance: Vigilance
    # Première collecte où ce niveau a été vu (le flux ne date pas le bulletin).
    depuis: datetime
    # Présente dès le jaune, au format commun que lira le moteur d'alertes.
    alerte: Alerte | None = None


def _troncons_cesson(brut: dict) -> list[dict]:
    return [f["properties"] for f in brut["features"]
            if isinstance(f, dict) and isinstance(f.get("properties"), dict)
            and f["properties"].get("CdEntCru") == CODE_TRONCON]


class CollecteurVigicrues(Collecteur[DonneesVigicrues]):
    module = "vigicrues"
    source = "Vigicrues (SCHAPI)"
    licence = "Licence Ouverte 2.0"
    url_source = "https://www.vigicrues.gouv.fr/territoire/8"
    fraicheur = timedelta(minutes=15)
    modele_donnees = DonneesVigicrues

    def __init__(self) -> None:
        self._precedent_vigilance: tuple[Vigilance, datetime] | None = None

    def telecharger(self, client: httpx.Client) -> Any:
        r = obtenir(client, URL)
        try:
            return r.json()
        except ValueError as e:
            raise ErreurValidation(f"réponse non JSON : {e}") from e

    def valider(self, brut: Any) -> None:
        if not isinstance(brut, dict) or not isinstance(brut.get("features"), list):
            raise ErreurValidation("champ « features » absent")
        if len(brut["features"]) < MIN_TRONCONS:
            raise ErreurValidation(f"{len(brut['features'])} tronçon(s), moins que les {MIN_TRONCONS} attendus")
        troncons = _troncons_cesson(brut)
        if not troncons:
            raise ErreurValidation(f"tronçon {CODE_TRONCON} absent du flux")
        for p in troncons:
            if p.get("NivInfViCr") not in PAR_CODE:
                raise ErreurValidation(f"niveau de vigilance inattendu : {p.get('NivInfViCr')!r}")

    def convertir(self, brut: Any, maintenant: datetime) -> DonneesVigicrues:
        # Un tronçon peut être découpé en plusieurs géométries : on retient le niveau le plus élevé.
        vigilance = PAR_CODE[max(p["NivInfViCr"] for p in _troncons_cesson(brut))]
        depuis = maintenant
        if self._precedent_vigilance and self._precedent_vigilance[0] == vigilance:
            depuis = self._precedent_vigilance[1]
        alerte = None
        if vigilance in ALERTE:
            alerte = Alerte(
                id=f"vigicrues:{CODE_TRONCON}:{vigilance}",
                niveau=ALERTE[vigilance],
                titre=f"Vigilance crues {vigilance} sur la {NOM_TRONCON}",
                zone=NOM_TRONCON,
                debut=depuis,
                source="Vigicrues",
            )
        return DonneesVigicrues(troncon=TRONCON, vigilance=vigilance, depuis=depuis, alerte=alerte)

    def executer(self, publieur: Publieur, sante: Sante, client: httpx.Client | None = None,
                 maintenant: datetime | None = None) -> Resultat:
        # « depuis » se reporte d'une collecte à l'autre tant que le niveau ne change pas.
        ancien = self._precedent(publieur)
        if ancien is not None:
            self._precedent_vigilance = (ancien.donnees.vigilance, ancien.donnees.depuis)
        return super().executer(publieur, sante, client, maintenant)

    def valide_jusqu_a(self, donnees: DonneesVigicrues, maintenant: datetime) -> datetime:
        # Trois collectes manquées et la carte s'affiche comme ancienne : sur la sécurité, on ne
        # laisse pas croire qu'un niveau vieux d'une heure est le niveau actuel.
        return maintenant + 3 * self.fraicheur

    def comparer(self, ancien: DonneesVigicrues | None, nouveau: DonneesVigicrues) -> list[Changement]:
        if ancien is None or ancien.vigilance == nouveau.vigilance:
            return []
        sens = "hausse" if ORDRE.index(nouveau.vigilance) > ORDRE.index(ancien.vigilance) else "baisse"
        return [Changement(self.module, "vigilance", f"{ancien.vigilance} → {nouveau.vigilance}",
                           {"sens": sens, "troncon": CODE_TRONCON, "vigilance": str(nouveau.vigilance)})]
