package com.yogistudio.cesson.pratique

import com.yogistudio.cesson.core.donnees.DepotModules
import com.yogistudio.cesson.core.donnees.EtatModule
import com.yogistudio.cesson.core.modele.JourCalendaire
import com.yogistudio.cesson.core.modele.Lieu
import com.yogistudio.cesson.core.ui.FuseauCesson
import com.yogistudio.cesson.equipements.JourHoraire
import com.yogistudio.cesson.equipements.Ouverture
import com.yogistudio.cesson.equipements.ouverture
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.MonthDay

/**
 * Le moment de la semaine, vu des pharmacies :
 * - [Ouvertes] : du lundi au samedi, de 8 h à 20 h, les officines sont ouvertes (horaires propres à chacune) ;
 * - [GardeJour] : dimanche ou jour férié, de 8 h à 20 h. Ameli publie la liste des pharmacies de garde ;
 * - [GardeNuit] : de 20 h à 8 h. Pour la sécurité des pharmaciens, ameli ne publie pas leurs coordonnées : 3237.
 */
enum class PeriodePharmacie { Ouvertes, GardeJour, GardeNuit }

const val DEBUT_JOUR_PHARMACIE = 8
const val FIN_JOUR_PHARMACIE = 20

fun periodePharmacie(maintenant: Instant): PeriodePharmacie {
    val z = maintenant.atZone(FuseauCesson)
    if (z.hour < DEBUT_JOUR_PHARMACIE || z.hour >= FIN_JOUR_PHARMACIE) return PeriodePharmacie.GardeNuit
    val date = z.toLocalDate()
    return if (date.dayOfWeek == DayOfWeek.SUNDAY || estFerie(date)) PeriodePharmacie.GardeJour else PeriodePharmacie.Ouvertes
}

private val FERIES_FIXES = listOf(
    MonthDay.of(1, 1), MonthDay.of(5, 1), MonthDay.of(5, 8), MonthDay.of(7, 14),
    MonthDay.of(8, 15), MonthDay.of(11, 1), MonthDay.of(11, 11), MonthDay.of(12, 25),
)

/** Jours fériés légaux en métropole (hors Alsace-Moselle) : dates fixes, lundi de Pâques, Ascension, lundi de Pentecôte. */
fun estFerie(date: LocalDate): Boolean {
    if (MonthDay.from(date) in FERIES_FIXES) return true
    val paques = dimancheDePaques(date.year)
    return date == paques.plusDays(1) || date == paques.plusDays(39) || date == paques.plusDays(50)
}

/** Dimanche de Pâques (calendrier grégorien, algorithme de Meeus/Jones/Butcher). */
fun dimancheDePaques(annee: Int): LocalDate {
    val a = annee % 19
    val b = annee / 100
    val c = annee % 100
    val d = b / 4
    val e = b % 4
    val f = (b + 8) / 25
    val g = (b - f + 1) / 3
    val h = (19 * a + b - d - g + 15) % 30
    val i = c / 4
    val k = c % 4
    val l = (32 + 2 * e + 2 * i - h - k) % 7
    val m = (a + 11 * h + 22 * l) / 451
    val mois = (h + l - 7 * m + 114) / 31
    val jour = (h + l - 7 * m + 114) % 31 + 1
    return LocalDate.of(annee, mois, jour)
}

// --- Pharmacies de Cesson et leurs horaires (module « pharmacies ») -------------------------------

/** Miroir de DonneesPharmacies (backend/cesson/collecteurs/pharmacies.py). */
@Serializable
data class DonneesPharmacies(val pharmacies: List<Pharmacie> = emptyList())

@Serializable
data class Pharmacie(
    val lieu: Lieu,
    val telephone: String? = null,
    val horaires: String? = null,
    @SerialName("horaires_verifies_le") val horairesVerifiesLe: JourCalendaire? = null,
    val jours: List<JourHoraire> = emptyList(),
)

const val MODULE_PHARMACIES = "pharmacies"

fun DepotModules.pharmacies(): Flow<EtatModule<DonneesPharmacies>> = suivre(MODULE_PHARMACIES, DonneesPharmacies.serializer())

/**
 * Ouvertes d'abord (celle qui ferme le plus tard en tête), puis fermées par prochaine ouverture,
 * puis celles dont on ne connaît pas les horaires.
 */
fun pharmaciesParOuverture(liste: List<Pharmacie>, maintenant: Instant): List<Pair<Pharmacie, Ouverture>> =
    liste.map { it to ouverture(it.jours, maintenant) }.sortedWith(
        compareBy<Pair<Pharmacie, Ouverture>> { (_, o) ->
            when (o) { is Ouverture.Ouvert -> 0; is Ouverture.Ferme -> 1; is Ouverture.Inconnu -> 2 }
        }.thenBy { (_, o) ->
            when (o) {
                is Ouverture.Ouvert -> -o.jusqua.epochSecond
                is Ouverture.Ferme -> o.prochaine?.epochSecond ?: Long.MAX_VALUE
                is Ouverture.Inconnu -> 0L
            }
        }.thenBy { it.first.lieu.nom },
    )
