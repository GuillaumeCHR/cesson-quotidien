plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Adresse des fichiers publiés par le backend. En debug, par défaut, le serveur local lancé avec
// `python -m http.server -d backend/public 8000`, relayé par `adb reverse tcp:8000 tcp:8000`.
// Pour une release : -Pcesson.donneesUrl=https://… (ou dans ~/.gradle/gradle.properties).
val donneesUrl = providers.gradleProperty("cesson.donneesUrl").orNull
// Adresse de la rubrique « Vos idées / signaler une erreur ». À renseigner avant publication.
val contact = providers.gradleProperty("cesson.contact").getOrElse("contact@example.org")

android {
    namespace = "com.yogistudio.cesson"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.yogistudio.cesson"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "CONTACT", "\"$contact\"")
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            buildConfigField("String", "DONNEES_URL", "\"${donneesUrl ?: "http://127.0.0.1:8000/"}\"")
        }
        release {
            buildConfigField("String", "DONNEES_URL", "\"${donneesUrl ?: "https://donnees.example.org/"}\"")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":actus"))
    implementation(project(":eau"))
    implementation(project(":environnement"))
    implementation(project(":equipements"))
    implementation(project(":pratique"))
    implementation(project(":transport"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit4)
}
