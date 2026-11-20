package com.yogistudio.cesson.ui.intro

import android.provider.Settings
import androidx.compose.foundation.Canvas
import com.yogistudio.cesson.core.carte.CarteCesson
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yogistudio.cesson.core.ui.Atkinson
import com.yogistudio.cesson.core.ui.LocalSombre
import kotlin.math.min

/** Couleurs de l'intro, en clair et en sombre : à plat, sans dégradé. */
private class Teintes(
    val fond: Color, val contour: Color, val urbain: Color, val vert: Color, val eau: Color, val vilaine: Color,
    val repere: Color, val pastille: Color, val texte: Color, val ouvert: Color, val ferme: Color, val alerte: Color,
    val discret: Color,
    val pastilleTexte: Color = Color.White,
)

private val Clair = Teintes(
    fond = Color(0xFFF6F3EC), contour = Color(0xFF23302B), urbain = Color(0xFFE0D6C4), vert = Color(0xFFB4D6B8),
    eau = Color(0xFF9CC4E0), vilaine = Color(0xFF1F5F8B), repere = Color(0xFF2F6B4F), pastille = Color.White,
    texte = Color(0xFF23302B), ouvert = Color(0xFF2F8A5B), ferme = Color(0xFFC0392B), alerte = Color(0xFFF2C94C),
    discret = Color(0xFF48524D),
)

private val Sombre = Teintes(
    fond = Color(0xFF121714), contour = Color(0xFFBFC8C2), urbain = Color(0xFF353D38), vert = Color(0xFF285A40),
    eau = Color(0xFF285A7C), vilaine = Color(0xFF9DCAF0), repere = Color(0xFF93D2B0), pastille = Color(0xFF323D37),
    texte = Color(0xFFE2E6E1), ouvert = Color(0xFF93D2B0), ferme = Color(0xFFFFB4AB), alerte = Color(0xFFF2C94C),
    discret = Color(0xFFBFC8C2),
    pastilleTexte = Color(0xFF121714),
)

/** Ce que l'intro montre en passant : l'essentiel de l'app, en un mot chacun. */
private enum class Genre { DEPART, OUVERT, FERME, ALERTE, PRIX, HORAIRE }

private class Repere(val lieu: String, val texte: String, val genre: Genre)

/** Ordre d'apparition : il alterne les coins de la carte pour que le flot circule. */
private val REPERES = listOf(
    Repere("viasilva", "Métro b · 2 min", Genre.DEPART),
    Repere("pont_briand", "Vilaine", Genre.ALERTE),
    Repere("piscine", "Ouvert", Genre.OUVERT),
    Repere("carrefour", "1,918 €", Genre.PRIX),
    Repere("atalante", "C4 · 6 min", Genre.DEPART),
    Repere("decheterie", "9h – 12h", Genre.HORAIRE),
    Repere("golf", "Ouvert", Genre.OUVERT),
    Repere("halte", "TER · 7h42", Genre.DEPART),
    Repere("mediatheque", "Fermé", Genre.FERME),
)

/**
 * Intro de Cesson'App (≈ 4,7 s), sur le schéma de celles de Molkky Board et de VYLIA : la carte de Cesson-Sévigné
 * se dessine (contour, quartiers, espaces verts, la Vilaine), des repères de la vie locale surgissent et s'effacent
 * en flot (départs, ouvert/fermé, alerte, prix, horaires), la carte s'épure, signature Yogi Studio, puis le
 * logotype Cesson'App claque et glisse dans l'en-tête de l'accueil. Un tap passe au logotype. Avec « Supprimer
 * les animations », simple fondu du logo Yogi Studio.
 *
 * @param frameTimeMs fige l'intro à cet instant (captures) au lieu de la jouer.
 */
@Composable
fun IntroCesson(onFinished: () -> Unit, modifier: Modifier = Modifier, frameTimeMs: Long? = null) {
    val reduced = rememberReducedMotion()
    val end = if (reduced) IntroTimeline.REDUCED_END else IntroTimeline.END
    val elapsed = remember { mutableLongStateOf(frameTimeMs ?: 0L) }
    val currentOnFinished by rememberUpdatedState(onFinished)
    if (frameTimeMs == null) IntroClock(elapsed, reduced, end, currentOnFinished)
    val teintes = if (LocalSombre.current) Sombre else Clair

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(teintes.fond)
            .pointerInput(reduced) {
                detectTapGestures {
                    val cible = if (reduced) end else IntroTimeline.LOGO_SHOWN
                    elapsed.longValue = maxOf(elapsed.longValue, cible)
                }
            }
            .semantics { contentDescription = "$NOM_APP. Touchez l'écran pour passer l'introduction." },
    ) {
        if (reduced) {
            Canvas(Modifier.fillMaxSize()) {
                val t = elapsed.longValue
                val side = min(size.width, size.height) * 0.36f
                drawYogiStudioMark(alpha = progress(t, 0, 300) * (1f - progress(t, 700, IntroTimeline.REDUCED_END)), side = side)
            }
        } else {
            SceneCarte(elapsed, teintes)
            Signature(elapsed, teintes)
            LogoReveal(elapsed)
        }
    }
}

@Composable
private fun IntroClock(elapsed: MutableLongState, reduced: Boolean, end: Long, onFinished: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(reduced) {
        var last = withFrameMillis { it }
        while (elapsed.longValue < end) {
            withFrameMillis { now ->
                val before = elapsed.longValue
                val next = before + (now - last)
                last = now
                if (!reduced && before < IntroTimeline.LOGO_IN && next >= IntroTimeline.LOGO_IN) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) // le logotype se pose
                }
                elapsed.longValue = next
            }
        }
        onFinished()
    }
}

/** Une zone (quartier, bois, plan d'eau) qui surgit depuis son centre, avec un retard selon sa distance à la mairie. */
private class Zone(val chemin: Path, val centre: Offset, val retard: Long, val genre: Int)

private const val URBAIN = 0
private const val VERT = 1
private const val EAU = 2

/** Chemins de la carte, mis à l'échelle une fois pour toutes (rien n'est alloué image par image, hors tracé). */
private class CarteDessinee(largeur: Float, hauteur: Float) {
    val echelle = min(largeur * 0.84f / CarteCesson.LARGEUR, hauteur * 0.6f / CarteCesson.HAUTEUR)
    val origine = Offset((largeur - CarteCesson.LARGEUR * echelle) / 2, hauteur * 0.44f - CarteCesson.HAUTEUR * echelle / 2)
    val pivot = Offset(origine.x + CarteCesson.CENTRE_X * echelle, origine.y + CarteCesson.CENTRE_Y * echelle)

    fun point(xy: FloatArray) = Offset(origine.x + xy[0] * echelle, origine.y + xy[1] * echelle)
    val centre = pivot
    val lieux = CarteCesson.lieux.mapValues { point(it.value) }

    private fun chemin(xy: FloatArray, ferme: Boolean) = Path().apply {
        moveTo(origine.x + xy[0] * echelle, origine.y + xy[1] * echelle)
        var i = 2
        while (i < xy.size) {
            lineTo(origine.x + xy[i] * echelle, origine.y + xy[i + 1] * echelle)
            i += 2
        }
        if (ferme) close()
    }

    private fun barycentre(xy: FloatArray): Offset {
        var x = 0f
        var y = 0f
        var i = 0
        while (i < xy.size) { x += xy[i]; y += xy[i + 1]; i += 2 }
        val n = xy.size / 2
        return Offset(origine.x + x / n * echelle, origine.y + y / n * echelle)
    }

    val contour = chemin(CarteCesson.contour, ferme = true)

    /** Les zones surgissent en vague depuis le centre-ville : les plus proches d'abord. */
    val zones: List<Zone> = run {
        val brutes = CarteCesson.urbain.map { it to URBAIN } + CarteCesson.vert.map { it to VERT } + CarteCesson.eau.map { it to EAU }
        val loin = brutes.maxOf { (xy, _) -> (barycentre(xy) - pivot).getDistance() }.coerceAtLeast(1f)
        brutes.map { (xy, genre) ->
            val c = barycentre(xy)
            Zone(chemin(xy, true), c, ((c - pivot).getDistance() / loin * IntroTimeline.ZONES_VAGUE).toLong(), genre)
        }
    }
    val vilaine = CarteCesson.vilaine.map { chemin(it, ferme = false) }

    private val mesure = PathMeasure()
    private val morceau = Path()
    val longueurContour = mesure.run { setPath(contour, false); length }
    val longueursVilaine = vilaine.map { mesure.setPath(it, false); mesure.length }

    /** Dessine la portion [de, a] (fractions) du chemin [c] ; renvoie le point de tête. */
    fun DrawScope.tracer(c: Path, longueur: Float, de: Float, a: Float, couleur: Color, epaisseur: Float): Offset {
        if (a <= de) return Offset.Unspecified
        morceau.rewind()
        mesure.setPath(c, false)
        mesure.getSegment(longueur * de.coerceIn(0f, 1f), longueur * a.coerceIn(0f, 1f), morceau, true)
        drawPath(morceau, couleur, style = Stroke(epaisseur, cap = StrokeCap.Round, join = StrokeJoin.Round))
        return mesure.getPosition(longueur * a.coerceIn(0f, 1f))
    }
}

@Composable
private fun SceneCarte(elapsed: MutableLongState, teintes: Teintes) {
    val mesureur = rememberTextMeasurer()
    val density = LocalDensity.current
    val style = remember { TextStyle(fontFamily = Atkinson, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
    val textes = remember(style) { REPERES.map { mesureur.measure(it.texte, style) } }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val carte = remember(w, h) { CarteDessinee(w, h) }
        val dp = with(density) { 1.dp.toPx() }
        Canvas(Modifier.fillMaxSize()) {
            val t = elapsed.longValue
            val epure = easeInOutCubic(progress(t, IntroTimeline.EPURE_IN, IntroTimeline.EPURE_END))
            if (epure >= 1f) return@Canvas
            // Entrée : la carte arrive de plus près et se pose ; sortie : elle s'éloigne en s'effaçant.
            val arrivee = 1f - easeOutCubic(progress(t, 0, IntroTimeline.ZONES_END))
            scale(1f + 0.12f * arrivee + 0.06f * epure, pivot = carte.pivot) {
                clipPath(carte.contour) {
                    carte.zones.forEach { z ->
                        val p = progress(t, IntroTimeline.ZONES_IN + z.retard, IntroTimeline.ZONES_IN + z.retard + 380)
                        if (p <= 0f) return@forEach
                        val couleur = when (z.genre) { URBAIN -> teintes.urbain; VERT -> teintes.vert; else -> teintes.eau }
                        scale(springCurve(p), pivot = z.centre) {
                            drawPath(z.chemin, couleur.copy(alpha = progress(t, IntroTimeline.ZONES_IN + z.retard, IntroTimeline.ZONES_IN + z.retard + 150) * (1f - epure)))
                        }
                    }
                }
                with(carte) {
                    // Le contour se trace derrière un point lumineux.
                    val pc = easeInOutCubic(progress(t, 0, IntroTimeline.CONTOUR_END))
                    val tete = tracer(contour, longueurContour, 0f, pc, teintes.contour.copy(alpha = 0.85f * (1f - epure)), 1.5f * dp)
                    if (pc in 0.001f..0.999f && tete.isSpecified) {
                        drawCircle(teintes.vilaine.copy(alpha = 0.25f), radius = 12f * dp, center = tete)
                        drawCircle(teintes.pastille, radius = 3.5f * dp, center = tete)
                    }
                    // La Vilaine coule, puis un reflet la parcourt jusqu'à l'épure.
                    val vilaineAlpha = 1f - progress(t, IntroTimeline.EPURE_IN + 250, IntroTimeline.EPURE_END)
                    val pv = easeInOutCubic(progress(t, IntroTimeline.VILAINE_IN, IntroTimeline.VILAINE_END))
                    val reflet = progress(t, IntroTimeline.VILAINE_END - 150, IntroTimeline.EPURE_IN + 300)
                    vilaine.forEachIndexed { i, c ->
                        tracer(c, longueursVilaine[i], 0f, pv, teintes.vilaine.copy(alpha = vilaineAlpha), 4.5f * dp)
                        val bout = tracer(c, longueursVilaine[i], 0f, pv, teintes.vilaine.copy(alpha = 0.18f * vilaineAlpha), 12f * dp)
                        if (pv < 1f && bout.isSpecified) drawCircle(teintes.vilaine.copy(alpha = vilaineAlpha), radius = 5f * dp, center = bout)
                        if (reflet in 0.001f..0.999f) {
                            tracer(c, longueursVilaine[i], reflet - 0.12f, reflet, teintes.pastille.copy(alpha = 0.75f * vilaineAlpha), 2f * dp)
                        }
                    }
                }
                // Le centre-ville : la mairie s'allume, deux ondes partent.
                val c = carte.centre
                listOf(0L, 220L).forEach { decale ->
                    val onde = progress(t, IntroTimeline.ZONES_IN + decale, IntroTimeline.ZONES_IN + decale + 700)
                    if (onde in 0.001f..0.999f) {
                        drawCircle(teintes.repere.copy(alpha = (1f - onde) * 0.5f), radius = (6f + 40f * easeOutCubic(onde)) * dp,
                            center = c, style = Stroke(2f * dp))
                    }
                }
                val mairie = springCurve(progress(t, IntroTimeline.ZONES_IN, IntroTimeline.ZONES_IN + 350))
                if (mairie > 0f) drawCircle(teintes.repere.copy(alpha = 1f - epure), radius = 5f * dp * mairie, center = c)
                REPERES.forEachIndexed { i, r ->
                    val debut = IntroTimeline.REPERES_IN + i * IntroTimeline.REPERE_ECART
                    dessinerRepere(r, textes[i], carte.lieux.getValue(r.lieu), t - debut, teintes, dp, w)
                }
            }
        }
    }
}

/**
 * Un repère : l'épingle tombe d'en haut, rebondit en s'écrasant légèrement et lance une onde au sol ; la pastille,
 * pleine et contrastée, jaillit au-dessus avec un dépassement ; puis tout se rétracte.
 */
private fun DrawScope.dessinerRepere(r: Repere, texte: TextLayoutResult, ancre: Offset, age: Long, teintes: Teintes, dp: Float, largeur: Float) {
    if (age <= 0 || age >= IntroTimeline.REPERE_VIE) return
    val couleur = when (r.genre) {
        Genre.OUVERT -> teintes.ouvert
        Genre.FERME -> teintes.ferme
        Genre.ALERTE -> teintes.alerte
        Genre.DEPART, Genre.HORAIRE -> teintes.vilaine
        Genre.PRIX -> teintes.repere
    }
    val encre = if (r.genre == Genre.ALERTE) Color(0xFF23302B) else teintes.pastilleTexte
    val sortie = easeInOutCubic(progress(age, IntroTimeline.REPERE_VIE - 180L, IntroTimeline.REPERE_VIE))
    val alpha = 1f - sortie

    // Chute (0 – 170 ms), puis rebond amorti.
    val chute = progress(age, 0, 170)
    val hauteurChute = 46f * dp
    val decalage = if (chute < 1f) -hauteurChute * (1f - chute * chute) else 0f
    val rebond = progress(age, 170, 470)
    val ecrase = if (chute >= 1f) 1f - 0.22f * (1f - springCurve(rebond)).coerceIn(-1f, 1f) else 1f
    // Onde au sol à l'impact.
    val onde = progress(age, 170, 620)
    if (onde in 0.001f..0.999f) {
        drawOval(couleur.copy(alpha = (1f - onde) * 0.55f * alpha), topLeft = Offset(ancre.x - 22f * dp * onde, ancre.y - 7f * dp * onde),
            size = Size(44f * dp * onde, 14f * dp * onde), style = Stroke(2f * dp))
    }
    scale(scaleX = 2f - ecrase, scaleY = ecrase * (1f - 0.6f * sortie), pivot = ancre) {
        val tete = Offset(ancre.x, ancre.y - 18f * dp + decalage)
        val pointe = Path().apply {
            moveTo(ancre.x, ancre.y + decalage)
            lineTo(tete.x - 7f * dp, tete.y + 4f * dp)
            lineTo(tete.x + 7f * dp, tete.y + 4f * dp)
            close()
        }
        drawPath(pointe, couleur.copy(alpha = alpha))
        drawCircle(couleur.copy(alpha = alpha), radius = 9f * dp, center = tete)
        drawCircle(teintes.pastilleTexte.copy(alpha = alpha), radius = 3.5f * dp, center = tete)
    }
    // Pastille pleine : jaillit après l'impact, avec dépassement, puis se rétracte vers l'épingle.
    val jaillit = springCurve(progress(age, 190, 520))
    if (jaillit <= 0f) return
    val pad = 10f * dp
    val icone = 9f * dp
    val lw = icone + 6f * dp + texte.size.width
    val lh = texte.size.height.toFloat() + 2 * 6f * dp
    val tw = lw + 2 * pad
    val gauche = (ancre.x - tw / 2).coerceIn(8f * dp, largeur - tw - 8f * dp)
    val haut = ancre.y - 34f * dp - lh
    scale(jaillit * (1f - sortie), pivot = Offset(ancre.x, haut + lh)) {
        drawRoundRect(Color.Black.copy(alpha = 0.18f * alpha), Offset(gauche, haut + 2f * dp), Size(tw, lh), CornerRadius(lh / 2))
        drawRoundRect(couleur.copy(alpha = alpha), Offset(gauche, haut), Size(tw, lh), CornerRadius(lh / 2))
        val cy = haut + lh / 2
        if (r.genre == Genre.ALERTE) {
            val tri = Path().apply {
                moveTo(gauche + pad + icone / 2, cy - icone / 2 - 1f * dp)
                lineTo(gauche + pad + icone + 1f * dp, cy + icone / 2)
                lineTo(gauche + pad - 1f * dp, cy + icone / 2)
                close()
            }
            drawPath(tri, encre.copy(alpha = alpha))
        } else {
            drawCircle(encre.copy(alpha = alpha), radius = icone / 2, center = Offset(gauche + pad + icone / 2, cy))
        }
        drawText(texte, color = encre, topLeft = Offset(gauche + pad + icone + 6f * dp, cy - texte.size.height / 2f), alpha = alpha)
    }
}

/** Signature Yogi Studio : le cercle bleu Vilaine se trace, le repère apparaît avec un rebond, « Yogi Studio présente ». */
@Composable
private fun Signature(elapsed: MutableLongState, teintes: Teintes) {
    val mesureur = rememberTextMeasurer()
    val presente = remember(teintes) {
        mesureur.measure("Yogi Studio présente", TextStyle(fontFamily = Atkinson, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = teintes.discret))
    }
    Canvas(Modifier.fillMaxSize()) {
        val t = elapsed.longValue
        if (t < IntroTimeline.SIGNATURE_IN || t > IntroTimeline.SIGNATURE_END) return@Canvas
        val sortie = 1f - progress(t, IntroTimeline.SIGNATURE_END - 200, IntroTimeline.SIGNATURE_END)
        val anneau = easeOutCubic(progress(t, IntroTimeline.SIGNATURE_IN, IntroTimeline.SIGNATURE_IN + 400))
        val perso = springCurve(progress(t, IntroTimeline.SIGNATURE_IN + 250, IntroTimeline.SIGNATURE_IN + 650))
        val side = min(size.width, size.height) * 0.34f
        val haut = size.height * 0.40f - side / 2
        val alpha = progress(t, IntroTimeline.SIGNATURE_IN, IntroTimeline.SIGNATURE_IN + 150) * sortie
        drawYogiStudioMark(ringProgress = anneau, characterScale = perso, alpha = alpha, side = side,
            topLeft = Offset((size.width - side) / 2, haut))
        val texte = progress(t, IntroTimeline.SIGNATURE_IN + 350, IntroTimeline.SIGNATURE_IN + 600) * sortie
        drawText(presente, topLeft = Offset((size.width - presente.size.width) / 2, haut + side + 20f), alpha = texte)
    }
}

/**
 * Le logotype Cesson'App claque au centre (ressort), la baseline apparaît, puis le logotype glisse et se réduit
 * jusqu'au titre de l'accueil (en haut à gauche, sous la barre d'état) : c'est le même logotype qui sert d'en-tête.
 */
@Composable
private fun LogoReveal(elapsed: MutableLongState) {
    val density = LocalDensity.current
    val statusBar = WindowInsets.statusBars.getTop(density).toFloat()
    var titreLargeur by remember { mutableIntStateOf(0) }
    var titreHauteur by remember { mutableIntStateOf(0) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        val centreY = height * 0.42f
        fun apparition() = progress(elapsed.longValue, IntroTimeline.LOGO_IN, IntroTimeline.LOGO_SHOWN)
        fun glisse() = easeInOutCubic(progress(elapsed.longValue, IntroTimeline.MOVE_START, IntroTimeline.END))
        val fondu = { 1f - progress(elapsed.longValue, IntroTimeline.MOVE_START, IntroTimeline.MOVE_START + 250) }
        Text(
            "Cesson-Sévigné, au quotidien",
            fontFamily = Atkinson,
            fontSize = 16.sp,
            color = if (LocalSombre.current) Sombre.discret else Clair.discret,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .graphicsLayer {
                    alpha = progress(elapsed.longValue, IntroTimeline.LOGO_SHOWN - 100, IntroTimeline.LOGO_SHOWN + 150) * fondu()
                    translationY = centreY + with(density) { 30.dp.toPx() }
                },
        )
        CessonAppLogotype(
            fontSize = TAILLE_LOGOTYPE_INTRO,
            modifier = Modifier
                .onSizeChanged { titreLargeur = it.width; titreHauteur = it.height }
                .graphicsLayer {
                    val p = apparition()
                    val m = glisse()
                    alpha = (p * 3f).coerceAtMost(1f)
                    transformOrigin = TransformOrigin(0f, 0.5f)
                    val claque = START_SCALE + (1f - START_SCALE) * springCurve(p)
                    val cible = HeaderLogotypeSize.value / TAILLE_LOGOTYPE_INTRO.value
                    val echelle = claque + (cible - claque) * m
                    scaleX = echelle
                    scaleY = echelle
                    val departX = (width - titreLargeur * claque) / 2
                    val departY = centreY - titreHauteur / 2f
                    val finX = with(density) { 16.dp.toPx() }
                    val finY = statusBar + with(density) { 32.dp.toPx() } - titreHauteur / 2f
                    translationX = departX + (finX - departX) * m
                    translationY = departY + (finY - departY) * m
                },
        )
    }
}

@Composable
private fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

private val TAILLE_LOGOTYPE_INTRO = 46.sp
private const val START_SCALE = 1.5f
