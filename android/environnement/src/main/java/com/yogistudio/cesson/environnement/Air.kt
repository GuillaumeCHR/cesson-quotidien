package com.yogistudio.cesson.environnement

import com.yogistudio.cesson.core.donnees.DepotModules
import com.yogistudio.cesson.core.donnees.EtatModule
import com.yogistudio.cesson.core.modele.Alerte
import com.yogistudio.cesson.core.modele.Horodatage
import com.yogistudio.cesson.core.modele.JourCalendaire
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate

// Miroirs de backend/cesson/collecteurs/atmo.py : trois modules, trois cartes.

const val MODULE_QUALITE_AIR = "qualite_air"
const val MODULE_POLLENS = "pollens"
const val MODULE_EPISODES = "episodes_pollution"
const val URL_AIR_BREIZH = "https://www.airbreizh.asso.fr/"

// --- Indice ATMO ---------------------------------------------------------------------------------

@Serializable
data class DonneesQualiteAir(
    val zone: String,
    @SerialName("mis_a_jour") val misAJour: Horodatage,
    val jours: List<JourAtmo>,
)

@Serializable
data class JourAtmo(
    val date: JourCalendaire,
    val code: Int,
    val libelle: String,
    val couleur: String,
    @SerialName("sous_indices") val sousIndices: List<SousIndice> = emptyList(),
    val responsables: List<String> = emptyList(),
)

@Serializable
data class SousIndice(val polluant: String, val nom: String, val code: Int)

fun DepotModules.qualiteAir(): Flow<EtatModule<DonneesQualiteAir>> =
    suivre(MODULE_QUALITE_AIR, DonneesQualiteAir.serializer())

/** Libellés officiels de l'indice ATMO (arrêté du 10 juillet 2020). */
fun libelleAtmo(code: Int): String = when (code) {
    0 -> "Absent"
    1 -> "Bon"
    2 -> "Moyen"
    3 -> "Dégradé"
    4 -> "Mauvais"
    5 -> "Très mauvais"
    6 -> "Extrêmement mauvais"
    7 -> "Événement"
    else -> "Indice inconnu"
}

/** Couleurs officielles de l'indice ATMO, pour les sous-indices (l'indice global porte la sienne). */
fun couleurAtmo(code: Int): Long = when (code) {
    1 -> 0xFF50F0E6
    2 -> 0xFF50CCAA
    3 -> 0xFFF0E641
    4 -> 0xFFFF5050
    5 -> 0xFF960032
    6 -> 0xFF872181
    7 -> 0xFF888888
    else -> 0xFFDDDDDD
}

/** Les trois couleurs les plus foncées de l'échelle prennent un texte blanc. */
fun texteClairSurAtmo(code: Int): Boolean = code in 4..6

/** « #50ccaa » publié par Air Breizh, en ARGB ; couleur de l'échelle si la chaîne est mal formée. */
fun JourAtmo.argb(): Long =
    couleur.removePrefix("#").takeIf { it.length == 6 }?.toLongOrNull(16)?.let { 0xFF000000 or it } ?: couleurAtmo(code)

/** Polluant responsable écrit pour un humain : « l'ozone (O₃) ». */
fun nomPolluant(polluant: String): String = when (polluant) {
    "no2" -> "le dioxyde d'azote (NO₂)"
    "o3" -> "l'ozone (O₃)"
    "pm10" -> "les particules PM10"
    "pm25" -> "les particules fines PM2,5"
    "so2" -> "le dioxyde de soufre (SO₂)"
    else -> polluant
}

// --- Pollens -------------------------------------------------------------------------------------

@Serializable
data class DonneesPollens(
    val commune: String,
    @SerialName("mis_a_jour") val misAJour: Horodatage,
    val jours: List<JourPollen>,
)

@Serializable
data class JourPollen(
    val date: JourCalendaire,
    val code: Int,
    val libelle: String,
    val alerte: Boolean = false,
    val especes: List<IndiceEspece> = emptyList(),
    val responsables: List<String> = emptyList(),
)

@Serializable
data class IndiceEspece(val espece: String, val nom: String, val code: Int)

fun DepotModules.pollens(): Flow<EtatModule<DonneesPollens>> = suivre(MODULE_POLLENS, DonneesPollens.serializer())

fun libellePollen(code: Int): String = when (code) {
    0 -> "Indisponible"
    1 -> "Très faible"
    2 -> "Faible"
    3 -> "Modéré"
    4 -> "Élevé"
    5 -> "Très élevé"
    6 -> "Extrêmement élevé"
    else -> "Niveau inconnu"
}

/** Hors saison : toutes les espèces au plus « très faible », et pas d'alerte. */
val JourPollen.horsSaison: Boolean get() = !alerte && code <= 1 && especes.all { it.code <= 1 }

fun JourPollen.nomsResponsables(): List<String> =
    responsables.mapNotNull { r -> especes.firstOrNull { it.espece == r }?.nom }

// --- Épisodes de pollution -----------------------------------------------------------------------

/** Dans l'ordre de gravité. INCONNU couvre un état que cette version de l'app ne connaît pas. */
@Serializable
enum class EtatEpisode {
    @SerialName("aucun") AUCUN,
    @SerialName("information") INFORMATION,
    @SerialName("alerte_persistance") ALERTE_PERSISTANCE,
    @SerialName("alerte") ALERTE,
    INCONNU,
}

@Serializable
data class DonneesEpisodes(
    val zone: String,
    @SerialName("mis_a_jour") val misAJour: Horodatage,
    val jours: List<JourEpisodes>,
    val alerte: Alerte? = null,
)

@Serializable
data class JourEpisodes(
    val date: JourCalendaire,
    val etat: EtatEpisode = EtatEpisode.INCONNU,
    val polluants: List<EpisodePolluant> = emptyList(),
)

@Serializable
data class EpisodePolluant(val polluant: String, val nom: String, val etat: EtatEpisode = EtatEpisode.INCONNU)

fun DepotModules.episodesPollution(): Flow<EtatModule<DonneesEpisodes>> =
    suivre(MODULE_EPISODES, DonneesEpisodes.serializer())

val EtatEpisode.enCours: Boolean get() = this != EtatEpisode.AUCUN

fun EtatEpisode.libelle(): String = when (this) {
    EtatEpisode.AUCUN -> "Pas d'épisode"
    EtatEpisode.INFORMATION -> "Information et recommandation"
    EtatEpisode.ALERTE_PERSISTANCE -> "Alerte sur persistance"
    EtatEpisode.ALERTE -> "Alerte"
    EtatEpisode.INCONNU -> "À vérifier"
}

/** Ce que la procédure veut dire, d'après les définitions réglementaires. */
fun EtatEpisode.definition(): String = when (this) {
    EtatEpisode.INFORMATION -> "Seuil d'information dépassé ou prévu : les personnes sensibles (enfants, " +
        "personnes âgées, asthmatiques, malades du cœur) sont invitées à limiter les efforts physiques intenses."
    EtatEpisode.ALERTE_PERSISTANCE, EtatEpisode.ALERTE -> "Seuil d'alerte : des mesures préfectorales peuvent " +
        "s'appliquer (vitesse abaissée, restrictions de circulation). Tout le monde est invité à limiter les efforts " +
        "physiques intenses."
    EtatEpisode.AUCUN -> "Aucun seuil réglementaire dépassé."
    EtatEpisode.INCONNU -> "Consultez Air Breizh pour connaître la procédure en vigueur."
}

/** Jours d'aujourd'hui et de demain où une procédure est déclenchée ou prévue. */
fun DonneesEpisodes.aVenir(aujourdhui: LocalDate): List<JourEpisodes> =
    jours.filter { !it.date.isBefore(aujourdhui) && it.date.isBefore(aujourdhui.plusDays(2)) && it.etat.enCours }

fun EtatModule<DonneesEpisodes>.enAlerte(aujourdhui: LocalDate): Boolean =
    (this as? EtatModule.Disponible)?.fichier?.donnees?.aVenir(aujourdhui)?.isNotEmpty() == true

/** Polluants visés par la procédure du jour, en noms courts : « PM10, PM2,5 ». */
fun JourEpisodes.polluantsVises(): String = polluants.filter { it.etat == etat }.joinToString(", ") { it.polluant }
