package com.yogistudio.cesson.actus

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
import androidx.compose.material.icons.rounded.SportsHandball
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.yogistudio.cesson.core.donnees.EtatModule
import com.yogistudio.cesson.core.ui.CarteDonnee
import com.yogistudio.cesson.core.ui.FuseauCesson
import com.yogistudio.cesson.core.ui.LocalCompact
import com.yogistudio.cesson.core.ui.Pastille
import com.yogistudio.cesson.core.ui.TexteBientot
import com.yogistudio.cesson.core.ui.Ton
import com.yogistudio.cesson.core.ui.rememberMaintenant
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val SUIVANTS_COMPACT = 2
private const val SUIVANTS_DETAILLE = 5
private val JOUR_MATCH = DateTimeFormatter.ofPattern("EEE d MMM", Locale.FRENCH)

/**
 * Matchs à domicile du Cesson Rennes Métropole Handball, à la Glaz Arena (équipe pro : championnat et coupes). Le
 * prochain en avant avec le bouton de billetterie quand les places sont en vente, puis les suivants, et le dernier
 * résultat à domicile. Étoile : match rappelé la veille à 18h (voir RappelsSpectacles).
 */
@Composable
fun CarteHandball(
    etat: EtatModule<DonneesHandball>,
    modifier: Modifier = Modifier,
    favoris: Set<String> = emptySet(),
    basculerFavori: (String) -> Unit = {},
) {
    val maintenant = rememberMaintenant()
    val uri = LocalUriHandler.current
    fun ouvrir(lien: String) { runCatching { uri.openUri(lien) } }
    val d = (etat as? EtatModule.Disponible)?.fichier?.donnees
    val matchs = d?.let { prochainsMatchs(it, maintenant) }.orEmpty()
    val aujourdhui = maintenant.atZone(FuseauCesson).toLocalDate()
    CarteDonnee("Handball à la Glaz Arena", Icons.Rounded.SportsHandball, modifier,
        (etat as? EtatModule.Disponible)?.fraicheur, maintenant) {
        val compact = LocalCompact.current
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!compact) Text("Les matchs à domicile du Cesson Rennes Métropole Handball.",
                style = MaterialTheme.typography.bodyMedium)
            val prochain = matchs.firstOrNull()
            if (prochain == null) TexteBientot("Pas de match à domicile annoncé pour l'instant.")
            prochain?.let { m ->
                val jour = m.debut.atZone(FuseauCesson).toLocalDate()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("${libelleJour(jour, aujourdhui)} · ${heureMatch(m)}",
                            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Text("Cesson – ${m.adversaire}", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Pastille(m.tour, Ton.INFO)
                            m.competition?.takeIf { it.startsWith("Coupe") }?.let { Pastille(it, Ton.NEUTRE) }
                        }
                    }
                    Etoile(m, favoris, basculerFavori)
                }
                if (m.billetterie != null) {
                    Button(onClick = { ouvrir(m.billetterie) }) {
                        Text("Réserver des places")
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                } else if (!compact) {
                    Text("Places pas encore en vente.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val suivants = matchs.drop(1).take(if (compact) SUIVANTS_COMPACT else SUIVANTS_DETAILLE)
            if (suivants.isNotEmpty()) {
                HorizontalDivider()
                suivants.forEach { m ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(JOUR_MATCH.format(m.debut.atZone(FuseauCesson)).replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(84.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Cesson – ${m.adversaire}", style = MaterialTheme.typography.titleSmall)
                            if (!compact) Text(listOf(heureMatch(m), m.tour).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Etoile(m, favoris, basculerFavori)
                    }
                }
            }
            if (!compact) d?.dernier?.let { resultat(it) }?.let {
                Text("Dernier match à domicile : ${it.replaceFirstChar { c -> c.lowercase() }}.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp))
            }
            if (!compact) TextButton(onClick = { ouvrir(URL_CLUB_HANDBALL) }) { Text("Toute la saison du club") }
        }
    }
}

@Composable
private fun Etoile(m: Match, favoris: Set<String>, basculer: (String) -> Unit) {
    val favori = m.cleFavori() in favoris
    IconToggleButton(checked = favori, onCheckedChange = { basculer(m.cleFavori()) }) {
        Icon(if (favori) Icons.Rounded.Star else Icons.Rounded.StarOutline,
            contentDescription = if (favori) "Retirer des favoris" else "Me le rappeler la veille",
            tint = if (favori) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
