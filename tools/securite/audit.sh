#!/usr/bin/env bash
# Audit de sécurité des dépendances : SBOM CycloneDX (backend et app) et vulnérabilités connues (OSV).
# Usage : tools/securite/audit.sh [dossier de sortie]   (par défaut : build/securite)
set -euo pipefail
RACINE=$(cd "$(dirname "$0")/../.." && pwd)
SORTIE=${1:-$RACINE/build/securite}
mkdir -p "$SORTIE"
OUTILS="$SORTIE/.venv"
[[ -x $OUTILS/bin/pip-audit ]] || { python3 -m venv "$OUTILS" && "$OUTILS/bin/pip" install -q pip-audit cyclonedx-bom; }

echo "== Backend : SBOM et vulnérabilités (PyPI)"
"$OUTILS/bin/cyclonedx-py" environment "$RACINE/backend/.venv/bin/python" --of JSON -o "$SORTIE/sbom-backend.cdx.json"
"$RACINE/backend/.venv/bin/pip" freeze --exclude-editable > "$SORTIE/backend-requirements.txt"
"$OUTILS/bin/pip-audit" -r "$SORTIE/backend-requirements.txt" --no-deps --disable-pip --progress-spinner off

echo "== App Android : SBOM et vulnérabilités (Maven, base OSV)"
(cd "$RACINE/android" && ./gradlew -q :app:dependencies --configuration releaseRuntimeClasspath) > "$SORTIE/android-deps.txt"
python3 -I - "$SORTIE" <<'PY'
import json, re, sys, urllib.request
sortie = sys.argv[1]
paquets = sorted({(m[0], m[2] or m[1]) for m in re.findall(r"([\w.-]+:[\w.-]+):([\w.-]+)(?: -> ([\w.-]+))?", open(f"{sortie}/android-deps.txt").read())})
q = {"queries": [{"package": {"ecosystem": "Maven", "name": n}, "version": v} for n, v in paquets]}
req = urllib.request.Request("https://api.osv.dev/v1/querybatch", json.dumps(q).encode(), {"Content-Type": "application/json"})
res = json.load(urllib.request.urlopen(req, timeout=60))["results"]
json.dump({"bomFormat": "CycloneDX", "specVersion": "1.5", "version": 1,
           "metadata": {"component": {"type": "application", "name": "Cesson'App"}},
           "components": [{"type": "library", "group": n.split(":")[0], "name": n.split(":")[1], "version": v,
                           "purl": f"pkg:maven/{n.replace(':', '/')}@{v}"} for n, v in paquets]},
          open(f"{sortie}/sbom-android.cdx.json", "w"), indent=1)
touches = [(p, [x["id"] for x in r.get("vulns", [])]) for p, r in zip(paquets, res) if r.get("vulns")]
print(f"{len(paquets)} paquets, {len(touches)} avec une vulnérabilité connue")
for (n, v), ids in touches:
    print(f"  {n}:{v} {' '.join(ids)}")
sys.exit(1 if touches else 0)
PY
echo "SBOM : $SORTIE/sbom-backend.cdx.json, $SORTIE/sbom-android.cdx.json"
