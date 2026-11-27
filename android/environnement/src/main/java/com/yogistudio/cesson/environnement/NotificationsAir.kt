package com.yogistudio.cesson.environnement

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

/*
 * Décisions des notifications « air », calculées sur le téléphone à partir des fichiers en cache :
 * aucun serveur ne sait qui est prévenu de quoi. L'app (rappels/AlertesAir.kt) se réveille aux
 * heures de contrôle, relit les fichiers et envoie ce que ces fonctions lui disent d'envoyer.
 */

private val Paris: ZoneId = ZoneId.of("Europe/Paris")

/**
 * Heures de contrôle, dix minutes après les collectes du serveur (épisodes à 7h20, 11h20, 16h20 et 18h20 ;
 * indice et pollens à 16h20 et 18h20). Rien la nuit : une alerte de demain arrive la veille en fin d'après-midi,
 * celle du jour au plus tard à 7h30.
 */
val CONTROLES_AIR: List<LocalTime> = listOf(LocalTime.of(7, 30), LocalTime.of(11, 30), LocalTime.of(16, 30), LocalTime.of(18, 30))

/**
 * Vigilance météo : une vigilance orange peut tomber à toute heure. Quand son alerte est active, on contrôle en plus
 * chaque heure de 7h35 à 22h35, juste après la collecte du serveur (toutes les 15 min, à la 31e minute notamment).
 */
val CONTROLES_VIGILANCE: List<LocalTime> = (7..22).map { LocalTime.of(it, 35) }

/** Prochaine heure de contrôle strictement après [maintenant], parmi [creneaux]. */
fun prochainControleAir(maintenant: Instant, creneaux: List<LocalTime> = CONTROLES_AIR): Instant {
    val tries = creneaux.sorted()
    val z = maintenant.atZone(Paris)
    val aujourdhui = tries.map { z.toLocalDate().atTime(it).atZone(Paris).toInstant() }.firstOrNull { it.isAfter(maintenant) }
    return aujourdhui ?: z.toLocalDate().plusDays(1).atTime(tries.first()).atZone(Paris).toInstant()
}

/** Seuils proposés pour l'alerte pollens, avec leur libellé. */
val SEUILS_POLLENS: List<Int> = listOf(3, 4, 5)

/** Seuils proposés pour l'alerte qualité de l'air : dégradé (par défaut), mauvais, très mauvais. */
val SEUILS_QUALITE_AIR: List<Int> = listOf(3, 4, 5)

data class NotificationAir(val cle: String, val titre: String, val texte: String)

private fun quand(d: LocalDate, aujourdhui: LocalDate) = if (d == aujourdhui) "aujourd'hui" else "demain"

/**
 * Pollens d'aujourd'hui et de demain : une notification par jour qui atteint le seuil, ou dès qu'Air Breizh
 * déclenche son alerte pollens. Une seule fois par jour concerné, même si le niveau monte encore.
 */
fun notificationsPollens(d: DonneesPollens, aujourdhui: LocalDate, seuil: Int, dejaEnvoyees: Set<String>): List<NotificationAir> =
    d.jours.filter { !it.date.isBefore(aujourdhui) && it.date.isBefore(aujourdhui.plusDays(2)) }
        .filter { it.code in seuil..6 || it.alerte }
        .map { j ->
            val especes = j.nomsResponsables()
            NotificationAir(
                cle = "pollens:${j.date}",
                titre = (if (j.alerte) "Alerte pollens" else "Pollens : risque ${j.libelle.lowercase(Locale.FRANCE)}") +
                    " ${quand(j.date, aujourdhui)} à ${d.commune}",
                texte = buildString {
                    append("Indice ${j.libelle.lowercase(Locale.FRANCE)}")
                    if (especes.isNotEmpty()) append(", surtout ${especes.joinToString(", ").lowercase(Locale.FRANCE)}")
                    append(". Source : Atmo France / AASQA.")
                },
            )
        }
        .filter { it.cle !in dejaEnvoyees }

/**
 * Indice ATMO d'aujourd'hui et de demain, dès [seuil] (3 dégradé, 4 mauvais, 5 très mauvais). On prévient de nouveau
 * si l'indice s'aggrave pour un jour déjà signalé (la clé porte le niveau), jamais s'il s'améliore.
 */
fun notificationsQualiteAir(d: DonneesQualiteAir, aujourdhui: LocalDate, seuil: Int, dejaEnvoyees: Set<String>): List<NotificationAir> =
    d.jours.filter { !it.date.isBefore(aujourdhui) && it.date.isBefore(aujourdhui.plusDays(2)) }
        .filter { it.code in seuil..6 }
        .filter { j ->
            val dejaSignale = dejaEnvoyees.filter { it.startsWith("air:${j.date}:") }
                .mapNotNull { it.substringAfterLast(":").toIntOrNull() }.maxOrNull() ?: 0
            j.code > dejaSignale
        }
        .map { j ->
            val niveau = libelleAtmo(j.code).lowercase(Locale.FRANCE)
            val cause = j.responsables.joinToString(" et ") { nomPolluant(it) }
            NotificationAir(
                cle = "air:${j.date}:${j.code}",
                titre = "Qualité de l'air : indice $niveau ${quand(j.date, aujourdhui)}",
                texte = buildString {
                    append("Indice ATMO $niveau pour ${d.zone}")
                    if (cause.isNotEmpty()) append(", dû à $cause")
                    append(". Source : Atmo France / Air Breizh.")
                },
            )
        }

/**
 * Procédures préfectorales d'aujourd'hui et de demain. On prévient de nouveau si la procédure s'aggrave
 * (information, puis alerte) : la clé porte l'état.
 */
fun notificationsEpisodes(d: DonneesEpisodes, aujourdhui: LocalDate, dejaEnvoyees: Set<String>): List<NotificationAir> =
    d.aVenir(aujourdhui).map { j ->
        NotificationAir(
            cle = "episode:${j.date}:${j.etat.name.lowercase(Locale.ROOT)}",
            titre = "Pollution ${j.polluantsVises()} ${quand(j.date, aujourdhui)} en ${d.zone}",
            texte = "${j.etat.libelle()}. ${j.etat.definition()}",
        )
    }.filter { it.cle !in dejaEnvoyees }

/** Les clés de plus d'une semaine ne servent plus : on ne les garde pas indéfiniment. */
fun clesAirAGarder(cles: Set<String>, aujourdhui: LocalDate): Set<String> = cles.filterTo(mutableSetOf()) { cle ->
    val date = cle.split(":").getOrNull(1)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    date != null && !date.isBefore(aujourdhui.minusDays(7))
}
