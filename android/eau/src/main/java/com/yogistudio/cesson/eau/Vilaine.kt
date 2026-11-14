package com.yogistudio.cesson.eau

import com.yogistudio.cesson.core.donnees.DepotModules
import com.yogistudio.cesson.core.donnees.EtatModule
import com.yogistudio.cesson.core.modele.Horodatage
import com.yogistudio.cesson.core.modele.Lieu
import com.yogistudio.cesson.core.modele.Mesure
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.Locale

/** Miroir de DonneesVilaine (backend/cesson/collecteurs/vilaine.py). */
@Serializable
data class DonneesVilaine(
    val station: Lieu,
    val mesure: Mesure,
    @SerialName("serie_24h") val serie24h: List<PointSerie> = emptyList(),
)

@Serializable
data class PointSerie(val horodatage: Horodatage, val valeur: Double)

const val MODULE_VILAINE = "vilaine"

fun DepotModules.vilaine(): Flow<EtatModule<DonneesVilaine>> = suivre(MODULE_VILAINE, DonneesVilaine.serializer())

/** Repère historique affiché sur la carte : crue du 26 octobre 1966 à Pont Briand. */
const val CRUE_1966_M = 3.73

/** Hauteur en mètres, à la française : « 0,22 m ». Hub'Eau publie la hauteur en millimètres. */
fun formaterHauteur(mesure: Mesure): String {
    val metres = when (mesure.unite) {
        "mm" -> mesure.valeur / 1000.0
        "cm" -> mesure.valeur / 100.0
        else -> mesure.valeur
    }
    return String.format(Locale.FRANCE, "%.2f m", metres)
}
