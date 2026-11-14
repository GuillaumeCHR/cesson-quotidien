# Cesson au quotidien

App Android non officielle (Yogi Studio) qui regroupe l'information pratique de Cesson-Sévigné à
partir de données publiques. Aucun compte, aucune publicité, aucune donnée collectée.
Architecture : voir « Cesson au quotidien — Document d'architecture.md ».

## Backend (collecteurs Python → fichiers JSON statiques)

    cd backend
    python3 -m venv .venv && .venv/bin/pip install -e '.[dev]'
    .venv/bin/pytest                                  # tests
    .venv/bin/python -m cesson collecter vilaine      # publie public/vilaine.json + manifest.json
    .venv/bin/python -m cesson sante                  # tableau de santé des sources
    .venv/bin/python -m cesson schemas                # régénère schemas/*.json (contrat avec l'app)

Planification et service : `backend/deploy/crontab`, `backend/deploy/nginx.conf`.

## App Android

    cd android
    ./gradlew testDebugUnitTest assembleDebug

En debug, l'app lit `http://127.0.0.1:8000/`. Pour tester sur un téléphone branché en USB :

    python3 -m http.server -b 127.0.0.1 -d backend/public 8000
    adb reverse tcp:8000 tcp:8000

Release : `-Pcesson.donneesUrl=https://…` et `-Pcesson.contact=…`.
