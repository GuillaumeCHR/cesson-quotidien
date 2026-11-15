package com.yogistudio.cesson.ui.intro

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.yogistudio.cesson.core.ui.Atkinson
import com.yogistudio.cesson.core.ui.LocalSombre

/** Taille du logotype dans l'en-tête de l'accueil : l'intro le fait glisser exactement jusque-là. */
val HeaderLogotypeSize = 24.sp

const val NOM_APP = "Cesson'App"

/**
 * Logotype « Cesson'App » : « Cesson » en vert bocage, « 'App » en bleu Vilaine, même ligne de base.
 * Le jeu de mots s'entend (« c'est son app ») ; TalkBack lit simplement « Cesson'App ».
 */
@Composable
fun CessonAppLogotype(modifier: Modifier = Modifier, fontSize: TextUnit = HeaderLogotypeSize) {
    val sombre = LocalSombre.current
    val vert = if (sombre) Color(0xFF93D2B0) else Color(0xFF2F6B4F)
    val bleu = if (sombre) Color(0xFF9DCAF0) else Color(0xFF1F5F8B)
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = vert)) { append("Cesson") }
            withStyle(SpanStyle(color = bleu)) { append("'App") }
        },
        fontFamily = Atkinson,
        fontWeight = FontWeight.Bold,
        fontSize = fontSize,
        letterSpacing = (-0.02).em,
        maxLines = 1,
        modifier = modifier.semantics { contentDescription = NOM_APP },
    )
}
