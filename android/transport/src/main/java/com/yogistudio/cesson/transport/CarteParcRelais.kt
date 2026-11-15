package com.yogistudio.cesson.transport

import android.content.ActivityNotFoundException
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalParking
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.yogistudio.cesson.core.ui.CarteDonnee
import com.yogistudio.cesson.core.ui.LocalCompact
import com.yogistudio.cesson.core.ui.Pastille
import com.yogistudio.cesson.core.ui.StyleChiffres
import com.yogistudio.cesson.core.ui.TexteBientot
import com.yogistudio.cesson.core.ui.Ton
import com.yogistudio.cesson.core.ui.formaterHeure
import com.yogistudio.cesson.core.ui.rememberMaintenant
import kotlinx.coroutines.delay

private const val INTERVALLE_PARCS_MS = 120_000L

/**
 * Parc relais Cesson-ViaSilva (terminus du métro b) : ouvert ou complet, places libres, jauge, places pour
 * véhicules électriques et personnes à mobilité réduite. En détaillé, les parcs voisins (La Poterie, Les Préales).
 * Demandé à la STAR toutes les 2 min, seulement quand l'app est au premier plan.
 */
@Composable
fun CarteParcRelais(client: ClientStar, modifier: Modifier = Modifier) {
    var parcs by remember { mutableStateOf<List<ParcRelais>?>(null) }
    var erreur by remember { mutableStateOf(false) }
    val cycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(client, cycle) {
        cycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                try { parcs = client.parcsRelais(); erreur = false } catch (_: ErreurStar) { erreur = true }
                delay(INTERVALLE_PARCS_MS)
            }
        }
    }
    val maintenant = rememberMaintenant()
    val uri = LocalUriHandler.current
    val compact = LocalCompact.current
    CarteDonnee("Parc relais ViaSilva", Icons.Rounded.LocalParking, modifier, maintenant = maintenant) {
        val viasilva = parcs?.firstOrNull { it.idparc == PARC_VIASILVA }
        Column(verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp)) {
            when {
                parcs == null && !erreur -> TexteBientot("Interrogation de la STAR…")
                viasilva == null -> TexteBientot("Occupation du parc relais indisponible pour le moment.")
                else -> {
                    val p = viasilva
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.semantics(mergeDescendants = true) {
                            contentDescription = when {
                                !p.ouvert -> "Parc relais fermé"
                                p.complet -> "Parc relais complet"
                                else -> "${p.jrdinfosoliste} places libres sur ${p.capacitesoliste}"
                            }
                        },
                    ) {
                        if (p.ouvert && !p.complet) {
                            Text("${p.jrdinfosoliste}", style = StyleChiffres.copy(fontSize = 32.sp, fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.secondary)
                            Spacer(Modifier.width(8.dp))
                            Text("places libres sur ${p.capacitesoliste}", style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f))
                        } else Spacer(Modifier.weight(1f))
                        when {
                            !p.ouvert -> Pastille("Fermé", Ton.FERME)
                            p.complet -> Pastille("Complet", Ton.FERME)
                            else -> Pastille("Ouvert", Ton.OUVERT)
                        }
                    }
                    if (p.ouvert) LinearProgressIndicator(progress = { p.occupation }, modifier = Modifier.fillMaxWidth().height(6.dp))
                    if (p.capaciteve > 0 || p.capacitepmr > 0) {
                        Text(listOfNotNull(
                            "Électrique : ${p.jrdinfoelectrique} / ${p.capaciteve}".takeIf { p.capaciteve > 0 },
                            "PMR : ${p.jrdinfopmr} / ${p.capacitepmr}".takeIf { p.capacitepmr > 0 },
                        ).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                    }
                    if (!compact) {
                        val voisins = PARCS_VOISINS.mapNotNull { (id, nom) -> parcs?.firstOrNull { it.idparc == id }?.let { nom to it } }
                        if (voisins.isNotEmpty()) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Text("Parcs relais voisins", style = MaterialTheme.typography.titleSmall)
                            voisins.forEach { (nom, v) ->
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    Text(nom, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                    if (v.ouvert && !v.complet) {
                                        Text("${v.jrdinfosoliste} places", style = MaterialTheme.typography.bodySmall)
                                        Spacer(Modifier.width(8.dp))
                                    }
                                    Pastille(if (!v.ouvert) "Fermé" else if (v.complet) "Complet" else "Ouvert",
                                        if (v.ouvert && !v.complet) Ton.OUVERT else Ton.FERME)
                                }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            p.misAJour?.let {
                                Text("Temps réel STAR · ${formaterHeure(it, maintenant)}", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                            }
                            p.coordonnees?.let { c ->
                                TextButton(onClick = {
                                    try { uri.openUri("geo:${c.lat},${c.lon}?q=${c.lat},${c.lon}(Parc relais ViaSilva)") }
                                    catch (_: ActivityNotFoundException) { } catch (_: IllegalArgumentException) { }
                                }) { Text("Itinéraire") }
                            }
                        }
                    }
                }
            }
            if (erreur && parcs != null) TexteBientot("STAR injoignable : dernière occupation connue affichée.")
        }
    }
}
