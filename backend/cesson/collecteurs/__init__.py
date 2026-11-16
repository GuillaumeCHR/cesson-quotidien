"""Registre des collecteurs : nom du module -> classe."""

from ..commun.collecteur import Collecteur
from .actus import CollecteurActus
from .agenda import CollecteurAgenda
from .atmo import CollecteurEpisodes, CollecteurPollens, CollecteurQualiteAir
from .carburants import CollecteurCarburants
from .cinema import CollecteurCinema
from .circulation import CollecteurCirculation
from .collecte import CollecteurCollecte
from .equipements import CollecteurEquipements
from .familles import CollecteurFamilles
from .lieux_pratiques import CollecteurBornes, CollecteurDefibrillateurs
from .lignes_star import CollecteurLignesStar
from .meteo import CollecteurMeteo
from .trains import CollecteurTrains
from .usages_eau import CollecteurUsagesEau
from .vacances_scolaires import CollecteurVacancesScolaires
from .vigicrues import CollecteurVigicrues
from .vilaine import CollecteurVilaine

COLLECTEURS: dict[str, type[Collecteur]] = {
    c.module: c for c in (CollecteurActus, CollecteurAgenda, CollecteurBornes, CollecteurCarburants, CollecteurCinema, CollecteurCirculation, CollecteurCollecte, CollecteurDefibrillateurs, CollecteurEpisodes, CollecteurEquipements, CollecteurFamilles, CollecteurLignesStar, CollecteurMeteo, CollecteurPollens, CollecteurQualiteAir, CollecteurTrains, CollecteurUsagesEau, CollecteurVacancesScolaires, CollecteurVigicrues, CollecteurVilaine)
}
