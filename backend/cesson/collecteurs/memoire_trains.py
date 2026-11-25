"""Rémanence des retards et des suppressions de trains.

Le flux temps réel SNCF (GTFS-RT) cesse souvent de décrire un train avant son départ effectif : un train en retard
repasserait alors en « horaire prévu » et disparaîtrait à son heure théorique, alors qu'il n'est pas parti. Un train
supprimé, lui, n'a pas d'heure réelle et disparaissait à son heure prévue, au moment même où le voyageur arrive à
quai.

Deux mécanismes, partagés par les modules trains (aller) et trains_retour :

- `MemoireTrains` garde, dans un petit fichier d'état hors du dossier publié, le dernier statut temps réel connu de
  chaque train (numéro, jour, gare). Quand le flux ne dit plus rien d'un train, ce dernier statut est repris ;
- `visible_jusqu_a` fixe jusqu'à quand un départ reste affiché : un train en retard jusqu'à son départ réel, un
  train supprimé jusqu'au départ du train suivant vers la même destination (au plus une heure après son horaire).
"""

from __future__ import annotations

import json
from datetime import datetime, timedelta
from pathlib import Path

from ..commun.modele import StatutDepart
from ..commun.publication import ecrire_atomique

# Un statut que le flux n'a pas confirmé depuis 90 min ne vaut plus rien.
AGE_MAX_STATUT = timedelta(minutes=90)
# Un train supprimé reste affiché au plus une heure après son horaire prévu.
REMANENCE_MAX_SUPPRIME = timedelta(hours=1)
# Un départ reste affiché 2 min après son heure : le train peut être encore à quai.
TOLERANCE_PASSE = timedelta(minutes=2)
# Fenêtre des horaires examinés en arrière : un train très en retard part bien après son heure prévue.
FENETRE_PASSE = timedelta(hours=3)


class MemoireTrains:
    """Derniers statuts temps réel connus, par clé « numéro|jour|gare »."""

    def __init__(self, chemin: Path | None) -> None:
        """Sans [chemin] (convertir appelé hors collecte), la mémoire ne dure que le temps de l'appel."""
        self.chemin = chemin
        self.statuts: dict[str, dict] = {}
        if chemin is not None and chemin.exists():
            try:
                self.statuts = json.loads(chemin.read_text(encoding="utf-8"))
            except (OSError, ValueError):
                self.statuts = {}  # état illisible : on repart de zéro, sans bloquer la collecte

    def completer(self, cle: str, statut: StatutDepart, reelle: datetime | None, retard: int | None,
                  maintenant: datetime) -> tuple[StatutDepart, datetime | None, int | None]:
        """Retient un statut donné par le flux ; à défaut (horaire prévu seulement), reprend le dernier connu."""
        if statut != StatutDepart.THEORIQUE:
            self.statuts[cle] = {"statut": statut.value, "reelle": reelle.isoformat() if reelle else None,
                                 "retard": retard, "vu_le": maintenant.isoformat()}
            return statut, reelle, retard
        m = self.statuts.get(cle)
        if m is None or maintenant - datetime.fromisoformat(m["vu_le"]) > AGE_MAX_STATUT:
            return statut, reelle, retard
        return StatutDepart(m["statut"]), datetime.fromisoformat(m["reelle"]) if m["reelle"] else None, m["retard"]

    def enregistrer(self, maintenant: datetime) -> None:
        """Écrit l'état, sans les statuts périmés."""
        self.statuts = {c: m for c, m in self.statuts.items()
                        if maintenant - datetime.fromisoformat(m["vu_le"]) <= AGE_MAX_STATUT}
        if self.chemin is not None:
            ecrire_atomique(self.chemin, json.dumps(self.statuts, ensure_ascii=False) + "\n")


def visible_jusqu_a(statut: StatutDepart, theorique: datetime, reelle: datetime | None,
                    suivant: datetime | None) -> datetime:
    """Jusqu'à quand afficher un départ. [suivant] : départ (réel, sinon prévu) du prochain train non supprimé vers
    la même destination, pour un train supprimé."""
    if statut == StatutDepart.SUPPRIME:
        limite = theorique + REMANENCE_MAX_SUPPRIME
        fin = min(suivant + TOLERANCE_PASSE, limite) if suivant is not None else limite
        return max(fin, theorique + TOLERANCE_PASSE)
    return (reelle or theorique) + TOLERANCE_PASSE


def depart_suivant(departs: list, i: int, meme_groupe) -> datetime | None:
    """Départ du premier train non supprimé, après departs[i], du même groupe ; [departs] triés par horaire prévu."""
    for d in departs[i + 1:]:
        if meme_groupe(d) and d.statut != StatutDepart.SUPPRIME:
            return d.heure_reelle or d.heure_theorique
    return None
