package com.yogistudio.cesson

import android.app.Application
import com.yogistudio.cesson.core.donnees.DepotModules
import com.yogistudio.cesson.core.preferences.PreferencesLocales
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/** Conteneur de dépendances, sans framework : l'app est petite et le restera par domaine. */
class CessonApp : Application() {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .cache(Cache(File(cacheDir, "http"), 5L * 1024 * 1024))
            .build()
    }
    val depot: DepotModules by lazy { DepotModules(BuildConfig.DONNEES_URL, File(filesDir, "donnees"), client) }
    val preferences: PreferencesLocales by lazy { PreferencesLocales(this) }
}
