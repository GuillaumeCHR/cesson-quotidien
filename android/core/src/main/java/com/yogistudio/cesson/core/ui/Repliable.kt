package com.yogistudio.cesson.core.ui

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Au-delà, le contenu d'une carte se replie : l'écran se parcourt sans faire défiler une seule carte. */
val HAUTEUR_REPLIEE: Dp = 320.dp

/**
 * Contenu limité à [hauteurMax], avec un fondu et « Étendre » quand il dépasse. Le contenu est mesuré
 * en entier : un contenu court ne montre rien de plus. Le choix d'étendre survit au défilement.
 */
@Composable
fun ContenuRepliable(
    fond: Color,
    hauteurMax: Dp = HAUTEUR_REPLIEE,
    contenu: @Composable () -> Unit,
) {
    var etendu by rememberSaveable { mutableStateOf(false) }
    var hauteurTotale by remember { mutableIntStateOf(0) }
    val maxPx = with(LocalDensity.current) { hauteurMax.roundToPx() }
    val depasse = hauteurTotale > maxPx + with(LocalDensity.current) { 48.dp.roundToPx() } // pas pour quelques lignes
    val cible = if (depasse && !etendu) maxPx else Int.MAX_VALUE
    val limite by animateIntAsState(if (cible == Int.MAX_VALUE) hauteurTotale.coerceAtLeast(maxPx) else cible,
        tween(260), label = "repli")
    Column {
        Box(Modifier.clipToBounds()) {
            Layout(contenu) { mesurables, c ->
                val p = mesurables.first().measure(c.copy(minHeight = 0, maxHeight = Constraints.Infinity))
                if (p.height != hauteurTotale) hauteurTotale = p.height
                val h = if (depasse) minOf(p.height, limite) else p.height
                layout(p.width, h) { p.place(0, 0) }
            }
            if (depasse && !etendu) {
                Box(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(56.dp)
                        .background(Brush.verticalGradient(listOf(fond.copy(alpha = 0f), fond))),
                )
            }
        }
        if (depasse) {
            TextButton(onClick = { etendu = !etendu }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(if (etendu) "Réduire" else "Étendre", style = MaterialTheme.typography.labelLarge)
                Icon(if (etendu) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown, contentDescription = null)
            }
        }
    }
}
