"""Identifiants des API à compte (Atmo Data, Météo-France), lus sur le serveur seulement.

Chaque service a son fichier « cle=valeur » dans /srv/cesson/secrets/ (640, root:cesson), saisi à la main par
le mainteneur ; une variable d'environnement peut désigner un autre fichier (tests, poste de développement).
Jamais dans le dépôt, jamais dans l'app, et jamais repris dans un message d'erreur.
"""

from __future__ import annotations

import os
from pathlib import Path

from .collecteur import ErreurSource

DOSSIER = "/srv/cesson/secrets"


def lire_secrets(service: str, variable: str, fichier: str, cles: tuple[str, ...]) -> dict[str, str]:
    """Valeurs [cles] du fichier de [service] ; les guillemets copiés autour d'une valeur sont ignorés."""
    p = Path(os.environ.get(variable) or f"{DOSSIER}/{fichier}")
    try:
        lignes = p.read_text(encoding="utf-8").splitlines()
    except OSError as e:
        raise ErreurSource(f"identifiants {service} illisibles ({p}) : {e.strerror}") from e
    valeurs = {k.strip(): v.strip().strip("\"'") for k, v in (l.split("=", 1) for l in lignes if "=" in l)}
    manquantes = [c for c in cles if not valeurs.get(c)]
    if manquantes:
        raise ErreurSource(f"identifiants {service} incomplets dans {p} : {', '.join(manquantes)} manquant(s)")
    return {c: valeurs[c] for c in cles}
