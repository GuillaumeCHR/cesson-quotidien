package com.yogistudio.cesson.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

val FuseauCesson: ZoneId = ZoneId.of("Europe/Paris")
private val Heure = DateTimeFormatter.ofPattern("HH'h'mm", Locale.FRANCE)
private val JourHeure = DateTimeFormatter.ofPattern("d MMM 'à' HH'h'mm", Locale.FRANCE)

/** Instant courant, recalculé toutes les 30 s pour que « il y a 3 min » reste juste. */
@Composable
fun rememberMaintenant(intervalleMs: Long = 30_000): Instant {
    var maintenant by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(intervalleMs) {
        while (true) {
            delay(intervalleMs)
            maintenant = Instant.now()
        }
    }
    return maintenant
}

/** « à l'instant », « il y a 12 min », « il y a 3 h », puis la date au-delà de 24 h. */
fun formaterDepuis(instant: Instant, maintenant: Instant): String {
    val d = Duration.between(instant, maintenant)
    return when {
        d.isNegative || d.toMinutes() < 1 -> "à l'instant"
        d.toMinutes() < 60 -> "il y a ${d.toMinutes()} min"
        d.toHours() < 24 -> "il y a ${d.toHours()} h"
        else -> "le ${JourHeure.format(instant.atZone(FuseauCesson))}"
    }
}

/** « de 23h30 » le jour même, « du 14 nov. à 23h30 » sinon : à placer après « Mesure ». */
fun formaterDeHeure(instant: Instant, maintenant: Instant): String {
    val h = formaterHeure(instant, maintenant)
    return if (h.first().isDigit() && h.contains(' ')) "du $h" else "de $h"
}

/** « 23h30 » le jour même, « 14 nov. à 23h30 » sinon. */
fun formaterHeure(instant: Instant, maintenant: Instant): String {
    val z = instant.atZone(FuseauCesson)
    val memeJour = z.toLocalDate() == maintenant.atZone(FuseauCesson).toLocalDate()
    return if (memeJour) Heure.format(z) else JourHeure.format(z)
}
