"""Registre des collecteurs : nom du module -> classe."""

from ..commun.collecteur import Collecteur
from .carburants import CollecteurCarburants
from .collecte import CollecteurCollecte
from .equipements import CollecteurEquipements
from .lignes_star import CollecteurLignesStar
from .meteo import CollecteurMeteo
from .trains import CollecteurTrains
from .vigicrues import CollecteurVigicrues
from .vilaine import CollecteurVilaine

COLLECTEURS: dict[str, type[Collecteur]] = {
    c.module: c for c in (CollecteurCarburants, CollecteurCollecte, CollecteurEquipements, CollecteurLignesStar, CollecteurMeteo, CollecteurTrains, CollecteurVigicrues, CollecteurVilaine)
}
