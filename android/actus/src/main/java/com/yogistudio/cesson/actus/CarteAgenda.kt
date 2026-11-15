package com.yogistudio.cesson.actus

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yogistudio.cesson.core.donnees.EtatModule
import com.yogistudio.cesson.core.ui.CarteDonnee
import com.yogistudio.cesson.core.ui.FuseauCesson
import com.yogistudio.cesson.core.ui.Pastille
import com.yogistudio.cesson.core.ui.TexteBientot
import com.yogistudio.cesson.core.ui.Ton
import com.yogistudio.cesson.core.ui.rememberMaintenant

private const val JOURS_REPLIES = 4

/**
 * Agenda : spectacles du Carré Sévigné et du Pont des Arts (OpenAgenda) et autres rendez-vous de la ville,
 * groupés par jour. Un appui ouvre l'événement chez son éditeur (billetterie, inscription).
 */
@Composable
fun CarteAgenda(etat: EtatModule<DonneesAgenda>, modifier: Modifier = Modifier) {
    val maintenant = rememberMaintenant()
    val aujourdhui = maintenant.atZone(FuseauCesson).toLocalDate()
    var theme by rememberSaveable { mutableStateOf<String?>(null) }
    var tout by rememberSaveable { mutableStateOf(false) }
    when (etat) {
        EtatModule.Absent -> CarteDonnee("Agenda", Icons.Rounded.Event, modifier, maintenant = maintenant) {
            TexteBientot("Agenda pas encore reçu. Tirez vers le bas pour actualiser.")
        }
        is EtatModule.Disponible -> {
            val d = etat.fichier.donnees
            val filtres = FILTRES_AGENDA.filter { (id, _) -> id == null || d.evenements.any { it.theme == id } }
            val jours = agendaParJour(d, theme, aujourdhui)
            CarteDonnee("Agenda", Icons.Rounded.Event, modifier, etat.fraicheur, maintenant) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (filtres.size > 1) {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            filtres.forEach { (id, nom) -> FilterChip(selected = theme == id, onClick = { theme = id }, label = { Text(nom) }) }
                        }
                    }
                    if (jours.isEmpty()) TexteBientot("Aucun événement à venir.")
                    (if (tout) jours else jours.take(JOURS_REPLIES)).forEach { (jour, evenements) ->
                        Text(libelleJour(jour, aujourdhui), style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp).semantics { heading() })
                        evenements.forEach { LigneEvenement(it) }
                    }
                    if (jours.size > JOURS_REPLIES) {
                        TextButton(onClick = { tout = !tout }) {
                            Text(if (tout) "Afficher moins" else "Tout l'agenda (${jours.sumOf { it.second.size }} événements)")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LigneEvenement(e: Evenement) {
    val uri = LocalUriHandler.current
    val jusquau = libelleJusquau(e)
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = "Voir l'événement") { runCatching { uri.openUri(e.lien) } }
            .padding(vertical = 6.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = listOfNotNull(e.horaires, e.titre, e.lieu, jusquau, e.conditions).joinToString(", ")
            },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            e.horaires?.let {
                Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
            }
            Text(e.titre, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), maxLines = 2,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
        Text(listOfNotNull(e.lieu, jusquau).joinToString(" · "), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        e.conditions?.let {
            Pastille(it, if (it.contains("libre", ignoreCase = true) || it.contains("gratuit", ignoreCase = true)) Ton.OUVERT else Ton.NEUTRE)
        }
    }
}
