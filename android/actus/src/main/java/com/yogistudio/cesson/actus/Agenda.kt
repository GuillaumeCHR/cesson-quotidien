package com.yogistudio.cesson.actus

import com.yogistudio.cesson.core.donnees.DepotModules
import com.yogistudio.cesson.core.donnees.EtatModule
import com.yogistudio.cesson.core.modele.JourCalendaire
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Miroir de DonneesAgenda (backend/cesson/collecteurs/agenda.py). */
@Serializable
data class DonneesAgenda(val evenements: List<Evenement> = emptyList())

@Serializable
data class Evenement(
    val id: String,
    val titre: String,
    val categorie: String? = null,
    val du: JourCalendaire,
    val au: JourCalendaire,
    val horaires: String? = null,
    val lieu: String? = null,
    val conditions: String? = null,
    val resume: String? = null,
    val lien: String,
    val theme: String? = null,
    val saison: Boolean = false,
    val famille: String = "autre",
)

const val MODULE_AGENDA = "agenda"

fun DepotModules.agenda(): Flow<EtatModule<DonneesAgenda>> = suivre(MODULE_AGENDA, DonneesAgenda.serializer())

/** Filtres de l'agenda (hors saison culturelle, qui a sa carte) : famille (null = tout) et libellé. */
val FILTRES_AGENDA = listOf(null to "Tout", "associations" to "Associations", "culture" to "Culture libre",
    "sport" to "Sport et Glaz Arena", "mediatheque" to "Médiathèque")

/** Événements de l'agenda encore en cours ou à venir, hors saison culturelle, filtrés par famille, groupés par jour. */
fun agendaParJour(d: DonneesAgenda, famille: String?, aujourdhui: LocalDate): List<Pair<LocalDate, List<Evenement>>> =
    grouper(d.evenements.filter { !it.saison && (famille == null || it.famille == famille) }, aujourdhui)

/** Spectacles de la saison culturelle à venir, groupés par jour. */
fun saisonParJour(d: DonneesAgenda, aujourdhui: LocalDate): List<Pair<LocalDate, List<Evenement>>> =
    grouper(d.evenements.filter { it.saison }, aujourdhui)

private fun grouper(evenements: List<Evenement>, aujourdhui: LocalDate): List<Pair<LocalDate, List<Evenement>>> =
    evenements
        .filter { !it.au.isBefore(aujourdhui) }
        .groupBy { maxOf(it.du, aujourdhui) }
        .toSortedMap()
        .toList()

/** « Aujourd'hui », « Demain », « Jeudi 19 novembre ». */
fun libelleJour(j: LocalDate, aujourdhui: LocalDate): String = when (j) {
    aujourdhui -> "Aujourd'hui"
    aujourdhui.plusDays(1) -> "Demain"
    else -> j.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.FRANCE).replaceFirstChar { it.uppercase() } +
        " ${j.dayOfMonth} ${j.month.getDisplayName(TextStyle.FULL, Locale.FRANCE)}"
}

/** Pour un événement sur plusieurs jours : « jusqu'au 18 déc. ». */
fun libelleJusquau(e: Evenement): String? =
    if (e.au == e.du) null else "jusqu'au ${e.au.dayOfMonth} ${e.au.month.getDisplayName(TextStyle.SHORT, Locale.FRANCE)}"
