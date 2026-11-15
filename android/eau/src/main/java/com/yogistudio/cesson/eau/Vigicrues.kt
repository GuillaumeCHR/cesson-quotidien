package com.yogistudio.cesson.eau

import com.yogistudio.cesson.core.donnees.DepotModules
import com.yogistudio.cesson.core.donnees.EtatModule
import com.yogistudio.cesson.core.modele.Alerte
import com.yogistudio.cesson.core.modele.Horodatage
import com.yogistudio.cesson.core.modele.Lieu
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Miroir de DonneesVigicrues (backend/cesson/collecteurs/vigicrues.py). */
@Serializable
data class DonneesVigicrues(
    val troncon: Lieu,
    val vigilance: Vigilance = Vigilance.INCONNUE,
    val depuis: Horodatage,
    val alerte: Alerte? = null,
)

/** Dans l'ordre de gravité. INCONNUE couvre une valeur que cette version de l'app ne connaît pas. */
@Serializable
enum class Vigilance {
    @SerialName("vert") VERT,
    @SerialName("jaune") JAUNE,
    @SerialName("orange") ORANGE,
    @SerialName("rouge") ROUGE,
    INCONNUE,
}

const val MODULE_VIGICRUES = "vigicrues"

fun DepotModules.vigicrues(): Flow<EtatModule<DonneesVigicrues>> =
    suivre(MODULE_VIGICRUES, DonneesVigicrues.serializer())

/** Jaune et au-delà : bandeau en tête de l'accueil, que l'utilisateur ne peut pas masquer. */
val Vigilance.estUneAlerte: Boolean get() = this == Vigilance.JAUNE || this == Vigilance.ORANGE || this == Vigilance.ROUGE

fun EtatModule<DonneesVigicrues>.enAlerte(): Boolean =
    (this as? EtatModule.Disponible)?.fichier?.donnees?.vigilance?.estUneAlerte == true

fun Vigilance.libelleCourt(): String = when (this) {
    Vigilance.VERT -> "Pas de vigilance crues"
    Vigilance.JAUNE -> "Vigilance crues jaune"
    Vigilance.ORANGE -> "Vigilance crues orange"
    Vigilance.ROUGE -> "Vigilance crues rouge"
    Vigilance.INCONNUE -> "Vigilance crues : niveau non reconnu"
}

/** Définitions officielles des niveaux de vigilance crues (Vigicrues). */
fun Vigilance.definition(): String = when (this) {
    Vigilance.VERT -> "Pas de vigilance particulière requise."
    Vigilance.JAUNE -> "Risque de crue avec débordements et dommages localisés, ou de montée rapide et " +
        "dangereuse des eaux. Vigilance particulière pour les activités exposées."
    Vigilance.ORANGE -> "Risque de crue avec débordements importants, susceptibles d'avoir un impact " +
        "significatif sur la vie collective et la sécurité des biens et des personnes."
    Vigilance.ROUGE -> "Risque de crue majeure. Menace directe et généralisée de la sécurité des " +
        "personnes et des biens."
    Vigilance.INCONNUE -> "Consultez Vigicrues pour connaître le niveau en vigueur."
}

const val URL_VIGICRUES = "https://www.vigicrues.gouv.fr/station/J709063002"
