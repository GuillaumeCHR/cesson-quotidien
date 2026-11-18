package com.yogistudio.cesson.rappels

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yogistudio.cesson.CessonApp
import com.yogistudio.cesson.core.donnees.EtatModule
import com.yogistudio.cesson.transport.JOURS_OUVRES
import com.yogistudio.cesson.transport.TrainFavori
import com.yogistudio.cesson.transport.heureLisible
import com.yogistudio.cesson.transport.libelleJours
import com.yogistudio.cesson.transport.lireTrainsFavoris
import com.yogistudio.cesson.transport.trains
import kotlinx.coroutines.launch

private val INITIALES = listOf("L", "M", "M", "J", "V", "S", "D")
private val NOMS_JOURS = listOf("lundi", "mardi", "mercredi", "jeudi", "vendredi", "samedi", "dimanche")

/**
 * « Mes trains » : trains pris régulièrement à la halte, choisis parmi les horaires de la semaine. L'app prévient
 * seulement s'ils sont supprimés ou en retard de 5 min ou plus (voir AlertesTrains).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReglageTrainsFavoris(app: CessonApp) {
    val context = LocalContext.current
    val portee = rememberCoroutineScope()
    val json by app.preferences.trainsFavoris.collectAsStateWithLifecycle(null)
    val favoris = lireTrainsFavoris(json)
    val etat by remember(app) { app.depot.trains() }.collectAsStateWithLifecycle(EtatModule.Absent)
    val donnees = (etat as? EtatModule.Disponible)?.fichier?.donnees
    var edition by remember { mutableStateOf<TrainFavori?>(null) } // favori en cours de modification
    var ajout by rememberSaveable { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    fun enregistrer(liste: List<TrainFavori>) = portee.launch {
        if (!RappelsCollecte.notificationsAutorisees(context) && Build.VERSION.SDK_INT >= 33) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        AlertesTrains.enregistrer(context, liste)
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "Le train que vous prenez souvent : l'app vous prévient s'il est supprimé ou en retard de 5 min ou plus, " +
                "dans l'heure qui précède. Rien si tout va bien. Calculé sur le téléphone.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        favoris.forEach { f ->
            if (edition == f) {
                EditeurTrain(donnees?.directions?.map { it.id to it.libelle }.orEmpty(), donnees?.horaires.orEmpty(), f,
                    onAnnuler = { edition = null }) { modifie ->
                    edition = null
                    enregistrer(favoris.map { if (it == f) modifie else it })
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("${heureLisible(f.heure)} vers ${f.destination}", style = MaterialTheme.typography.bodyMedium)
                        Text(libelleJours(f.jours).replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { edition = f; ajout = false }) { Text("Jours") }
                    IconButton(onClick = { enregistrer(favoris - f) }) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Retirer le train de ${heureLisible(f.heure)}")
                    }
                }
            }
        }
        when {
            ajout -> EditeurTrain(donnees?.directions?.map { it.id to it.libelle }.orEmpty(), donnees?.horaires.orEmpty(), null,
                onAnnuler = { ajout = false }) { nouveau ->
                ajout = false
                enregistrer(favoris + nouveau)
            }
            donnees == null || donnees.horaires.isEmpty() ->
                Text("Horaires des trains pas encore reçus : tirez vers le bas pour actualiser.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> OutlinedButton(onClick = { ajout = true; edition = null }) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Text("Ajouter un train")
            }
        }
    }
}

/** Direction, heure (parmi les départs de la semaine) et jours ; un favori existant ne change que ses jours. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditeurTrain(
    directions: List<Pair<String, String>>,
    horaires: List<com.yogistudio.cesson.transport.HoraireTrain>,
    existant: TrainFavori?,
    onAnnuler: () -> Unit,
    onValider: (TrainFavori) -> Unit,
) {
    var direction by rememberSaveable { mutableStateOf(existant?.direction ?: directions.firstOrNull()?.first) }
    var heure by rememberSaveable { mutableStateOf(existant?.heure) }
    var jours by rememberSaveable { mutableStateOf(existant?.jours ?: JOURS_OUVRES) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (existant == null) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                directions.forEach { (id, libelle) ->
                    FilterChip(selected = id == direction, onClick = { direction = id; heure = null }, label = { Text(libelle) })
                }
            }
            Text("Heure de départ à Cesson", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                horaires.filter { it.direction == direction }.forEach { h ->
                    FilterChip(selected = h.heure == heure, onClick = { heure = h.heure }, label = { Text(heureLisible(h.heure)) })
                }
            }
        } else {
            Text("${heureLisible(existant.heure)} vers ${existant.destination}", style = MaterialTheme.typography.bodyMedium)
        }
        Text("Les jours où vous le prenez", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            (1..7).forEach { j ->
                FilterChip(
                    selected = j in jours,
                    onClick = { jours = if (j in jours) jours - j else (jours + j).sorted() },
                    label = { Text(INITIALES[j - 1]) },
                    modifier = Modifier.semantics { contentDescription = NOMS_JOURS[j - 1] },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val choisi = existant ?: horaires.firstOrNull { it.heure == heure && it.direction == direction }
                ?.let { TrainFavori(it.heure, it.direction, it.destination) }
            Button(enabled = choisi != null && jours.isNotEmpty(), onClick = { choisi?.let { onValider(it.copy(jours = jours)) } }) {
                Text("Enregistrer")
            }
            TextButton(onClick = onAnnuler) { Text("Annuler") }
        }
    }
}
