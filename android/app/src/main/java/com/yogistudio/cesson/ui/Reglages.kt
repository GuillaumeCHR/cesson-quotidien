package com.yogistudio.cesson.ui

import com.yogistudio.cesson.transport.ChoixArret
import com.yogistudio.cesson.transport.lignesStar
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.yogistudio.cesson.core.donnees.EtatModule
import com.yogistudio.cesson.pratique.CARBURANT_PAR_DEFAUT
import com.yogistudio.cesson.pratique.carburants
import com.yogistudio.cesson.pratique.carburantsProposes
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import com.yogistudio.cesson.rappels.ReglageRappelCollecte
import com.yogistudio.cesson.rappels.AlertesBus
import com.yogistudio.cesson.transport.AlerteBus
import com.yogistudio.cesson.transport.DELAI_ALERTE_DEFAUT
import com.yogistudio.cesson.transport.DELAIS_ALERTE
import com.yogistudio.cesson.core.modele.JsonCesson
import com.yogistudio.cesson.core.ui.formaterHeure
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import java.time.Instant
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
import androidx.compose.material.icons.rounded.ViewAgenda
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
        val detaille by app.preferences.cartesDetaillees.collectAsStateWithLifecycle(false)
        CarteDonnee("Cartes", Icons.Rounded.ViewAgenda) {
            Column(Modifier.selectableGroup()) {
                listOf(
                    Triple(false, "Compactes", "L'essentiel d'un coup d'œil : moins de détails, moins de défilement"),
                    Triple(true, "Détaillées", "Toutes les informations, sur tous les écrans"),
                ).forEach { (valeur, libelle, aide) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                            .selectable(selected = detaille == valeur, role = Role.RadioButton) {
                                portee.launch { app.preferences.enregistrerCartesDetaillees(valeur) }
                            }
                            .padding(vertical = 6.dp),
                    ) {
                        RadioButton(selected = detaille == valeur, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(libelle, style = MaterialTheme.typography.bodyLarge)
                            Text(aide, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        CarteNotifications(app)
        CarteMonArretReglage(app)
        CarteMonCarburant(app)
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

/**
 * Les deux notifications de l'app, calculées sur le téléphone : rappel de collecte la veille, et alerte de passage
 * à mon arrêt (délai de prévenance ; l'alerte elle-même s'arme d'une cloche sur la carte « Mon arrêt »).
 */
@Composable
private fun CarteNotifications(app: CessonApp) {
    val context = LocalContext.current
    val portee = rememberCoroutineScope()
    val arret by app.preferences.arretSuivi.collectAsStateWithLifecycle(null)
    val delai by app.preferences.delaiAlerteBus.collectAsStateWithLifecycle(DELAI_ALERTE_DEFAUT)
    val alerteJson by app.preferences.alerteBus.collectAsStateWithLifecycle(null)
    val alerte = remember(alerteJson) {
        alerteJson?.let { runCatching { JsonCesson.decodeFromString(AlerteBus.serializer(), it) }.getOrNull() }
    }
    CarteDonnee("Notifications", Icons.Rounded.NotificationsActive) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Sortir la poubelle", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
            ReglageRappelCollecte(app)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text("Passage du bus à mon arrêt", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
            Text(
                if (arret == null) "Choisissez d'abord votre arrêt sur la carte « Mon arrêt de bus », puis touchez la cloche " +
                    "d'une ligne pour être prévenu avant son passage."
                else "Sur la carte « Mon arrêt de bus » ($arret), touchez la cloche d'une ligne : une notification arrive " +
                    "avant le passage du prochain bus.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Me prévenir", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { portee.launch { app.preferences.enregistrerDelaiAlerteBus((delai - 1).coerceIn(DELAIS_ALERTE)) } },
                    enabled = delai > DELAIS_ALERTE.first) { Icon(Icons.Rounded.Remove, contentDescription = "Prévenir moins tôt") }
                Text("$delai min avant", style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.semantics { contentDescription = "Prévenir $delai minutes avant le passage" })
                IconButton(onClick = { portee.launch { app.preferences.enregistrerDelaiAlerteBus((delai + 1).coerceIn(DELAIS_ALERTE)) } },
                    enabled = delai < DELAIS_ALERTE.last) { Icon(Icons.Rounded.Add, contentDescription = "Prévenir plus tôt") }
            }
            alerte?.let { a ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Alerte en cours : ${a.ligne} vers ${a.destination}, ${a.delaiMin} min avant " +
                        formaterHeure(Instant.ofEpochSecond(a.heureVisee), Instant.now()) + ".",
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = { portee.launch { AlertesBus.annuler(context) } }) { Text("Annuler") }
                }
            }
        }
    }
}

/** Carburant affiché par défaut sur la carte des prix (le choisir sur la carte le change aussi). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CarteMonCarburant(app: CessonApp) {
    val portee = rememberCoroutineScope()
    val carburants by remember(app) { app.depot.carburants() }.collectAsStateWithLifecycle(EtatModule.Absent)
    val proposes = (carburants as? EtatModule.Disponible)?.fichier?.donnees?.let(::carburantsProposes).orEmpty()
    val suivi by app.preferences.carburantSuivi.collectAsStateWithLifecycle(null)
    CarteDonnee("Mon carburant", Icons.Rounded.LocalGasStation) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Carburant affiché par défaut sur la carte des prix.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (proposes.isEmpty()) Text("Liste des carburants pas encore reçue.", style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                proposes.forEach { (code, nom) ->
                    FilterChip(selected = code == (suivi ?: CARBURANT_PAR_DEFAUT),
                        onClick = { portee.launch { app.preferences.enregistrerCarburant(code) } }, label = { Text(nom) })
                }
            }
        }
    }
}

/** Mon arrêt de bus et ma ligne préférée : ceux de la carte « Mon arrêt de bus » et de ses alertes de passage. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CarteMonArretReglage(app: CessonApp) {
    val context = LocalContext.current
    val portee = rememberCoroutineScope()
    val lignes by remember(app) { app.depot.lignesStar() }.collectAsStateWithLifecycle(EtatModule.Absent)
    val donnees = (lignes as? EtatModule.Disponible)?.fichier?.donnees
    val arret by app.preferences.arretSuivi.collectAsStateWithLifecycle(null)
    val alerteJson by app.preferences.alerteBus.collectAsStateWithLifecycle(null)
    val lignePreferee by app.preferences.lignePreferee.collectAsStateWithLifecycle(null)
    var choix by remember { mutableStateOf(false) }
    CarteDonnee("Mon arrêt de bus", Icons.Rounded.DirectionsBus) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(arret ?: "Aucun arrêt choisi", style = MaterialTheme.typography.bodyLarge)
            Text("Ses prochains passages s'affichent sur la carte « Mon arrêt de bus », avec les alertes de passage.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val desservies = donnees?.arrets?.firstOrNull { it.nom == arret }?.lignes.orEmpty()
            if (desservies.isNotEmpty()) {
                Text("Ma ligne préférée", style = MaterialTheme.typography.titleSmall)
                Text("Affichée en premier sur la carte, avant les autres lignes.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = lignePreferee == null || lignePreferee !in desservies,
                        onClick = { portee.launch { app.preferences.enregistrerLignePreferee(null) } }, label = { Text("Aucune") })
                    desservies.forEach { l ->
                        FilterChip(selected = l == lignePreferee,
                            onClick = { portee.launch { app.preferences.enregistrerLignePreferee(l) } }, label = { Text(l) })
                    }
                }
            }
            OutlinedButton(onClick = { choix = true }, enabled = donnees != null) {
                Text(when {
                    donnees == null -> "Liste des arrêts en cours de chargement…"
                    arret == null -> "Choisir mon arrêt"
                    else -> "Changer d'arrêt"
                })
            }
        }
    }
    if (choix && donnees != null) {
        ChoixArret(donnees.arrets, donnees, actuel = arret, onFermer = { choix = false }) { nouveau ->
            portee.launch {
                app.preferences.enregistrerArret(nouveau)
                // Une alerte armée sur l'ancien arrêt n'a plus de sens.
                val alerte = alerteJson?.let { runCatching { JsonCesson.decodeFromString(AlerteBus.serializer(), it) }.getOrNull() }
                if (alerte != null && alerte.arret != nouveau) AlertesBus.annuler(context)
            }
            choix = false
        }
    }
}
