package com.yogistudio.cesson.ui.intro

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

// Logo de l'éditeur Yogi Studio, déclinaison « locale » pour Cesson'App du logo de Molkky Board et de VYLIA (même
// composition, repère 512 × 512) : la quille de Molkky, devenue smartphone dans VYLIA, devient ici un repère de
// carte en méditation, posé au bord de la Vilaine, aux couleurs de l'app (ardoise, bleu Vilaine, sable, vert
// bocage). Dessiné en code pour l'animer dans l'intro. Nom de marque : non traduit.

private const val VIEWBOX = 512f
private const val RING_START = -62f
private const val RING_SWEEP = 314.2f

private val Tile = Color(0xFF23302B)
private val Ring = Color(0xFF5AA7D9)
private val Body = Color(0xFFF6F3EC)
private val BodyShade = Color(0xFFD9D2C3)
private val Limb = Color(0xFF4E8A6C)
private val LimbShade = Color(0xFF2F6B4F)
private val Leaf = Color(0xFF93D2B0)
private val Face = Color(0xFF23302B)

private fun path(data: String): Path = PathParser().parsePathString(data).toPath()

private object RepereChemins {
    /** Trois feuilles, à la place des étincelles de VYLIA : la ville verte. */
    val feuilles = listOf(
        path("M372,92 Q398,92 400,118 Q374,120 372,92 Z") to 1f,
        path("M410,146 Q428,148 428,166 Q410,164 410,146 Z") to 0.85f,
        path("M346,66 Q360,64 362,80 Q348,80 346,66 Z") to 0.7f,
    )

    /** Le repère : une goutte dont la pointe se pose dans le giron. */
    val corps = path("M256,334 C228,296 186,266 186,218 A70,70 0 1 1 326,218 C326,266 284,296 256,334 Z")
    val bras = listOf(path("M200,248 Q164,292 188,334"), path("M312,248 Q348,292 324,334"))

    /** Yeux fermés et sourire (même tracé que le personnage d'origine). */
    val visage = listOf(path("M226,210 Q236,219 246,210"), path("M266,210 Q276,219 286,210"), path("M241,236 Q256,249 271,236"))

    /** La Vilaine sous le personnage, à la place de l'ombre. */
    val vagues = listOf(path("M150,394 Q182,380 214,394 T278,394 T342,394 T362,390"), path("M178,418 Q206,406 234,418 T290,418 T334,418"))
}

/**
 * Dessine le logo Yogi Studio (déclinaison Cesson'App) dans la zone courante.
 *
 * @param ringProgress tracé du cercle bleu Vilaine, de 0 à 1.
 * @param characterScale échelle du repère (0 = absent, 1 = normal, > 1 pendant le rebond).
 */
fun DrawScope.drawYogiStudioMark(
    ringProgress: Float = 1f,
    characterScale: Float = 1f,
    alpha: Float = 1f,
    side: Float = minOf(size.width, size.height),
    topLeft: Offset = Offset((size.width - side) / 2, (size.height - side) / 2),
) {
    val unit = side / VIEWBOX
    withTransform({
        translate(topLeft.x, topLeft.y)
        scale(unit, unit, pivot = Offset.Zero)
    }) {
        drawRoundRect(Tile.copy(alpha = alpha), size = Size(VIEWBOX, VIEWBOX), cornerRadius = CornerRadius(112f))
        if (ringProgress > 0f) {
            drawArc(
                color = Ring.copy(alpha = alpha),
                startAngle = RING_START,
                sweepAngle = RING_SWEEP * ringProgress.coerceAtMost(1f),
                useCenter = false,
                topLeft = Offset(256f - 186f, 262f - 186f),
                size = Size(372f, 372f),
                style = Stroke(width = 20f, cap = StrokeCap.Round),
            )
        }
        if (characterScale > 0f) {
            scale(characterScale, pivot = Offset(256f, 300f)) { drawRepere(alpha) }
        }
    }
}

private fun DrawScope.drawRepere(alpha: Float) {
    RepereChemins.feuilles.forEach { (p, a) -> drawPath(p, Leaf.copy(alpha = a * alpha)) }
    RepereChemins.vagues.forEach { drawPath(it, Ring.copy(alpha = 0.7f * alpha), style = Stroke(10f, cap = StrokeCap.Round)) }
    RepereChemins.bras.forEach { drawPath(it, Limb.copy(alpha = alpha), style = Stroke(22f, cap = StrokeCap.Round)) }
    // Jambes croisées, puis le repère par-dessus : sa pointe se pose dans le giron.
    listOf(-7f to Limb, 7f to LimbShade).forEach { (angle, color) ->
        rotate(angle, pivot = Offset(256f, 352f)) {
            drawRoundRect(color.copy(alpha = alpha), topLeft = Offset(160f, 330f), size = Size(192f, 44f), cornerRadius = CornerRadius(22f))
        }
    }
    drawPath(RepereChemins.corps, Body.copy(alpha = alpha))
    drawCircle(BodyShade.copy(alpha = alpha), radius = 70f, center = Offset(256f, 218f), style = Stroke(6f))
    RepereChemins.visage.forEach { drawPath(it, Face.copy(alpha = alpha), style = Stroke(7f, cap = StrokeCap.Round)) }
    listOf(222f, 290f).forEach { drawCircle(Leaf.copy(alpha = 0.6f * alpha), radius = 9f, center = Offset(it, 232f)) }
    listOf(188f, 324f).forEach { drawCircle(Ring.copy(alpha = alpha), radius = 14f, center = Offset(it, 336f)) }
}

/** Logo carré Yogi Studio (statique). */
@Composable
fun YogiStudioMark(modifier: Modifier = Modifier, size: Dp = 96.dp) {
    Canvas(modifier.size(size).semantics { contentDescription = "Yogi Studio" }) { drawYogiStudioMark() }
}

/** Logo horizontal : le logo carré, « Yogi » et « STUDIO » espacé en bleu Vilaine. */
@Composable
fun YogiStudioLogo(modifier: Modifier = Modifier, markSize: Dp = 64.dp) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        YogiStudioMark(size = markSize)
        Column {
            Text("Yogi", fontWeight = FontWeight.Bold, fontSize = (markSize.value * 0.55f).sp,
                color = MaterialTheme.colorScheme.onSurface, letterSpacing = (-0.03).em)
            Text("STUDIO", fontWeight = FontWeight.Bold, fontSize = (markSize.value * 0.19f).sp, color = Ring, letterSpacing = 0.5.em)
        }
    }
}
