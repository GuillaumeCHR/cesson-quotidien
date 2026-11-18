package com.yogistudio.cesson.transport

import com.yogistudio.cesson.core.modele.JsonCesson
import com.yogistudio.cesson.core.ui.FuseauCesson
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * Train pris régulièrement (« le 7h08 vers Rennes, du lundi au vendredi ») : l'app prévient s'il est supprimé ou
 * en retard, rien sinon. Reconnu chaque jour par son heure de passage à la halte et sa direction (le numéro du TER
 * peut changer d'un jour à l'autre). Tout reste sur le téléphone.
 */
@Serializable
data class TrainFavori(
    val heure: String, // « 07:08 », heure de Paris
    val direction: String, // « rennes », « est »
    val destination: String,
    val jours: List<Int> = JOURS_OUVRES, // 1 = lundi … 7 = dimanche
)

val JOURS_OUVRES = listOf(1, 2, 3, 4, 5)
const val RETARD_SIGNALE_MIN = 5
/** Surveillance : de 60 min avant le départ prévu jusqu'au départ, toutes les 5 min. */
val DEBUT_SURVEILLANCE: Duration = Duration.ofMinutes(60)
val PAS_SURVEILLANCE: Duration = Duration.ofMinutes(5)

private val serialiseurFavoris = ListSerializer(TrainFavori.serializer())

fun lireTrainsFavoris(json: String?): List<TrainFavori> =
    json?.let { runCatching { JsonCesson.decodeFromString(serialiseurFavoris, it) }.getOrNull() }.orEmpty()

fun ecrireTrainsFavoris(favoris: List<TrainFavori>): String = JsonCesson.encodeToString(serialiseurFavoris, favoris)

/** « 7h08 » pour « 07:08 ». */
fun heureLisible(heure: String): String = heure.split(":").let { (h, m) -> "${h.toInt()}h$m" }

fun TrainFavori.estLe(t: DepartTrain): Boolean {
    val local = t.heureTheorique.atZone(FuseauCesson)
    return t.direction == direction && "%02d:%02d".format(local.hour, local.minute) == heure
}

fun TrainFavori.depart(jour: LocalDate): ZonedDateTime = LocalTime.parse(heure).atDate(jour).atZone(FuseauCesson)

/**
 * Prochain départ à surveiller, à partir d'aujourd'hui (sur 8 jours) : un jour choisi, pas férié, où le train
 * circule d'après les horaires publiés (au-delà de la semaine connue, on suppose qu'il circule), et pas encore parti.
 */
fun TrainFavori.prochainDepart(maintenant: Instant, horaires: List<HoraireTrain>, estFerie: (LocalDate) -> Boolean): ZonedDateTime? {
    val aujourdhui = maintenant.atZone(FuseauCesson).toLocalDate()
    val connus = horaires.firstOrNull { it.heure == heure && it.direction == direction }?.dates
    val finConnue = horaires.flatMap { it.dates }.maxOrNull()
    return (0L..8L).asSequence().map { aujourdhui.plusDays(it) }
        .filter { it.dayOfWeek.value in jours && !estFerie(it) }
        .filter { j -> connus == null || j in connus || (finConnue != null && j.isAfter(finConnue)) }
        .map { depart(it) }
        .firstOrNull { it.toInstant().isAfter(maintenant) }
}

/** Ce qu'il faut dire d'un train favori, ou null si tout va bien (ou si l'information n'est pas encore là). */
data class AlerteTrain(val cle: String, val titre: String, val texte: String)

fun alerteTrain(f: TrainFavori, jour: LocalDate, t: DepartTrain, deja: Set<String>): AlerteTrain? {
    val base = "$jour|${f.heure}|${f.direction}"
    val heure = heureLisible(f.heure)
    return when {
        t.statut == StatutTrain.SUPPRIME -> AlerteTrain("$base|supprime", "Train de $heure supprimé",
            "Le TER de $heure vers ${t.destination} ne circule pas aujourd'hui (SNCF, temps réel).")
        t.statut == StatutTrain.RETARD && (t.retardMin ?: 0) >= RETARD_SIGNALE_MIN -> {
            val retard = t.retardMin ?: 0
            // Une nouvelle alerte seulement si le retard s'aggrave d'au moins 5 min depuis la dernière.
            val dernier = deja.filter { it.startsWith("$base|retard:") }.maxOfOrNull { it.substringAfterLast(":").toInt() }
            if (dernier != null && retard < dernier + RETARD_SIGNALE_MIN) return null
            val reel = t.heureReelle?.atZone(FuseauCesson)?.let { "${it.hour}h%02d".format(it.minute) }
            AlerteTrain("$base|retard:$retard", "Train de $heure : +$retard min",
                "Le TER de $heure vers ${t.destination} part ${reel?.let { "vers $it" } ?: "en retard"} (SNCF, temps réel).")
        }
        else -> null
    }.takeIf { it == null || it.cle !in deja }
}

/** Libellé des jours : « du lundi au vendredi », « lundi, mercredi », « tous les jours ». */
fun libelleJours(jours: List<Int>): String {
    val tries = jours.distinct().sorted()
    val nom = { j: Int -> DayOfWeek.of(j).getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.FRENCH) }
    return when {
        tries.size == 7 -> "tous les jours"
        tries.isEmpty() -> "aucun jour"
        tries.size >= 3 && tries.last() - tries.first() == tries.size - 1 -> "du ${nom(tries.first())} au ${nom(tries.last())}"
        else -> tries.joinToString(", ") { nom(it) }
    }
}
