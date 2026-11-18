package com.yogistudio.cesson.actus

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Image publiée par le serveur de l'app avec les données (affiche d'un film, vignette d'un spectacle) : jamais
 * demandée à TMDB ni à OpenAgenda. Un aplat le temps qu'elle arrive, ou si elle ne vient pas.
 */
@Composable
fun ImageServeur(chemin: String, largeur: Dp, hauteur: Dp, charger: suspend (String) -> ByteArray?) {
    val image by produceState<ImageBitmap?>(null, chemin) {
        value = charger(chemin)?.let { o -> runCatching { BitmapFactory.decodeByteArray(o, 0, o.size)?.asImageBitmap() }.getOrNull() }
    }
    Box(Modifier.size(largeur, hauteur).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
        image?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(largeur, hauteur)) }
    }
}
