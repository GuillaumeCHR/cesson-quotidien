"""Registre des collecteurs : nom du module -> classe."""

from ..commun.collecteur import Collecteur
from .meteo import CollecteurMeteo
from .vigicrues import CollecteurVigicrues
from .vilaine import CollecteurVilaine

COLLECTEURS: dict[str, type[Collecteur]] = {
    c.module: c for c in (CollecteurMeteo, CollecteurVigicrues, CollecteurVilaine)
}
