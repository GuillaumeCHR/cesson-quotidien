"""Registre des collecteurs : nom du module -> classe."""

from ..commun.collecteur import Collecteur
from .actus import CollecteurActus
from .carburants import CollecteurCarburants
from .circulation import CollecteurCirculation
from .collecte import CollecteurCollecte
from .equipements import CollecteurEquipements
from .lieux_pratiques import CollecteurBornes, CollecteurDefibrillateurs
from .lignes_star import CollecteurLignesStar
from .meteo import CollecteurMeteo
from .trains import CollecteurTrains
from .usages_eau import CollecteurUsagesEau
from .vigicrues import CollecteurVigicrues
from .vilaine import CollecteurVilaine

COLLECTEURS: dict[str, type[Collecteur]] = {
    c.module: c for c in (CollecteurActus, CollecteurBornes, CollecteurCarburants, CollecteurCirculation, CollecteurCollecte, CollecteurDefibrillateurs, CollecteurEquipements, CollecteurLignesStar, CollecteurMeteo, CollecteurTrains, CollecteurUsagesEau, CollecteurVigicrues, CollecteurVilaine)
}
