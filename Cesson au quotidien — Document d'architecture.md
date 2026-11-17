# Cesson au quotidien — Document d'architecture

Nov 14, 2026 · @Guillaume Chéreau

## Objet et périmètre

« Cesson au quotidien » est une app Android native qui regroupe en un écran l'information pratique de Cesson-Sévigné, à partir de données publiques. Elle répond à une question simple : qu'est-ce qui est ouvert, qu'est-ce qui passe, qu'est-ce qui m'attend aujourd'hui ?

Périmètre fonctionnel, en 6 domaines :

- **Se déplacer** : bus et métro STAR, trains à la halte de Cesson-Sévigné, vélos en libre-service, parcs-relais, carburants, bornes de recharge.
- **Équipements** : piscine, médiathèque, Pont des Arts, golf municipal, Base Sports Nature, déchèterie.
- **Sortir** : spectacles du Carré Sévigné, cinéma Le Sévigné, agenda de la ville.
- **Environnement et risques** : météo, niveau de la Vilaine, vigilance crues, vigilance météo, qualité de l'air.
- **Pratique** : collecte des déchets, défibrillateurs, accessibilité des lieux publics.
- **Actualités** : fil de la ville, liens vers la chaîne WhatsApp municipale et le groupe Facebook Cesson Entraide (non officiel).

Hors périmètre : offres d'emploi, comptes utilisateurs, contenus politiques.

**Statut** : application personnelle et **non officielle**, publiée par Yogi Studio. Elle n'est pas éditée par la Ville de Cesson-Sévigné ni par Rennes Métropole. Elle ne remplace ni les canaux officiels de la ville, ni Vigicrues, ni FR-Alert.

## Principes directeurs

Cinq règles tranchent les choix techniques du projet.

1. **Données publiques d'abord.** Une API ouverte passe avant l'extraction d'une page web, et l'extraction avant la saisie manuelle. Pas d'extraction là où les conditions d'utilisation l'interdisent (Allociné, WhatsApp).
2. **Fiabilité affichée.** Chaque donnée montre sa source et son heure de mise à jour. Une donnée périmée est signalée comme telle, jamais masquée.
3. **Sobriété.** Un backend qui ne publie que des fichiers JSON statiques, sans base de données ni serveur d'API.
4. **Neutralité.** L'app est informative et factuelle. Aucune expression politique, aucun commentaire de la politique municipale.
5. **Vie privée par conception.** Comme toutes les apps Yogi Studio : **aucun compte, aucune publicité, aucune donnée collectée**. Cette promesse est affichée dans l'app (premier lancement, À propos). Les favoris et l'ordre de l'accueil restent sur le téléphone.

## Vue d'ensemble

&#91;embedded content: architecture · 3 types de sources, 1 backend statique, 1 app\]

Toutes les données passent par des fichiers JSON statiques avant d'atteindre l'app. Seuls les prochains passages STAR sont demandés en direct, et seules les alertes imprévues passent par FCM.

## Catalogue des sources

11 modules sur 19 reposent sur une API ou un flux ouvert déjà identifié. Le reste demande une extraction de page, une saisie annuelle ou un partenariat.

| Module | Source | Format, accès | Fraîcheur | Clé |
| --- | --- | --- | --- | --- |
| Bus et métro | [STAR GTFS + GTFS-RT](https://www.data.gouv.fr/datasets/versions-des-horaires-theoriques-des-lignes-de-bus-et-de-metro-du-reseau-star-au-format-gtfs) | GTFS publié chaque soir vers 23h30 ; GTFS-RT alertes, trip updates, positions | Temps réel | Non |
| Prochains passages, vélos, parcs-relais | [API data.explore.star.fr](https://www.data.gouv.fr/organizations/star/datasets), GBFS | REST JSON, ODbL ; clé d'API gratuite du compte (quota propre de 2 millions d'appels par jour) | Temps réel | Oui (gratuite, embarquée) |
| Trains (halte de Cesson) | [Horaires SNCF](https://www.data.gouv.fr/datasets/horaires-sncf) : GTFS national + GTFS-RT | Théorique : GTFS TER BreizhGo ou GTFS SNCF national. Temps réel : GTFS-RT SNCF national uniquement (TU toutes les 2 min pour les 60 prochaines minutes, SA pour les perturbations) | 2 min | Non |
| Niveau de la Vilaine | [Hub'Eau Hydrométrie](https://www.data.gouv.fr/dataservices/hubeau-hydrometrie/reuses), station de Pont Briand (J709063002), hauteur H en mm | REST JSON, Licence Ouverte | Quotidien (carte informative) | Non |
| Vigilance crues | [Flux Vigicrues des tronçons](https://www.data.gouv.fr/datasets/troncons-de-cours-deau-vigicrues-simplifies-avec-niveau-de-vigilance-crues) | GeoJSON national `services/InfoVigiCru.geojson` (≈ 2,2 Mo), champ `NivInfViCr` (1 vert à 4 rouge) ; Cesson est sur le tronçon BT9 « Vilaine amont » | 2 fois par jour au moins ; collecte toutes les 15 min | Non |
| Qualité de l'air, pollens, épisodes de pollution | [API Atmo Data](https://atmo-France.org/sites/federation/files/medias/documents/2023-10/FAQ_API_Atmo_Data_20231010_0.pdf) (Air Breizh) | REST avec compte (jeton JWT de 24 h). Indice ATMO de Rennes Métropole (EPCI 243500139, Air Breizh ne le calcule pas par commune), pollens de Cesson (35051), épisodes d'Ille-et-Vilaine en CSV | Indice vers 15h45, pollens vers 16h15 ; épisodes en journée | Oui |
| Météo (prévisions) | [Open-Meteo](https://open-meteo.com/), modèles Météo-France AROME et ARPEGE | REST JSON, CC BY 4.0 ; pas de probabilité de pluie avec ces modèles, d'où des cumuls en mm | Horaire | Non |
| Vigilance météo | [API Bulletin Vigilance](https://portail-api.meteofrance.fr/) de Météo-France | REST JSON, OAuth2 (identifiant d'application échangé contre un jeton d'une heure) ; carte (couleur par phénomène et chronologie, J et J+1) et textes du bulletin départemental, filtrés sur l'Ille-et-Vilaine | 15 min | Oui |
| Carburants | [Flux instantané v2](https://data.economie.gouv.fr/explore/dataset/prix-des-carburants-en-france-flux-instantane-v2/table/?flg=fr-fr) | REST (API Opendatasoft), prix, horaires et services par station, filtré sur le code postal 35510 ; pas de nom de station, saisi dans `saisie/carburants.yaml` d'après OpenStreetMap (ODbL) | 10 min | Non |
| Bornes de recharge | Fichier national IRVE (data.gouv) | CSV | Hebdomadaire | Non |
| Défibrillateurs | Base nationale Géo'DAE (data.gouv) | CSV | Hebdomadaire | Non |
| Accessibilité | API acceslibre | REST | Hebdomadaire | Oui (clé d'essai de 3 mois reçue le 17/11/2026, clé pérenne à demander mi-février 2027) |
| Déchèterie | [Jeu de données Rennes Métropole](https://www.data.gouv.fr/datasets/decheteries-et-plateformes-vegetaux-sur-rennes-metropole-1) + page des horaires | CSV + extraction de page | Quotidien | Non |
| Collecte des déchets | Calendrier papier de Rennes Métropole | Saisie YAML par secteur | Annuel + jours fériés | — |
| Piscine, médiathèque, Pont des Arts | Site et magazine de la ville | Saisie YAML + exceptions | À chaque changement | — |
| Golf municipal | [Plaquette des horaires](https://ville-cesson-sevigne.fr/wp-content/uploads/2024/12/golftarifs2025.pdf) | Saisie YAML, saisons hiver et été | Saisonnier | — |
| Base Sports Nature | Plaquettes des stages et locations | Saisie YAML, périodes d'activité | Saisonnier | — |
| Spectacles | [Billetterie de la ville](https://billetterie.ville-cesson-sevigne.fr/), OpenAgenda (à vérifier) | Flux à identifier, sinon extraction | Quotidien | — |
| Cinéma Le Sévigné | Partenariat avec la salle | Flux de son logiciel de billetterie | Quotidien | — |
| Actualités | Site de la ville (RSS ou API WordPress, à tester) | RSS | Horaire | Non |

**Trains.** La [halte de Cesson-Sévigné](https://www.ter.sncf.com/bretagne/se-deplacer/gares/cesson-sevigne-87334490) (code 87334490, chemin du Bois de la Justice) est desservie par la [ligne Rennes – Vitré – Laval – Le Mans](https://www.ter.sncf.com/bretagne/se-deplacer/lignes-ter-breizhgo/rennes-laval-le-mans). Le temps réel ne vient que de la SNCF : la Région publie un [GTFS TER BreizhGo](https://www.data.gouv.fr/datasets/breizhgo-ter/reuses_and_dataservices) (ODbL) limité aux horaires théoriques, sans flux temps réel. Le collecteur combine donc le théorique et le [GTFS-RT national SNCF](https://www.data.gouv.fr/datasets/horaires-sncf), filtrés sur ce seul arrêt.

Trois limites de ce temps réel conditionnent l'affichage :

- **Couverture TER non garantie.** La SNCF ne [garantit le périmètre](https://data.opendatasoft.com/explore/dataset/sncf-ter-gtfs@datasncf) que pour les TGV et les Intercités.
- **Interruptions passées.** Le flux de trip updates est [resté vide](https://www.data.gouv.fr/datasets/horaires-des-tgv/discussions) fin juillet 2025 puis en octobre 2025.
- **Pas d'API simple.** Seul un flux national en protobuf existe ; le backend le décode et publie un `trains.json` limité à la halte.

Chaque départ affiche l'heure théorique et un statut : « à l'heure », « +7 min », « supprimé », ou « horaire théorique » quand le temps réel manque pour ce train.

**Mise en œuvre.** Le collecteur n'extrait du GTFS national (5 à 6 Mo, 84 Mo de `stop_times`) que les trains qui desservent la halte, une fois par jour, dans un fichier d'état couvrant 8 jours. Toutes les 2 min, il lit le GTFS-RT national (≈ 0,6 Mo) et rapproche les trains par numéro et date de départ, plus stables que le `trip_id`. Un retard publié pour une gare précédente se propage jusqu'à Cesson.

### Focus : les lignes qui passent par Cesson

Le module transport met en avant les seules lignes qui desservent la commune, métro b en tête. La liste n'est pas saisie à la main : le collecteur la déduit chaque nuit du GTFS. Il retient toute ligne dont au moins un arrêt est situé dans le périmètre communal (code INSEE 35051). Une refonte du réseau STAR est ainsi prise en compte sans intervention.

| Mode | Exemples de lignes | Arrêts clés à Cesson | Source |
| --- | --- | --- | --- |
| Métro | [Ligne b](https://www.lejournaldesentreprises.com/article/la-ligne-b-du-metro-rennais-mise-en-service-ce-mardi-20-septembre-5-h-10-2045163), 15 stations entre Saint-Jacques-de-la-Lande et Cesson-ViaSilva | [Cesson-ViaSilva et Atalante](https://en.wikipedia.com/wiki/Rennes_Metro), toutes deux sur la commune | GTFS-RT STAR |
| Bus STAR | [C1, 67, 164ex, 221, 223](https://www.transbus.org/reseaux/35000.html)… (liste à recalculer depuis le GTFS) | Cesson-ViaSilva, Lycée Sévigné, Château de Vaux | GTFS-RT STAR |
| Train TER | Rennes – Vitré – Laval – Le Mans | Halte de Cesson-Sévigné | GTFS + GTFS-RT SNCF |

L'écran « Bouger » propose en premier un **pôle Cesson-ViaSilva** qui regroupe métro et bus STAR sur une seule vue des départs. C'est le principal point de correspondance de la commune. La halte ferroviaire a sa propre carte, avec les deux directions Rennes et Vitré.

## Collecte : cadences et gabarit

Chaque source a un collecteur Python dédié, lancé à la cadence que sa donnée justifie.

| Cadence | Modules | Planificateur |
| --- | --- | --- |
| Sur le téléphone, en direct | Prochains passages STAR | Aucun |
| 2 min | Trains (GTFS-RT TU), filtrés sur la halte | Serveur avec cron fiable |
| 10 à 15 min | Vigicrues, carburants, alertes STAR et SNCF | Serveur avec cron fiable |
| Horaire | Météo, actualités | Serveur |
| 15 min | Vigilance crues, vigilance météo | Serveur |
| Quotidien | Hauteur de la Vilaine (6h05), GTFS STAR (0h30), GTFS SNCF, qualité de l'air et pollens (16h20, rattrapage à 18h20), épisodes de pollution (7h20, 11h20, 16h20, 18h20), spectacles, cinéma, déchèterie | Serveur |
| Hebdomadaire ou mensuel | Défibrillateurs, bornes, acceslibre, contrôle des YAML | Serveur |

Les crons de GitHub Actions peuvent prendre des dizaines de minutes de retard. Ils ne conviennent donc pas aux cadences de 2 à 15 min, en particulier pour Vigicrues. Ces tâches tournent sur un petit VPS : le décodage du GTFS-RT SNCF national dépasserait les limites de calcul de Cloudflare Workers.

**Hauteur de la Vilaine.** Une collecte par jour suffit pour cette carte informative, qui affiche l'heure de la mesure. La sécurité repose sur la vigilance Vigicrues, collectée toutes les 10 à 15 min. Point ouvert : accélérer la collecte de la hauteur quand Vigicrues passe en jaune ou plus.

**Gabarit commun à tous les collecteurs :**

1. Télécharger la source, avec un délai maximal et 2 nouvelles tentatives.
2. Valider le format reçu (schéma attendu, nombre d'éléments plausible).
3. Convertir vers le modèle commun.
4. Comparer avec la version précédente et noter les changements utiles aux alertes.
5. Publier le JSON du module.

En cas d'échec à l'une de ces étapes, on garde la dernière version valide et on la marque `perime`. On ne publie jamais un fichier vide.

## Modèle de données commun

Six entités et un bloc `Meta` couvrent l'ensemble des 19 modules.

| Entité | Champs | Utilisée par |
| --- | --- | --- |
| `Lieu` | id, nom, type, coord, adresse, source, url | Tous les modules géolocalisés |
| `Horaire` | lieu\_id, sous\_espace, periode, opening\_hours (format OSM), exceptions \[du, au, motif\] | Équipements, déchèterie, golf, base nautique |
| `Depart` | arret, ligne, mode (bus, metro, train), destination, heure\_theorique, heure\_reelle, statut | STAR, trains |
| `Evenement` | id, titre, lieu\_id, debut, fin, categorie, prix, url\_billet | Spectacles, cinéma, agenda |
| `Mesure` | station, grandeur (H, Q, ATMO, prix), valeur, unite, horodatage, tendance | Vilaine, air, carburants |
| `Alerte` | id, niveau (info, jaune, orange, rouge), titre, zone, debut, fin, source | Crues, météo, STAR, SNCF, fermetures |
| `Meta` | source, licence, recupere\_le, valide\_jusqu\_a, etat (ok, perime, erreur) | Chaque fichier publié |

**Horaires.** Le format OSM `opening_hours` gère les saisons (golf hiver et été), les jours fériés et les plages multiples. Le champ `sous_espace` distingue le parcours du practice, ou la piscine du sauna. Le champ `periode` décrit les activités saisonnières comme la location de kayaks.

**Meta.** Ce bloc alimente la mention « mis à jour il y a 12 min, source Vigicrues » sur chaque carte. Il répond aussi aux obligations d'attribution des licences ODbL et Licence Ouverte.

## Publication et hébergement

Le backend ne publie que des fichiers statiques sur un CDN. L'app ne parle à aucun serveur d'API maison.

- **Un fichier JSON par module** : `vilaine.json`, `trains.json`, `carburants.json`, `agenda.json`, `equipements.json`…
- **Un `manifest.json`** qui donne pour chaque module sa version, sa date et son état. L'app le lit en premier et ne télécharge que les modules qui ont changé.
- **Hébergement** : nginx sur le VPS (ou un bucket R2) derrière le CDN Cloudflare, avec un cache court (60 s pour le manifest, 120 s pour les modules). Cloudflare Pages et GitHub Pages sont écartés : ils redéploient tout le site à chaque publication, incompatible avec une mise à jour toutes les 2 min.
- **Dépôt Git unique** : collecteurs, fichiers YAML de saisie, tests. Les YAML sont versionnés, ce qui garde l'historique des horaires et des fermetures.

Les JSON publiés sont ouverts. Ils pourront servir plus tard à une page web de consultation pour les utilisateurs d'iPhone, sans travail supplémentaire côté backend.

## Application Android native

L'app est écrite en Kotlin avec Jetpack Compose, et organisée en un module Gradle par domaine.

| Brique | Choix | Raison |
| --- | --- | --- |
| Langage, interface | Kotlin, Jetpack Compose, Material 3 | Standard actuel d'Android |
| Réseau | OkHttp, kotlinx.serialization | Lecture des JSON de modules et de l'API STAR |
| Cache hors connexion | JSON bruts sur disque, préférences en DataStore | Dernière version de chaque module disponible sans réseau, utile pendant une crue |
| Tâches de fond | WorkManager | Rafraîchissement périodique et rappels locaux, compatibles avec la mise en veille d'Android |
| Carte | MapLibre, fonds OpenStreetMap | Pas de clé Google Maps, cohérent avec les données OSM |
| Widget d'accueil | Jetpack Glance | Widget « Aujourd'hui à Cesson » : collecte, Vilaine, prochain bus ou train |
| Push | Firebase Cloud Messaging, par sujets | Seul moyen fiable de réveiller un téléphone en veille |
| Architecture | MVVM, un module Gradle par domaine | Ajouter ou retirer un module sans toucher au reste |

**Modules Gradle** : `core` (modèle, cache, manifest), `transport` (STAR, trains, vélos, parcs-relais), `eau` (Vilaine, vigilance crues), `equipements`, `agenda`, `environnement` (météo, air, vigilance météo), `pratique` (déchets, défibrillateurs, accessibilité, carburants, bornes), `actus`, `widget`.

**Temps réel.** Les prochains passages STAR sont demandés directement à l'API data.explore.star.fr, en JSON. Pour les trains, le backend décode le GTFS-RT national (protobuf, toute la France) et publie un `trains.json` allégé, limité à la halte de Cesson. Le téléphone n'a ainsi jamais à télécharger le flux national.

## Notifications et moteur d'alertes

Les rappels prévisibles sont calculés sur le téléphone. Seuls les événements imprévus passent par le serveur et FCM.

| Règle | Déclencheur | Mécanisme |
| --- | --- | --- |
| Crue | Tronçon de la Vilaine en vigilance jaune ou plus, ou montée rapide à Pont Briand | Serveur, sujet FCM `crue` |
| Fermeture | Exception ajoutée sur un équipement suivi | Serveur, sujet `fermeture-<equipement>` |
| Pollution | Indice ATMO prévu « mauvais » ou pire pour le lendemain | Serveur, sujet `air`, vers 14h30 |
| Bus | Alerte STAR sur une ligne suivie | Serveur, sujet `star-<ligne>` |
| Train | Suppression ou retard d'un train à la halte de Cesson | Serveur, sujet `train-cesson` |
| Collecte | Veille au soir du jour de collecte, reports fériés compris | Local, WorkManager |
| Spectacle | Rappel la veille d'un spectacle mis en favori | Local, WorkManager |

**Sujets FCM.** Le serveur publie sur un sujet sans connaître les abonnés. Il n'existe donc aucune base d'utilisateurs à protéger.

**Crue.** L'alerte reprend le niveau de vigilance officiel de Vigicrues, et non un seuil maison. Les fiches peuvent donner des repères historiques, comme la [crue du 26 octobre 1966](https://www.reperesdecrues.developpement-durable.gouv.fr/export/754750/repere) : 3,73 m au-dessus du zéro de l'échelle de Pont Briand (orthographe officielle de la station Hub'Eau). Chaque alerte rappelle que l'app ne remplace ni Vigicrues ni FR-Alert.

**Permission.** La permission `POST_NOTIFICATIONS` (Android 13 et plus) est demandée quand l'utilisateur active sa première alerte, pas au premier lancement. Pas d'alarmes exactes : un rappel à 20h à 15 minutes près suffit.

## Direction de design

L'app reprend les bons usages du [site de la ville](https://www.ville-cesson-sevigne.fr/), sans sa charte graphique : aucun logo, blason, couleur, police ou photo officielle. Cette distance visuelle montre aussi d'emblée que l'app n'est pas un outil municipal.

**Ce que l'on reprend du site :**

- **Des accès rapides en tête de page.** Le site ouvre sur 6 raccourcis vers les tâches les plus fréquentes (piscine, billetterie, signalement…). L'app ouvre sur une rangée de 4 à 6 tuiles configurables : mes arrêts, ma collecte, mes équipements, la Vilaine.
- **Une organisation par besoin.** Le site range ses services par situation de vie plutôt que par service municipal. L'app fait de même : Bouger, Sortir, Pratique, et non « Direction des sports ».
- **Des cartes d'agenda avec un badge de date** (jour et mois), et une actualité à la une suivie d'une liste.
- **L'accessibilité mise en avant** : textes agrandissables, contrastes AA, libellés explicites pour TalkBack.
- **Un ton civique et chaleureux** : titres courts à l'impératif (Découvrir, Sortir ce soir), vouvoiement, phrases brèves.
- **L'identité « ville-jardin » au bord de la Vilaine**, traduite par une palette végétale et aquatique propre à l'app.

**Palette propre à l'app** (à vérifier pour qu'elle ne coïncide pas avec la charte de la ville) :

| Rôle | Nom | Valeur | Usage |
| --- | --- | --- | --- |
| Primaire | Vert bocage | #2F6B4F | Barre d'onglets, boutons, état « ouvert » |
| Secondaire | Bleu Vilaine | #1F5F8B | Module eau, transports, liens |
| Fond | Sable | #F6F3EC | Fond clair des écrans |
| Texte | Ardoise | #23302B | Texte principal |
| Vigilance jaune | — | #F2C94C | Codes officiels de vigilance uniquement |
| Vigilance orange | — | #F2994A | Codes officiels de vigilance uniquement |
| Vigilance rouge | — | #D64545 | Codes officiels de vigilance uniquement |

Le jaune, l'orange et le rouge sont réservés aux niveaux de vigilance, pour que leur sens reste sans ambiguïté. Un thème sombre dérivé est fourni par Material 3.

**Typographie.** Atkinson Hyperlegible pour le texte, conçue pour la lisibilité par les malvoyants. Roboto Flex pour les chiffres d'horaires et de mesures. Aucune des deux n'est la police du site municipal.

**Iconographie.** Icônes Material Symbols au trait arrondi. Illustrations plates d'éléments du paysage (rivière, arbres, passerelle), sans photo de bâtiment municipal.

## Écrans

L'app compte 5 onglets et un widget d'accueil. L'onglet Aujourd'hui répond seul à l'essentiel.

| Écran | Contenu | Données |
| --- | --- | --- |
| Aujourd'hui | Bandeau d'alertes ; tuiles que l'utilisateur ordonne et masque (écran Personnaliser) ; météo (actuelle, prochaines heures, aujourd'hui et demain) ; équipements suivis ouverts ou fermés ; prochaine collecte ; prochains départs à Cesson-ViaSilva et à la halte ; Vilaine ; qualité de l'air | Tous modules, version résumée |
| Bouger | Pôle Cesson-ViaSilva ; halte ferroviaire ; lignes passant par Cesson ; vélos ; parcs-relais ; carburants ; bornes de recharge | STAR, SNCF, carburants, IRVE |
| Sortir | Carré Sévigné ; cinéma Le Sévigné ; Pont des Arts et médiathèque ; golf ; Base Sports Nature ; piscine | Agenda, équipements, Vilaine (sur le golf et la base nautique) |
| Pratique | Collecte par secteur ; déchèterie ; défibrillateurs ; accessibilité des lieux | YAML collecte, Rennes Métropole, Géo'DAE, acceslibre |
| Actus | Fil de la ville ; liens vers la chaîne WhatsApp et le groupe Cesson Entraide | RSS de la ville |
| Widget | Prochaine collecte, niveau de la Vilaine, prochain métro b ou train | Cache local |

**Accueil personnalisable.** Comme l'app de Nantes, l'utilisateur ordonne et masque ses tuiles, ce qui répond à la diversité des usages. Le réordonnancement passe par des boutons « monter / descendre » compatibles TalkBack. Un appui long sur une carte propose « Masquer cette carte » (avec Annuler) ou « Personnaliser l'accueil » ; ces actions sont aussi offertes à TalkBack. Une tuile ajoutée par une mise à jour apparaît en fin d'accueil sans effacer l'ordre choisi.

**Météo.** Carte informative. Elle mentionne les rafales au-delà de 50 km/h, sans couleur de vigilance : seule la vigilance officielle de Météo-France peut afficher jaune, orange ou rouge et déclencher une alerte.

**Vos idées / signaler une erreur.** Pour une app non officielle, c'est le moyen le plus rapide de corriger un horaire faux. La rubrique ouvre l'application mail avec un message pré-rempli : aucun serveur, aucun compte. Chaque carte porte un lien « Signaler une erreur » qui joint le module, la valeur affichée et l'heure de la donnée.

Chaque fiche d'équipement suit la même structure : état ouvert ou fermé en tête, horaires du jour, exceptions à venir, accès en transports, lien officiel, source et heure de mise à jour. Un écran « Sources et licences », accessible depuis chaque fiche, liste les jeux de données et leur licence.

## Exploitation, sécurité, RGPD et Play Store

L'app ne collecte aucune donnée personnelle. Le seul identifiant technique est le jeton FCM, déclaré au Play Store.

- **Tableau de santé.** Pour chaque source : dernière récupération réussie, fraîcheur attendue, état. Une alerte part vers le développeur quand une source décroche, en priorité les pages extraites, qui cassent quand leur mise en page change.
- **Secrets.** Les clés Atmo Data, Météo-France et acceslibre, ainsi que le compte de service FCM, restent côté serveur. Une seule exception, décidée le 16/11/2026 : la clé d'API du portail open data STAR est embarquée dans l'APK. Elle est gratuite, ne donne accès qu'à des données publiques, et les conditions du portail n'en interdisent pas le partage. Sans elle, les téléphones partagent le quota anonyme de tout le portail (150 000 appels par jour, épuisé certains soirs) ; avec elle, ils utilisent le quota du compte (2 millions par jour, soit environ 36 000 utilisateurs). Elle est injectée à la compilation, jamais versionnée, et l'app retente anonymement si elle est refusée ou si son quota est épuisé. Le serveur l'utilise aussi pour la collecte de nuit `lignes_star`.
- **RGPD.** Aucun compte, aucun traceur, aucune statistique d'usage tierce. Les favoris (arrêts, secteur de collecte, équipements suivis) restent sur le téléphone.
- **Licences.** Attribution affichée pour chaque source (ODbL pour STAR et Rennes Métropole, Licence Ouverte pour Hub'Eau, Vigicrues, carburants). Les JSON publiés dérivés de données ODbL sont eux-mêmes diffusés sous ODbL.
- **Play Store.** Publication sous le compte développeur Yogi Studio. Fiche « Sécurité des données » : aucune donnée collectée ni partagée, en dehors du jeton FCM. Description et écran d'accueil indiquant clairement le statut non officiel.
- **Mentions.** Écran « À propos » avec l'éditeur, le contact, la promesse « aucun compte, aucune publicité, aucune donnée collectée », le statut non officiel et les liens vers les canaux officiels (ville, Vigicrues, STAR, SNCF).

## Feuille de route

Le projet avance en 7 étapes, des données les plus sûres vers celles qui demandent saisie ou partenariat.

1. **Socle.** Modèle commun, gabarit de collecteur, publication JSON et manifest, squelette Compose avec les 5 onglets, accueil personnalisable, cache disque, écrans Sources, À propos et Vos idées, tableau de santé. Premier module bout en bout : la Vilaine.
2. **Données sûres.** STAR (métro b et lignes passant par Cesson), trains à la halte, Vilaine et Vigicrues, carburants, qualité de l'air.
3. **Saisie.** YAML des équipements (piscine, médiathèque, Pont des Arts, golf, Base Sports Nature) et des secteurs de collecte ; extraction des horaires de la déchèterie.
4. **Notifications.** Rappels locaux de collecte, puis alertes FCM : crue d'abord, puis fermetures, trains et bus.
5. **Guide de démarrage et personnalisation.** Au premier lancement, un guide court fait choisir les cartes de l'accueil et les réglages qui vont avec (mon arrêt, mon carburant, mes équipements, plus tard mon secteur de collecte). Chaque onglet (Bouger, Sortir, Pratique, Actus) devient personnalisable comme l'accueil : cartes affichées et ordre. Le guide reste accessible depuis le menu pour tout revoir. Tous les choix restent sur le téléphone.
6. **Widget Glance** « Aujourd'hui à Cesson ».
7. **Contenus et partenariats.** Actualités de la ville et d'Eau du Bassin Rennais (coupures d'eau, travaux sur le réseau, qualité de l'eau) dans l'onglet Actus, spectacles du Carré Sévigné, cinéma Le Sévigné, programme de la médiathèque dans l'onglet Sortir (lectures pour les tout-petits et les enfants, ateliers créatifs, club de lecture, jeux, expositions), défibrillateurs, bornes, accessibilité. Dans l'onglet Pratique : demandes d'urbanisme (permis de construire, déclaration préalable…), avec l'accès au dépôt en ligne et les informations utiles avant de déposer.

Une page web de consultation pour iPhone, alimentée par les mêmes JSON, reste possible après l'étape 7.

## Risques et points ouverts

Le risque principal est une information fausse sur une crue ou une fermeture. Elle se retournerait contre l'app et contre son auteur.

| Risque | Effet | Parade |
| --- | --- | --- |
| Alerte de crue erronée ou manquée | Atteinte à la crédibilité, risque pour les usagers | Niveau officiel Vigicrues uniquement, renvoi vers Vigicrues et FR-Alert, cron fiable |
| Page extraite qui change de structure | Horaires faux ou absents | Validation de schéma, donnée marquée périmée, alerte au développeur |
| Saisie YAML oubliée (fermeture, saison) | Horaires faux | Date de dernière vérification affichée, contrôle mensuel |
| Panne d'un flux national (SNCF, carburants) | Module vide | Dernière version conservée, mention « données du … » |
| App perçue comme outil politique | Perte de confiance | Contenu strictement factuel, statut non officiel visible, aucune tribune |
| Utilisateurs d'iPhone exclus | Audience réduite | Page web de consultation à partir des mêmes JSON |

**Points ouverts à trancher :**

- [ ] Le site de la ville expose-t-il un flux RSS ou l'API WordPress pour les actualités ?
- [ ] L'agenda de la ville est-il publié sur OpenAgenda ou un flux équivalent ?
- [ ] Quel logiciel de billetterie utilise le cinéma Le Sévigné, et exporte-t-il un programme ?
- [x] Météo-France exige un compte (identifiant d'application OAuth2, obtenu le 16/11/2026).
- [x] acceslibre exige une clé (« Authorization: Api-Key … »), obtenue le 17/11/2026 pour 3 mois d'essai ; demander la clé pérenne avant le 17/02/2027.
- [ ] Adresse de contact de la rubrique « Vos idées » (paramètre de build `cesson.contact`).
- [ ] Accélérer la collecte de la hauteur de la Vilaine en cas de vigilance crues ?
- [ ] Combien de secteurs de collecte compte Cesson, et quels sont leurs jours ?
- [ ] La palette de l'app est-elle bien distincte de la charte de la ville ?

## Sources

- [STAR — GTFS et GTFS-RT](https://www.data.gouv.fr/datasets/versions-des-horaires-theoriques-des-lignes-de-bus-et-de-metro-du-reseau-star-au-format-gtfs)
- [STAR — jeux de données](https://www.data.gouv.fr/organizations/star/datasets)
- [Lignes STAR et BreizhGo desservant Cesson (Transbus)](https://www.transbus.org/reseaux/35000.html)
- [Métro b, mise en service (Le Journal des entreprises)](https://www.lejournaldesentreprises.com/article/la-ligne-b-du-metro-rennais-mise-en-service-ce-mardi-20-septembre-5-h-10-2045163)
- [Stations du métro de Rennes (Wikipedia)](https://en.wikipedia.com/wiki/Rennes_Metro)
- [Adaptations BreizhGo liées au métro b (Région Bretagne)](https://www.bretagne.bzh/app/uploads/322-CP-Adaptations-BreizhGo-Ligne-B-Metro-Rennes-Janvier.pdf)
- [Horaires SNCF — GTFS et temps réel](https://www.data.gouv.fr/datasets/horaires-sncf)
- [Halte de Cesson-Sévigné (TER Bretagne)](https://www.ter.sncf.com/bretagne/se-deplacer/gares/cesson-sevigne-87334490)
- [Ligne Rennes – Vitré – Laval – Le Mans (TER Bretagne)](https://www.ter.sncf.com/bretagne/se-deplacer/lignes-ter-breizhgo/rennes-laval-le-mans)
- [Hub'Eau Hydrométrie](https://www.data.gouv.fr/dataservices/hubeau-hydrometrie/reuses)
- [Station de Cesson-Sévigné sur la Vilaine (RiverApp)](https://www.riverapp.net/station/52a0ca24e4b03f58295e779a)
- [Tronçons Vigicrues avec niveau de vigilance](https://www.data.gouv.fr/datasets/troncons-de-cours-deau-vigicrues-simplifies-avec-niveau-de-vigilance-crues)
- [Repère de crue de 1966, Pont Briand](https://www.reperesdecrues.developpement-durable.gouv.fr/export/754750/repere)
- [Indice ATMO par commune](https://www.data.gouv.fr/datasets/6149925a2ff0ab6cebdd6fe8)
- [FAQ de l'API Atmo Data](https://atmo-France.org/sites/federation/files/medias/documents/2023-10/FAQ_API_Atmo_Data_20231010_0.pdf)
- [Prix des carburants — flux instantané v2](https://data.economie.gouv.fr/explore/dataset/prix-des-carburants-en-france-flux-instantane-v2/table/?flg=fr-fr)
- [Déchèteries de Rennes Métropole](https://www.data.gouv.fr/datasets/decheteries-et-plateformes-vegetaux-sur-rennes-metropole-1)
- [Golf municipal — horaires 2025](https://ville-cesson-sevigne.fr/wp-content/uploads/2024/12/golftarifs2025.pdf)
- [Golf municipal — règlement intérieur](https://www.ville-cesson-sevigne.fr/wp-content/uploads/2020/12/reglementinterieur2021.pdf)
- [Base Sports Nature — bulletin 2024-2025](https://ville-cesson-sevigne.fr/wp-content/uploads/2024/06/bulletinadulte20242025.pdf)
- [Billetterie de la ville](https://billetterie.ville-cesson-sevigne.fr/)
- [Site de la ville de Cesson-Sévigné](https://www.ville-cesson-sevigne.fr/)
