package com.yogistudio.cesson.transport

import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.Instant
import kotlin.math.abs

/**
 * Alerte de passage armée sur « Mon arrêt de bus » : prévenir quand le bus visé est à [delaiMin] minutes.
 * Gardée sur le téléphone ; seul le nom de l'arrêt part chez STAR, comme pour la carte.
 */
@Serializable
data class AlerteBus(
    val arret: String,
    val ligne: String,
    val destination: String,
    /** Heure de passage du bus visé (secondes epoch), mise à jour au fil du temps réel. */
    val heureVisee: Long,
    val delaiMin: Int,
)

const val DELAI_ALERTE_DEFAUT = 5
val DELAIS_ALERTE = 2..15

/** Bus à viser pour une direction : le premier qui passe dans plus de [delaiMin] minutes. */
fun departAViser(departs: List<Depart>, ligne: String, destination: String, delaiMin: Int, maintenant: Instant): Depart? =
    departsUtiles(departs, maintenant)
        .filter { it.ligne == ligne && it.destination == destination }
        .firstOrNull { Duration.between(maintenant, it.heure).toMinutes() >= delaiMin }

/**
 * Retrouve le bus visé dans de nouveaux horaires : même ligne et direction, l'heure la plus proche de celle
 * visée (un bus en retard reste le même bus ; une avance de 10 min ou un retard de 30 min au plus).
 */
fun retrouver(departs: List<Depart>, alerte: AlerteBus): Depart? {
    val visee = Instant.ofEpochSecond(alerte.heureVisee)
    return departs
        .filter { it.ligne == alerte.ligne && it.destination == alerte.destination }
        .filter { !it.heure.isBefore(visee.minusSeconds(600)) && !it.heure.isAfter(visee.plusSeconds(1800)) }
        .minByOrNull { abs(Duration.between(visee, it.heure).seconds) }
}

sealed interface Decision {
    /** Le bus est à [minutes] minutes ou moins : on prévient, l'alerte est consommée. */
    data class Prevenir(val depart: Depart, val minutes: Long) : Decision

    /** Revenir à [quand], en visant désormais [heure] (le temps réel a pu bouger). */
    data class Revoir(val quand: Instant, val heure: Instant) : Decision

    /** Le bus a disparu des horaires et son heure est passée : on abandonne sans bruit. */
    data object Abandonner : Decision
}

/** Le temps réel est relu au moins toutes les 10 min, puis juste avant l'heure de l'alerte. */
private val RELECTURE_MAX: Duration = Duration.ofMinutes(10)
private val MARGE_REVEIL: Duration = Duration.ofSeconds(90)

fun decider(alerte: AlerteBus, departs: List<Depart>, maintenant: Instant): Decision {
    val bus = retrouver(departs, alerte)
    val visee = Instant.ofEpochSecond(alerte.heureVisee)
    if (bus == null) {
        return if (maintenant.isAfter(visee.plusSeconds(120))) Decision.Abandonner
        else Decision.Revoir(maintenant.plusSeconds(60), visee)
    }
    val reste = Duration.between(maintenant, bus.heure)
    val delai = Duration.ofMinutes(alerte.delaiMin.toLong())
    if (reste <= delai.plusSeconds(30)) return Decision.Prevenir(bus, maxOf(0, (reste.seconds + 30) / 60))
    // Réveil un peu avant l'heure de l'alerte (Android peut décaler un réveil), sans dépasser 10 min d'attente.
    val reveil = minOf(bus.heure.minus(delai).minus(MARGE_REVEIL), maintenant.plus(RELECTURE_MAX))
    return Decision.Revoir(maxOf(reveil, maintenant.plusSeconds(30)), bus.heure)
}

/** « Le C6 vers Cesson-Viasilva passe dans 5 min » ; « … arrive » à moins d'une minute. */
fun texteAlerte(ligne: String, destination: String, minutes: Long): String =
    if (minutes < 1) "Le $ligne vers $destination arrive" else "Le $ligne vers $destination passe dans $minutes min"
