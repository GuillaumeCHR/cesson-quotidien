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
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Train
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.TripOrigin
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextOverflow
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
fun CarteTrains(
    etat: EtatModule<DonneesTrains>,
    rafraichir: suspend () -> Unit,
    modifier: Modifier = Modifier,
    favoris: List<TrainFavori> = emptyList(),
    reglageFavoris: @Composable () -> Unit = {},
    /** Trains du retour, depuis toutes les gares retenues ; la carte n'affiche que ceux de [gare]. */
    retour: DonneesTrainsRetour? = null,
    /** Gare de destination (code UIC), null sans destination : la carte n'a alors que l'aller. */
    gare: String? = null,
    onChoisirGare: (String?) -> Unit = {},
) {
    var choixGare by rememberSaveable { mutableStateOf(false) }
    // Trajet pendulaire : l'aller part de la halte de Cesson, le retour de la gare de destination. Le matin l'aller,
    // l'après-midi le retour.
    var sensRetour by rememberSaveable(gare) { mutableStateOf(gare != null && java.time.LocalTime.now().hour >= 13) }
    val gareRetour = retour?.gares?.firstOrNull { it.id == gare }
    val nomGare = gareRetour?.nom ?: gare?.let { "Gare choisie" }
    if (choixGare && retour != null) {
        ChoixGare(retour.gares, gare, onFermer = { choixGare = false }) {
            onChoisirGare(it)
            choixGare = false
        }
    }
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
        EtatModule.Absent -> CarteDonnee(TITRE_TRAIN, Icons.Rounded.Train, modifier, maintenant = maintenant) {
            TexteBientot("Horaires pas encore reçus. Tirez vers le bas pour actualiser.")
        }
        is EtatModule.Disponible -> {
            val d = etat.fichier.donnees
            CarteDonnee(TITRE_TRAIN, Icons.Rounded.Train, modifier, etat.fraicheur, maintenant) {
                // Compact : le prochain train de chaque direction, sans le numéro du TER.
                val compact = LocalCompact.current
                Column(verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)) {
                    if (!compact) Column {
                        LigneTrajet(Icons.Rounded.TripOrigin, "À Cesson", "Halte de Cesson-Sévigné", choisi = true, actif = false) {}
                        LigneTrajet(Icons.Rounded.Place, "Destination", nomGare ?: "Une gare, pour le retour (facultatif)",
                            choisi = gare != null, actif = !retour?.gares.isNullOrEmpty()) { choixGare = true }
                    }
                    if (gare != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = !sensRetour, onClick = { sensRetour = false }, label = { Text("Aller") })
                            FilterChip(selected = sensRetour, onClick = { sensRetour = true }, label = { Text("Retour") })
                            Text(if (sensRetour) "Départs de $nomGare" else "Départs de la halte de Cesson",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        }
                    }
                    if (gare != null && sensRetour) {
                        Retour(retour, gare, maintenant, compact)
                        return@Column
                    }
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
                        val trains = prochainsEtPasses(departsDeLaDirection(d, dir.id, maintenant), if (compact) 1 else PAR_DIRECTION, maintenant) { it.heureTheorique }
                        Column(verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 6.dp)) {
                            Text(dir.libelle, style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.semantics { heading() })
                            if (trains.isEmpty()) TexteBientot("Plus de train aujourd'hui.")
                            trains.forEach { t -> LigneTrain(t, maintenant, numero = !compact, favori = favoris.any { it.estLe(t) }) }
                        }
                    }
                    if (!compact) {
                        Text("Mes trains", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                        reglageFavoris()
                    }
                }
            }
        }
    }
}

/** Retour : les trains de la gare de destination qui s'arrêtent ensuite à Rennes ou à Cesson, avec l'heure d'arrivée. */
@Composable
private fun Retour(retour: DonneesTrainsRetour?, gare: String, maintenant: Instant, compact: Boolean) {
    Text("Trains vers Rennes ou Cesson uniquement", style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.semantics { heading() })
    if (retour == null) {
        TexteBientot("Horaires du retour pas encore reçus. Tirez vers le bas pour actualiser.")
        return
    }
    if (!retour.tempsReelDisponible && !compact) {
        Text("Temps réel SNCF indisponible : horaires prévus, sans retards ni suppressions.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    val trains = prochainsEtPasses(departsDeLaGare(retour, gare, maintenant), if (compact) 2 else 4, maintenant) { it.heureTheorique }
    if (trains.isEmpty()) TexteBientot("Plus de train vers Rennes ou Cesson aujourd'hui.")
    trains.forEach { LigneRetour(it, maintenant, detail = !compact) }
}

@Composable
private fun LigneRetour(t: DepartRetour, maintenant: Instant, detail: Boolean) {
    val prevue = formaterHeure(t.heureTheorique, maintenant)
    val reelle = t.heureReelle?.takeIf { t.statut == StatutTrain.RETARD }?.let { formaterHeure(it, maintenant) }
    val arrivee = formaterHeure(t.heureArrivee, maintenant)
    val statut = libelleStatut(t)
    val supprime = t.statut == StatutTrain.SUPPRIME
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
            contentDescription = buildString {
                append("Train $prevue vers ${t.destination}, ")
                append(if (t.statut == StatutTrain.RETARD) "retard de ${t.retardMin} minutes, départ prévu à $reelle" else statut)
                if (!supprime) append(". Arrivée à ${t.arrivee} à $arrivee")
                append(". ${t.ligne} numéro ${t.numero}")
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
                Text(t.destination, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (!supprime) Text(
                buildString {
                    if (detail) append("${t.ligne} · n° ${t.numero} · ")
                    append("arrivée à ${t.arrivee} à $arrivee")
                },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        PastilleStatut(t.statut, statut)
    }
}

const val TITRE_TRAIN = "Mon trajet en train"

@Composable
private fun LigneTrain(t: DepartTrain, maintenant: Instant, numero: Boolean = true, favori: Boolean = false) {
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
                if (favori) append(". Train favori")
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
                if (favori) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Rounded.Star, contentDescription = null, modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary)
                }
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
