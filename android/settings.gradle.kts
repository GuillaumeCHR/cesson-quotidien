pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "CessonAuQuotidien"
include(":app")
// Socle : modèle commun, manifest, cache hors connexion, thème, composants partagés.
include(":core")
// Domaine eau : niveau de la Vilaine et vigilance crues.
include(":eau")
// Domaine environnement : météo (puis qualité de l'air et vigilance météo à l'étape 2).
include(":environnement")
// Domaine pratique : carburants (puis déchets, défibrillateurs, accessibilité, bornes).
include(":pratique")
// Domaine transport : prochains départs STAR à Cesson-ViaSilva, lignes de Cesson (puis trains).
include(":transport")
// Domaine équipements : horaires calculés par le backend (piscine, médiathèque, golf, Base Sports Nature).
include(":equipements")
// Domaine actus : actualités de la ville et d'Eau du Bassin Rennais.
include(":actus")
// Domaine familles : vacances scolaires et portail familles (puis menus des écoles).
include(":familles")
// Les autres domaines (agenda, widget)
// arrivent avec leur étape de la feuille de route.
