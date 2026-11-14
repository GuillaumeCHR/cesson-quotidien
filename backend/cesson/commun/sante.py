"""Tableau de santé des sources, réservé au développeur (jamais publié sur le CDN).

Pour chaque module : dernière tentative, dernière réussite, fraîcheur attendue, état et dernière
erreur. Quand un module quitte l'état « ok », un message part vers `CESSON_ALERTE_URL` (par
exemple un sujet ntfy.sh privé), une seule fois par incident.
"""

from __future__ import annotations

import json
import logging
import os
from datetime import datetime, timedelta, timezone
from pathlib import Path

import httpx
from pydantic import BaseModel

from .modele import Etat
from .publication import ecrire_atomique

log = logging.getLogger(__name__)
FICHIER = "sante.json"


class EtatSource(BaseModel):
    module: str
    source: str
    fraicheur_attendue_s: int
    derniere_tentative: datetime
    derniere_reussite: datetime | None = None
    etat: Etat
    derniere_erreur: str | None = None
    echecs_consecutifs: int = 0

    def en_retard(self, maintenant: datetime) -> bool:
        if self.derniere_reussite is None:
            return True
        return maintenant - self.derniere_reussite > timedelta(seconds=2 * self.fraicheur_attendue_s)


class Sante:
    def __init__(self, dossier: Path, url_alerte: str | None = None, client: httpx.Client | None = None):
        self.chemin = Path(dossier) / FICHIER
        self.url_alerte = url_alerte if url_alerte is not None else os.environ.get("CESSON_ALERTE_URL")
        self.client = client

    def lire(self) -> dict[str, EtatSource]:
        if not self.chemin.exists():
            return {}
        brut = json.loads(self.chemin.read_text(encoding="utf-8"))
        return {k: EtatSource.model_validate(v) for k, v in brut.items()}

    def enregistrer(self, module: str, source: str, fraicheur: timedelta, reussi: bool,
                    erreur: str | None = None, etat: Etat | None = None) -> EtatSource:
        maintenant = datetime.now(timezone.utc)
        etats = self.lire()
        precedent = etats.get(module)
        nouvel = EtatSource(
            module=module,
            source=source,
            fraicheur_attendue_s=int(fraicheur.total_seconds()),
            derniere_tentative=maintenant,
            derniere_reussite=maintenant if reussi else (precedent.derniere_reussite if precedent else None),
            etat=Etat.OK if reussi else (etat or Etat.ERREUR),
            derniere_erreur=None if reussi else erreur,
            echecs_consecutifs=0 if reussi else (precedent.echecs_consecutifs + 1 if precedent else 1),
        )
        etats[module] = nouvel
        contenu = {k: v.model_dump(mode="json") for k, v in sorted(etats.items())}
        ecrire_atomique(self.chemin, json.dumps(contenu, indent=2, ensure_ascii=False) + "\n")

        etait_ok = precedent is None or precedent.etat == Etat.OK
        if etait_ok and not reussi:
            self._alerter(f"[cesson] {module} décroche : {erreur}")
        elif precedent is not None and precedent.etat != Etat.OK and reussi:
            self._alerter(f"[cesson] {module} rétabli après {precedent.echecs_consecutifs} échec(s)")
        return nouvel

    def _alerter(self, message: str) -> None:
        log.warning(message)
        if not self.url_alerte:
            return
        try:
            client = self.client or httpx.Client(timeout=10)
            client.post(self.url_alerte, content=message.encode())
        except httpx.HTTPError as e:  # l'alerte ne doit jamais faire échouer la collecte
            log.error("alerte non envoyée : %s", e)
