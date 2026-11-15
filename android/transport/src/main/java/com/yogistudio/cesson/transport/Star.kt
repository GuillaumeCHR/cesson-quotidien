package com.yogistudio.cesson.transport

import com.yogistudio.cesson.core.donnees.DepotModules
import com.yogistudio.cesson.core.donnees.EtatModule
import com.yogistudio.cesson.core.modele.Horodatage
import com.yogistudio.cesson.core.modele.JsonCesson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.text.Normalizer
import java.time.Duration
import java.time.Instant

// --- Lignes de Cesson (module lignes_star du backend) -------------------------------------------

/** Miroir de DonneesLignesStar (backend/cesson/collecteurs/lignes_star.py). */
@Serializable
data class DonneesLignesStar(val lignes: List<LigneStar> = emptyList(), val arrets: List<ArretStar> = emptyList())

/** Arrêt de Cesson, quais regroupés ; [nom] est celui des jeux temps réel. */
@Serializable
data class ArretStar(val nom: String, val modes: List<Mode> = emptyList(), val lignes: List<String> = emptyList())

/** Recherche insensible aux accents, à la casse et aux tirets : « chene » trouve « Chêne Germain ». */
fun filtrerArrets(arrets: List<ArretStar>, recherche: String): List<ArretStar> {
    val r = normaliser(recherche)
    return if (r.isEmpty()) arrets else arrets.filter { normaliser(it.nom).contains(r) || it.lignes.any { l -> normaliser(l) == r } }
}

@Serializable
data class LigneStar(
    val id: String,
    @SerialName("nom_court") val nomCourt: String,
    @SerialName("nom_long") val nomLong: String,
    val mode: Mode = Mode.BUS,
    val famille: String? = null,
    val couleur: String,
    @SerialName("couleur_texte") val couleurTexte: String,
)

@Serializable
enum class Mode {
    @SerialName("bus") BUS,
    @SerialName("metro") METRO,
    @SerialName("train") TRAIN,
}

const val MODULE_LIGNES_STAR = "lignes_star"

fun DepotModules.lignesStar(): Flow<EtatModule<DonneesLignesStar>> =
    suivre(MODULE_LIGNES_STAR, DonneesLignesStar.serializer())

// --- Prochains départs, demandés directement à STAR -----------------------------------------------

enum class StatutDepart { TEMPS_REEL, THEORIQUE }

/** Un départ, au format du modèle commun (entité Depart). */
data class Depart(
    val arret: String,
    val ligne: String,
    val mode: Mode,
    val destination: String,
    val heure: Instant, // heure réelle si connue, sinon théorique
    val heureTheorique: Instant?,
    val statut: StatutDepart,
)

/** Pôle d'échanges suivi : nom de l'arrêt dans les jeux STAR (bus et métro portent le même nom). */
const val POLE_VIASILVA = "Cesson - Viasilva"

@Serializable
private data class Reponse<T>(val results: List<T> = emptyList())

@Serializable
private data class PassageBus(
    val nomcourtligne: String,
    val destination: String,
    val nomarret: String,
    val depart: Horodatage? = null,
    val departtheorique: Horodatage? = null,
    val precision: String? = null,
    val visibilite: String? = null,
)

@Serializable
private data class PassageMetro(
    val nomcourtligne: String,
    val destination: String,
    val nomarret: String,
    val depart: Horodatage? = null,
    // Chaîne seule pour le bus, liste pour le métro : on accepte les deux.
    val precision: JsonElement? = null,
)

private fun JsonElement?.contientTempsReel(): Boolean = when (this) {
    null -> false
    is JsonPrimitive -> content == TEMPS_REEL
    else -> runCatching { jsonArray.any { it.jsonPrimitive.content == TEMPS_REEL } }.getOrDefault(false)
}

private const val TEMPS_REEL = "Temps réel"

/** « Cesson - Viasilva » et « Cesson-Viasilva » désignent le même lieu. */
internal fun normaliser(nom: String): String =
    Normalizer.normalize(nom, Normalizer.Form.NFD).replace(Regex("[^A-Za-z0-9]"), "").lowercase()

fun decoderBus(json: String): List<Depart> =
    JsonCesson.decodeFromString(Reponse.serializer(PassageBus.serializer()), json).results
        .filter { it.visibilite == null || it.visibilite == "Grand public" }
        .mapNotNull { p ->
            val heure = p.depart ?: p.departtheorique ?: return@mapNotNull null
            Depart(p.nomarret, p.nomcourtligne, Mode.BUS, p.destination, heure, p.departtheorique,
                if (p.precision == TEMPS_REEL) StatutDepart.TEMPS_REEL else StatutDepart.THEORIQUE)
        }

fun decoderMetro(json: String): List<Depart> =
    JsonCesson.decodeFromString(Reponse.serializer(PassageMetro.serializer()), json).results
        .mapNotNull { p ->
            val heure = p.depart ?: return@mapNotNull null
            Depart(p.nomarret, p.nomcourtligne, Mode.METRO, p.destination, heure, null,
                if (p.precision.contientTempsReel()) StatutDepart.TEMPS_REEL else StatutDepart.THEORIQUE)
        }

/**
 * Départs utiles à afficher : ni déjà passés, ni arrivées au terminus (un bus dont la destination
 * est l'arrêt lui-même ne prend personne), triés par heure.
 */
fun departsUtiles(departs: List<Depart>, maintenant: Instant): List<Depart> =
    departs
        .filter { normaliser(it.destination) != normaliser(it.arret) }
        .filter { !it.heure.isBefore(maintenant.minusSeconds(30)) }
        .sortedBy { it.heure }

/** Une ligne et une direction, avec leurs prochains départs : c'est ainsi que la carte les montre. */
data class Direction(val ligne: String, val mode: Mode, val destination: String, val departs: List<Depart>)

/** Métro d'abord, puis par prochain départ ; au plus [parDirection] horaires par direction. */
fun regrouper(departs: List<Depart>, parDirection: Int = 3): List<Direction> =
    departs.groupBy { it.ligne to it.destination }
        .map { (cle, d) -> Direction(cle.first, d.first().mode, cle.second, d.sortedBy { it.heure }.take(parDirection)) }
        .sortedWith(compareBy({ it.mode != Mode.METRO }, { it.departs.first().heure }))

/** « 1 min », « 12 min », puis l'heure au-delà d'une heure. « Maintenant » sous la minute. */
fun formaterAttente(heure: Instant, maintenant: Instant, heureLocale: (Instant) -> String): String {
    val minutes = Duration.between(maintenant, heure).toMinutes()
    return when {
        minutes < 1 -> "maintenant"
        minutes < 60 -> "$minutes min"
        else -> heureLocale(heure)
    }
}

class ErreurStar(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * Client des jeux temps réel de data.explore.star.fr (ODbL, sans clé). L'app l'interroge
 * directement : seuls le nom de l'arrêt et l'adresse IP du téléphone parviennent à STAR.
 */
class ClientStar(
    internal val client: OkHttpClient,
    private val base: String = "https://data.explore.star.fr/api/explore/v2.1/catalog/datasets",
) {
    suspend fun departs(nomArret: String): List<Depart> = withContext(Dispatchers.IO) {
        coroutineScope {
            val bus = async { lire("tco-bus-circulation-passages-tr", nomArret, ::decoderBus) }
            val metro = async { lire("tco-metro-circulation-passages-tr", nomArret, ::decoderMetro) }
            metro.await() + bus.await()
        }
    }

    private fun lire(jeu: String, nomArret: String, decoder: (String) -> List<Depart>): List<Depart> {
        val url = "$base/$jeu/records".toHttpUrl().newBuilder()
            .addQueryParameter("where", "nomarret=\"${nomArret.replace("\"", "")}\"")
            .addQueryParameter("order_by", "depart")
            .addQueryParameter("limit", "100")
            .build()
        // Pas de cache HTTP : un horaire temps réel d'il y a deux minutes est faux.
        val requete = Request.Builder().url(url).header("Cache-Control", "no-cache").build()
        try {
            client.newCall(requete).execute().use { r ->
                if (!r.isSuccessful) throw ErreurStar("STAR a répondu ${r.code}")
                return decoder(r.body.string())
            }
        } catch (e: ErreurStar) {
            throw e
        } catch (e: IOException) {
            throw ErreurStar("STAR injoignable", e)
        } catch (e: IllegalArgumentException) {
            throw ErreurStar("réponse STAR illisible", e) // SerializationException en hérite
        }
    }
}
