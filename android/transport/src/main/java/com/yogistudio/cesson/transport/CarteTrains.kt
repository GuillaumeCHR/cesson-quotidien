package com.yogistudio.cesson.transport

import com.yogistudio.cesson.core.ui.Ton
import com.yogistudio.cesson.core.ui.Pastille
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Train
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.yogistudio.cesson.core.donnees.EtatModule
import com.yogistudio.cesson.core.ui.CarteDonnee
import com.yogistudio.cesson.core.ui.LocalCompact
import com.yogistudio.cesson.core.ui.Palette
import com.yogistudio.cesson.core.ui.StyleChiffres
import com.yogistudio.cesson.core.ui.TexteBientot
import com.yogistudio.cesson.core.ui.formaterHeure
import com.yogistudio.cesson.core.ui.rememberMaintenant
import kotlinx.coroutines.delay
import java.time.Instant

/** Le backend publie toutes les 2 min : la carte redemande le manifest au même rythme. */
private const val INTERVALLE_TRAINS_MS = 120_000L
private const val PAR_DIRECTION = 3

/**
 * Halte de Cesson-Sévigné : prochains trains vers Rennes et vers Vitré, avec retard ou
 * suppression quand la SNCF les publie. [rafraichir] relit les données tant que la carte est visible.
 */
@Composable
fun CarteTrains(etat: EtatModule<DonneesTrains>, rafraichir: suspend () -> Unit, modifier: Modifier = Modifier) {
    val maintenant = rememberMaintenant(intervalleMs = 15_000)
    val cycle = LocalLifecycleOwner.current.lifecycle
    val relire by rememberUpdatedState(rafraichir)
    LaunchedEffect(cycle) {
        cycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                delay(INTERVALLE_TRAINS_MS)
                relire()
            }
        }
    }
    when (etat) {
        EtatModule.Absent -> CarteDonnee("Trains à Halte-Cesson", Icons.Rounded.Train, modifier, maintenant = maintenant) {
            TexteBientot("Horaires pas encore reçus. Tirez vers le bas pour actualiser.")
        }
        is EtatModule.Disponible -> {
            val d = etat.fichier.donnees
            CarteDonnee("Trains à Halte-Cesson", Icons.Rounded.Train, modifier, etat.fraicheur, maintenant) {
                // Compact : le prochain train de chaque direction, sans le numéro du TER.
                val compact = LocalCompact.current
                Column(verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)) {
                    if (!d.tempsReelDisponible && !compact) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Info, contentDescription = null, modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(6.dp))
                            Text("Temps réel SNCF indisponible : horaires prévus, sans retards ni suppressions.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    d.directions.forEach { dir ->
                        val trains = departsDeLaDirection(d, dir.id, maintenant).take(if (compact) 1 else PAR_DIRECTION)
                        Column(verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 6.dp)) {
                            Text(dir.libelle, style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.semantics { heading() })
                            if (trains.isEmpty()) TexteBientot("Plus de train aujourd'hui.")
                            trains.forEach { LigneTrain(it, maintenant, numero = !compact) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LigneTrain(t: DepartTrain, maintenant: Instant, numero: Boolean = true) {
    val prevue = formaterHeure(t.heureTheorique, maintenant)
    val reelle = t.heureReelle?.takeIf { t.statut == StatutTrain.RETARD }?.let { formaterHeure(it, maintenant) }
    val statut = libelleStatut(t)
    val supprime = t.statut == StatutTrain.SUPPRIME
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
            contentDescription = buildString {
                append("Train $prevue vers ${t.destination}, ")
                append(when (t.statut) {
                    StatutTrain.RETARD -> "retard de ${t.retardMin} minutes, départ prévu à $reelle"
                    else -> statut
                })
                append(". TER ${t.ligne} numéro ${t.numero}")
            }
        },
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    prevue,
                    style = StyleChiffres.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontStyle = if (t.statut == StatutTrain.THEORIQUE) FontStyle.Italic else FontStyle.Normal,
                        textDecoration = if (supprime || reelle != null) TextDecoration.LineThrough else null,
                    ),
                    color = if (supprime || reelle != null) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                )
                if (reelle != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(reelle, style = StyleChiffres.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.width(10.dp))
                Text(t.destination, style = MaterialTheme.typography.bodyMedium)
            }
            if (numero) Text("TER ${t.ligne} · n° ${t.numero}", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        PastilleStatut(t.statut, statut)
    }
}

@Composable
private fun PastilleStatut(statut: StatutTrain, texte: String) {
    Pastille(texte.replaceFirstChar { it.uppercase() }, when (statut) {
        StatutTrain.A_L_HEURE -> Ton.OUVERT
        StatutTrain.RETARD -> Ton.ALERTE
        StatutTrain.SUPPRIME -> Ton.FERME
        StatutTrain.THEORIQUE -> Ton.NEUTRE
    })
}
