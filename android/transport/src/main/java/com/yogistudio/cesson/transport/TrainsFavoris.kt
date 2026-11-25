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
/**
 * Surveillance : de 60 min avant le départ prévu jusqu'au départ effectif, toutes les 5 min. Un train en retard ou
 * supprimé reste surveillé après son heure prévue, tant que le serveur l'affiche, au plus 90 min : un retard qui
 * s'aggrave ou une suppression annoncée tard sont ainsi signalés.
 */
val DEBUT_SURVEILLANCE: Duration = Duration.ofMinutes(60)
val PAS_SURVEILLANCE: Duration = Duration.ofMinutes(5)
val SURVEILLANCE_MAX_APRES: Duration = Duration.ofMinutes(90)

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

/** Départ d'un train favori en cours de surveillance, avec le train publié par le serveur qui lui correspond. */
data class DepartSurveille(val prevu: ZonedDateTime, val fin: Instant, val publie: DepartTrain?)

/**
 * Fin de surveillance d'un départ prévu à [prevu] : la fin d'affichage du train publié (départ réel d'un train en
 * retard, départ du suivant pour un train supprimé), sinon 2 min après l'heure prévue ; jamais plus de 90 min après.
 */
fun finSurveillance(prevu: Instant, publie: DepartTrain?): Instant {
    val fin = publie?.let { finAffichage(it.visibleJusquA, it.heureReelle, it.heureTheorique) } ?: prevu.plusSeconds(120)
    return minOf(maxOf(fin, prevu.plusSeconds(120)), prevu.plus(SURVEILLANCE_MAX_APRES))
}

/** Le départ du favori à surveiller à [maintenant] (une heure avant son horaire, jusqu'au départ effectif), ou null. */
fun TrainFavori.departSurveille(
    maintenant: Instant, horaires: List<HoraireTrain>, departs: List<DepartTrain>, estFerie: (LocalDate) -> Boolean,
): DepartSurveille? {
    // Une seconde de plus : prochainDepart ne garde que les départs strictement après l'instant donné.
    val prevu = prochainDepart(maintenant.minus(SURVEILLANCE_MAX_APRES).minusSeconds(1), horaires, estFerie) ?: return null
    val jour = prevu.toLocalDate()
    val publie = departs.firstOrNull { estLe(it) && it.heureTheorique.atZone(FuseauCesson).toLocalDate() == jour }
    val fin = finSurveillance(prevu.toInstant(), publie)
    val enCours = !maintenant.isBefore(prevu.toInstant().minus(DEBUT_SURVEILLANCE)) && !maintenant.isAfter(fin)
    return if (enCours) DepartSurveille(prevu, fin, publie) else null
}

/**
 * Prochain réveil des alertes : dans 5 min si un favori est en cours de surveillance, sinon une heure avant le
 * prochain départ d'un favori ; null s'il n'y a rien à surveiller.
 */
fun prochainReveil(
    favoris: List<TrainFavori>, maintenant: Instant, horaires: List<HoraireTrain>, departs: List<DepartTrain>,
    estFerie: (LocalDate) -> Boolean,
): Instant? {
    if (favoris.any { it.departSurveille(maintenant, horaires, departs, estFerie) != null }) return maintenant.plus(PAS_SURVEILLANCE)
    val prochain = favoris.mapNotNull { it.prochainDepart(maintenant, horaires, estFerie) }.minOrNull() ?: return null
    return maxOf(prochain.toInstant().minus(DEBUT_SURVEILLANCE), maintenant)
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
