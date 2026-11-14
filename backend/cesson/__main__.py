"""Point d'entrée : python -m cesson <commande>.

  collecter <module>… [--sortie public] [--etat etat]   lance un ou plusieurs collecteurs
  schemas [--dossier schemas]                           régénère les JSON Schema
  sante [--etat etat]                                   affiche le tableau de santé
"""

from __future__ import annotations

import argparse
import fcntl
import json
import logging
import sys
from datetime import datetime, timezone
from pathlib import Path

from .collecteurs import COLLECTEURS
from .commun.collecteur import nouveau_client
from .commun.modele import Etat, FichierModule, Manifest
from .commun.publication import Publieur, ecrire_atomique
from .commun.sante import Sante


def collecter(args: argparse.Namespace) -> int:
    inconnus = [m for m in args.modules if m not in COLLECTEURS]
    if inconnus:
        print(f"module(s) inconnu(s) : {', '.join(inconnus)} ; disponibles : {', '.join(COLLECTEURS)}",
              file=sys.stderr)
        return 2
    sortie, etat = Path(args.sortie), Path(args.etat)
    etat.mkdir(parents=True, exist_ok=True)
    # Verrou global : le manifest est mis à jour par lecture-modification-écriture.
    with open(etat / ".verrou", "w") as verrou:
        fcntl.flock(verrou, fcntl.LOCK_EX)
        publieur, sante = Publieur(sortie), Sante(etat)
        code = 0
        with nouveau_client() as client:
            for m in args.modules:
                r = COLLECTEURS[m]().executer(publieur, sante, client)
                print(f"{m}: {r.etat}" + (f" ({r.erreur})" if r.erreur else ""))
                if r.etat != Etat.OK:
                    code = 1
        return code


def schemas(args: argparse.Namespace) -> int:
    dossier = Path(args.dossier)
    ecrire_atomique(dossier / "manifest.schema.json",
                    json.dumps(Manifest.model_json_schema(), indent=2, ensure_ascii=False) + "\n")
    for nom, c in COLLECTEURS.items():
        schema = FichierModule[c.modele_donnees].model_json_schema()
        ecrire_atomique(dossier / f"{nom}.schema.json", json.dumps(schema, indent=2, ensure_ascii=False) + "\n")
    print(f"{len(COLLECTEURS) + 1} schéma(s) écrits dans {dossier}")
    return 0


def sante(args: argparse.Namespace) -> int:
    maintenant = datetime.now(timezone.utc)
    etats = Sante(Path(args.etat)).lire()
    if not etats:
        print("aucune collecte enregistrée")
        return 0
    code = 0
    for e in etats.values():
        retard = e.en_retard(maintenant)
        reussite = e.derniere_reussite.isoformat(timespec="minutes") if e.derniere_reussite else "jamais"
        print(f"{e.module:<14} {e.etat:<7} réussite {reussite:<26} "
              f"attendu toutes les {e.fraicheur_attendue_s // 60} min"
              + ("  EN RETARD" if retard else "") + (f"  [{e.derniere_erreur}]" if e.derniere_erreur else ""))
        if retard or e.etat != Etat.OK:
            code = 1
    return code


def main(argv: list[str] | None = None) -> int:
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")
    p = argparse.ArgumentParser(prog="cesson")
    sp = p.add_subparsers(dest="commande", required=True)
    c = sp.add_parser("collecter")
    c.add_argument("modules", nargs="+")
    c.add_argument("--sortie", default="public")
    c.add_argument("--etat", default="etat")
    c.set_defaults(f=collecter)
    s = sp.add_parser("schemas")
    s.add_argument("--dossier", default="schemas")
    s.set_defaults(f=schemas)
    h = sp.add_parser("sante")
    h.add_argument("--etat", default="etat")
    h.set_defaults(f=sante)
    args = p.parse_args(argv)
    return args.f(args)


if __name__ == "__main__":
    sys.exit(main())
