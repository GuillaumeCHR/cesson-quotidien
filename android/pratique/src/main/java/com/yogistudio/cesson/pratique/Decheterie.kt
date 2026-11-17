package com.yogistudio.cesson.pratique

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.yogistudio.cesson.core.donnees.DepotModules
import com.yogistudio.cesson.core.donnees.EtatModule
import com.yogistudio.cesson.core.ui.Pastille
import com.yogistudio.cesson.core.ui.Ton
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Miroir de DonneesDecheterie (backend/cesson/collecteurs/decheterie.py). */
@Serializable
data class DonneesDecheterie(
    val nom: String = "Déchetterie",
    val acceptes: List<String> = emptyList(),
    val refuses: List<String> = emptyList(),
    val reemploi: Boolean = false,
    @SerialName("fermeture_exceptionnelle") val fermetureExceptionnelle: String? = null,
)

const val MODULE_DECHETERIE = "decheterie"

fun DepotModules.decheterie(): Flow<EtatModule<DonneesDecheterie>> = suivre(MODULE_DECHETERIE, DonneesDecheterie.serializer())

/** Sous les horaires de la déchetterie : ce qu'on peut y apporter, et ce qui va ailleurs. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InfosDecheterie(d: DonneesDecheterie) {
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        d.fermetureExceptionnelle?.let { Pastille("Fermeture exceptionnelle : $it", Ton.FERME) }
        Text("Déchets acceptés", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        FlowRow(
            Modifier.semantics(mergeDescendants = true) { contentDescription = "Déchets acceptés : " + d.acceptes.joinToString(", ") },
            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            d.acceptes.forEach { Pastille(it, Ton.NEUTRE) }
        }
        if (d.refuses.isNotEmpty()) {
            Text("Non acceptés : ${d.refuses.joinToString(", ")}.", style = MaterialTheme.typography.bodyMedium)
        }
        if (d.reemploi) {
            Text("Espace réemploi : déposez-y les objets encore en bon état, ils auront une seconde vie.",
                style = MaterialTheme.typography.bodyMedium)
        }
        Text("Source : Rennes Métropole (ODbL).", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
