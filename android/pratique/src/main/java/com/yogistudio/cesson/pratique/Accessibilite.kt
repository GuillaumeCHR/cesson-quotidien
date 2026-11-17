package com.yogistudio.cesson.pratique

import com.yogistudio.cesson.core.donnees.DepotModules
import com.yogistudio.cesson.core.donnees.EtatModule
import com.yogistudio.cesson.core.modele.JourCalendaire
import com.yogistudio.cesson.core.modele.Lieu
import com.yogistudio.cesson.core.ui.Ton
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.Normalizer
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Miroirs de DonneesAccessibilite (backend/cesson/collecteurs/accessibilite.py). null : non renseigné. */
@Serializable
data class DonneesAccessibilite(val etablissements: List<Etablissement> = emptyList())

@Serializable
data class Etablissement(
    val lieu: Lieu,
    val activite: String? = null,
    val famille: String = "commerces",
    val acces: Acces = Acces(),
    val commentaire: String? = null,
    @SerialName("mis_a_jour") val misAJour: JourCalendaire? = null,
)

@Serializable
data class Acces(
    @SerialName("stationnement_pmr") val stationnementPmr: Boolean? = null,
    @SerialName("stationnement_pmr_proche") val stationnementPmrProche: Boolean? = null,
    @SerialName("arret_transport") val arretTransport: Boolean? = null,
    @SerialName("chemin_plain_pied") val cheminPlainPied: Boolean? = null,
    @SerialName("entree_plain_pied") val entreePlainPied: Boolean? = null,
    @SerialName("entree_marches") val entreeMarches: Int? = null,
    @SerialName("entree_rampe") val entreeRampe: String? = null,
    @SerialName("entree_ascenseur") val entreeAscenseur: Boolean? = null,
    @SerialName("entree_largeur_cm") val entreeLargeurCm: Int? = null,
    @SerialName("entree_appel") val entreeAppel: Boolean? = null,
    @SerialName("entree_specifique") val entreeSpecifique: Boolean? = null,
    @SerialName("accueil_plain_pied") val accueilPlainPied: Boolean? = null,
    @SerialName("personnel_forme") val personnelForme: Boolean? = null,
    val malentendants: List<String> = emptyList(),
    val sanitaires: Boolean? = null,
    @SerialName("sanitaires_adaptes") val sanitairesAdaptes: Boolean? = null,
)

const val MODULE_ACCESSIBILITE = "accessibilite"

fun DepotModules.accessibilite(): Flow<EtatModule<DonneesAccessibilite>> =
    suivre(MODULE_ACCESSIBILITE, DonneesAccessibilite.serializer())

/** Familles publiées par le backend, dans l'ordre des filtres. */
enum class FamilleLieu(val code: String?, val libelle: String) {
    Tous(null, "Tous"),
    ServicesPublics("services_publics", "Services publics"),
    Sante("sante", "Santé"),
    Loisirs("loisirs", "Culture et sport"),
    Restauration("restauration", "Restaurants et hôtels"),
    Commerces("commerces", "Commerces et services"),
}

private fun sansAccents(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase(Locale.FRANCE)

/** Recherche sur le nom, l'activité et l'adresse, sans tenir compte des accents ni de la casse. */
fun filtrerEtablissements(liste: List<Etablissement>, recherche: String, famille: FamilleLieu): List<Etablissement> {
    val mots = sansAccents(recherche).split(Regex("[\\s,'’-]+")).filter { it.isNotBlank() }
    return liste.filter { e ->
        (famille.code == null || e.famille == famille.code) &&
            (mots.isEmpty() || sansAccents(listOfNotNull(e.lieu.nom, e.activite, e.lieu.adresse).joinToString(" ")).let { t ->
                mots.all { it in t }
            })
    }
}

private fun pluriel(n: Int, mot: String) = "$n $mot${if (n > 1) "s" else ""}"

/** L'entrée en quelques mots : « de plain-pied », « 3 marches, rampe fixe », ou null si non renseignée. */
fun Acces.entree(): String? = when (entreePlainPied) {
    true -> "de plain-pied"
    false -> listOfNotNull(
        entreeMarches?.takeIf { it > 0 }?.let { pluriel(it, "marche") } ?: "marches ou ressaut",
        entreeRampe?.let { "rampe $it" },
        if (entreeAscenseur == true) "ascenseur" else null,
    ).joinToString(", ")
    null -> null
}

/** Entrée sans marche d'après la fiche : de plain-pied, ou marches compensées par une rampe ou un ascenseur. */
val Acces.entreeAccessible: Boolean
    get() = entreePlainPied == true || (entreePlainPied == false && (entreeRampe != null || entreeAscenseur == true))

/** Pastilles de la ligne : l'entrée, le stationnement réservé, les sanitaires adaptés. Le texte porte l'information. */
fun Acces.pastilles(): List<Pair<String, Ton>> = buildList {
    when {
        entreePlainPied == true -> add("Entrée de plain-pied" to Ton.OUVERT)
        entreePlainPied == false && (entreeRampe != null || entreeAscenseur == true) ->
            add((if (entreeRampe != null) "Rampe à l'entrée" else "Ascenseur à l'entrée") to Ton.INFO)
        entreePlainPied == false ->
            add((entreeMarches?.takeIf { it > 0 }?.let { "${pluriel(it, "marche")} à l'entrée" } ?: "Marches à l'entrée") to Ton.NEUTRE)
    }
    when {
        stationnementPmr == true -> add("Place réservée sur place" to Ton.OUVERT)
        stationnementPmrProche == true -> add("Place réservée à proximité" to Ton.INFO)
    }
    if (sanitaires == true && sanitairesAdaptes == true) add("Sanitaires adaptés" to Ton.OUVERT)
    if (malentendants.isNotEmpty()) add("Équipement malentendants" to Ton.INFO)
    if (isEmpty()) add("Peu renseigné" to Ton.NEUTRE)
}

private val DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRANCE)

/** Le détail, phrase par phrase, de ce que dit la fiche ; ce qui n'est pas renseigné n'est pas affiché. */
fun Etablissement.details(): List<Pair<String, String>> = buildList {
    val a = acces
    when {
        a.stationnementPmr == true -> "Place réservée dans l'établissement."
        a.stationnementPmrProche == true -> "Place réservée à moins de 200 m."
        a.stationnementPmr == false && a.stationnementPmrProche == false -> "Pas de place réservée à proximité."
        else -> null
    }?.let { add("Stationnement" to it) }
    if (a.arretTransport == true) add("Transports" to "Arrêt de bus ou de métro à moins de 200 m.")
    a.cheminPlainPied?.let { add("Depuis la rue" to if (it) "Chemin de plain-pied." else "Marches, pente ou obstacle sur le chemin.") }
    val entree = listOfNotNull(
        a.entree()?.replaceFirstChar { it.uppercase() },
        a.entreeLargeurCm?.let { "porte d'au moins $it cm" },
        if (a.entreeAppel == true) "sonnette ou aide à l'entrée" else null,
        if (a.entreeSpecifique == true) "entrée dédiée aux personnes à mobilité réduite" else null,
    )
    if (entree.isNotEmpty()) add("Entrée" to entree.joinToString(", ").replaceFirstChar { it.uppercase() } + ".")
    val accueil = listOfNotNull(
        a.accueilPlainPied?.let { if (it) "De plain-pied jusqu'à l'accueil" else "Marches ou obstacle jusqu'à l'accueil" },
        when (a.personnelForme) {
            true -> "personnel formé à l'accueil des personnes handicapées"
            false -> "personnel non formé à l'accueil des personnes handicapées"
            null -> null
        },
    )
    if (accueil.isNotEmpty()) add("Accueil" to accueil.joinToString(", ").replaceFirstChar { it.uppercase() } + ".")
    if (a.malentendants.isNotEmpty()) add("Malentendants" to a.malentendants.joinToString(", ").replaceFirstChar { it.uppercase() } + ".")
    when (a.sanitaires) {
        true -> when (a.sanitairesAdaptes) {
            true -> "Sanitaires adaptés."
            false -> "Sanitaires non adaptés."
            null -> "Sanitaires (adaptation non renseignée)."
        }
        false -> "Pas de sanitaires."
        null -> null
    }?.let { add("Sanitaires" to it) }
    commentaire?.let { add("Commentaire" to "« $it »") }
    misAJour?.let { add("Fiche" to "Mise à jour le ${DATE.format(it)}.") }
}
