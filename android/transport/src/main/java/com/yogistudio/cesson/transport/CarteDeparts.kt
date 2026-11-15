package com.yogistudio.cesson.transport

import androidx.compose.material3.IconButton
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.yogistudio.cesson.core.ui.CarteDonnee
import com.yogistudio.cesson.core.ui.LocalCompact
import com.yogistudio.cesson.core.ui.StyleChiffres
import com.yogistudio.cesson.core.ui.TexteBientot
import com.yogistudio.cesson.core.ui.formaterDepuis
import com.yogistudio.cesson.core.ui.formaterHeure
import com.yogistudio.cesson.core.ui.rememberMaintenant
import kotlinx.coroutines.delay
import java.time.Instant

/** Intervalle de rafraîchissement tant que la carte est à l'écran. */
private const val INTERVALLE_MS = 30_000L

private class EtatDeparts(
    val departs: List<Depart>? = null, // null : jamais reçus depuis l'ouverture
    val recusA: Instant? = null,
    val erreur: Boolean = false,
)

/**
 * Prochains départs d'un arrêt STAR (métro et bus), en temps réel. Demandés à STAR toutes les
 * 30 s, seulement quand l'app est au premier plan. [pied] accueille une action (changer d'arrêt).
 */
@Composable
fun CarteDeparts(
    client: ClientStar,
    lignes: DonneesLignesStar?,
    nomArret: String = POLE_VIASILVA,
    titre: String = "Métro et bus ViaSilva",
    modifier: Modifier = Modifier,
    /** Si fourni, une cloche par direction propose une alerte de passage. */
    onAlerter: ((Direction) -> Unit)? = null,
    /** Direction (ligne, destination) dont l'alerte est armée : sa cloche est pleine. */
    alerteArmee: Pair<String, String>? = null,
    pied: @Composable () -> Unit = {},
) {
    var etat by remember(nomArret) { mutableStateOf(EtatDeparts()) }
    val cycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(client, cycle, nomArret) {
        cycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                etat = try {
                    EtatDeparts(client.departs(nomArret), Instant.now())
                } catch (_: ErreurStar) {
                    EtatDeparts(etat.departs, etat.recusA, erreur = true)
                }
                delay(INTERVALLE_MS)
            }
        }
    }
    val maintenant = rememberMaintenant(intervalleMs = 15_000)
    val couleurs = remember(lignes) { lignes?.lignes?.associateBy { it.nomCourt } ?: emptyMap() }

    CarteDonnee(titre, Icons.Rounded.DirectionsBus, modifier, maintenant = maintenant) {
        val recus = etat.departs
        val directions = recus?.let { regrouper(departsUtiles(it, maintenant)) }
        // Compact : 4 directions, 2 passages chacune, sans la légende.
        val compact = LocalCompact.current
        Column(verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp)) {
            when {
                recus == null && !etat.erreur -> TexteBientot("Interrogation de STAR…")
                recus == null -> LigneHorsConnexion("Impossible de joindre STAR pour le moment.")
                directions.isNullOrEmpty() -> TexteBientot("Aucun départ annoncé dans les prochaines heures.")
                else -> directions.take(if (compact) 4 else 8).forEach {
                    val dir = if (compact) it.copy(departs = it.departs.take(2)) else it
                    LigneDirection(dir, couleurs[it.ligne], maintenant, onAlerter, alerteArmee == (it.ligne to it.destination))
                }
            }
            if (recus != null && etat.erreur) {
                LigneHorsConnexion("STAR injoignable : horaires reçus ${formaterDepuis(etat.recusA!!, maintenant)}.")
            }
            if (!compact) Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Sensors, contentDescription = null, modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(4.dp))
                Text("temps réel  ·  en italique : horaire prévu  ·  source STAR",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            pied()
        }
    }
}

@Composable
private fun LigneDirection(
    d: Direction,
    ligne: LigneStar?,
    maintenant: Instant,
    onAlerter: ((Direction) -> Unit)? = null,
    armee: Boolean = false,
) {
    val heures = d.departs.map { formaterAttente(it.heure, maintenant) { h -> formaterHeure(h, maintenant) } }
    val description = buildString {
        append(if (d.mode == Mode.METRO) "Métro ${d.ligne}" else "Ligne ${d.ligne}")
        append(" vers ${d.destination} : ")
        append(d.departs.zip(heures).joinToString(", ") { (dep, h) ->
            val quand = if (h.endsWith("min")) "dans ${h.replace("min", "minutes")}" else h
            if (dep.statut == StatutDepart.TEMPS_REEL) quand else "$quand, horaire prévu"
        })
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        PastilleLigne(d.ligne, ligne)
        Spacer(Modifier.width(10.dp))
        Text(d.destination, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        d.departs.zip(heures).forEachIndexed { i, (dep, h) ->
            if (i > 0) Spacer(Modifier.width(10.dp))
            val tempsReel = dep.statut == StatutDepart.TEMPS_REEL
            Text(
                h,
                style = StyleChiffres.copy(
                    fontWeight = if (i == 0) FontWeight.SemiBold else FontWeight.Normal,
                    fontStyle = if (tempsReel) FontStyle.Normal else FontStyle.Italic,
                ),
                color = if (i == 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onAlerter != null) {
            IconButton(onClick = { onAlerter(d) }, modifier = Modifier.size(40.dp)) {
                Icon(
                    if (armee) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsNone,
                    contentDescription = if (armee) "Alerte armée pour le ${d.ligne} vers ${d.destination}"
                    else "M'alerter avant le passage du ${d.ligne} vers ${d.destination}",
                    tint = if (armee) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Pastille aux couleurs officielles de la ligne ; neutre tant que les couleurs ne sont pas reçues. */
@Composable
internal fun PastilleLigne(nom: String, ligne: LigneStar?) {
    val fond = ligne?.couleur?.let(::couleurHex) ?: MaterialTheme.colorScheme.secondaryContainer
    val texte = ligne?.couleurTexte?.let(::couleurHex) ?: MaterialTheme.colorScheme.onSecondaryContainer
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .defaultMinSize(minWidth = 40.dp)
            .background(fond, MaterialTheme.shapes.small)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(nom, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = texte, maxLines = 1)
    }
}

@Composable
private fun LigneHorsConnexion(texte: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.CloudOff, contentDescription = null, modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(6.dp))
        Text(texte, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

internal fun couleurHex(hex: String): Color? =
    hex.removePrefix("#").takeIf { it.length == 6 }?.toLongOrNull(16)?.let { Color(0xFF000000 or it) }
