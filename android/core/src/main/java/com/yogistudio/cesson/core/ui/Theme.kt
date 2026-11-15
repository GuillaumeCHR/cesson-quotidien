package com.yogistudio.cesson.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.yogistudio.cesson.core.R

/*
 * Palette propre à l'app (« ville-jardin » au bord de la Vilaine), distincte de la charte
 * municipale. Le jaune, l'orange et le rouge sont réservés aux niveaux officiels de vigilance.
 */
object Palette {
    val VertBocage = Color(0xFF2F6B4F)
    val BleuVilaine = Color(0xFF1F5F8B)
    val Sable = Color(0xFFF6F3EC)
    val Ardoise = Color(0xFF23302B)

    val VigilanceJaune = Color(0xFFF2C94C)
    val VigilanceOrange = Color(0xFFF2994A)
    val VigilanceRouge = Color(0xFFD64545)
}

private val Clair = lightColorScheme(
    primary = Palette.VertBocage,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDE6D8),
    onPrimaryContainer = Color(0xFF0E2E20),
    secondary = Palette.BleuVilaine,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD3E4F3),
    onSecondaryContainer = Color(0xFF0B2A40),
    tertiary = Color(0xFF5E6A3A),
    background = Palette.Sable,
    onBackground = Palette.Ardoise,
    surface = Palette.Sable,
    onSurface = Palette.Ardoise,
    surfaceVariant = Color(0xFFE5E2D9),
    onSurfaceVariant = Color(0xFF48524D),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFBF9F4),
    surfaceContainer = Color(0xFFF0EDE5),
    surfaceContainerHigh = Color(0xFFEAE7DF),
    surfaceContainerHighest = Color(0xFFE4E1D9),
    outline = Color(0xFF78817C),
    outlineVariant = Color(0xFFC8CCC6),
    // Les erreurs de l'interface n'empruntent pas le rouge de vigilance.
    error = Color(0xFF9C2B2B),
)

private val Sombre = darkColorScheme(
    primary = Color(0xFF93D2B0),
    onPrimary = Color(0xFF003824),
    primaryContainer = Color(0xFF185237),
    onPrimaryContainer = Color(0xFFCDE6D8),
    secondary = Color(0xFF9DCAF0),
    onSecondary = Color(0xFF00324F),
    secondaryContainer = Color(0xFF0E4A6F),
    onSecondaryContainer = Color(0xFFD3E4F3),
    tertiary = Color(0xFFC4D29A),
    background = Color(0xFF121714),
    onBackground = Color(0xFFE2E6E1),
    surface = Color(0xFF121714),
    onSurface = Color(0xFFE2E6E1),
    surfaceVariant = Color(0xFF3F4843),
    onSurfaceVariant = Color(0xFFBFC8C2),
    surfaceContainerLowest = Color(0xFF0D110F),
    surfaceContainerLow = Color(0xFF1A201C),
    surfaceContainer = Color(0xFF1E2420),
    surfaceContainerHigh = Color(0xFF282F2A),
    surfaceContainerHighest = Color(0xFF333A35),
    outline = Color(0xFF89928C),
    outlineVariant = Color(0xFF3F4843),
    error = Color(0xFFFFB4AB),
)

/** Atkinson Hyperlegible, conçue pour la lisibilité par les personnes malvoyantes. */
val Atkinson = FontFamily(
    Font(R.font.atkinson_regular, FontWeight.Normal),
    Font(R.font.atkinson_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.atkinson_bold, FontWeight.Bold),
)

/** Roboto Flex (police variable) pour les chiffres d'horaires et de mesures. */
val RobotoFlex = FontFamily(
    Font(R.font.roboto_flex, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.roboto_flex, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.roboto_flex, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
)

/** Style des chiffres : Roboto Flex à chasse fixe, pour que les valeurs ne « dansent » pas. */
val StyleChiffres = TextStyle(fontFamily = RobotoFlex, fontFeatureSettings = "tnum")

private val TypoBase = Typography()
private fun TextStyle.atkinson() = copy(fontFamily = Atkinson)

private val Typo = Typography(
    displayLarge = TypoBase.displayLarge.atkinson(),
    displayMedium = TypoBase.displayMedium.atkinson(),
    displaySmall = TypoBase.displaySmall.atkinson(),
    headlineLarge = TypoBase.headlineLarge.atkinson().copy(fontWeight = FontWeight.Bold),
    headlineMedium = TypoBase.headlineMedium.atkinson().copy(fontWeight = FontWeight.Bold),
    headlineSmall = TypoBase.headlineSmall.atkinson().copy(fontWeight = FontWeight.Bold),
    titleLarge = TypoBase.titleLarge.atkinson().copy(fontWeight = FontWeight.Bold),
    titleMedium = TypoBase.titleMedium.atkinson().copy(fontWeight = FontWeight.Bold),
    titleSmall = TypoBase.titleSmall.atkinson().copy(fontWeight = FontWeight.Bold),
    bodyLarge = TypoBase.bodyLarge.atkinson().copy(fontSize = 17.sp),
    bodyMedium = TypoBase.bodyMedium.atkinson().copy(fontSize = 15.sp),
    bodySmall = TypoBase.bodySmall.atkinson().copy(fontSize = 13.sp),
    labelLarge = TypoBase.labelLarge.atkinson().copy(fontWeight = FontWeight.Bold),
    labelMedium = TypoBase.labelMedium.atkinson(),
    labelSmall = TypoBase.labelSmall.atkinson(),
)

/** Vrai si l'app s'affiche en sombre (choix de l'utilisateur, ou celui du téléphone). À lire plutôt que isSystemInDarkTheme(). */
val LocalSombre = staticCompositionLocalOf { false }

@Composable
fun CessonTheme(sombre: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalSombre provides sombre) {
        MaterialTheme(colorScheme = if (sombre) Sombre else Clair, typography = Typo, content = content)
    }
}
