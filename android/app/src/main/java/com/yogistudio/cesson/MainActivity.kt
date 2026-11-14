package com.yogistudio.cesson

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
import com.yogistudio.cesson.ui.AppCesson
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = application as CessonApp
        lifecycleScope.launch {
            app.depot.chargerCache()
            app.depot.rafraichir()
        }
        setContent {
            CessonTheme {
                CompositionLocalProvider(LocalSignaleur provides Signaleur(::ouvrirMail)) {
                    AppCesson(app)
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
            putExtra(Intent.EXTRA_SUBJECT, "Cesson au quotidien : ${contexte.sujet}")
            putExtra(Intent.EXTRA_TEXT, corps)
        }
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "Aucune application mail. Écrivez-nous à ${BuildConfig.CONTACT}", Toast.LENGTH_LONG).show()
        }
    }
}
