package com.yogistudio.cesson.ui.intro

import kotlin.math.cos
import kotlin.math.exp

/**
 * Découpage de l'intro (millisecondes), sur le modèle de Molkky Board et de VYLIA : mise en place, action,
 * image forte qui s'efface, signature Yogi Studio, puis le logotype qui claque et rejoint l'en-tête.
 */
internal object IntroTimeline {
    /** 1. La carte : le contour de la commune se trace, les quartiers et les espaces verts apparaissent, la Vilaine coule. */
    const val CONTOUR_END = 700L
    const val ZONES_IN = 200L
    /** Les zones surgissent en vague depuis la mairie : la plus lointaine part ce délai après la première. */
    const val ZONES_VAGUE = 520L
    const val ZONES_END = 1_100L
    const val VILAINE_IN = 380L
    const val VILAINE_END = 1_150L

    /** 2. La vie locale : repères, horaires, alertes, ouvert/fermé surgissent puis s'effacent, en flot continu. */
    const val REPERES_IN = 950L
    const val REPERE_ECART = 165L
    const val REPERE_VIE = 820L

    /** 3. Le flot s'épure : la carte s'éloigne et s'efface, la Vilaine en dernier. */
    const val EPURE_IN = 2_550L
    const val EPURE_END = 3_150L

    /** 4. La signature Yogi Studio. */
    const val SIGNATURE_IN = 2_950L
    const val SIGNATURE_END = 3_850L

    /** 5. Le logotype Cesson'App claque au centre, puis glisse vers l'en-tête de l'accueil. */
    const val LOGO_IN = 3_850L
    const val LOGO_SHOWN = 4_150L
    const val MOVE_START = 4_300L
    const val END = 4_750L

    /** « Réduire les animations » : simple fondu du logo Yogi Studio. */
    const val REDUCED_END = 1_000L
}

internal fun progress(time: Long, from: Long, to: Long): Float =
    ((time - from).toFloat() / (to - from)).coerceIn(0f, 1f)

/** Ressort amorti (0 → 1 avec léger dépassement), sans allocation. */
internal fun springCurve(p: Float): Float = if (p <= 0f) 0f else 1f - exp(-6f * p) * cos(10f * p)

internal fun easeOutCubic(p: Float): Float = 1f - (1f - p) * (1f - p) * (1f - p)

internal fun easeInOutCubic(p: Float): Float =
    if (p < 0.5f) 4f * p * p * p else 1f - (-2f * p + 2f).let { it * it * it } / 2f
