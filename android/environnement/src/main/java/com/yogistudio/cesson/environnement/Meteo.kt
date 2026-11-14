package com.yogistudio.cesson.environnement

import com.yogistudio.cesson.core.donnees.DepotModules
import com.yogistudio.cesson.core.donnees.EtatModule
import com.yogistudio.cesson.core.modele.Horodatage
import com.yogistudio.cesson.core.modele.JourCalendaire
import com.yogistudio.cesson.core.modele.Lieu
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.Instant
import java.util.Locale
import kotlin.math.roundToInt

/** Miroir de DonneesMeteo (backend/cesson/collecteurs/meteo.py). */
@Serializable
data class DonneesMeteo(
    val lieu: Lieu,
    val actuelle: MeteoActuelle,
    val jours: List<MeteoJour>,
    val heures: List<MeteoHeure>,
)

@Serializable
data class MeteoActuelle(
    val horodatage: Horodatage,
    val temperature: Double,
    val ressentie: Double,
    val code: Int,
    val libelle: String,
    val jour: Boolean,
    @SerialName("vent_kmh") val ventKmh: Double,
    @SerialName("rafales_kmh") val rafalesKmh: Double,
    @SerialName("precipitation_mm") val precipitationMm: Double,
)

@Serializable
data class MeteoHeure(
    val horodatage: Horodatage,
    val temperature: Double,
    val code: Int,
    val libelle: String,
    val jour: Boolean,
    @SerialName("precipitation_mm") val precipitationMm: Double,
)

@Serializable
data class MeteoJour(
    val date: JourCalendaire,
    val min: Double,
    val max: Double,
    val code: Int,
    val libelle: String,
    @SerialName("precipitation_mm") val precipitationMm: Double,
    @SerialName("rafales_max_kmh") val rafalesMaxKmh: Double,
    val lever: Horodatage,
    val coucher: Horodatage,
)

const val MODULE_METEO = "meteo"

fun DepotModules.meteo(): Flow<EtatModule<DonneesMeteo>> = suivre(MODULE_METEO, DonneesMeteo.serializer())

/** Au-delà, les rafales sont mentionnées sur la carte (simple information, pas une vigilance). */
const val SEUIL_RAFALES_KMH = 50.0

fun degres(t: Double) = "${t.roundToInt()}°"

fun millimetres(mm: Double) = String.format(Locale.FRANCE, "%.1f mm", mm)

/**
 * Créneaux affichés : à partir de l'heure en cours (les données peuvent dater de quelques heures),
 * une heure sur deux, six créneaux.
 */
fun prochainsCreneaux(heures: List<MeteoHeure>, maintenant: Instant, nombre: Int = 6, pas: Int = 2): List<MeteoHeure> =
    heures.filter { Duration.between(it.horodatage, maintenant) < Duration.ofHours(1) }
        .filterIndexed { i, _ -> i % pas == 0 }
        .take(nombre)
