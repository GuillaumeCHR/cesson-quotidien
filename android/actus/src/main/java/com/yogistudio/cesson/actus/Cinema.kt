package com.yogistudio.cesson.actus

import com.yogistudio.cesson.core.donnees.DepotModules
import com.yogistudio.cesson.core.donnees.EtatModule
import com.yogistudio.cesson.core.modele.Horodatage
import com.yogistudio.cesson.core.ui.FuseauCesson
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate

/** Miroir de DonneesCinema (backend/cesson/collecteurs/cinema.py). */
@Serializable
data class DonneesCinema(
    val salle: String,
    val adresse: String? = null,
    val films: List<Film> = emptyList(),
    val annonces: List<String> = emptyList(),
    val tarifs: String? = null,
    val grille: String,
)

@Serializable
data class Film(
    val titre: String,
    val vo: Boolean = false,
    @SerialName("sortie_nationale") val sortieNationale: Boolean = false,
    val seances: List<Seance> = emptyList(),
)

@Serializable
data class Seance(val debut: Horodatage, val tarif: String? = null, val note: String? = null)

const val MODULE_CINEMA = "cinema"

fun DepotModules.cinema(): Flow<EtatModule<DonneesCinema>> = suivre(MODULE_CINEMA, DonneesCinema.serializer())

/** Séances d'un jour, film par film, dans l'ordre de l'horaire. */
fun seancesDuJour(d: DonneesCinema, jour: LocalDate, maintenant: Instant): List<Pair<Film, List<Seance>>> =
    d.films.mapNotNull { f ->
        f.seances.filter { it.debut.atZone(FuseauCesson).toLocalDate() == jour && !it.debut.isBefore(maintenant.minusSeconds(15 * 60)) }
            .takeIf { it.isNotEmpty() }?.let { f to it }
    }.sortedBy { it.second.first().debut }

/** Jours qui ont au moins une séance à venir, à partir d'aujourd'hui. */
fun joursDeSeance(d: DonneesCinema, maintenant: Instant): List<LocalDate> {
    val aujourdhui = maintenant.atZone(FuseauCesson).toLocalDate()
    return d.films.flatMap { f -> f.seances.map { it.debut.atZone(FuseauCesson).toLocalDate() } }
        .filter { !it.isBefore(aujourdhui) }.distinct().sorted()
        .filter { j -> seancesDuJour(d, j, maintenant).isNotEmpty() }
}
