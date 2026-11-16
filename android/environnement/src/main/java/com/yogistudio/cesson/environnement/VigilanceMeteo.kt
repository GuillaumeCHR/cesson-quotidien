package com.yogistudio.cesson.environnement

import com.yogistudio.cesson.core.donnees.DepotModules
import com.yogistudio.cesson.core.donnees.EtatModule
import com.yogistudio.cesson.core.modele.Alerte
import com.yogistudio.cesson.core.modele.Horodatage
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

/** Miroir de DonneesVigilanceMeteo (backend/cesson/collecteurs/vigilance_meteo.py). */
@Serializable
data class DonneesVigilanceMeteo(
    val departement: String,
    @SerialName("mis_a_jour") val misAJour: Horodatage,
    val jours: List<JourVigilance>,
    val textes: List<TexteVigilance> = emptyList(),
    val alerte: Alerte? = null,
)

@Serializable
data class JourVigilance(
    val echeance: String,
    val debut: Horodatage,
    val fin: Horodatage,
    val couleur: CouleurVigilance = CouleurVigilance.INCONNUE,
    val phenomenes: List<PhenomeneVigilance> = emptyList(),
)

@Serializable
data class PhenomeneVigilance(
    val id: String,
    val nom: String,
    val couleur: CouleurVigilance = CouleurVigilance.INCONNUE,
    val plages: List<PlageVigilance> = emptyList(),
)

@Serializable
data class PlageVigilance(val debut: Horodatage, val fin: Horodatage, val couleur: CouleurVigilance = CouleurVigilance.INCONNUE)

@Serializable
data class TexteVigilance(val titre: String, val alea: String? = null, val texte: String)

/** Dans l'ordre de gravité. INCONNUE couvre une couleur que cette version de l'app ne connaît pas. */
@Serializable
enum class CouleurVigilance {
    @SerialName("vert") VERT,
    @SerialName("jaune") JAUNE,
    @SerialName("orange") ORANGE,
    @SerialName("rouge") ROUGE,
    INCONNUE,
}

const val MODULE_VIGILANCE_METEO = "vigilance_meteo"
const val URL_VIGILANCE_METEO = "https://vigilance.meteofrance.fr/fr/ille-et-vilaine"

/** Les crues ont leur source (Vigicrues) et leur bandeau : la vigilance « crues » ne déclenche rien ici. */
private const val PHENOMENE_CRUES = "4"

fun DepotModules.vigilanceMeteo(): Flow<EtatModule<DonneesVigilanceMeteo>> =
    suivre(MODULE_VIGILANCE_METEO, DonneesVigilanceMeteo.serializer())

private val Paris: ZoneId = ZoneId.of("Europe/Paris")

fun CouleurVigilance.nom(): String = when (this) {
    CouleurVigilance.VERT -> "verte"
    CouleurVigilance.JAUNE -> "jaune"
    CouleurVigilance.ORANGE -> "orange"
    CouleurVigilance.ROUGE -> "rouge"
    CouleurVigilance.INCONNUE -> "à vérifier"
}

/** Ce que veut dire chaque couleur, d'après Météo-France. */
fun CouleurVigilance.consigne(): String = when (this) {
    CouleurVigilance.JAUNE -> "Soyez attentif : des phénomènes habituels dans la région, mais occasionnellement et localement dangereux, sont prévus."
    CouleurVigilance.ORANGE -> "Soyez très vigilant : des phénomènes dangereux sont prévus. Tenez-vous au courant et suivez les conseils des autorités."
    CouleurVigilance.ROUGE -> "Vigilance absolue : des phénomènes dangereux d'intensité exceptionnelle sont prévus. Suivez les consignes des autorités."
    CouleurVigilance.VERT -> "Pas de vigilance particulière."
    CouleurVigilance.INCONNUE -> "Consultez le site de vigilance de Météo-France."
}

/** Le jour (heure de Paris) que couvre l'échéance : J1 commence à minuit. */
fun JourVigilance.date(): LocalDate = debut.atZone(Paris).toLocalDate()

/** Phénomènes qui comptent pour le bandeau et les notifications : jaune ou plus, hors crues. */
fun JourVigilance.aSignaler(): List<PhenomeneVigilance> =
    phenomenes.filter { it.id != PHENOMENE_CRUES && it.couleur in CouleurVigilance.JAUNE..CouleurVigilance.ROUGE }

/** Couleur la plus forte de ces phénomènes, VERT s'il n'y en a pas. */
fun List<PhenomeneVigilance>.couleurMax(): CouleurVigilance =
    filter { it.couleur != CouleurVigilance.INCONNUE }.maxOfOrNull { it.couleur } ?: CouleurVigilance.VERT

/** Jours encore en cours (aujourd'hui, demain) où une vigilance jaune ou plus est à signaler. */
fun DonneesVigilanceMeteo.aSignaler(maintenant: Instant): List<JourVigilance> =
    jours.filter { it.fin.isAfter(maintenant) && it.aSignaler().isNotEmpty() }

fun EtatModule<DonneesVigilanceMeteo>.enVigilance(maintenant: Instant): Boolean =
    (this as? EtatModule.Disponible)?.fichier?.donnees?.aSignaler(maintenant)?.isNotEmpty() == true

private fun heure(z: ZonedDateTime): String = if (z.minute == 0) "${z.hour}h" else "${z.hour}h%02d".format(z.minute)

/** « de 15h à 20h », « jusqu'à 8h », « à partir de 14h », « toute la journée ». */
fun PlageVigilance.texte(): String {
    val d = debut.atZone(Paris)
    val f = fin.atZone(Paris)
    val depuisMinuit = d.hour == 0 && d.minute == 0
    val jusquaMinuit = f.hour == 0 && f.minute == 0
    return when {
        depuisMinuit && jusquaMinuit -> "toute la journée"
        depuisMinuit -> "jusqu'à ${heure(f)}"
        jusquaMinuit -> "à partir de ${heure(d)}"
        else -> "de ${heure(d)} à ${heure(f)}"
    }
}

/** « vent violent jusqu'à 8h et de 15h à 20h ». */
fun PhenomeneVigilance.description(): String =
    if (plages.isEmpty()) nom else "$nom ${plages.joinToString(" et ") { it.texte() }}"

fun nomJourVigilance(d: LocalDate, aujourdhui: LocalDate): String = when (d) {
    aujourdhui -> "aujourd'hui"
    aujourdhui.plusDays(1) -> "demain"
    else -> java.time.format.DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRANCE).format(d)
}

/** Seuils proposés pour la notification de vigilance. */
val SEUILS_VIGILANCE: List<CouleurVigilance> = listOf(CouleurVigilance.JAUNE, CouleurVigilance.ORANGE, CouleurVigilance.ROUGE)

/**
 * Une notification par jour où un phénomène atteint le seuil. La clé porte la couleur et les phénomènes :
 * si la vigilance s'aggrave ou s'étend à un autre phénomène, on prévient de nouveau.
 */
fun notificationsVigilance(d: DonneesVigilanceMeteo, maintenant: Instant, seuil: CouleurVigilance, dejaEnvoyees: Set<String>): List<NotificationAir> {
    val aujourdhui = maintenant.atZone(Paris).toLocalDate()
    return d.aSignaler(maintenant).mapNotNull { j ->
        val retenus = j.aSignaler().filter { it.couleur >= seuil }
        if (retenus.isEmpty()) return@mapNotNull null
        val couleur = retenus.couleurMax()
        NotificationAir(
            cle = "vigilance:${j.date()}:${couleur.name.lowercase(Locale.ROOT)}:${retenus.map { it.id }.sorted().joinToString("-")}",
            titre = "Vigilance ${couleur.nom()} ${nomJourVigilance(j.date(), aujourdhui)} en ${d.departement}",
            texte = retenus.joinToString(" ; ") { it.description() }.replaceFirstChar { it.uppercase() } + ". " + couleur.consigne(),
        )
    }.filter { it.cle !in dejaEnvoyees }
}
