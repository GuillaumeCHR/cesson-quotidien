"""Gabarit commun à tous les collecteurs.

1. Télécharger la source (délai maximal, 2 nouvelles tentatives).
2. Valider le format reçu.
3. Convertir vers le modèle commun.
4. Comparer avec la version précédente et noter les changements utiles aux alertes.
5. Publier le JSON du module.

En cas d'échec à l'une de ces étapes, la dernière version valide est conservée et marquée
`perime`. Un fichier vide n'est jamais publié.
"""

from __future__ import annotations

import logging
import time
from abc import ABC, abstractmethod
from dataclasses import dataclass, field
from datetime import datetime, timedelta, timezone
from typing import Any, ClassVar, Generic, TypeVar

import httpx
from pydantic import BaseModel, ValidationError

from .modele import Etat, FichierModule, Meta
from .publication import Publieur, empreinte
from .sante import Sante

log = logging.getLogger(__name__)

D = TypeVar("D", bound=BaseModel)

DELAI = httpx.Timeout(20.0, connect=10.0)
TENTATIVES_SUPPLEMENTAIRES = 2
USER_AGENT = "cesson-au-quotidien/0.1 (collecteur de donnees publiques)"


class ErreurSource(Exception):
    """La source n'a pas répondu ou a répondu quelque chose d'inexploitable."""


class ErreurValidation(ErreurSource):
    """La réponse ne correspond pas au format attendu."""


@dataclass(frozen=True)
class Changement:
    module: str
    type: str
    detail: str
    donnees: dict[str, Any] = field(default_factory=dict)


@dataclass
class Resultat:
    module: str
    etat: Etat
    publie: bool
    changements: list[Changement] = field(default_factory=list)
    erreur: str | None = None


def nouveau_client() -> httpx.Client:
    return httpx.Client(timeout=DELAI, headers={"User-Agent": USER_AGENT}, follow_redirects=True)


def obtenir(client: httpx.Client, url: str, *, params: dict[str, Any] | None = None,
            headers: dict[str, str] | None = None, pause: float = 2.0,
            delai: httpx.Timeout | None = None) -> httpx.Response:
    """GET avec délai maximal ([delai], sinon celui du client) et 2 nouvelles tentatives sur erreur réseau ou 5xx/429."""
    derniere: Exception | None = None
    for essai in range(TENTATIVES_SUPPLEMENTAIRES + 1):
        try:
            r = client.get(url, params=params, headers=headers, timeout=delai or client.timeout)
            if r.status_code == 429 or r.status_code >= 500:
                raise httpx.HTTPStatusError(f"HTTP {r.status_code}", request=r.request, response=r)
            r.raise_for_status()
            return r
        except httpx.HTTPStatusError as e:
            derniere = e
            if e.response.status_code < 500 and e.response.status_code != 429:
                break  # une 4xx ne se corrige pas en réessayant
        except httpx.TransportError as e:
            derniere = e
        if essai < TENTATIVES_SUPPLEMENTAIRES:
            time.sleep(pause * (essai + 1))
    raise ErreurSource(f"{url} : {derniere}") from derniere


class Collecteur(ABC, Generic[D]):
    module: ClassVar[str]
    source: ClassVar[str]
    licence: ClassVar[str]
    url_source: ClassVar[str | None] = None
    fraicheur: ClassVar[timedelta]
    modele_donnees: ClassVar[type[BaseModel]]

    @abstractmethod
    def telecharger(self, client: httpx.Client) -> Any: ...

    @abstractmethod
    def valider(self, brut: Any) -> None:
        """Lève ErreurValidation si le format ou le volume reçu n'est pas plausible."""

    @abstractmethod
    def convertir(self, brut: Any, maintenant: datetime) -> D: ...

    def valide_jusqu_a(self, donnees: D, maintenant: datetime) -> datetime:
        return maintenant + self.fraicheur

    def comparer(self, ancien: D | None, nouveau: D) -> list[Changement]:
        return []

    # --- déroulé commun -------------------------------------------------------------------

    def executer(self, publieur: Publieur, sante: Sante, client: httpx.Client | None = None,
                 maintenant: datetime | None = None) -> Resultat:
        maintenant = maintenant or datetime.now(timezone.utc)
        client = client or nouveau_client()
        try:
            brut = self.telecharger(client)
            self.valider(brut)
            try:
                donnees = self.convertir(brut, maintenant)
            except (ValidationError, KeyError, TypeError, ValueError) as e:
                raise ErreurValidation(f"conversion impossible : {e}") from e
        except ErreurSource as e:
            return self._echec(publieur, sante, str(e))

        ancien = self._precedent(publieur)
        changements = self.comparer(ancien.donnees if ancien else None, donnees)
        for c in changements:
            log.info("changement %s/%s : %s", c.module, c.type, c.detail)

        meta = Meta(source=self.source, licence=self.licence, url_source=self.url_source,
                    recupere_le=maintenant, valide_jusqu_a=self.valide_jusqu_a(donnees, maintenant),
                    etat=Etat.OK)
        fichier = FichierModule[self.modele_donnees](module=self.module, meta=meta, donnees=donnees)
        publieur.publier(self.module, fichier, empreinte(donnees, meta.source, meta.licence, meta.url_source), meta.recupere_le,
                         meta.valide_jusqu_a, Etat.OK)
        sante.enregistrer(self.module, self.source, self.fraicheur, reussi=True)
        return Resultat(self.module, Etat.OK, publie=True, changements=changements)

    def _precedent(self, publieur: Publieur) -> FichierModule | None:
        brut = publieur.lire_brut(self.module)
        if brut is None:
            return None
        try:
            return FichierModule[self.modele_donnees].model_validate(brut)
        except ValidationError:
            log.warning("%s : version précédente illisible, ignorée", self.module)
            return None

    def _echec(self, publieur: Publieur, sante: Sante, erreur: str) -> Resultat:
        log.error("%s : échec de la collecte : %s", self.module, erreur)
        ancien = self._precedent(publieur)
        if ancien is None:
            # Rien de valide à conserver : on ne publie rien plutôt qu'un fichier vide.
            sante.enregistrer(self.module, self.source, self.fraicheur, reussi=False,
                              erreur=erreur, etat=Etat.ERREUR)
            return Resultat(self.module, Etat.ERREUR, publie=False, erreur=erreur)

        meta = ancien.meta.model_copy(update={"etat": Etat.PERIME})
        fichier = ancien.model_copy(update={"meta": meta})
        publieur.publier(self.module, fichier, empreinte(ancien.donnees, meta.source, meta.licence, meta.url_source),
                         meta.recupere_le,
                         meta.valide_jusqu_a, Etat.PERIME)
        sante.enregistrer(self.module, self.source, self.fraicheur, reussi=False,
                          erreur=erreur, etat=Etat.PERIME)
        return Resultat(self.module, Etat.PERIME, publie=True, erreur=erreur)
