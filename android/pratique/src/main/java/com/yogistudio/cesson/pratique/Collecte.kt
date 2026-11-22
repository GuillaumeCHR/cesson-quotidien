package com.yogistudio.cesson.pratique

import com.yogistudio.cesson.core.donnees.DepotModules
import com.yogistudio.cesson.core.donnees.EtatModule
import com.yogistudio.cesson.core.modele.JourCalendaire
import com.yogistudio.cesson.core.ui.FuseauCesson
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

/** Miroir de DonneesCollecte (backend/cesson/collecteurs/collecte.py). */
@Serializable
data class DonneesCollecte(
    val public: String,
    val flux: List<FluxCollecte> = emptyList(),
    val passages: List<Passage> = emptyList(),
    val avis: List<AvisCollecte> = emptyList(),
    val conseil: String? = null,
    val info: String? = null,
    val url: String? = null,
    @SerialName("verifie_le") val verifieLe: JourCalendaire,
    @SerialName("valable_au") val valableAu: JourCalendaire,
)

@Serializable
data class FluxCollecte(val id: String, val nom: String, val bac: String, val regle: String)

@Serializable
data class Passage(val flux: String, val date: JourCalendaire, @SerialName("decale_par") val decalePar: String? = null)

@Serializable
data class AvisCollecte(val du: JourCalendaire, val au: JourCalendaire, val texte: String)

const val MODULE_COLLECTE = "collecte"

fun DepotModules.collecte(): Flow<EtatModule<DonneesCollecte>> = suivre(MODULE_COLLECTE, DonneesCollecte.serializer())

/** La collecte commence à 5h30 : passé midi, celle du jour est faite et on montre la suivante. */
private const val HEURE_COLLECTE_FAITE = 12

data class ProchaineCollecte(val flux: FluxCollecte, val passage: Passage?)

fun prochaines(d: DonneesCollecte, maintenant: Instant): List<ProchaineCollecte> {
    val z = maintenant.atZone(FuseauCesson)
    val premierJour = if (z.hour >= HEURE_COLLECTE_FAITE) z.toLocalDate().plusDays(1) else z.toLocalDate()
    return d.flux.map { f -> ProchaineCollecte(f, d.passages.firstOrNull { it.flux == f.id && !it.date.isBefore(premierJour) }) }
}

/** Bacs à sortir ce soir : collecte demain. Rappel montré à partir de midi. */
fun aSortirCeSoir(d: DonneesCollecte, maintenant: Instant): List<FluxCollecte> {
    val z = maintenant.atZone(FuseauCesson)
    if (z.hour < HEURE_COLLECTE_FAITE) return emptyList()
    val demain = z.toLocalDate().plusDays(1)
    return prochaines(d, maintenant).filter { it.passage?.date == demain }.map { it.flux }
}

fun avisEnCours(d: DonneesCollecte, maintenant: Instant): List<AvisCollecte> {
    val jour = maintenant.atZone(FuseauCesson).toLocalDate()
    return d.avis.filter { !jour.isBefore(it.du) && !jour.isAfter(it.au) }
}

/** Heure du rappel de la veille (notification). */
val HEURE_RAPPEL: LocalTime = LocalTime.of(17, 30)

/** Prochain rappel strictement après [maintenant] : la veille d'un passage, à 17h30. */
fun prochainRappel(d: DonneesCollecte, maintenant: Instant): Instant? =
    d.passages.map { it.date }.distinct().sorted()
        .map { it.minusDays(1).atTime(HEURE_RAPPEL).atZone(FuseauCesson).toInstant() }
        .firstOrNull { it.isAfter(maintenant) }

/** Bacs à rappeler maintenant : collecte demain, et il est 17h30 passées (jusqu'à minuit). */
fun bacsARappeler(d: DonneesCollecte, maintenant: Instant): List<FluxCollecte> {
    val z = maintenant.atZone(FuseauCesson)
    if (z.toLocalTime() < HEURE_RAPPEL) return emptyList()
    val demain = z.toLocalDate().plusDays(1)
    return d.flux.filter { f -> d.passages.any { it.flux == f.id && it.date == demain } }
}

/** Texte de la notification : « Sortez le bac gris ce soir ». */
fun texteRappel(bacs: List<FluxCollecte>): Pair<String, String> {
    val noms = bacs.joinToString(" et ") { "bac ${it.bac}" }
    val flux = bacs.joinToString(" et ") { it.nom.lowercase() }
    return "Sortez le $noms ce soir" to "Collecte demain matin dès 5h30 ($flux)."
}

/** « aujourd'hui », « demain », « lundi 23 nov. ». */
fun formaterJourCollecte(date: LocalDate, maintenant: Instant): String {
    val aujourdhui = maintenant.atZone(FuseauCesson).toLocalDate()
    return when (date) {
        aujourdhui -> "aujourd'hui"
        aujourdhui.plusDays(1) -> "demain (${date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.FRANCE)})"
        else -> "${date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.FRANCE)} ${date.dayOfMonth} " +
            date.month.getDisplayName(TextStyle.SHORT, Locale.FRANCE)
    }
}
