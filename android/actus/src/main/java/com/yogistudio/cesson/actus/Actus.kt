package com.yogistudio.cesson.actus

import com.yogistudio.cesson.core.donnees.DepotModules
import com.yogistudio.cesson.core.donnees.EtatModule
import com.yogistudio.cesson.core.modele.Horodatage
import com.yogistudio.cesson.core.ui.FuseauCesson
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.format.TextStyle
import java.util.Locale

/** Miroir de DonneesActus (backend/cesson/collecteurs/actus.py). */
@Serializable
data class DonneesActus(val flux: List<FluxActus> = emptyList())

@Serializable
data class FluxActus(val id: String, val nom: String, val articles: List<Article> = emptyList())

@Serializable
data class Article(
    val source: String,
    val titre: String,
    val date: Horodatage,
    val lien: String,
    val extrait: String? = null,
    @SerialName("concerne_cesson") val concerneCesson: Boolean = false,
)

const val MODULE_ACTUS = "actus"
const val FLUX_VILLE = "ville"

fun DepotModules.actus(): Flow<EtatModule<DonneesActus>> = suivre(MODULE_ACTUS, DonneesActus.serializer())

/** « Aujourd'hui », « Hier », « 12 nov. », « 28 sept. 2025 » (année seulement si ce n'est pas l'année en cours). */
fun formaterDateArticle(date: Instant, maintenant: Instant): String {
    val j = date.atZone(FuseauCesson).toLocalDate()
    val auj = maintenant.atZone(FuseauCesson).toLocalDate()
    return when {
        j == auj -> "Aujourd'hui"
        j == auj.minusDays(1) -> "Hier"
        else -> "${j.dayOfMonth} ${j.month.getDisplayName(TextStyle.SHORT, Locale.FRANCE)}" + if (j.year != auj.year) " ${j.year}" else ""
    }
}

/** Articles des 7 derniers jours : signalés comme nouveaux. */
fun estRecent(a: Article, maintenant: Instant): Boolean = a.date.isAfter(maintenant.minusSeconds(7 * 86_400))
