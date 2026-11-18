package com.yogistudio.cesson

import androidx.glance.appwidget.updateAll
import com.yogistudio.cesson.widget.WidgetCesson
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.lifecycleScope
import com.yogistudio.cesson.core.signalement.ContexteSignalement
import com.yogistudio.cesson.core.signalement.LocalSignaleur
import com.yogistudio.cesson.core.signalement.Signaleur
import com.yogistudio.cesson.core.ui.CessonTheme
import com.yogistudio.cesson.core.preferences.AFFICHAGE_CLAIR
import com.yogistudio.cesson.core.preferences.AFFICHAGE_SOMBRE
import com.yogistudio.cesson.core.preferences.AFFICHAGE_TELEPHONE
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yogistudio.cesson.rappels.RappelsCollecte
import com.yogistudio.cesson.ui.AppCesson
import com.yogistudio.cesson.ui.intro.IntroCesson
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = application as CessonApp
        lifecycleScope.launch {
            app.depot.chargerCache()
            app.depot.rafraichir()
            // Calendrier peut-être neuf : on recale le prochain rappel (sans effet s'il est désactivé).
            RappelsCollecte.planifier(app)
            // Alertes pollens et pollution : prochain contrôle recalé, et vérification avec les données fraîches.
            com.yogistudio.cesson.rappels.AlertesAir.planifier(app)
            com.yogistudio.cesson.rappels.AlertesAir.verifier(app)
            // Trains et spectacles favoris : horaires et agenda peut-être neufs.
            com.yogistudio.cesson.rappels.AlertesTrains.planifier(app)
            com.yogistudio.cesson.rappels.AlertesTrains.verifier(app)
            com.yogistudio.cesson.rappels.RappelsSpectacles.planifier(app)
            com.yogistudio.cesson.rappels.RappelsSpectacles.verifier(app)
            // Données fraîches : le widget d'écran d'accueil en profite aussitôt.
            runCatching { WidgetCesson().updateAll(app) }
        }
        setContent {
            val affichage by app.preferences.affichage.collectAsStateWithLifecycle(AFFICHAGE_TELEPHONE)
            val sombre = when (affichage) {
                AFFICHAGE_CLAIR -> false
                AFFICHAGE_SOMBRE -> true
                else -> isSystemInDarkTheme()
            }
            CessonTheme(sombre) {
                CompositionLocalProvider(LocalSignaleur provides Signaleur(::ouvrirMail)) {
                    // L'intro se joue pendant que le cache et les données se chargent (lancés plus haut) ;
                    // elle ne rejoue pas après une rotation de l'écran.
                    var introVue by rememberSaveable { mutableStateOf(false) }
                    if (introVue) AppCesson(app) else IntroCesson(onFinished = { introVue = true })
                }
            }
        }
    }

    /** Ouvre l'application mail avec un message pré-rempli. Rien ne part sans action de l'utilisateur. */
    private fun ouvrirMail(contexte: ContexteSignalement) {
        val corps = buildString {
            appendLine("Bonjour,")
            appendLine()
            appendLine("(Décrivez ici votre idée ou l'erreur constatée.)")
            appendLine()
            appendLine()
            appendLine("— Contexte, modifiable ou supprimable —")
            contexte.module?.let { appendLine("Module : $it") }
            contexte.details.forEach { appendLine(it) }
            appendLine("App : ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}), Android ${Build.VERSION.RELEASE}")
        }
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
            putExtra(Intent.EXTRA_EMAIL, arrayOf(BuildConfig.CONTACT))
            putExtra(Intent.EXTRA_SUBJECT, "Cesson'App : ${contexte.sujet}")
            putExtra(Intent.EXTRA_TEXT, corps)
        }
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "Aucune application mail. Écrivez-nous à ${BuildConfig.CONTACT}", Toast.LENGTH_LONG).show()
        }
    }
}
