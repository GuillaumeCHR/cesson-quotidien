"""Registre des collecteurs : nom du module -> classe."""

from ..commun.collecteur import Collecteur
from .meteo import CollecteurMeteo
from .vilaine import CollecteurVilaine

COLLECTEURS: dict[str, type[Collecteur]] = {
    c.module: c for c in (CollecteurMeteo, CollecteurVilaine)
}
