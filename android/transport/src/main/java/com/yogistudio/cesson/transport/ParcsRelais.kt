package com.yogistudio.cesson.transport

import com.yogistudio.cesson.core.modele.JsonCesson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.time.Instant
import java.time.OffsetDateTime

/** Parc relais de Cesson, au terminus du métro b. */
const val PARC_VIASILVA = "CVI"
/** Parcs relais voisins, utiles à certains quartiers de Cesson (sud, ouest). */
val PARCS_VOISINS = listOf("POT" to "La Poterie", "PRE" to "Les Préales")

/** Un parc relais STAR, tel que publié en temps réel (jeu tco-parcsrelais-star-etat-tr). */
@Serializable
data class ParcRelais(
    val idparc: String,
    val nom: String,
    val coordonnees: Coordonnees? = null,
    val lastupdate: String? = null,
    val etatouverture: String? = null, // OUVERT, FERME
    val etatremplissage: String? = null, // LIBRE, COMPLET…
    val capacitesoliste: Int = 0,
    val jrdinfosoliste: Int = 0,
    val capaciteve: Int = 0,
    val jrdinfoelectrique: Int = 0,
    val capacitepmr: Int = 0,
    val jrdinfopmr: Int = 0,
) {
    @Serializable
    data class Coordonnees(val lat: Double, val lon: Double)

    val ouvert: Boolean get() = etatouverture == "OUVERT"
    val complet: Boolean get() = ouvert && (etatremplissage == "COMPLET" || jrdinfosoliste <= 0)
    val misAJour: Instant? get() = lastupdate?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() }
    /** Part des places occupées, de 0 à 1 (pour la jauge). */
    val occupation: Float get() =
        if (capacitesoliste <= 0) 0f else (1f - jrdinfosoliste.toFloat() / capacitesoliste).coerceIn(0f, 1f)
}

@Serializable
private data class ReponseParcs(val results: List<ParcRelais> = emptyList())

fun decoderParcsRelais(json: String): List<ParcRelais> = JsonCesson.decodeFromString(ReponseParcs.serializer(), json).results

/** Interroge la STAR directement depuis le téléphone, comme pour les passages (même clé, même secours anonyme). */
suspend fun ClientStar.parcsRelais(): List<ParcRelais> = withContext(Dispatchers.IO) {
    val url = "$base/tco-parcsrelais-star-etat-tr/records".toHttpUrl().newBuilder().addQueryParameter("limit", "20").build()
    decoder(appeler(url), ::decoderParcsRelais)
}
