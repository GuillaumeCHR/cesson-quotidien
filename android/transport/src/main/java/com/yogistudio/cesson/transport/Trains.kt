package com.yogistudio.cesson.transport

import com.yogistudio.cesson.core.donnees.DepotModules
import com.yogistudio.cesson.core.donnees.EtatModule
import com.yogistudio.cesson.core.modele.Horodatage
import com.yogistudio.cesson.core.modele.Lieu
import com.yogistudio.cesson.core.ui.FuseauCesson
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

/** Miroir de DonneesTrains (backend/cesson/collecteurs/trains.py). */
@Serializable
data class DonneesTrains(
    val halte: Lieu,
    val directions: List<DirectionTrain> = emptyList(),
    val departs: List<DepartTrain> = emptyList(),
    @SerialName("temps_reel_disponible") val tempsReelDisponible: Boolean = false,
)

@Serializable
data class DirectionTrain(val id: String, val libelle: String)

@Serializable
data class DepartTrain(
    val ligne: String,
    val destination: String,
    @SerialName("heure_theorique") val heureTheorique: Horodatage,
    @SerialName("heure_reelle") val heureReelle: Horodatage? = null,
    val statut: StatutTrain = StatutTrain.THEORIQUE,
    val numero: String,
    val direction: String,
    @SerialName("retard_min") val retardMin: Int? = null,
)

@Serializable
enum class StatutTrain {
    @SerialName("a_l_heure") A_L_HEURE,
    @SerialName("retard") RETARD,
    @SerialName("supprime") SUPPRIME,
    @SerialName("theorique") THEORIQUE,
}

const val MODULE_TRAINS = "trains"

fun DepotModules.trains(): Flow<EtatModule<DonneesTrains>> = suivre(MODULE_TRAINS, DonneesTrains.serializer())

/**
 * Départs encore à venir aujourd'hui (à Cesson) dans une direction ; un train supprimé reste
 * affiché, barré. Le jour est celui de l'horaire prévu : un train de 23h55 retardé après minuit
 * reste un train du jour.
 */
fun departsDeLaDirection(d: DonneesTrains, direction: String, maintenant: Instant): List<DepartTrain> {
    val aujourdhui = maintenant.atZone(FuseauCesson).toLocalDate()
    return d.departs.filter { it.direction == direction }
        .filter { it.heureTheorique.atZone(FuseauCesson).toLocalDate() == aujourdhui }
        .filter { (it.heureReelle ?: it.heureTheorique).isAfter(maintenant.minusSeconds(120)) }
        .sortedBy { it.heureTheorique }
}

/** Libellé du statut, toujours en texte : la couleur ne porte jamais seule l'information. */
fun libelleStatut(t: DepartTrain): String = when (t.statut) {
    StatutTrain.A_L_HEURE -> "à l'heure"
    StatutTrain.RETARD -> "+${t.retardMin ?: 0} min"
    StatutTrain.SUPPRIME -> "supprimé"
    StatutTrain.THEORIQUE -> "horaire prévu"
}
