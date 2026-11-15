package com.yogistudio.cesson.transport

import com.yogistudio.cesson.core.donnees.DepotModules
import com.yogistudio.cesson.core.donnees.EtatModule
import com.yogistudio.cesson.core.modele.Horodatage
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Miroir de DonneesCirculation (backend/cesson/collecteurs/circulation.py). */
@Serializable
data class DonneesCirculation(
    @SerialName("mesure_le") val mesureLe: Horodatage,
    @SerialName("rocade_est") val rocadeEst: List<SensRocade> = emptyList(),
    @SerialName("axes_cesson") val axesCesson: List<AxeCesson> = emptyList(),
)

@Serializable
data class SensRocade(
    val id: String,
    val libelle: String,
    val etat: EtatTrafic = EtatTrafic.INCONNU,
    val vitesse: Int? = null,
    val ralentissements: List<RalentissementRocade> = emptyList(),
)

@Serializable
data class RalentissementRocade(val entre: String, val etat: EtatTrafic = EtatTrafic.INCONNU, val vitesse: Int? = null)

@Serializable
data class AxeCesson(val nom: String, val etat: EtatTrafic = EtatTrafic.INCONNU, val vitesse: Int? = null)

@Serializable
enum class EtatTrafic {
    @SerialName("fluide") FLUIDE,
    @SerialName("dense") DENSE,
    @SerialName("sature") SATURE,
    @SerialName("bloque") BLOQUE,
    @SerialName("inconnu") INCONNU,
}

const val MODULE_CIRCULATION = "circulation"

fun DepotModules.circulation(): Flow<EtatModule<DonneesCirculation>> = suivre(MODULE_CIRCULATION, DonneesCirculation.serializer())

fun EtatTrafic.libelle(): String = when (this) {
    EtatTrafic.FLUIDE -> "Fluide"
    EtatTrafic.DENSE -> "Dense"
    EtatTrafic.SATURE -> "Saturé"
    EtatTrafic.BLOQUE -> "Bloqué"
    EtatTrafic.INCONNU -> "Non mesuré"
}

/** Axes de Cesson à signaler : seulement ceux qui ne sont pas fluides (ni inconnus). */
fun axesASignaler(d: DonneesCirculation): List<AxeCesson> =
    d.axesCesson.filter { it.etat == EtatTrafic.DENSE || it.etat == EtatTrafic.SATURE || it.etat == EtatTrafic.BLOQUE }
