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

/**
 * Ce que montre la carte des menus, vacances scolaires comprises :
 * - [jours] : les puces, sans les jours d'avant une coupure de vacances (on ne colle pas « vendredi 18 » et « lundi 4 ») ;
 * - [enCours] : les vacances (ou le pont) d'aujourd'hui, avec [menusPendant] si le PDF donne quand même des menus ;
 * - [ensuite] : des vacances qui commencent juste après les jours affichés, pour l'annoncer sous le menu.
 * Un jour du PDF avec un vrai menu pendant les vacances reste affiché ; une simple mention (« vacances ») est écartée.
 */
data class VueMenus(val jours: List<MenuDuJour>, val enCours: Periode?, val menusPendant: Boolean, val ensuite: Periode?)

fun vueMenus(d: DonneesFamilles, vacances: DonneesVacances?, aujourdhui: LocalDate): VueMenus {
    val periodes = vacances?.periodes.orEmpty()
    fun periodeDe(j: LocalDate) = periodes.firstOrNull { !j.isBefore(it.du) && !j.isAfter(it.dernierJour()) }
    val candidats = d.menus.filter { !it.jour.isBefore(aujourdhui) }.sortedBy { it.jour }
        .filter { periodeDe(it.jour) == null || (it.note == null && it.rubriques().isNotEmpty()) }
    val enCours = periodeDe(aujourdhui)
    val menusPendant = enCours != null && candidats.any { !it.jour.isAfter(enCours.dernierJour()) }
    val premier = candidats.firstOrNull()?.jour
    // Prochaines vacances (pas un simple pont) après le premier jour affiché, hors celles d'aujourd'hui.
    val coupure = premier?.let { p -> periodes.filter { !it.pont && it != enCours && it.du.isAfter(p) }.minByOrNull { it.du } }
    val jours = candidats.filter { coupure == null || it.jour.isBefore(coupure.du) }.take(5)
    val ensuite = coupure?.takeIf { c -> jours.isNotEmpty() && !c.du.isAfter(jours.last().jour.plusDays(7)) }
    return VueMenus(jours, enCours, menusPendant, ensuite)
}

/** Lundi de la semaine de [j]. */
private fun lundi(j: LocalDate): LocalDate = j.minusDays(j.dayOfWeek.value - 1L)

/** Le mois s'ajoute à la puce d'un jour qui n'est pas dans la semaine en cours : « Lun. 4 janv. ». */
fun pucePorteLeMois(jour: LocalDate, aujourdhui: LocalDate): Boolean = lundi(jour) != lundi(aujourdhui)

/** « Pas de cantine jusqu'au dimanche 3 janvier, reprise le lundi 4 janvier. » */
fun texteVacancesEnCours(p: Periode, aujourdhui: LocalDate, menusPendant: Boolean): String {
    val reprise = p.reprise ?: p.dernierJour().plusDays(1)
    val fin = p.dernierJour()
    val debut = if (menusPendant) p.nom else "Pas de cantine"
    val jusqua = if (fin == aujourdhui) "aujourd'hui" else "jusqu'au ${jourLong(fin)}"
    return "$debut $jusqua, reprise le ${jourLong(reprise)}."
}

/** « Puis vacances de Noël à partir du samedi 19 décembre, reprise le lundi 4 janvier. » */
fun texteVacancesEnsuite(p: Periode): String =
    "Puis ${p.nom.replaceFirstChar { it.lowercase() }} à partir du ${jourLong(p.du)}" +
        (p.reprise?.let { ", reprise le ${jourLong(it)}" } ?: "") + "."
