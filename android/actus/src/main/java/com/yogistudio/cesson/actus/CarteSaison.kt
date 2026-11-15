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
import com.yogistudio.cesson.core.ui.Pastille
import com.yogistudio.cesson.core.ui.TexteBientot
import com.yogistudio.cesson.core.ui.Ton
import com.yogistudio.cesson.core.ui.LocalCompact
import com.yogistudio.cesson.core.ui.FuseauCesson
import com.yogistudio.cesson.core.ui.rememberMaintenant
import java.time.format.DateTimeFormatter
import java.util.Locale

const val URL_BILLETTERIE = "https://billetterie.ville-cesson-sevigne.fr/"
const val URL_SAISON = "https://www.ville-cesson-sevigne.fr/saison-culturelle/"
private const val SPECTACLES_COMPACT = 3
private const val SPECTACLES_DETAILLE = 6
private val JOUR_SPECTACLE = DateTimeFormatter.ofPattern("EEE d MMM", Locale.FRENCH)

/**
 * Saison culturelle (Carré Sévigné, Pont des Arts) : les spectacles payants ou sur réservation, avec leur genre et
 * leur salle, et l'accès à la billetterie en ligne de la ville. Le reste des événements est dans l'Agenda.
 */
@Composable
fun CarteSaison(etat: EtatModule<DonneesAgenda>, modifier: Modifier = Modifier) {
    val maintenant = rememberMaintenant()
    val uri = LocalUriHandler.current
    fun ouvrir(lien: String) { runCatching { uri.openUri(lien) } }
    val aujourdhui = maintenant.atZone(FuseauCesson).toLocalDate()
    val prochains = (etat as? EtatModule.Disponible)?.fichier?.donnees?.let { d ->
        saisonParJour(d, aujourdhui).flatMap { (jour, evts) -> evts.map { jour to it } }
    }.orEmpty()
    CarteDonnee("Saison culturelle", Icons.Rounded.TheaterComedy, modifier, (etat as? EtatModule.Disponible)?.fraicheur, maintenant) {
        // Compact : les 3 prochains spectacles et le bouton.
        val compact = LocalCompact.current
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!compact) Text("Les spectacles du Carré Sévigné et du Pont des Arts, places en vente en ligne.",
                style = MaterialTheme.typography.bodyMedium)
            Button(onClick = { ouvrir(URL_BILLETTERIE) }) {
                Text("Réserver des places")
                Spacer(Modifier.width(6.dp))
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
            }
            if (prochains.isEmpty()) TexteBientot("Pas de spectacle annoncé pour l'instant.")
            if (prochains.isNotEmpty()) {
                (if (compact) prochains.take(SPECTACLES_COMPACT) else prochains.take(SPECTACLES_DETAILLE)).forEach { (jour, e) ->
                    Row(
                        Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "Voir le spectacle") { ouvrir(e.lien) }
                            .padding(vertical = 4.dp),
                    ) {
                        Text(JOUR_SPECTACLE.format(jour).replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(84.dp).padding(top = 2.dp))
                        Column(Modifier.weight(1f)) {
                            Text(titreSpectacle(e.titre), style = MaterialTheme.typography.titleSmall)
                            e.categorie?.let { Pastille(it, Ton.INFO) }
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

/** Les titres d'OpenAgenda sont parfois en capitales (« LA FOLLE JOURNÉE… ») : remis en casse de phrase. */
fun titreSpectacle(t: String): String {
    val lettres = t.filter { it.isLetter() }
    return if (lettres.isNotEmpty() && lettres.count { it.isUpperCase() } > lettres.length * 0.8)
        t.lowercase().replaceFirstChar { it.uppercase() } else t
}
