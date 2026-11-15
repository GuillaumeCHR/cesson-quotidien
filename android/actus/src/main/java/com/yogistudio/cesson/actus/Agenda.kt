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
)

const val MODULE_AGENDA = "agenda"

fun DepotModules.agenda(): Flow<EtatModule<DonneesAgenda>> = suivre(MODULE_AGENDA, DonneesAgenda.serializer())

/** Filtres de la carte : identifiant de thème (null = tout) et libellé. */
val FILTRES_AGENDA = listOf(null to "Tout", "carre_sevigne" to "Carré Sévigné et Pont des Arts", "mediatheque" to "Médiathèque")

/** Événements encore en cours ou à venir, filtrés par thème, groupés par jour (le jour affiché est le premier à venir). */
fun agendaParJour(d: DonneesAgenda, theme: String?, aujourdhui: LocalDate): List<Pair<LocalDate, List<Evenement>>> =
    d.evenements
        .filter { !it.au.isBefore(aujourdhui) && (theme == null || it.theme == theme) }
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
