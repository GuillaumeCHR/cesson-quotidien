package com.yogistudio.cesson.actus

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.TheaterComedy
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.yogistudio.cesson.core.donnees.EtatModule
import com.yogistudio.cesson.core.ui.CarteDonnee
import com.yogistudio.cesson.core.ui.LocalCompact
import com.yogistudio.cesson.core.ui.FuseauCesson
import com.yogistudio.cesson.core.ui.rememberMaintenant
import java.time.format.DateTimeFormatter
import java.util.Locale

const val URL_BILLETTERIE = "https://billetterie.ville-cesson-sevigne.fr/"
const val URL_SAISON = "https://www.ville-cesson-sevigne.fr/saison-culturelle/"
private const val SPECTACLES_AFFICHES = 3
private val JOUR_SPECTACLE = DateTimeFormatter.ofPattern("EEE d MMM", Locale.FRENCH)

/**
 * Billetterie de la saison culturelle (Carré Sévigné, Pont des Arts) : les prochains spectacles, tirés de l'agenda,
 * et l'accès à la billetterie en ligne de la ville.
 */
@Composable
fun CarteBilletterie(etat: EtatModule<DonneesAgenda>, modifier: Modifier = Modifier) {
    val maintenant = rememberMaintenant()
    val uri = LocalUriHandler.current
    fun ouvrir(lien: String) { runCatching { uri.openUri(lien) } }
    val aujourdhui = maintenant.atZone(FuseauCesson).toLocalDate()
    val prochains = (etat as? EtatModule.Disponible)?.fichier?.donnees?.let { d ->
        agendaParJour(d, "carre_sevigne", aujourdhui).flatMap { (jour, evts) -> evts.map { jour to it } }.take(SPECTACLES_AFFICHES)
    }.orEmpty()
    CarteDonnee("Billetterie spectacles", Icons.Rounded.TheaterComedy, modifier, maintenant = maintenant) {
        // Compact : le bouton et les deux prochains spectacles.
        val compact = LocalCompact.current
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!compact) Text("Saison culturelle du Carré Sévigné et du Pont des Arts : places en vente en ligne.",
                style = MaterialTheme.typography.bodyMedium)
            Button(onClick = { ouvrir(URL_BILLETTERIE) }) {
                Text("Réserver des places")
                Spacer(Modifier.width(6.dp))
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
            }
            if (prochains.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text("Prochains spectacles", style = MaterialTheme.typography.titleSmall)
                (if (compact) prochains.take(2) else prochains).forEach { (jour, e) ->
                    Row(
                        Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "Voir le spectacle") { ouvrir(e.lien) }
                            .padding(vertical = 4.dp),
                    ) {
                        Text(JOUR_SPECTACLE.format(jour).replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(84.dp).padding(top = 2.dp))
                        Column(Modifier.weight(1f)) {
                            Text(e.titre, style = MaterialTheme.typography.bodyMedium)
                            if (!compact) listOfNotNull(e.horaires, e.lieu).takeIf { it.isNotEmpty() }?.let {
                                Text(it.joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            if (!compact) TextButton(onClick = { ouvrir(URL_SAISON) }) { Text("Toute la saison culturelle") }
        }
    }
}
