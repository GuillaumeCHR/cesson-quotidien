"""Modèle commun à tous les modules : six entités et le bloc Meta.

Ce fichier est le contrat entre le backend et l'app Android. Les JSON Schema publiés dans
`schemas/` en sont générés (`python -m cesson schemas`) ; l'app reprend les mêmes noms de champs.
"""

from __future__ import annotations

from datetime import date, datetime
from enum import StrEnum
from typing import Generic, TypeVar

from pydantic import BaseModel, ConfigDict, Field, field_validator

VERSION_SCHEMA = 1


class Modele(BaseModel):
    """Base stricte : un champ inconnu ou mal typé est une erreur, pas un silence."""

    model_config = ConfigDict(extra="forbid", frozen=True)


class Etat(StrEnum):
    OK = "ok"
    PERIME = "perime"
    ERREUR = "erreur"


class Coord(Modele):
    lat: float = Field(ge=-90, le=90)
    lon: float = Field(ge=-180, le=180)


class Meta(Modele):
    """Accompagne chaque fichier publié : source, licence et fraîcheur."""

    source: str
    licence: str
    url_source: str | None = None
    recupere_le: datetime
    valide_jusqu_a: datetime
    etat: Etat = Etat.OK

    @field_validator("recupere_le", "valide_jusqu_a")
    @classmethod
    def _avec_fuseau(cls, v: datetime) -> datetime:
        if v.tzinfo is None:
            raise ValueError("horodatage sans fuseau horaire")
        return v


class Lieu(Modele):
    id: str
    nom: str
    type: str
    coord: Coord | None = None
    adresse: str | None = None
    source: str | None = None
    url: str | None = None


class ExceptionHoraire(Modele):
    du: date
    au: date
    motif: str
    opening_hours: str | None = None  # None : fermé sur la période


class Horaire(Modele):
    lieu_id: str
    sous_espace: str | None = None
    periode: str | None = None
    opening_hours: str  # format OSM
    exceptions: list[ExceptionHoraire] = []


class Mode(StrEnum):
    BUS = "bus"
    METRO = "metro"
    TRAIN = "train"


class StatutDepart(StrEnum):
    A_L_HEURE = "a_l_heure"
    RETARD = "retard"
    SUPPRIME = "supprime"
    THEORIQUE = "theorique"  # pas de temps réel pour ce départ


class Depart(Modele):
    arret: str
    ligne: str
    mode: Mode
    destination: str
    heure_theorique: datetime
    heure_reelle: datetime | None = None
    statut: StatutDepart


class Evenement(Modele):
    id: str
    titre: str
    lieu_id: str | None = None
    debut: datetime
    fin: datetime | None = None
    categorie: str | None = None
    prix: str | None = None
    url_billet: str | None = None


class Tendance(StrEnum):
    HAUSSE = "hausse"
    STABLE = "stable"
    BAISSE = "baisse"
    INCONNUE = "inconnue"


class Mesure(Modele):
    station: str
    grandeur: str  # H, Q, ATMO, prix…
    valeur: float
    unite: str
    horodatage: datetime
    tendance: Tendance = Tendance.INCONNUE


class NiveauAlerte(StrEnum):
    INFO = "info"
    JAUNE = "jaune"
    ORANGE = "orange"
    ROUGE = "rouge"


class Alerte(Modele):
    id: str
    niveau: NiveauAlerte
    titre: str
    zone: str | None = None
    debut: datetime | None = None
    fin: datetime | None = None
    source: str


T = TypeVar("T", bound=BaseModel)


class FichierModule(Modele, Generic[T]):
    """Enveloppe de chaque `<module>.json` publié."""

    module: str
    version_schema: int = VERSION_SCHEMA
    meta: Meta
    donnees: T


class EntreeManifest(Modele):
    fichier: str
    version: str  # empreinte des données : change seulement quand les données changent
    recupere_le: datetime
    valide_jusqu_a: datetime
    etat: Etat


class Manifest(Modele):
    version_schema: int = VERSION_SCHEMA
    genere_le: datetime
    modules: dict[str, EntreeManifest]
