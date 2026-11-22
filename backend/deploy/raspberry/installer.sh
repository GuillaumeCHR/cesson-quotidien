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
# --auto : lancé par la mise à jour automatique (mise-a-jour-auto.sh) ; ne collecte que les modules nouveaux.
# Arguments suivants : modules à recollecter quoi qu'il arrive (leur saisie ou leur collecteur a changé).
AUTO=0; [[ ${1:-} == --auto ]] && { AUTO=1; shift; }
TOUCHES=" $* "
MODULES="vilaine meteo vigicrues carburants lignes_star trains trains_retour equipements collecte actus circulation usages_eau defibrillateurs bornes agenda cinema handball vacances_scolaires familles qualite_air pollens episodes_pollution vigilance_meteo pharmacies accessibilite decheterie"

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
apt-get install -y -q python3-venv nginx rsync logrotate unattended-upgrades curl git

etape "Durcissement"
# Connexion SSH par clé seulement (déjà réglé par Raspberry Pi Imager), jamais en root. « 00- » : sshd garde
# la première valeur lue, ce fichier passe avant les autres.
install -d /etc/ssh/sshd_config.d
printf 'PermitRootLogin no\nPasswordAuthentication no\nKbdInteractiveAuthentication no\n' > /etc/ssh/sshd_config.d/00-cesson.conf
sshd -t && systemctl reload ssh
# Mises à jour automatiques : sécurité Debian (par défaut), mais aussi correctifs Debian, dépôt Raspberry Pi
# (noyau, firmware, openssl) et Tailscale, exposé à Internet.
cat > /etc/apt/apt.conf.d/52cesson-unattended <<'FIN'
Unattended-Upgrade::Origins-Pattern {
        "origin=Raspberry Pi Foundation,codename=${distro_codename},label=Raspberry Pi Foundation";
        "origin=Debian,codename=${distro_codename},label=Debian";
        "origin=Tailscale";
};
FIN

etape "Utilisateur et dossiers"
id cesson &>/dev/null || useradd --system --create-home --home-dir "$RACINE" --shell /usr/sbin/nologin cesson
# useradd crée le dossier personnel en 700 : nginx (www-data) doit pouvoir le traverser pour lire public/.
chmod 755 "$RACINE"
install -d -o cesson -g cesson -m 755 "$RACINE/public"
install -d -o cesson -g cesson -m 750 "$RACINE/etat" "$RACINE/log"
# Identifiants des API à compte (Atmo Data) : saisis à la main par le mainteneur, jamais dans le dépôt.
# root les possède, l'utilisateur cesson peut seulement les lire (fichiers en 640 root:cesson).
install -d -o root -g cesson -m 750 "$RACINE/secrets"
[[ -s $RACINE/secrets/atmo.env ]] || echo "Attention : $RACINE/secrets/atmo.env manque, qualité de l'air et pollens resteront vides."
[[ -s $RACINE/secrets/meteofrance.env ]] || echo "Attention : $RACINE/secrets/meteofrance.env manque, la vigilance météo restera vide."
[[ -s $RACINE/secrets/acceslibre.env ]] || echo "Attention : $RACINE/secrets/acceslibre.env manque, l'accessibilité des lieux restera vide."
[[ -s $RACINE/secrets/tmdb.env ]] || echo "Attention : $RACINE/secrets/tmdb.env manque, les films du cinéma resteront sans fiche."
[[ -s $RACINE/secrets/rennes.env ]] || echo "Remarque : $RACINE/secrets/rennes.env manque, circulation et déchèterie appelleront Rennes Métropole sans clé (quota de l'adresse IP)."

etape "Code et environnement Python"
# Le code appartient à root : l'utilisateur cesson ne peut écrire que public, etat et log.
rsync -a --delete --exclude .venv --exclude public --exclude etat --exclude __pycache__ \
      --exclude .pytest_cache "$SOURCE/" "$CODE/"
chown -R root:root "$CODE"
chmod -R go-w "$CODE"
[[ -x $CODE/.venv/bin/python ]] || python3 -m venv "$CODE/.venv"
"$CODE/.venv/bin/pip" install -q --upgrade pip
"$CODE/.venv/bin/pip" install -q -e "$CODE"

etape "nginx (local uniquement, port 8080)"
# Cache des tuiles de la carte (contexte http) : voir tuiles.py.
install -d -o www-data -g www-data -m 750 /var/cache/nginx/cesson-tuiles
install -m 644 "$ICI/tuiles-http.conf" /etc/nginx/conf.d/cesson-tuiles.conf
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

etape "Mise à jour automatique depuis GitHub (toutes les heures)"
# Le Pi suit la branche main du dépôt public, seulement vers des commits dont les tests ont réussi.
install -m 755 "$ICI/mise-a-jour-auto.sh" /usr/local/sbin/cesson-mise-a-jour
install -m 644 "$ICI/cesson-mise-a-jour.service" "$ICI/cesson-mise-a-jour.timer" /etc/systemd/system/
systemctl daemon-reload
systemctl enable --now cesson-mise-a-jour.timer

etape "Première collecte"
A_COLLECTER=$MODULES
if (( AUTO )); then  # mise à jour : seulement les modules qui n'ont encore rien publié
    A_COLLECTER=$(for m in $MODULES; do
        if [[ ! -s $RACINE/public/$m.json || $TOUCHES == *" $m "* ]]; then printf '%s ' "$m"; fi
    done)
fi
if [[ -n ${A_COLLECTER// } ]]; then
    sudo -u cesson env CESSON_ALERTE_URL="https://ntfy.sh/$SUJET" bash -c \
        "cd $CODE && .venv/bin/python -m cesson collecter $A_COLLECTER --sortie $RACINE/public --etat $RACINE/etat" \
        || echo "Attention : une source a échoué, voir ci-dessus (le cron réessaiera)."
fi
if curl -fsS -o /dev/null http://127.0.0.1:8080/manifest.json; then echo "nginx sert bien manifest.json"
else echo "Attention : nginx ne sert pas manifest.json (voir /var/log/nginx/error.log)."; fi

if (( AUTO )); then echo "Mise à jour terminée."; exit 0; fi

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
