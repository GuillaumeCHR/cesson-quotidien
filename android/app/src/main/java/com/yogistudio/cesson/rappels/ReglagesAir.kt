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
import com.yogistudio.cesson.core.preferences.SEUIL_QUALITE_AIR_DEFAUT
import com.yogistudio.cesson.environnement.SEUILS_QUALITE_AIR
import com.yogistudio.cesson.environnement.libelleAtmo
import com.yogistudio.cesson.core.preferences.SEUIL_VIGILANCE_DEFAUT
import com.yogistudio.cesson.environnement.CouleurVigilance
import com.yogistudio.cesson.environnement.SEUILS_VIGILANCE
import com.yogistudio.cesson.environnement.nom
import com.yogistudio.cesson.environnement.SEUILS_POLLENS
import com.yogistudio.cesson.environnement.libellePollen
import kotlinx.coroutines.launch

/** « Me prévenir d'un air dégradé », puis l'indice ATMO à partir duquel prévenir (dégradé par défaut). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReglageAlerteQualiteAir(app: CessonApp) {
    val context = LocalContext.current
    val portee = rememberCoroutineScope()
    val actif by app.preferences.alerteQualiteAirActive.collectAsStateWithLifecycle(false)
    val seuil by app.preferences.seuilQualiteAir.collectAsStateWithLifecycle(SEUIL_QUALITE_AIR_DEFAUT)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        InterrupteurNotification(
            "Me prévenir d'une qualité de l'air dégradée",
            "Aujourd'hui ou demain, dès l'indice « ${libelleAtmo(seuil).lowercase()} ». Calculé sur le téléphone.",
            actif,
        ) { valeur ->
            portee.launch {
                app.preferences.activerAlerteQualiteAir(valeur)
                AlertesAir.planifier(context)
                if (valeur) AlertesAir.verifier(context)
            }
        }
        if (actif) {
            Text("Me prévenir à partir de", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SEUILS_QUALITE_AIR.forEach { s ->
                    FilterChip(
                        selected = s == seuil,
                        onClick = { portee.launch { app.preferences.enregistrerSeuilQualiteAir(s); AlertesAir.verifier(context) } },
                        label = { Text(libelleAtmo(s), maxLines = 1, softWrap = false) },
                    )
                }
            }
        }
    }
}

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

/** « Me prévenir d'une vigilance météo », puis la couleur à partir de laquelle prévenir (jaune par défaut). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReglageAlerteVigilance(app: CessonApp) {
    val context = LocalContext.current
    val portee = rememberCoroutineScope()
    val actif by app.preferences.alerteVigilanceActive.collectAsStateWithLifecycle(false)
    val seuil by app.preferences.seuilVigilance.collectAsStateWithLifecycle(SEUIL_VIGILANCE_DEFAUT)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        InterrupteurNotification(
            "Me prévenir d'une vigilance météo",
            "Aujourd'hui ou demain en Ille-et-Vilaine, dès la vigilance ${CouleurVigilance.entries.firstOrNull { it.name == seuil }?.nom() ?: "jaune"}. Calculé sur le téléphone.",
            actif,
        ) { valeur ->
            portee.launch {
                app.preferences.activerAlerteVigilance(valeur)
                AlertesAir.planifier(context)
                if (valeur) AlertesAir.verifier(context)
            }
        }
        if (actif) {
            Text("Me prévenir à partir de", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SEUILS_VIGILANCE.forEach { c ->
                    FilterChip(
                        selected = c.name == seuil,
                        onClick = { portee.launch { app.preferences.enregistrerSeuilVigilance(c.name); AlertesAir.verifier(context) } },
                        label = { Text(c.nom().replaceFirstChar { it.uppercase() }, maxLines = 1, softWrap = false) },
                    )
                }
            }
        }
    }
}
