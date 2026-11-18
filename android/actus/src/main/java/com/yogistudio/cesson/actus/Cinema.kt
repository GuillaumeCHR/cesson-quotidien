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
    val fiche: Fiche? = null,
)

/** Miroir de Fiche (backend/cesson/collecteurs/fiches_films.py) : TMDB et Wikidata, facultative. */
@Serializable
data class Fiche(
    val tmdb: Int,
    val titre: String,
    @SerialName("titre_original") val titreOriginal: String? = null,
    val annee: Int? = null,
    val realisateurs: List<String> = emptyList(),
    val distribution: List<String> = emptyList(),
    @SerialName("duree_min") val dureeMin: Int? = null,
    val genres: List<String> = emptyList(),
    val synopsis: String? = null,
    val resume: String? = null,
    val classification: String? = null,
    val affiche: String? = null,
    val wikipedia: String? = null,
    val allocine: String? = null,
    @SerialName("page_tmdb") val pageTmdb: String,
)

/** « 1h54 », « 52 min ». */
fun duree(minutes: Int): String = if (minutes < 60) "$minutes min" else "${minutes / 60}h%02d".format(minutes % 60)

/** Ligne sous le titre : « Martin Scorsese · 1h54 · Crime ». */
fun ligneFiche(f: Fiche): String = listOfNotNull(
    f.realisateurs.firstOrNull(), f.dureeMin?.let(::duree), f.genres.firstOrNull(),
).joinToString(" · ")

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
