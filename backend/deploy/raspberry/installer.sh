#!/usr/bin/env bash
# Installe ou met à jour le backend Cesson au quotidien sur un Raspberry Pi 4
# (Raspberry Pi OS Lite 64 bits, version Trixie ou plus récente : Python 3.12 minimum).
#
# À lancer en root depuis une copie du dossier backend, normalement via envoyer.sh :
#     sudo ./deploy/raspberry/installer.sh
# Relancer le script = mettre à jour le code ; les données publiées et l'état sont conservés.
#
# Coût : zéro. Exposition publique par Tailscale Funnel (gratuit, HTTPS, aucun port ouvert sur la box),
# alertes par ntfy.sh.
set -euo pipefail

[[ $EUID -eq 0 ]] || { echo "À lancer avec sudo." >&2; exit 1; }

SOURCE=$(cd "$(dirname "$0")/../.." && pwd)   # le dossier backend copié sur le Pi
RACINE=/srv/cesson
CODE=$RACINE/backend
ICI=$SOURCE/deploy/raspberry

etape() { printf '\n== %s\n' "$*"; }

etape "Vérification du système"
python3 -c 'import sys; sys.exit(sys.version_info < (3, 12))' || {
    echo "Python $(python3 -V) trop ancien : il faut Raspberry Pi OS Trixie (Python 3.13)." >&2; exit 1; }
timedatectl set-timezone Europe/Paris   # les heures du crontab (6h05…) sont en heure de Paris

etape "Paquets"
# Au premier démarrage, les mises à jour automatiques de Debian tiennent le verrou de dpkg : on attend
# qu'elles finissent (jusqu'à 10 min) au lieu d'échouer, y compris pour l'installeur de Tailscale.
echo 'DPkg::Lock::Timeout "600";' > /etc/apt/apt.conf.d/90cesson-attente-verrou
apt-get update -q
apt-get install -y -q python3-venv nginx rsync logrotate unattended-upgrades curl

etape "Utilisateur et dossiers"
id cesson &>/dev/null || useradd --system --create-home --home-dir "$RACINE" --shell /usr/sbin/nologin cesson
# useradd crée le dossier personnel en 700 : nginx (www-data) doit pouvoir le traverser pour lire public/.
chmod 755 "$RACINE"
install -d -o cesson -g cesson -m 755 "$RACINE/public"
install -d -o cesson -g cesson -m 750 "$RACINE/etat" "$RACINE/log"

etape "Code et environnement Python"
# Le code appartient à root : l'utilisateur cesson ne peut écrire que public, etat et log.
rsync -a --delete --exclude .venv --exclude public --exclude etat --exclude __pycache__ \
      --exclude .pytest_cache "$SOURCE/" "$CODE/"
chown -R root:root "$CODE"
[[ -x $CODE/.venv/bin/python ]] || python3 -m venv "$CODE/.venv"
"$CODE/.venv/bin/pip" install -q --upgrade pip
"$CODE/.venv/bin/pip" install -q -e "$CODE"

etape "nginx (local uniquement, port 8080)"
install -m 644 "$ICI/nginx.conf" /etc/nginx/sites-available/cesson
ln -sf /etc/nginx/sites-available/cesson /etc/nginx/sites-enabled/cesson
rm -f /etc/nginx/sites-enabled/default
nginx -t
systemctl enable --now nginx
systemctl reload nginx

etape "Rotation des journaux"
install -m 644 "$ICI/logrotate" /etc/logrotate.d/cesson

etape "Planification (crontab de l'utilisateur cesson)"
# Sujet ntfy tiré au hasard la première fois puis conservé : c'est lui qui rend les alertes privées.
SUJET_FICHIER=$RACINE/etat/ntfy-sujet
[[ -s $SUJET_FICHIER ]] || { echo "cesson-$(head -c 12 /dev/urandom | od -An -tx1 | tr -d ' \n')" > "$SUJET_FICHIER"; }
SUJET=$(cat "$SUJET_FICHIER")
sed "s#^CESSON_ALERTE_URL=.*#CESSON_ALERTE_URL=https://ntfy.sh/$SUJET#" "$SOURCE/deploy/crontab" \
    | crontab -u cesson -

etape "Première collecte"
sudo -u cesson env CESSON_ALERTE_URL="https://ntfy.sh/$SUJET" bash -c \
    "cd $CODE && .venv/bin/python -m cesson collecter vilaine meteo vigicrues carburants lignes_star trains equipements collecte actus circulation usages_eau defibrillateurs bornes agenda cinema --sortie $RACINE/public --etat $RACINE/etat" \
    || echo "Attention : une source a échoué, voir ci-dessus (le cron réessaiera)."
if curl -fsS -o /dev/null http://127.0.0.1:8080/manifest.json; then echo "nginx sert bien manifest.json"
else echo "Attention : nginx ne sert pas manifest.json (voir /var/log/nginx/error.log)."; fi

etape "Exposition publique (Tailscale Funnel)"
command -v tailscale &>/dev/null || curl -fsSL https://tailscale.com/install.sh | sh
if ! tailscale status &>/dev/null; then
    echo "Connexion à Tailscale : ouvre le lien affiché et connecte-toi (compte gratuit)."
    tailscale up --hostname cesson-pi
fi
# --bg : le relais est mémorisé par tailscaled et survit aux redémarrages.
# Au premier lancement, la commande peut afficher un lien pour autoriser Funnel sur ton compte.
tailscale funnel --bg 8080
tailscale funnel status

cat <<FIN

Terminé.
- Adresse publique des données : celle affichée ci-dessus (https://cesson-pi.….ts.net/).
  C'est la valeur de -Pcesson.donneesUrl pour la release : elle est figée dans l'app.
- Alertes : installe l'app ntfy et abonne-toi au sujet  $SUJET
- Santé des sources : sudo -u cesson $CODE/.venv/bin/python -m cesson sante --etat $RACINE/etat
FIN
