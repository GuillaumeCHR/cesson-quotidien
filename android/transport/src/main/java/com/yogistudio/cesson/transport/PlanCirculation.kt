package com.yogistudio.cesson.transport

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.yogistudio.cesson.core.carte.CarteCesson
import com.yogistudio.cesson.core.carte.CarteRennes
import com.yogistudio.cesson.core.ui.LocalSombre
import com.yogistudio.cesson.core.ui.Palette
import kotlin.math.min

/** Couleur d'un état sur le plan : celles des pastilles, plus foncées sur fond clair pour rester lisibles en trait fin. */
private fun couleur(e: EtatTrafic, sombre: Boolean, inconnu: Color): Color = when (e) {
    EtatTrafic.FLUIDE -> if (sombre) Color(0xFF6FCF97) else Color(0xFF2F8A5B)
    EtatTrafic.DENSE -> if (sombre) Palette.VigilanceJaune else Color(0xFFD99A00)
    EtatTrafic.SATURE -> if (sombre) Color(0xFFFF8A80) else Color(0xFFD64545)
    EtatTrafic.BLOQUE -> if (sombre) Color(0xFFFF5252) else Color(0xFF8E1010)
    EtatTrafic.INCONNU -> inconnu
}

/** Épaisseur en dp : la rocade deux fois plus épaisse que les rues de Cesson ; un ralentissement ressort un peu. */
private fun epaisseur(e: EtatTrafic, rocade: Boolean = false) = (if (rocade) 2f else 1f) * when (e) {
    EtatTrafic.INCONNU -> 1f
    EtatTrafic.FLUIDE -> 1.5f
    EtatTrafic.DENSE, EtatTrafic.SATURE -> 2f
    EtatTrafic.BLOQUE -> 2.5f
}

/** Tracés projetés sur la carte de Cesson (coordonnées x, y à plat, comme CarteCesson). */
private class TraceProjete(val etat: EtatTrafic, val rocade: Boolean, val xy: FloatArray)

private fun projeter(
    troncons: List<TronconTrace>,
    projection: (Double, Double) -> Pair<Float, Float> = CarteCesson::projeter,
): List<TraceProjete> = troncons.mapNotNull { t ->
    if (t.points.size < 2) return@mapNotNull null
    val xy = FloatArray(t.points.size * 2)
    t.points.forEachIndexed { i, p ->
        val (x, y) = projection(p[0], p[1])
        xy[2 * i] = x
        xy[2 * i + 1] = y
    }
    TraceProjete(t.etat, t.rocade, xy)
}

private fun chemin(xy: FloatArray, echelle: Float, ferme: Boolean) = Path().apply {
    moveTo(xy[0] * echelle, xy[1] * echelle)
    var i = 2
    while (i < xy.size) {
        lineTo(xy[i] * echelle, xy[i + 1] * echelle)
        i += 2
    }
    if (ferme) close()
}

/**
 * Plan de la circulation : limite de la commune, Vilaine en bleu, et chaque tronçon mesuré (rocade est et axes de
 * Cesson) dans la couleur de son état. Pas de légende : le détail suit en texte sous le plan,
 * la couleur n'est jamais seule à parler.
 */
@Composable
fun PlanCirculation(d: DonneesCirculation, modifier: Modifier = Modifier, compact: Boolean = false) {
    if (d.tronconsMetropole.isNotEmpty()) {
        PlanMetropole(d.tronconsMetropole, modifier, compact)
        return
    }
    val sombre = LocalSombre.current
    val schema = MaterialTheme.colorScheme
    // Compact : plus petit, et seulement les tronçons mesurés (les non mesurés brouilleraient le dessin à cette taille).
    val traces = remember(d.troncons, compact) {
        projeter(if (compact) d.troncons.filter { it.etat != EtatTrafic.INCONNU } else d.troncons)
    }
    val hauteur = if (compact) 80.dp else 130.dp
    val bleu = if (sombre) Color(0xFF9DCAF0) else Palette.BleuVilaine
    val inconnu = schema.outline
    val resume = remember(d.troncons) { resumer(d.troncons) }
    Box(modifier.fillMaxWidth().height(hauteur), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier.fillMaxWidth().height(hauteur).clearAndSetSemantics {
                contentDescription = "Plan de la circulation à Cesson-Sévigné : $resume. Le résumé suit en texte."
            },
        ) {
            val echelle = min(size.width / CarteCesson.LARGEUR, size.height / CarteCesson.HAUTEUR)
            val origine = Offset((size.width - CarteCesson.LARGEUR * echelle) / 2, (size.height - CarteCesson.HAUTEUR * echelle) / 2)
            translate(origine.x, origine.y) {
                val contour = chemin(CarteCesson.contour, echelle, ferme = true)
                drawPath(contour, schema.surfaceVariant.copy(alpha = 0.6f))
                drawPath(contour, schema.outline, style = Stroke(1.dp.toPx(), join = StrokeJoin.Round))
                CarteCesson.vilaine.forEach {
                    drawPath(chemin(it, echelle, ferme = false), bleu,
                        style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                traces.forEach { t ->
                    drawPath(chemin(t.xy, echelle, ferme = false), couleur(t.etat, sombre, inconnu),
                        style = Stroke(epaisseur(t.etat, t.rocade).dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
        }
    }
}

private fun resumer(troncons: List<TronconTrace>): String {
    val n = troncons.groupingBy { it.etat }.eachCount()
    return listOfNotNull(
        n[EtatTrafic.BLOQUE]?.let { "$it bloqués" }, n[EtatTrafic.SATURE]?.let { "$it saturés" },
        n[EtatTrafic.DENSE]?.let { "$it denses" }, n[EtatTrafic.FLUIDE]?.let { "$it fluides" },
    ).joinToString(", ").ifEmpty { "aucun tronçon mesuré" }
}

/**
 * Plan de Rennes et de sa première couronne : limites des communes, Cesson-Sévigné teintée et cernée, Vilaine et Ille
 * en bleu, puis la rocade entière, les voies rapides et les grands axes de la métropole (et les rues de Cesson) dans
 * la couleur de leur état. Les voies rapides sont épaisses, les rues fines.
 */
@Composable
private fun PlanMetropole(troncons: List<TronconTrace>, modifier: Modifier, compact: Boolean) {
    val sombre = LocalSombre.current
    val schema = MaterialTheme.colorScheme
    val traces = remember(troncons, compact) {
        projeter(if (compact) troncons.filter { it.etat != EtatTrafic.INCONNU } else troncons, CarteRennes::projeter)
    }
    val bleu = if (sombre) Color(0xFF9DCAF0) else Palette.BleuVilaine
    val inconnu = schema.outline.copy(alpha = 0.6f)
    val resume = remember(troncons) { resumer(troncons) }
    val taille = if (compact) Modifier.height(110.dp) else Modifier.aspectRatio(CarteRennes.LARGEUR / CarteRennes.HAUTEUR)
    Canvas(
        modifier.fillMaxWidth().then(taille).clearAndSetSemantics {
            contentDescription = "Plan de la circulation à Rennes et autour, Cesson-Sévigné mise en évidence : $resume. " +
                "Le résumé suit en texte."
        },
    ) {
        val echelle = min(size.width / CarteRennes.LARGEUR, size.height / CarteRennes.HAUTEUR)
        val origine = Offset((size.width - CarteRennes.LARGEUR * echelle) / 2, (size.height - CarteRennes.HAUTEUR * echelle) / 2)
        clipRect {
            translate(origine.x, origine.y) {
                val trait = Stroke(0.75.dp.toPx(), join = StrokeJoin.Round)
                CarteRennes.communes.forEach { c ->
                    val contour = chemin(c.contour, echelle, ferme = true)
                    when (c.insee) {
                        CarteRennes.INSEE_CESSON -> {
                            drawPath(contour, schema.primary.copy(alpha = if (sombre) 0.22f else 0.14f))
                            drawPath(contour, schema.primary, style = Stroke(1.5.dp.toPx(), join = StrokeJoin.Round))
                        }
                        CarteRennes.INSEE_RENNES -> {
                            drawPath(contour, schema.surfaceVariant.copy(alpha = 0.6f))
                            drawPath(contour, schema.outline.copy(alpha = 0.7f), style = trait)
                        }
                        else -> drawPath(contour, schema.outlineVariant, style = trait)
                    }
                }
                CarteRennes.rivieres.forEach {
                    drawPath(chemin(it, echelle, ferme = false), bleu,
                        style = Stroke(1.25.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                traces.forEach { t ->
                    drawPath(chemin(t.xy, echelle, ferme = false), couleur(t.etat, sombre, inconnu),
                        style = Stroke(epaisseur(t.etat, t.rocade).dp.toPx() * 0.8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
        }
    }
}
