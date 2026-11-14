"""Publication des fichiers statiques : un JSON par module et le manifest.

Toutes les écritures sont atomiques (fichier temporaire puis renommage) : le CDN ou l'app ne
lisent jamais un fichier à moitié écrit.
"""

from __future__ import annotations

import hashlib
import json
import os
import tempfile
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from pydantic import BaseModel

from .modele import EntreeManifest, Etat, Manifest

MANIFEST = "manifest.json"


def ecrire_atomique(chemin: Path, contenu: str) -> None:
    chemin.parent.mkdir(parents=True, exist_ok=True)
    fd, tmp = tempfile.mkstemp(dir=chemin.parent, prefix=f".{chemin.name}.", suffix=".tmp")
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as f:
            f.write(contenu)
        os.chmod(tmp, 0o644)
        os.replace(tmp, chemin)
    except BaseException:
        Path(tmp).unlink(missing_ok=True)
        raise


def en_json(modele: BaseModel) -> str:
    return modele.model_dump_json(indent=2, exclude_none=True) + "\n"


def empreinte(donnees: BaseModel) -> str:
    """Empreinte stable des seules données (sans Meta) : l'app ne retélécharge que si elle change."""
    brut = json.dumps(donnees.model_dump(mode="json"), sort_keys=True, ensure_ascii=False)
    return hashlib.sha256(brut.encode()).hexdigest()[:16]


class Publieur:
    def __init__(self, dossier: Path):
        self.dossier = Path(dossier)

    def chemin(self, module: str) -> Path:
        return self.dossier / f"{module}.json"

    def lire_brut(self, module: str) -> dict[str, Any] | None:
        chemin = self.chemin(module)
        if not chemin.exists():
            return None
        try:
            return json.loads(chemin.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError):
            return None

    def publier(self, module: str, fichier: BaseModel, version: str, recupere_le: datetime,
                valide_jusqu_a: datetime, etat: Etat) -> None:
        ecrire_atomique(self.chemin(module), en_json(fichier))
        self._maj_manifest(module, EntreeManifest(
            fichier=f"{module}.json", version=version, recupere_le=recupere_le,
            valide_jusqu_a=valide_jusqu_a, etat=etat,
        ))

    def lire_manifest(self) -> Manifest | None:
        chemin = self.dossier / MANIFEST
        if not chemin.exists():
            return None
        try:
            return Manifest.model_validate_json(chemin.read_text(encoding="utf-8"))
        except ValueError:
            return None

    def _maj_manifest(self, module: str, entree: EntreeManifest) -> None:
        # Un seul collecteur écrit à la fois (verrou dans le CLI) : lecture-modification-écriture sûre.
        actuel = self.lire_manifest()
        modules = dict(actuel.modules) if actuel else {}
        modules[module] = entree
        manifest = Manifest(genere_le=datetime.now(timezone.utc), modules=dict(sorted(modules.items())))
        ecrire_atomique(self.dossier / MANIFEST, en_json(manifest))
