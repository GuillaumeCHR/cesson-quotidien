#!/usr/bin/env bash
# Depuis le PC : copie le backend sur le Pi puis lance l'installation (ou la mise à jour).
#     backend/deploy/raspberry/envoyer.sh [hôte]      # hôte par défaut : cesson-pi.local
set -euo pipefail
HOTE=${1:-cesson-pi.local}
BACKEND=$(cd "$(dirname "$0")/../.." && pwd)

rsync -az --delete --exclude .venv --exclude public --exclude etat --exclude __pycache__ \
      --exclude .pytest_cache "$BACKEND/" "$HOTE:cesson-backend/"
ssh -t "$HOTE" sudo bash cesson-backend/deploy/raspberry/installer.sh
