package com.yogistudio.cesson.rappels

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yogistudio.cesson.CessonApp
import kotlinx.coroutines.launch

/** Interrupteur « Me le rappeler la veille à 19h », avec la demande de permission d'Android 13+. */
@Composable
fun ReglageRappelCollecte(app: CessonApp) {
    val context = LocalContext.current
    val portee = rememberCoroutineScope()
    val actif by app.preferences.rappelCollecteActif.collectAsStateWithLifecycle(false)
    InterrupteurNotification("Me le rappeler la veille à 19h", "Notification calculée sur le téléphone, sans serveur.", actif) { valeur ->
        portee.launch {
            app.preferences.activerRappelCollecte(valeur)
            RappelsCollecte.planifier(context)
        }
    }
}

/**
 * Interrupteur d'une notification calculée sur le téléphone. L'activer demande la permission de notification
 * (Android 13+) si besoin ; si elle est refusée ou retirée, un lien mène aux réglages d'Android.
 */
@Composable
fun InterrupteurNotification(titre: String, detail: String, actif: Boolean, onChanger: (Boolean) -> Unit) {
    val context = LocalContext.current
    var refusee by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { accordee ->
        refusee = !accordee
        if (accordee) onChanger(true)
    }

    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().toggleable(value = actif, role = Role.Switch) { voulu ->
                when {
                    !voulu -> onChanger(false)
                    RappelsCollecte.notificationsAutorisees(context) -> onChanger(true)
                    Build.VERSION.SDK_INT >= 33 -> permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
        ) {
            Column(Modifier.weight(1f)) {
                Text(titre, style = MaterialTheme.typography.bodyMedium)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = actif, onCheckedChange = null)
        }
        if (refusee || (actif && !RappelsCollecte.notificationsAutorisees(context))) {
            Text("Les notifications de l'app sont désactivées dans Android.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = {
                context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            }) { Text("Ouvrir les réglages des notifications") }
        }
    }
}
