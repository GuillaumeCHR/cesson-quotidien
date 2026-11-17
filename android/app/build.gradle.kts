import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Adresse des fichiers publiés par le backend. En debug, par défaut, le serveur local lancé avec
// `python -m http.server -d backend/public 8000`, relayé par `adb reverse tcp:8000 tcp:8000`.
// Pour une release : -Pcesson.donneesUrl=https://… (ou dans ~/.gradle/gradle.properties).
val donneesUrl = providers.gradleProperty("cesson.donneesUrl").orNull
// Clé d'API du portail open data STAR (data.explore.star.fr), gratuite et limitée aux données publiques : avec elle,
// les appels comptent sur le quota du compte (2 millions par jour) et non sur le quota anonyme commun à tout le
// portail (150 000 par jour, épuisé certains soirs). Jamais dans le dépôt : -Pcesson.starCle=…, ou à défaut le
// fichier ~/.config/cesson/star.env (ligne « cle_api=… »). Sans clé, l'app interroge STAR anonymement.
val starCle: String = providers.gradleProperty("cesson.starCle").orNull
    ?: File(System.getProperty("user.home"), ".config/cesson/star.env").takeIf { it.isFile }?.readLines()
        ?.firstOrNull { it.startsWith("cle_api=") }?.substringAfter("=")?.trim()?.trim('"', '\'')
    ?: ""
// Adresse de la rubrique « Vos idées / signaler une erreur ». À renseigner avant publication.
val contact = providers.gradleProperty("cesson.contact").getOrElse("contact@example.org")

android {
    namespace = "com.yogistudio.cesson"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.yogistudio.cesson"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 6
        versionName = "0.5.1"
        buildConfigField("String", "CONTACT", "\"$contact\"")
        buildConfigField("String", "STAR_CLE", "\"${starCle.filter { it.isLetterOrDigit() }}\"")
    }

    // Clé d'envoi Play Store (upload key) : jamais dans le dépôt. Ses coordonnées sont lues dans un fichier
    // local ; sans lui, la release est construite sans signature.
    val cleEnvoi = providers.environmentVariable("CESSON_UPLOAD_KEY_PROPERTIES")
        .orElse(providers.provider { "${System.getProperty("user.home")}/.config/cessonapp/upload-key.properties" })
        .map { file(it) }.get()
    if (cleEnvoi.isFile) {
        val props = Properties().apply { cleEnvoi.inputStream().use { load(it) } }
        signingConfigs.create("upload") {
            storeFile = file(props.getProperty("storeFile"))
            storePassword = props.getProperty("storePassword")
            keyAlias = props.getProperty("keyAlias")
            keyPassword = props.getProperty("keyPassword")
        }
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
            signingConfig = signingConfigs.findByName("upload")
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
    implementation(project(":familles"))
    implementation(project(":eau"))
    implementation(project(":environnement"))
    implementation(project(":equipements"))
    implementation(project(":pratique"))
    implementation(project(":transport"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.osmdroid.android)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit4)
}
