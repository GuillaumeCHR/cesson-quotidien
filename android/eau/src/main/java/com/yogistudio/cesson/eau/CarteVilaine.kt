package com.yogistudio.cesson.eau

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingFlat
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Water
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yogistudio.cesson.core.donnees.EtatModule
import com.yogistudio.cesson.core.modele.Tendance
import com.yogistudio.cesson.core.ui.CarteDonnee
import com.yogistudio.cesson.core.ui.StyleChiffres
import com.yogistudio.cesson.core.ui.TexteBientot
import com.yogistudio.cesson.core.ui.formaterDeHeure
import com.yogistudio.cesson.core.ui.rememberMaintenant

@Composable
fun CarteVilaine(
    etat: EtatModule<DonneesVilaine>,
    vigilance: EtatModule<DonneesVigicrues>,
    modifier: Modifier = Modifier,
) {
    val uri = LocalUriHandler.current
    val maintenant = rememberMaintenant()
    when (etat) {
        EtatModule.Absent -> CarteDonnee("La Vilaine", Icons.Rounded.Water, modifier, maintenant = maintenant) {
            LigneVigilance(vigilance)
            TexteBientot("Niveau pas encore reçu. Tirez vers le bas pour actualiser.")
        }
        is EtatModule.Disponible -> {
            val d = etat.fichier.donnees
            val hauteur = formaterHauteur(d.mesure)
            val heure = formaterDeHeure(d.mesure.horodatage, maintenant)
            CarteDonnee(
                titre = "La Vilaine à Pont Briand",
                icone = Icons.Rounded.Water,
                modifier = modifier,
                fraicheur = etat.fraicheur,
                maintenant = maintenant,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LigneVigilance(vigilance)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.semantics(mergeDescendants = true) {
                            contentDescription = "Hauteur $hauteur, ${libelleTendance(d.mesure.tendance)}, mesure $heure"
                        },
                    ) {
                        Text(hauteur, style = StyleChiffres.copy(fontSize = 40.sp, fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(iconeTendance(d.mesure.tendance), contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary)
                                Spacer(Modifier.width(4.dp))
                                Text(libelleTendance(d.mesure.tendance).replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.bodyMedium)
                            }
                            Text("Mesure $heure", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (d.serie24h.size >= 2) {
                        CourbeSerie(d.serie24h, Modifier.fillMaxWidth().height(56.dp))
                        Text("Dernières 24 h", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        "Repère : crue du 26 octobre 1966, 3,73 m.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { uri.openUri(d.station.url ?: URL_VIGICRUES) }) {
                        Text("Voir sur Vigicrues")
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

fun libelleTendance(t: Tendance) = when (t) {
    Tendance.HAUSSE -> "en hausse"
    Tendance.BAISSE -> "en baisse"
    Tendance.STABLE -> "stable"
    Tendance.INCONNUE -> "tendance inconnue"
}

private fun iconeTendance(t: Tendance) = when (t) {
    Tendance.HAUSSE -> Icons.AutoMirrored.Rounded.TrendingUp
    Tendance.BAISSE -> Icons.AutoMirrored.Rounded.TrendingDown
    Tendance.STABLE -> Icons.AutoMirrored.Rounded.TrendingFlat
    Tendance.INCONNUE -> Icons.AutoMirrored.Rounded.HelpOutline
}

/** Courbe simple des dernières 24 h (décorative : la valeur et la tendance sont dites en texte). */
@Composable
private fun CourbeSerie(points: List<PointSerie>, modifier: Modifier) {
    val couleur = MaterialTheme.colorScheme.secondary
    val min = points.minOf { it.valeur }
    val max = points.maxOf { it.valeur }
    // Une amplitude minimale de 5 cm évite de transformer un bruit de mesure en montagne.
    val amplitude = maxOf(max - min, 50.0)
    val t0 = points.first().horodatage.epochSecond
    val duree = maxOf(points.last().horodatage.epochSecond - t0, 1L).toFloat()
    Canvas(modifier.clearAndSetSemantics { }) {
        val chemin = Path()
        points.forEachIndexed { i, p ->
            val x = (p.horodatage.epochSecond - t0) / duree * size.width
            val y = size.height - ((p.valeur - (min + max - amplitude) / 2) / amplitude).toFloat() * size.height
            if (i == 0) chemin.moveTo(x, y) else chemin.lineTo(x, y)
        }
        drawPath(chemin, couleur, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(couleur, radius = 3.dp.toPx(), center = Offset(size.width,
            size.height - ((points.last().valeur - (min + max - amplitude) / 2) / amplitude).toFloat() * size.height))
    }
}
