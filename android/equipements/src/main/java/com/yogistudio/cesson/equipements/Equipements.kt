package com.yogistudio.cesson.equipements

import com.yogistudio.cesson.core.donnees.DepotModules
import com.yogistudio.cesson.core.donnees.EtatModule
import com.yogistudio.cesson.core.modele.Horodatage
import com.yogistudio.cesson.core.modele.JourCalendaire
import com.yogistudio.cesson.core.modele.Lieu
import com.yogistudio.cesson.core.ui.FuseauCesson
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Miroir de DonneesEquipements (backend/cesson/collecteurs/equipements.py). */
@Serializable
data class DonneesEquipements(val equipements: List<Equipement> = emptyList())

@Serializable
data class Equipement(
    val lieu: Lieu,
    val telephone: String? = null,
    @kotlinx.serialization.SerialName("verifie_le") val verifieLe: JourCalendaire,
    val remarque: String? = null,
    val espaces: List<Espace> = emptyList(),
)

@Serializable
data class Espace(val id: String, val nom: String, val jours: List<JourHoraire> = emptyList(), val remarque: String? = null)

@Serializable
data class JourHoraire(
    val date: JourCalendaire,
    val connu: Boolean,
    val plages: List<Plage> = emptyList(),
    val motif: String? = null,
    val periode: String? = null,
)

@Serializable
data class Plage(val debut: Horodatage, val fin: Horodatage)

const val MODULE_EQUIPEMENTS = "equipements"
const val DECHETERIE = "equipement:decheterie"

fun DepotModules.equipements(): Flow<EtatModule<DonneesEquipements>> =
    suivre(MODULE_EQUIPEMENTS, DonneesEquipements.serializer())

/** Ouvert, fermé ou inconnu à un instant donné. */
sealed interface Ouverture {
    data class Ouvert(val jusqua: Instant) : Ouverture
    /** [prochaine] : prochaine ouverture connue dans les 14 jours publiés. */
    data class Ferme(val prochaine: Instant?, val motif: String?) : Ouverture
    /** Aucun horaire publié pour aujourd'hui : on ne dit pas « fermé ». */
    data object Inconnu : Ouverture
}

fun ouverture(espace: Espace, maintenant: Instant): Ouverture {
    val aujourdhui = maintenant.atZone(FuseauCesson).toLocalDate()
    val jour = espace.jours.firstOrNull { it.date == aujourdhui } ?: return Ouverture.Inconnu
    jour.plages.firstOrNull { !maintenant.isBefore(it.debut) && maintenant.isBefore(it.fin) }
        ?.let { return Ouverture.Ouvert(it.fin) }
    if (!jour.connu) return Ouverture.Inconnu
    val prochaine = espace.jours.filter { !it.date.isBefore(aujourdhui) }
        .flatMap { it.plages }
        .firstOrNull { it.debut.isAfter(maintenant) }
    return Ouverture.Ferme(prochaine?.debut, jour.motif)
}

/** « 16h », « 7h45 » : l'usage local, sans zéro inutile. */
fun formaterHeureCourte(instant: Instant): String {
    val z = instant.atZone(FuseauCesson)
    return if (z.minute == 0) "${z.hour}h" else "${z.hour}h%02d".format(z.minute)
}

/** « à 16h », « demain à 7h45 », « mardi à 14h30 », « le 4 janv. à 10h30 ». */
fun formaterProchaine(instant: Instant, maintenant: Instant): String {
    val jour = instant.atZone(FuseauCesson).toLocalDate()
    val aujourdhui = maintenant.atZone(FuseauCesson).toLocalDate()
    val h = formaterHeureCourte(instant)
    return when {
        jour == aujourdhui -> "à $h"
        jour == aujourdhui.plusDays(1) -> "demain à $h"
        jour.isBefore(aujourdhui.plusDays(7)) -> "${jour.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.FRANCE)} à $h"
        else -> "le ${jour.dayOfMonth} ${jour.month.getDisplayName(TextStyle.SHORT, Locale.FRANCE)} à $h"
    }
}

fun libelleOuverture(o: Ouverture, maintenant: Instant): String = when (o) {
    is Ouverture.Ouvert -> "Ouvert jusqu'à ${formaterHeureCourte(o.jusqua)}"
    is Ouverture.Ferme -> buildString {
        append("Fermé")
        if (o.motif != null) append(" (${o.motif.replaceFirstChar { it.lowercase() }})")
        if (o.prochaine != null) append(" · ouvre ${formaterProchaine(o.prochaine, maintenant)}")
    }
    Ouverture.Inconnu -> "Horaires non communiqués"
}

/** Plages d'un jour, en clair : « 10h – 12h30, 14h30 – 19h », « fermé », « non communiqué ». */
fun libelleJour(j: JourHoraire): String = when {
    !j.connu -> "non communiqué"
    j.plages.isEmpty() -> if (j.motif != null) "fermé (${j.motif.replaceFirstChar { it.lowercase() }})" else "fermé"
    else -> j.plages.joinToString(", ") { "${formaterHeureCourte(it.debut)} – ${formaterHeureCourte(it.fin)}" }
}

fun nomDuJour(d: LocalDate, aujourdhui: LocalDate): String = when (d) {
    aujourdhui -> "Aujourd'hui"
    aujourdhui.plusDays(1) -> "Demain"
    else -> d.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.FRANCE).replaceFirstChar { it.uppercase() } +
        " ${d.dayOfMonth}"
}
