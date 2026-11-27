package com.yogistudio.cesson.rappels

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yogistudio.cesson.CessonApp
import kotlinx.coroutines.launch

/** Notifications des spectacles favoris de la saison culturelle : les éteindre garde les étoiles. */
@Composable
fun ReglageRappelsSpectacles(app: CessonApp) {
    val context = LocalContext.current
    val portee = rememberCoroutineScope()
    val actif by app.preferences.rappelsSpectaclesActifs.collectAsStateWithLifecycle(true)
    InterrupteurNotification(
        "Me prévenir pour mes spectacles favoris",
        "La veille à 18h, et si la Ville annule ou reporte un spectacle étoilé sur la carte Saison culturelle. " +
            "Éteint, les étoiles restent.",
        actif,
    ) { valeur ->
        portee.launch {
            app.preferences.activerRappelsSpectacles(valeur)
            RappelsSpectacles.planifier(context)
            if (valeur) RappelsSpectacles.verifier(context)
        }
    }
}

/** Notifications des matchs de handball favoris à la Glaz Arena : les éteindre garde les étoiles. */
@Composable
fun ReglageRappelsMatchs(app: CessonApp) {
    val context = LocalContext.current
    val portee = rememberCoroutineScope()
    val actif by app.preferences.rappelsMatchsActifs.collectAsStateWithLifecycle(true)
    InterrupteurNotification(
        "Me prévenir pour mes matchs favoris",
        "La veille à 18h, pour un match étoilé sur la carte Handball à la Glaz Arena. Éteint, les étoiles restent.",
        actif,
    ) { valeur ->
        portee.launch {
            app.preferences.activerRappelsMatchs(valeur)
            RappelsSpectacles.planifier(context)
            if (valeur) RappelsSpectacles.verifier(context)
        }
    }
}
