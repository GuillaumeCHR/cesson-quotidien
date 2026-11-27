package com.yogistudio.cesson.familles

import com.yogistudio.cesson.core.donnees.DepotModules
import com.yogistudio.cesson.core.donnees.EtatModule
import com.yogistudio.cesson.core.modele.JourCalendaire
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Miroir de DonneesVacances (backend/cesson/collecteurs/vacances_scolaires.py). */
@Serializable
data class DonneesVacances(val zone: String, val academie: String, val periodes: List<Periode> = emptyList())

@Serializable
data class Periode(
    val nom: String,
    val du: JourCalendaire, // premier jour sans classe
    val reprise: JourCalendaire? = null, // jour de la reprise
    val pont: Boolean = false, // un seul jour sans classe
    val annee_scolaire: String = "",
)

const val MODULE_VACANCES = "vacances_scolaires"

fun DepotModules.vacancesScolaires(): Flow<EtatModule<DonneesVacances>> = suivre(MODULE_VACANCES, DonneesVacances.serializer())

/** Ce qu'il faut savoir aujourd'hui : en vacances (jusqu'à quand) ou les prochaines (dans combien de jours). */
sealed interface SituationScolaire {
    data class EnVacances(val periode: Periode, val joursRestants: Long) : SituationScolaire
    data class AVenir(val periode: Periode, val dansJours: Long) : SituationScolaire
    data object Inconnue : SituationScolaire
}

fun Periode.dernierJour(): LocalDate = if (pont) du else (reprise?.minusDays(1) ?: du)

fun situation(d: DonneesVacances, aujourdhui: LocalDate): SituationScolaire {
    d.periodes.firstOrNull { !aujourdhui.isBefore(it.du) && !aujourdhui.isAfter(it.dernierJour()) }?.let {
        return SituationScolaire.EnVacances(it, ChronoUnit.DAYS.between(aujourdhui, it.dernierJour()))
    }
    return d.periodes.firstOrNull { it.du.isAfter(aujourdhui) }
        ?.let { SituationScolaire.AVenir(it, ChronoUnit.DAYS.between(aujourdhui, it.du)) }
        ?: SituationScolaire.Inconnue
}

private val JOUR = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH)
private val JOUR_COURT = DateTimeFormatter.ofPattern("d MMM", Locale.FRENCH)

/** « mercredi 1er septembre », « lundi 4 janvier ». */
fun jourLong(d: LocalDate): String =
    if (d.dayOfMonth == 1) JOUR.format(d).replaceFirst(" 1 ", " 1er ") else JOUR.format(d)

/**
 * Nom à afficher : l'API écrit « Vacances d'Hiver », « de Printemps », « d'Été » ; en français, la saison prend une
 * minuscule (Noël, la Toussaint et l'Ascension gardent la leur).
 */
val Periode.nomAffiche: String
    get() = nom.replace("d'Hiver", "d'hiver").replace("de Printemps", "de printemps")
        .replace("d'Été", "d'été").replace("d'Automne", "d'automne")

/** Jour de la reprise ; pour un pont (sans reprise publiée), le jour de semaine qui suit. */
fun Periode.jourDeReprise(): LocalDate = reprise ?: generateSequence(dernierJour().plusDays(1)) { it.plusDays(1) }
    .first { it.dayOfWeek.value <= 5 }

/** « du 19 déc. au 3 janv. », « vendredi 7 mai » pour un pont. */
fun Periode.dates(): String = when {
    pont -> JOUR.format(du)
    reprise == null -> "à partir du ${JOUR_COURT.format(du)}"
    else -> "du ${JOUR_COURT.format(du)} au ${JOUR_COURT.format(dernierJour())}"
}

fun dansJours(n: Long): String = when (n) {
    0L -> "aujourd'hui"
    1L -> "demain"
    in 2..13 -> "dans $n jours"
    else -> "dans ${n / 7} semaines"
}
