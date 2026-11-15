#!/usr/bin/env python3
"""Signes vitaux du serveur de Cesson'App, vus de l'extérieur (le Pi ne peut pas signaler sa propre panne).

    python3 tools/vitals/verifier.py https://cesson-pi.tail7e6f0d.ts.net/ [--etat fichier] [--ntfy URL]

Échoue (code 1) si le manifest est injoignable ou s'il n'a pas été régénéré depuis SEUIL minutes : le Pi est
éteint, hors ligne, ou ses collectes sont arrêtées. Les modules en erreur sont signalés sans faire échouer
(l'alerte de santé du Pi s'en charge déjà). Avec --ntfy, envoie une notification au début d'une panne, puis
une fois toutes les 6 h tant qu'elle dure, et une au retour à la normale (état gardé dans --etat).
Bibliothèque standard seulement.
"""

from __future__ import annotations

import argparse
import json
import sys
import urllib.request
from datetime import datetime, timedelta, timezone
from pathlib import Path

SEUIL = timedelta(minutes=20)  # le manifest est régénéré toutes les 2 à 3 min (trains, circulation)
RAPPEL = timedelta(hours=6)


def lire_manifest(base: str) -> dict:
    req = urllib.request.Request(base.rstrip("/") + "/manifest.json", headers={"User-Agent": "cesson-vitals/1.0"})
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.load(r)


def diagnostic(base: str, maintenant: datetime) -> tuple[bool, str, list[str]]:
    """(en bonne santé, résumé, modules en erreur)."""
    try:
        m = lire_manifest(base)
    except Exception as e:  # noqa: BLE001 — toute erreur réseau ou de format est une panne vue de l'extérieur
        return False, f"serveur injoignable ({e.__class__.__name__}: {e})", []
    genere = datetime.fromisoformat(m["genere_le"].replace("Z", "+00:00"))
    age = maintenant - genere
    erreurs = sorted(k for k, v in m.get("modules", {}).items() if v.get("etat") == "erreur")
    minutes = int(age.total_seconds() // 60)
    if age > SEUIL:
        return False, f"données figées : manifest généré il y a {minutes} min ({genere:%d/%m %H:%M} UTC)", erreurs
    return True, f"ok : manifest généré il y a {minutes} min, {len(m.get('modules', {}))} modules", erreurs


def notifier(url: str, titre: str, message: str, priorite: str) -> None:
    req = urllib.request.Request(url, data=message.encode(), method="POST",
                                 headers={"Title": titre.encode("utf-8"), "Priority": priorite, "Tags": "satellite"})
    urllib.request.urlopen(req, timeout=20).close()


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("base")
    p.add_argument("--etat", type=Path, help="fichier d'état entre deux vérifications (début de panne, dernier rappel)")
    p.add_argument("--ntfy", help="URL du sujet ntfy à prévenir")
    a = p.parse_args()
    maintenant = datetime.now(timezone.utc)
    sain, resume, erreurs = diagnostic(a.base, maintenant)
    print(resume + (f" ; modules en erreur : {', '.join(erreurs)}" if erreurs else ""))

    etat = json.loads(a.etat.read_text()) if a.etat and a.etat.exists() else {}
    if a.ntfy:
        if not sain:
            debut = etat.get("panne_depuis")
            dernier = etat.get("dernier_rappel")
            if debut is None or dernier is None or maintenant - datetime.fromisoformat(dernier) > RAPPEL:
                notifier(a.ntfy, "Cesson'App : serveur en panne", resume, "high")
                etat["dernier_rappel"] = maintenant.isoformat()
            etat.setdefault("panne_depuis", maintenant.isoformat())
        elif etat.get("panne_depuis"):
            debut = datetime.fromisoformat(etat["panne_depuis"])
            notifier(a.ntfy, "Cesson'App : serveur rétabli", f"{resume} (panne détectée le {debut:%d/%m à %H:%M} UTC)", "default")
            etat = {}
    if a.etat:
        a.etat.write_text(json.dumps(etat))
    return 0 if sain else 1


if __name__ == "__main__":
    sys.exit(main())
