package com.yogistudio.cesson.familles

import com.yogistudio.cesson.core.donnees.DepotModules
import com.yogistudio.cesson.core.donnees.EtatModule
import com.yogistudio.cesson.core.modele.JourCalendaire
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalTime

/** Miroir de DonneesFamilles (backend/cesson/collecteurs/familles.py). */
@Serializable
data class DonneesFamilles(
    val actualites: List<ActuFamille> = emptyList(),
    val menus: List<MenuDuJour> = emptyList(),
    val restaurants: String? = null,
    val menu_pdf: String? = null,
)

@Serializable
data class ActuFamille(
    val id: String,
    val titre: String,
    val resume: String? = null,
    val categories: List<String> = emptyList(),
    val lien: String,
    val du: JourCalendaire? = null,
    val au: JourCalendaire? = null,
)

@Serializable
data class MenuDuJour(
    val jour: JourCalendaire,
    val hors_doeuvre: List<String> = emptyList(),
    val plat: List<String> = emptyList(),
    val laitier: List<String> = emptyList(),
    val dessert: List<String> = emptyList(),
    val gouter: List<String> = emptyList(),
    val note: String? = null,
) {
    /** Rubriques non vides, dans l'ordre du repas. */
    fun rubriques(): List<Pair<String, List<String>>> = listOf(
        "Entrée" to hors_doeuvre, "Plat" to plat, "Laitage" to laitier, "Dessert" to dessert, "Goûter" to gouter,
    ).filter { it.second.isNotEmpty() }
}

const val MODULE_FAMILLES = "familles"

fun DepotModules.familles(): Flow<EtatModule<DonneesFamilles>> = suivre(MODULE_FAMILLES, DonneesFamilles.serializer())

/** Après le déjeuner, on montre le menu du prochain jour d'école. */
val FIN_DU_DEJEUNER: LocalTime = LocalTime.of(14, 0)

/** Jours affichés : la semaine scolaire à venir (5 jours de menu au plus), à partir d'aujourd'hui. */
fun joursDeMenu(d: DonneesFamilles, aujourdhui: LocalDate): List<MenuDuJour> =
    d.menus.filter { !it.jour.isBefore(aujourdhui) }.take(5)

/** Le jour ouvert par défaut : aujourd'hui jusqu'à 14h, sinon le suivant. */
fun jourParDefaut(jours: List<MenuDuJour>, aujourdhui: LocalDate, heure: LocalTime): Int {
    val i = jours.indexOfFirst { it.jour == aujourdhui && heure.isBefore(FIN_DU_DEJEUNER) }
    if (i >= 0) return i
    return jours.indexOfFirst { it.jour.isAfter(aujourdhui) }.coerceAtLeast(0)
}
