package com.yogistudio.cesson.eau

import com.yogistudio.cesson.core.donnees.DepotModules
import com.yogistudio.cesson.core.donnees.EtatModule
import com.yogistudio.cesson.core.modele.Horodatage
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Miroir de DonneesUsagesEau (backend/cesson/collecteurs/usages_eau.py). */
@Serializable
data class DonneesUsagesEau(
    val niveau: NiveauSecheresse = NiveauSecheresse.AUCUNE,
    val zone: String? = null,
    val depuis: Horodatage? = null,
    @SerialName("jusqu_au") val jusquAu: Horodatage? = null,
    val arrete: String? = null,
    val usages: List<UsageEau> = emptyList(),
    val consignes: List<ConsigneEau> = emptyList(),
)

@Serializable
enum class NiveauSecheresse {
    @SerialName("aucune") AUCUNE,
    @SerialName("vigilance") VIGILANCE,
    @SerialName("alerte") ALERTE,
    @SerialName("alerte_renforcee") ALERTE_RENFORCEE,
    @SerialName("crise") CRISE,
}

@Serializable
data class UsageEau(val thematique: String, val nom: String, val regle: String)

@Serializable
data class ConsigneEau(val titre: String, val date: Horodatage, val lien: String)

const val MODULE_USAGES_EAU = "usages_eau"

fun DepotModules.usagesEau(): Flow<EtatModule<DonneesUsagesEau>> = suivre(MODULE_USAGES_EAU, DonneesUsagesEau.serializer())

fun NiveauSecheresse.libelle(): String = when (this) {
    NiveauSecheresse.AUCUNE -> "Aucune restriction"
    NiveauSecheresse.VIGILANCE -> "Vigilance sécheresse"
    NiveauSecheresse.ALERTE -> "Alerte sécheresse"
    NiveauSecheresse.ALERTE_RENFORCEE -> "Alerte renforcée"
    NiveauSecheresse.CRISE -> "Crise sécheresse"
}

/** Usages regroupés par thème, dans l'ordre d'apparition : « Arroser », « Nettoyer »… */
fun parTheme(usages: List<UsageEau>): List<Pair<String, List<UsageEau>>> =
    usages.groupBy { it.thematique }.toList()
