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
// Domaine eau : niveau de la Vilaine (puis vigilance crues à l'étape 2).
include(":eau")
// Les autres domaines (transport, equipements, agenda, environnement, pratique, actus, widget)
// arrivent avec leur étape de la feuille de route.
