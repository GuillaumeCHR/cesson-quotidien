package com.yogistudio.cesson.pratique

import com.yogistudio.cesson.core.donnees.DepotModules
import com.yogistudio.cesson.core.donnees.EtatModule
import com.yogistudio.cesson.core.modele.Horodatage
import com.yogistudio.cesson.core.modele.Lieu
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.Instant
import java.util.Locale

/** Miroir de DonneesCarburants (backend/cesson/collecteurs/carburants.py). */
@Serializable
data class DonneesCarburants(val stations: List<StationCarburant> = emptyList())

@Serializable
data class StationCarburant(
    val lieu: Lieu,
    @SerialName("automate_24_24") val automate2424: Boolean = false,
    val prix: List<PrixCarburant> = emptyList(),
    val ruptures: List<RuptureCarburant> = emptyList(),
)

@Serializable
data class PrixCarburant(val carburant: String, val libelle: String, val prix: Double, val maj: Horodatage)

@Serializable
data class RuptureCarburant(val carburant: String, val libelle: String, val debut: Horodatage? = null)

const val MODULE_CARBURANTS = "carburants"

fun DepotModules.carburants(): Flow<EtatModule<DonneesCarburants>> =
    suivre(MODULE_CARBURANTS, DonneesCarburants.serializer())

/** Le SP95-E10 est le carburant le plus vendu en France : choix par défaut tant que l'utilisateur n'a rien choisi. */
const val CARBURANT_PAR_DEFAUT = "e10"

/** Au-delà, un prix est affiché avec sa date et n'entre pas dans la comparaison « le moins cher ». */
val ANCIENNETE_MAX: Duration = Duration.ofDays(3)

/** Carburants vendus ou en rupture dans au moins une station, dans l'ordre du backend. */
fun carburantsProposes(d: DonneesCarburants): List<Pair<String, String>> =
    d.stations.flatMap { s -> s.prix.map { it.carburant to it.libelle } + s.ruptures.map { it.carburant to it.libelle } }
        .distinctBy { it.first }

sealed interface LigneCarburant {
    val station: StationCarburant

    data class Prix(
        override val station: StationCarburant,
        val prix: PrixCarburant,
        val ancien: Boolean,
        val moinsCher: Boolean,
    ) : LigneCarburant

    data class Rupture(override val station: StationCarburant, val rupture: RuptureCarburant) : LigneCarburant
}

/**
 * Stations qui vendent ce carburant, du moins cher au plus cher ; les prix anciens après les
 * prix récents, puis les ruptures. Les stations qui ne le vendent pas sont omises.
 */
fun classer(d: DonneesCarburants, carburant: String, maintenant: Instant): List<LigneCarburant> {
    class Releve(val station: StationCarburant, val prix: PrixCarburant, val ancien: Boolean)
    val releves = d.stations.mapNotNull { s ->
        s.prix.firstOrNull { it.carburant == carburant }
            ?.let { Releve(s, it, Duration.between(it.maj, maintenant) > ANCIENNETE_MAX) }
    }
    val recents = releves.filterNot { it.ancien }
    val minimum = recents.minOfOrNull { it.prix.prix }
    // « Le moins cher » n'a de sens que s'il y a au moins deux prix récents à comparer.
    val avecBadge = recents.size >= 2
    val lignes = releves.sortedWith(compareBy<Releve>({ it.ancien }, { it.prix.prix })).map {
        LigneCarburant.Prix(it.station, it.prix, it.ancien, avecBadge && !it.ancien && it.prix.prix == minimum)
    }
    val avecPrix = releves.map { it.station.lieu.id }.toSet()
    val ruptures = d.stations.filter { it.lieu.id !in avecPrix }.mapNotNull { s ->
        s.ruptures.firstOrNull { it.carburant == carburant }?.let { LigneCarburant.Rupture(s, it) }
    }
    return lignes + ruptures
}

/** « 1,918 € » : les stations affichent trois décimales. */
fun formaterPrix(prix: Double): String = String.format(Locale.FRANCE, "%.3f €", prix)
