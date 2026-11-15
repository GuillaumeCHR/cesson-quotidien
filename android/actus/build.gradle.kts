// Domaine actus : actualités de la ville et d'Eau du Bassin Rennais (titres, extraits, liens).
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.yogistudio.cesson.actus"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig { minSdk = libs.versions.minSdk.get().toInt() }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}

dependencies {
    implementation(project(":core"))
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit4)
}
