package com.yogistudio.cesson.ui

import android.app.UiModeManager
import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yogistudio.cesson.CessonApp
import com.yogistudio.cesson.core.preferences.AFFICHAGE_CLAIR
import com.yogistudio.cesson.core.preferences.AFFICHAGE_SOMBRE
import com.yogistudio.cesson.core.preferences.AFFICHAGE_TELEPHONE
import com.yogistudio.cesson.core.ui.CarteDonnee
import kotlinx.coroutines.launch

private val CHOIX_AFFICHAGE = listOf(
    Triple(AFFICHAGE_TELEPHONE, "Comme le téléphone", "Clair ou sombre selon le réglage d'Android"),
    Triple(AFFICHAGE_CLAIR, "Clair", null),
    Triple(AFFICHAGE_SOMBRE, "Sombre", null),
)

/** Réglages de l'app. Tout reste sur le téléphone. */
@Composable
fun EcranReglages(app: CessonApp) {
    val context = LocalContext.current
    val portee = rememberCoroutineScope()
    val mode by app.preferences.affichage.collectAsStateWithLifecycle(AFFICHAGE_TELEPHONE)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CarteDonnee("Affichage", Icons.Rounded.Contrast) {
            Column(Modifier.selectableGroup()) {
                CHOIX_AFFICHAGE.forEach { (id, libelle, aide) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                            .selectable(selected = mode == id, role = Role.RadioButton) {
                                portee.launch {
                                    app.preferences.enregistrerAffichage(id)
                                    appliquerAffichageSysteme(context, id)
                                }
                            }
                            .padding(vertical = 6.dp),
                    ) {
                        RadioButton(selected = mode == id, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(libelle, style = MaterialTheme.typography.bodyLarge)
                            aide?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Android 12+ : le choix est aussi confié au système, pour que l'écran de démarrage et les fenêtres de l'app
 * (avant même que Compose ne dessine) suivent le même thème. Avant Android 12, seul l'affichage de l'app suit.
 */
fun appliquerAffichageSysteme(context: Context, mode: String) {
    if (Build.VERSION.SDK_INT < 31) return
    val uiMode = context.getSystemService(UiModeManager::class.java) ?: return
    uiMode.setApplicationNightMode(
        when (mode) {
            AFFICHAGE_CLAIR -> UiModeManager.MODE_NIGHT_NO
            AFFICHAGE_SOMBRE -> UiModeManager.MODE_NIGHT_YES
            else -> UiModeManager.MODE_NIGHT_AUTO
        },
    )
}
