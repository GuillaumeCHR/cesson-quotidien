package com.yogistudio.cesson.transport

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.yogistudio.cesson.core.donnees.DepotModules
import com.yogistudio.cesson.core.donnees.EtatModule
import com.yogistudio.cesson.core.modele.Horodatage
import com.yogistudio.cesson.core.ui.TexteBientot
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

/** Miroir de DonneesTrainsRetour (backend/cesson/collecteurs/trains_retour.py). */
@Serializable
data class DonneesTrainsRetour(
    val gares: List<GareRetour> = emptyList(),
    val departs: List<DepartRetour> = emptyList(),
    @SerialName("temps_reel_disponible") val tempsReelDisponible: Boolean = false,
)

/** Gare d'Ille-et-Vilaine, d'un département voisin ou de Paris, d'où un train mène à Rennes ou à Cesson. */
@Serializable
data class GareRetour(val id: String, val nom: String, val departement: String)

/** Départ d'une gare vers Rennes ou Cesson : [arrivee] est le premier des deux où le train s'arrête. */
@Serializable
data class DepartRetour(
    val arret: String,
    val ligne: String,
    val destination: String,
    @SerialName("heure_theorique") val heureTheorique: Horodatage,
    @SerialName("heure_reelle") val heureReelle: Horodatage? = null,
    val statut: StatutTrain = StatutTrain.THEORIQUE,
    val numero: String,
    @SerialName("retard_min") val retardMin: Int? = null,
    val arrivee: String,
    @SerialName("heure_arrivee") val heureArrivee: Horodatage,
)

const val MODULE_TRAINS_RETOUR = "trains_retour"

fun DepotModules.trainsRetour(): Flow<EtatModule<DonneesTrainsRetour>> =
    suivre(MODULE_TRAINS_RETOUR, DonneesTrainsRetour.serializer())

/** Prochains départs de la gare [gare] vers Rennes ou Cesson ; un train supprimé reste affiché, barré. */
fun departsDeLaGare(d: DonneesTrainsRetour, gare: String, maintenant: Instant): List<DepartRetour> =
    d.departs.filter { it.arret == gare }
        .filter { (it.heureReelle ?: it.heureTheorique).isAfter(maintenant.minusSeconds(120)) }
        .sortedBy { it.heureTheorique }

/** Même texte que pour l'aller, la couleur ne portant jamais seule l'information. */
fun libelleStatut(t: DepartRetour): String = when (t.statut) {
    StatutTrain.A_L_HEURE -> "à l'heure"
    StatutTrain.RETARD -> "+${t.retardMin ?: 0} min"
    StatutTrain.SUPPRIME -> "supprimé"
    StatutTrain.THEORIQUE -> "horaire prévu"
}

private val DEPARTEMENTS = mapOf(
    "35" to "Ille-et-Vilaine", "44" to "Loire-Atlantique", "56" to "Morbihan", "22" to "Côtes-d'Armor",
    "53" to "Mayenne", "50" to "Manche", "75" to "Paris",
)

fun nomDepartement(code: String): String = DEPARTEMENTS[code]?.let { "$it ($code)" } ?: code

/** Recherche insensible aux accents, à la casse et aux tirets, sur le nom de la gare ou du département. */
fun filtrerGares(gares: List<GareRetour>, recherche: String): List<GareRetour> {
    val r = normaliser(recherche)
    return if (r.isEmpty()) gares else gares.filter {
        normaliser(it.nom).contains(r) || normaliser(DEPARTEMENTS[it.departement].orEmpty()).startsWith(r) || it.departement == r
    }
}

/** Choix de la gare de destination de « Mon trajet en train ». « Aucune destination » retire le retour. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChoixGare(gares: List<GareRetour>, actuelle: String?, onFermer: () -> Unit, onChoisir: (String?) -> Unit) {
    var recherche by rememberSaveable { mutableStateOf("") }
    val resultats = remember(gares, recherche) { filtrerGares(gares, recherche) }
    ModalBottomSheet(onDismissRequest = onFermer, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text("Ma gare de destination", style = MaterialTheme.typography.titleLarge)
            Text("Gares d'où un train mène à Rennes ou à Cesson : Ille-et-Vilaine, départements voisins et Paris.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = recherche,
                onValueChange = { recherche = it },
                label = { Text("Nom de la gare ou département") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )
        }
        LazyColumn(Modifier.heightIn(max = 520.dp)) {
            if (actuelle != null && recherche.isBlank()) item {
                Text("Aucune destination", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { onChoisir(null) }
                        .padding(horizontal = 16.dp, vertical = 14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            if (resultats.isEmpty()) {
                item { Column(Modifier.padding(16.dp)) { TexteBientot("Aucune gare ne correspond.") } }
            }
            items(resultats, key = { it.id }) { g ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button, onClickLabel = "Choisir ${g.nom}") { onChoisir(g.id) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Text(if (g.id == actuelle) "${g.nom} (actuelle)" else g.nom, style = MaterialTheme.typography.bodyLarge)
                    Text(nomDepartement(g.departement), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}
