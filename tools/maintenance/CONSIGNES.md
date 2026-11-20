# Maintenance hebdomadaire de Cesson'App (tâche Claude planifiée)

Chaque semaine, une tâche Claude relit les sources saisies à la main et répare ce qui a décroché. Elle **propose**
ses changements dans une pull request : elle ne pousse jamais sur `main`. Le mainteneur relit et fusionne ; le Pi
se met à jour tout seul dans l'heure qui suit (si les tests passent sur GitHub).

## 1. État du serveur

- Lire `https://cesson-pi.tail7e6f0d.ts.net/manifest.json`. Pour chaque module en `erreur` ou `perime`, ou dont
  `recupere_le` est plus vieux que sa fraîcheur attendue : comprendre pourquoi (source déplacée, mise en page
  changée, PDF différent) en interrogeant la source, puis corriger le collecteur dans `backend/cesson/collecteurs/`,
  avec un test sur une fixture réelle (jamais de données inventées).
- `genere_le` vieux de plus de 45 min : le Pi est arrêté. Ne rien corriger ; le signaler dans le résumé.

## 2. Sources saisies à la main (`backend/saisie/*.yaml`)

Pour chaque fichier, comparer avec la page officielle indiquée (`url`), et mettre à jour `verifie_le` à la date du
jour si tout concorde.

- **equipements.yaml** : horaires de la piscine, de la médiathèque, du golf, de la Base Sports Nature, de la
  déchetterie, des services urbanisme et police municipale. Ajouter les périodes de vacances scolaires annoncées,
  les fermetures exceptionnelles et les ouvertures de jours fériés (voir l'en-tête du fichier pour la syntaxe).
- **Base Sports Nature** (`saisonnier: true`) : hors des périodes saisies, l'app la dit fermée « hors saison ».
  De mars à juin, guetter l'annonce de la saison suivante sur sa page et la saisir avant l'ouverture (même
  structure que 2026 : pleine saison, après-saison ; eau calme, eaux vives), puis mettre à jour la remarque.
- **collecte.yaml** : calendrier des collectes de Rennes Métropole. Un nouveau calendrier sort chaque année
  (en général fin août, valable d'octobre à septembre) : dès qu'il est en ligne sur la page « Gestion des
  déchets », mettre à jour `calendrier`, `valable_du`, `valable_au` et les règles, puis vérifier que les tests
  retrouvent chaque date du PDF (décalages des jours fériés compris).
- **cinema.yaml** : le lien du mois est lu automatiquement sur https://www.cinesevigne.fr/. Si le module cinéma
  est en erreur, vérifier ce site (titre des liens « grille <mois> <aa> ») et mettre à jour `grille` en secours.
- **carburants.yaml** : noms des stations (Carrefour Rigourdière, Carrefour Market Bourgchevreuil, Total + rue).

## 3. Liens écrits dans l'app

Vérifier qu'ils répondent et mènent toujours au bon service ; sinon, trouver le nouveau lien sur le site de la ville :
billetterie (`actus/CarteSaison.kt`), carte d'identité et passeport, signalements ville et métropole
(`pratique/CarteDemarches.kt`), portail familles (`familles/CartesFamilles.kt`), urbanisme, police municipale,
canaux WhatsApp et Facebook de l'écran À propos (`actus/Canaux.kt`).

## 4. Sources automatiques à surveiller

- **Menus de la cantine** : actualité « Menu <mois> » du portail familles (`/api/news-list/public`). Si le menu
  du mois suivant n'est pas lu alors qu'il est publié, adapter `familles.py`. Ne jamais reprendre une actualité
  marquée `estPrive`, ni l'ajouter aux fixtures.
- **Agenda** et **actualités** de la ville : extraction du site, fragile.

## Règles

- Données publiques uniquement ; aucune clé, aucun identifiant dans le dépôt.
- `cd backend && python3 -m venv .venv && .venv/bin/pip install -q -e '.[dev]' && .venv/bin/pytest -q` et `cd android && ./gradlew testDebugUnitTest` doivent passer.
- Une seule pull request par semaine, titrée « Maintenance du <date> », avec un résumé en français : ce qui a
  changé, d'après quelle source, et ce qui reste à vérifier par un humain.
- Rien à changer : pas de pull request.
