#!/usr/bin/env bash
# Mise à jour automatique du Pi depuis GitHub (lancée toutes les heures par cesson-mise-a-jour.timer).
#
# Le Pi tient une copie du dépôt public (sans identifiant). Quand la branche main avance, il ne s'y met que si
# les tests de ce commit ont réussi sur GitHub (workflow « Tests » : backend et android), puis relance
# l'installeur en mode --auto. Échec : alerte ntfy, et le Pi reste sur la version précédente.
set -euo pipefail

DEPOT_URL=https://github.com/GuillaumeCHR/cesson-quotidien.git
API=https://api.github.com/repos/GuillaumeCHR/cesson-quotidien
RACINE=/srv/cesson
DEPOT=$RACINE/depot
DEJA=$RACINE/etat/commit-deploye
SUJET=$(cat "$RACINE/etat/ntfy-sujet" 2>/dev/null || true)

alerte() { [[ -n $SUJET ]] && curl -fsS -m 20 -H "Title: Cesson'App : mise à jour du Pi" -d "$1" "https://ntfy.sh/$SUJET" >/dev/null || true; }

if [[ ! -d $DEPOT/.git ]]; then
    git clone -q --filter=blob:none --no-checkout "$DEPOT_URL" "$DEPOT"
    git -C "$DEPOT" sparse-checkout set backend
fi
git -C "$DEPOT" fetch -q origin main
SHA=$(git -C "$DEPOT" rev-parse origin/main)
[[ $SHA == "$(cat "$DEJA" 2>/dev/null)" ]] && exit 0

# Les deux vérifications du workflow « Tests » doivent avoir réussi pour ce commit (API publique, sans jeton).
etat=$(curl -fsS -m 30 -H "Accept: application/vnd.github+json" "$API/commits/$SHA/check-runs?per_page=100" | python3 -c '
import json, sys
runs = {r["name"]: (r["status"], r["conclusion"]) for r in json.load(sys.stdin)["check_runs"]}
etats = [runs.get(n) for n in ("backend", "android")]
print("ok" if all(e == ("completed", "success") for e in etats) else
      "attente" if any(e is None or e[0] != "completed" for e in etats) else "echec")')
case $etat in
    attente) exit 0 ;;  # tests en cours : on repassera dans une heure
    echec) [[ $(cat "$DEJA.refuse" 2>/dev/null) == "$SHA" ]] || {
               alerte "Commit ${SHA:0:7} non déployé : ses tests ont échoué sur GitHub."; echo "$SHA" > "$DEJA.refuse"; }
           exit 0 ;;
esac

# Modules à recollecter tout de suite : ceux dont la saisie ou le collecteur a changé depuis le dernier déploiement
# (sinon, une correction d'horaires attendrait la collecte suivante, parfois la nuit).
AVANT=$(cat "$DEJA" 2>/dev/null || true)
TOUCHES=""
if [[ -n $AVANT ]] && git -C "$DEPOT" cat-file -e "$AVANT" 2>/dev/null; then
    TOUCHES=$(git -C "$DEPOT" diff --name-only "$AVANT" "$SHA" -- backend/saisie backend/cesson/collecteurs | python3 -c '
import sys, os
lies = {"lieux_pratiques": "defibrillateurs bornes", "actus": "actus usages_eau"}
mods = set()
for f in sys.stdin.read().split():
    nom = os.path.splitext(os.path.basename(f))[0]
    if nom not in ("__init__", "README"):
        mods.update(lies.get(nom, nom).split())
print(" ".join(sorted(mods)))')
fi

git -C "$DEPOT" checkout -q --force "$SHA"
if bash "$DEPOT/backend/deploy/raspberry/installer.sh" --auto $TOUCHES; then
    echo "$SHA" > "$DEJA"
    echo "$(date -Is) déployé ${SHA:0:7}"
else
    alerte "Échec de l'installation du commit ${SHA:0:7} : le Pi reste sur la version précédente. Voir /srv/cesson/log/mise-a-jour.log."
    exit 1
fi
