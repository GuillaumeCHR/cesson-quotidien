package com.yogistudio.cesson.actus

import com.yogistudio.cesson.core.donnees.DepotModules
import com.yogistudio.cesson.core.donnees.EtatModule
import com.yogistudio.cesson.core.modele.Horodatage
import com.yogistudio.cesson.core.ui.FuseauCesson
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Miroir de DonneesHandball (backend/cesson/collecteurs/handball.py). */
@Serializable
data class DonneesHandball(
    val saison: String,
    @SerialName("a_venir") val aVenir: List<Match> = emptyList(),
    val dernier: Match? = null,
)

@Serializable
data class Match(
    val id: String, // stable si le match est déplacé
    val debut: Horodatage,
    @SerialName("horaire_a_confirmer") val horaireAConfirmer: Boolean = false,
    val adversaire: String,
    val competition: String? = null,
    val tour: String,
    val salle: String,
    @SerialName("score_cesson") val scoreCesson: Int? = null,
    @SerialName("score_adversaire") val scoreAdversaire: Int? = null,
    val billetterie: String? = null,
)

const val MODULE_HANDBALL = "handball"
const val URL_CLUB_HANDBALL = "https://www.cesson-handball.com/pro/saison"

/** Les favoris des matchs partagent la liste des spectacles favoris, avec ce préfixe. */
const val PREFIXE_FAVORI_MATCH = "handball:"

fun Match.cleFavori() = PREFIXE_FAVORI_MATCH + id

fun DepotModules.handball(): Flow<EtatModule<DonneesHandball>> = suivre(MODULE_HANDBALL, DonneesHandball.serializer())

/** Un match reste affiché jusqu'à 2 h après le coup d'envoi (la fiche du serveur peut dater de quelques heures). */
private val DUREE_MATCH = Duration.ofHours(2)

fun prochainsMatchs(d: DonneesHandball, maintenant: Instant): List<Match> =
    d.aVenir.filter { it.debut.plus(DUREE_MATCH).isAfter(maintenant) }.sortedBy { it.debut }

/** Les matchs du club repris par l'agenda de la ville (« Cesson Rennes Métropole Handball : match ») : ils ont leur carte. */
fun estMatchDuClub(e: Evenement): Boolean = e.titre.startsWith("Cesson Rennes Métropole Handball", ignoreCase = true)

private val HEURE = DateTimeFormatter.ofPattern("H'h'mm", Locale.FRENCH)

/** « 20h30 », « 20h », ou « horaire à confirmer ». */
fun heureMatch(m: Match): String =
    if (m.horaireAConfirmer) "horaire à confirmer" else HEURE.format(m.debut.atZone(FuseauCesson)).removeSuffix("00")

/** « Victoire 29-28 contre Aix-en-Provence », « Défaite… », « Match nul… ». */
fun resultat(m: Match): String? {
    val c = m.scoreCesson ?: return null
    val a = m.scoreAdversaire ?: return null
    val issue = when {
        c > a -> "Victoire"
        c < a -> "Défaite"
        else -> "Match nul"
    }
    return "$issue $c-$a contre ${m.adversaire}"
}

/** Rappel d'un match favori, la veille à partir de 18h, comme les spectacles. La clé change si le match est déplacé. */
fun rappelsMatchs(d: DonneesHandball, favoris: Set<String>, maintenant: ZonedDateTime, deja: Set<String>): List<RappelSpectacle> {
    val demain = maintenant.toLocalDate().plusDays(1)
    if (maintenant.hour < HEURE_RAPPEL_VEILLE) return emptyList()
    return d.aVenir.filter { it.cleFavori() in favoris && it.debut.atZone(FuseauCesson).toLocalDate() == demain }.map { m ->
        val heure = if (m.horaireAConfirmer) "" else " ${heureMatch(m)}"
        RappelSpectacle("${m.cleFavori()}|veille|${m.debut}", "Demain$heure : Cesson – ${m.adversaire}",
            "Handball, ${m.tour}, à la ${m.salle}. Allez Cesson !")
    }.filter { it.cle !in deja }
}
