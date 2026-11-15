package com.yogistudio.cesson.pratique

import com.yogistudio.cesson.core.donnees.DepotModules
import com.yogistudio.cesson.core.donnees.EtatModule
import com.yogistudio.cesson.core.modele.Coord
import com.yogistudio.cesson.core.modele.Lieu
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Miroirs de DonneesDefibrillateurs et DonneesBornes (backend/cesson/collecteurs/lieux_pratiques.py). */
@Serializable
data class DonneesDefibrillateurs(val defibrillateurs: List<Defibrillateur> = emptyList())

@Serializable
data class Defibrillateur(
    val lieu: Lieu,
    val exterieur: Boolean = false,
    @SerialName("libre_acces") val libreAcces: Boolean = false,
    val horaires: String? = null,
    val precision: String? = null,
)

@Serializable
data class DonneesBornes(val stations: List<StationRecharge> = emptyList())

@Serializable
data class StationRecharge(
    val lieu: Lieu,
    val enseigne: String? = null,
    val operateur: String? = null,
    val h24: Boolean = false,
    val points: Int,
    @SerialName("puissance_max") val puissanceMax: Double,
    val prises: List<String> = emptyList(),
    val gratuit: Boolean = false,
    val horaires: String? = null,
    val acces: String? = null,
)

const val MODULE_DEFIBRILLATEURS = "defibrillateurs"
const val MODULE_BORNES = "bornes"

fun DepotModules.defibrillateurs(): Flow<EtatModule<DonneesDefibrillateurs>> =
    suivre(MODULE_DEFIBRILLATEURS, DonneesDefibrillateurs.serializer())

fun DepotModules.bornes(): Flow<EtatModule<DonneesBornes>> = suivre(MODULE_BORNES, DonneesBornes.serializer())

/** Distance à vol d'oiseau en mètres (assez précise à l'échelle d'une commune). */
fun distanceM(a: Coord, b: Coord): Double =
    hypot((b.lon - a.lon) * 111_320 * cos(Math.toRadians(a.lat)), (b.lat - a.lat) * 110_540)

/** « 350 m », « 1,2 km ». */
fun formaterDistance(m: Double): String =
    if (m < 1000) "${(m / 10).roundToInt() * 10} m" else String.format(java.util.Locale.FRANCE, "%.1f km", m / 1000)

/** Trie par distance si la position est connue, sinon garde l'ordre du backend. */
fun <T> parProximite(liste: List<T>, position: Coord?, lieu: (T) -> Lieu): List<Pair<T, Double?>> =
    if (position == null) liste.map { it to null }
    else liste.map { it to lieu(it).coord?.let { c -> distanceM(position, c) } }.sortedBy { it.second ?: Double.MAX_VALUE }

/** Accessible à tout moment : dehors et en libre accès. */
val Defibrillateur.toujoursAccessible: Boolean get() = exterieur && libreAcces

/** « 300 kW », « 22 kW », « 7,4 kW ». */
fun formaterPuissance(kw: Double): String =
    if (kw % 1.0 == 0.0 || kw >= 50) "${kw.roundToInt()} kW" else String.format(java.util.Locale.FRANCE, "%.1f kW", kw)
