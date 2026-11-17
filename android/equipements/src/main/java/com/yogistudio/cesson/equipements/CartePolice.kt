package com.yogistudio.cesson.equipements

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.LocalPolice
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yogistudio.cesson.core.donnees.EtatModule
import com.yogistudio.cesson.core.ui.CarteDonnee
import com.yogistudio.cesson.core.ui.LocalCompact
import com.yogistudio.cesson.core.ui.Pastille
import com.yogistudio.cesson.core.ui.Ton
import com.yogistudio.cesson.core.ui.rememberMaintenant

const val POLICE_MUNICIPALE = "equipement:police_municipale"
const val URL_PAGE_POLICE = "https://www.ville-cesson-sevigne.fr/contacts/lhotel-des-polices-police-municipale-et-police-nationale/"
const val URL_TRANQUILLITE_VACANCES = "https://www.ville-cesson-sevigne.fr/operation-tranquilite-vacances/"

/** Services de la ville qui ont leur propre carte : ils ne s'affichent pas dans la liste des équipements. */
val SERVICES_A_PART = setOf(SERVICE_URBANISME, POLICE_MUNICIPALE, DECHETERIE)

/**
 * Police municipale : l'urgence d'abord (17, 112), puis l'accueil à l'Hôtel des Polices, ouvert ou fermé
 * maintenant d'après les horaires saisis, les numéros et la surveillance pendant les vacances.
 * Coordonnées de la ville ; sa page fait foi.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CartePolice(etat: EtatModule<DonneesEquipements>, modifier: Modifier = Modifier) {
    val maintenant = rememberMaintenant()
    val uri = LocalUriHandler.current
    fun ouvrir(lien: String) { runCatching { uri.openUri(lien) } }
    val police = (etat as? EtatModule.Disponible)?.fichier?.donnees?.equipements?.firstOrNull { it.lieu.id == POLICE_MUNICIPALE }
    CarteDonnee("Police municipale", Icons.Rounded.LocalPolice, modifier, maintenant = maintenant) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("En cas d'urgence, appelez le 17 (police) ou le 112.",
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { ouvrir("tel:17") }) { IconeAppel(); Text("17") }
                OutlinedButton(onClick = { ouvrir("tel:112") }) { IconeAppel(); Text("112") }
            }
            // Compact : l'urgence, l'accueil ouvert ou fermé et le numéro direct.
            val compact = LocalCompact.current
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            if (!compact) Text("Hôtel des Polices", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
            if (!compact) Text("6 mail de Bourgchevreuil. Police municipale et police nationale au même endroit.",
                style = MaterialTheme.typography.bodySmall)
            police?.espaces?.firstOrNull()?.let { accueil ->
                val (pastille, precision) = etiquetteOuverture(ouverture(accueil, maintenant), maintenant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Pastille(pastille, when (pastille) { "Ouvert" -> Ton.OUVERT; "Fermé" -> Ton.FERME; else -> Ton.NEUTRE })
                    precision?.let { Spacer(Modifier.width(8.dp)); Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
            }
            if (!compact) Text("Du lundi au vendredi de 9h à 18h ; le samedi de 7h à 12h, police municipale seule.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow {
                TextButton(onClick = { ouvrir("tel:0299835214") }) { IconeAppel(); Text("02 99 83 52 14") }
                if (!compact) TextButton(onClick = { ouvrir("tel:0609357392") }) { IconeAppel(); Text("06 09 35 73 92") }
                if (!compact) TextButton(onClick = { ouvrir("mailto:police.municipale@ville-cesson-sevigne.fr") }) {
                    Icon(Icons.Rounded.Mail, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Courriel")
                }
            }
            if (compact) return@Column
            Text("Police nationale : 02 57 87 10 05", style = MaterialTheme.typography.bodySmall)
            FlowRow {
                TextButton(onClick = { ouvrir(URL_TRANQUILLITE_VACANCES) }) { Text("Surveillance pendant vos vacances") }
                TextButton(onClick = { ouvrir(URL_PAGE_POLICE) }) { Text("Page de la ville") }
            }
        }
    }
}

@Composable
private fun IconeAppel() {
    Icon(Icons.Rounded.Call, contentDescription = null, modifier = Modifier.size(16.dp))
    Spacer(Modifier.width(4.dp))
}
