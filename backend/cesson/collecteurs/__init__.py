"""Registre des collecteurs : nom du module -> classe."""

from ..commun.collecteur import Collecteur
from .accessibilite import CollecteurAccessibilite
from .actus import CollecteurActus
from .agenda import CollecteurAgenda
from .atmo import CollecteurEpisodes, CollecteurPollens, CollecteurQualiteAir
from .carburants import CollecteurCarburants
from .cinema import CollecteurCinema
from .circulation import CollecteurCirculation
from .collecte import CollecteurCollecte
from .decheterie import CollecteurDecheterie
from .equipements import CollecteurEquipements
from .familles import CollecteurFamilles
from .handball import CollecteurHandball
from .lieux_pratiques import CollecteurBornes, CollecteurDefibrillateurs
from .lignes_star import CollecteurLignesStar
from .meteo import CollecteurMeteo
from .pharmacies import CollecteurPharmacies
from .trains import CollecteurTrains
from .trains_retour import CollecteurTrainsRetour
from .usages_eau import CollecteurUsagesEau
from .vacances_scolaires import CollecteurVacancesScolaires
from .vigicrues import CollecteurVigicrues
from .vigilance_meteo import CollecteurVigilanceMeteo
from .vilaine import CollecteurVilaine

COLLECTEURS: dict[str, type[Collecteur]] = {
    c.module: c for c in (CollecteurAccessibilite, CollecteurActus, CollecteurAgenda, CollecteurBornes, CollecteurCarburants, CollecteurCinema, CollecteurCirculation, CollecteurCollecte, CollecteurDecheterie, CollecteurDefibrillateurs, CollecteurEpisodes, CollecteurEquipements, CollecteurFamilles, CollecteurHandball, CollecteurLignesStar, CollecteurMeteo, CollecteurPharmacies, CollecteurPollens, CollecteurQualiteAir, CollecteurTrains, CollecteurTrainsRetour, CollecteurUsagesEau, CollecteurVacancesScolaires, CollecteurVigicrues, CollecteurVigilanceMeteo, CollecteurVilaine)
}
