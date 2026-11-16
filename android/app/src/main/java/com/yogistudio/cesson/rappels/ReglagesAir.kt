package com.yogistudio.cesson.rappels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yogistudio.cesson.CessonApp
import com.yogistudio.cesson.core.preferences.SEUIL_POLLENS_DEFAUT
import com.yogistudio.cesson.environnement.SEUILS_POLLENS
import com.yogistudio.cesson.environnement.libellePollen
import kotlinx.coroutines.launch

/** « Me prévenir des pollens », puis le niveau à partir duquel prévenir. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReglageAlertePollens(app: CessonApp) {
    val context = LocalContext.current
    val portee = rememberCoroutineScope()
    val actif by app.preferences.alertePollensActive.collectAsStateWithLifecycle(false)
    val seuil by app.preferences.seuilPollens.collectAsStateWithLifecycle(SEUIL_POLLENS_DEFAUT)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        InterrupteurNotification(
            "Me prévenir des pollens",
            "Aujourd'hui ou demain, dès « ${libellePollen(seuil).lowercase()} » ou en cas d'alerte. Calculé sur le téléphone.",
            actif,
        ) { valeur ->
            portee.launch {
                app.preferences.activerAlertePollens(valeur)
                AlertesAir.planifier(context)
                if (valeur) AlertesAir.verifier(context)
            }
        }
        if (actif) {
            Text("Me prévenir à partir de", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            // Trois puces ne tiennent pas toujours sur une ligne (« Très élevé » et la coche de la puce choisie) :
            // elles passent à la ligne plutôt que de s'écraser.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SEUILS_POLLENS.forEach { s ->
                    FilterChip(
                        selected = s == seuil,
                        onClick = { portee.launch { app.preferences.enregistrerSeuilPollens(s); AlertesAir.verifier(context) } },
                        label = { Text(libellePollen(s), maxLines = 1, softWrap = false) },
                    )
                }
            }
        }
    }
}

@Composable
fun ReglageAlerteEpisodes(app: CessonApp) {
    val context = LocalContext.current
    val portee = rememberCoroutineScope()
    val actif by app.preferences.alerteEpisodesActive.collectAsStateWithLifecycle(false)
    InterrupteurNotification(
        "Me prévenir d'un épisode de pollution",
        "Dès qu'une procédure est annoncée pour aujourd'hui ou demain. Calculé sur le téléphone.",
        actif,
    ) { valeur ->
        portee.launch {
            app.preferences.activerAlerteEpisodes(valeur)
            AlertesAir.planifier(context)
            if (valeur) AlertesAir.verifier(context)
        }
    }
}
