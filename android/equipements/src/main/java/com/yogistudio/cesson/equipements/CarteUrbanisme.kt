package com.yogistudio.cesson.equipements

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.HomeWork
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.yogistudio.cesson.core.donnees.EtatModule
import com.yogistudio.cesson.core.ui.CarteDonnee
import com.yogistudio.cesson.core.ui.LocalCompact
import com.yogistudio.cesson.core.ui.Pastille
import com.yogistudio.cesson.core.ui.Ton
import com.yogistudio.cesson.core.ui.rememberMaintenant

const val SERVICE_URBANISME = "equipement:service_urbanisme"
const val URL_DEPOT_URBANISME = "https://urbanisme.ville-cesson-sevigne.fr/NetADS/sve/CESSON35051"
const val URL_PAGE_URBANISME = "https://www.ville-cesson-sevigne.fr/les-autorisations-durbanisme/"

/** Les quatre autorisations, telles que la ville les présente, avec les règles propres à Cesson. */
private val AUTORISATIONS = listOf(
    "Déclaration préalable" to "Petits travaux (extension modeste, façade, abri…). À Cesson, aussi pour toute clôture.",
    "Permis de construire" to "Construction neuve ou agrandissement important.",
    "Permis d'aménager" to "Lotissement, aménagement de terrain.",
    "Permis de démolir" to "À Cesson, obligatoire pour démolir toute construction.",
)

private val A_SAVOIR = listOf(
    "L'arrêté s'affiche sur le terrain dès sa réception et pendant tous les travaux ; le délai de recours est de deux mois à partir de cet affichage.",
    "Une autorisation vaut 3 ans pour commencer les travaux, prorogeable deux fois un an ; des travaux arrêtés plus d'un an la rendent caduque.",
    "À renvoyer en mairie : la déclaration d'ouverture de chantier (permis de construire) et l'attestation d'achèvement et de conformité (DAACT).",
)

/**
 * Demandes d'urbanisme : dépôt en ligne, les quatre autorisations, ce qu'il faut savoir, et le service urbanisme
 * (ouvert ou fermé maintenant, d'après ses horaires saisis). Informations de la ville ; sa page fait foi.
 */
@Composable
fun CarteUrbanisme(etat: EtatModule<DonneesEquipements>, modifier: Modifier = Modifier) {
    val maintenant = rememberMaintenant()
    val uri = LocalUriHandler.current
    var details by rememberSaveable { mutableStateOf(false) }
    val service = (etat as? EtatModule.Disponible)?.fichier?.donnees?.equipements?.firstOrNull { it.lieu.id == SERVICE_URBANISME }
    CarteDonnee("Urbanisme", Icons.Rounded.HomeWork, modifier, maintenant = maintenant) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!LocalCompact.current) Text("Permis de construire, déclaration préalable : les demandes se déposent en ligne, sur le service de la ville.",
                style = MaterialTheme.typography.bodyMedium)
            Button(onClick = { runCatching { uri.openUri(URL_DEPOT_URBANISME) } }) {
                Text("Déposer une demande en ligne")
                Spacer(Modifier.width(6.dp))
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
            }
            // Compact : le dépôt en ligne et le service (ouvert ou fermé, téléphone) ; les autorisations en détaillé.
            val compact = LocalCompact.current
            if (!compact) (if (details) AUTORISATIONS else AUTORISATIONS.take(2)).forEach { (nom, quand) ->
                Column {
                    Text(nom, style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                    Text(quand, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (details) {
                Text("À savoir", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                A_SAVOIR.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
            }
            if (!compact) TextButton(onClick = { details = !details }) { Text(if (details) "Afficher moins" else "Les quatre autorisations et ce qu'il faut savoir") }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text("Service urbanisme", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
            if (!compact) Text("Mairie annexe, 1 place de la Chalotais. Dépôt papier possible aux heures d'ouverture.",
                style = MaterialTheme.typography.bodySmall)
            service?.espaces?.firstOrNull()?.let { accueil ->
                val (pastille, precision) = etiquetteOuverture(ouverture(accueil, maintenant), maintenant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Pastille(pastille, when (pastille) { "Ouvert" -> Ton.OUVERT; "Fermé" -> Ton.FERME; else -> Ton.NEUTRE })
                    precision?.let { Spacer(Modifier.width(8.dp)); Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
            }
            Row {
                TextButton(onClick = { runCatching { uri.openUri("tel:0299835200") } }) {
                    Icon(Icons.Rounded.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("02 99 83 52 00")
                }
                if (!compact) TextButton(onClick = { runCatching { uri.openUri(URL_PAGE_URBANISME) } }) { Text("Page de la ville") }
            }
        }
    }
}
