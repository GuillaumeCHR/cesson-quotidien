package com.yogistudio.cesson.core.modele

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime

/*
 * Miroir Kotlin du modèle commun du backend (backend/cesson/commun/modele.py).
 * Les noms de champs JSON sont ceux du backend ; les JSON Schema de référence sont dans
 * backend/schemas/. L'app ignore les champs qu'elle ne connaît pas, pour rester compatible
 * quand le backend en ajoute.
 */

const val VERSION_SCHEMA = 1

val JsonCesson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true // une valeur d'énumération inconnue retombe sur la valeur par défaut
}

/** Horodatages ISO 8601 du backend, avec « Z » ou un décalage explicite. */
object InstantSerializer : KSerializer<Instant> {
    override val descriptor = PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): Instant {
        val s = decoder.decodeString()
        return if (s.endsWith("Z")) Instant.parse(s) else OffsetDateTime.parse(s).toInstant()
    }
}

typealias Horodatage = @Serializable(with = InstantSerializer::class) Instant

/** Dates sans heure du backend (« 2026-11-15 »). */
object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor = PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())
}

typealias JourCalendaire = @Serializable(with = LocalDateSerializer::class) LocalDate

@Serializable
enum class Etat {
    @SerialName("ok") OK,
    @SerialName("perime") PERIME,
    @SerialName("erreur") ERREUR,
}

@Serializable
data class Coord(val lat: Double, val lon: Double)

@Serializable
data class Meta(
    val source: String,
    val licence: String,
    @SerialName("url_source") val urlSource: String? = null,
    @SerialName("recupere_le") val recupereLe: Horodatage,
    @SerialName("valide_jusqu_a") val valideJusquA: Horodatage,
    val etat: Etat = Etat.OK,
)

@Serializable
data class Lieu(
    val id: String,
    val nom: String,
    val type: String,
    val coord: Coord? = null,
    val adresse: String? = null,
    val source: String? = null,
    val url: String? = null,
)

@Serializable
enum class Tendance {
    @SerialName("hausse") HAUSSE,
    @SerialName("stable") STABLE,
    @SerialName("baisse") BAISSE,
    @SerialName("inconnue") INCONNUE,
}

@Serializable
data class Mesure(
    val station: String,
    val grandeur: String,
    val valeur: Double,
    val unite: String,
    val horodatage: Horodatage,
    val tendance: Tendance = Tendance.INCONNUE,
)

@Serializable
enum class NiveauAlerte {
    @SerialName("info") INFO,
    @SerialName("jaune") JAUNE,
    @SerialName("orange") ORANGE,
    @SerialName("rouge") ROUGE,
}

@Serializable
data class Alerte(
    val id: String,
    val niveau: NiveauAlerte,
    val titre: String,
    val zone: String? = null,
    val debut: Horodatage? = null,
    val fin: Horodatage? = null,
    val source: String,
)

/** Enveloppe de chaque `<module>.json` publié. */
@Serializable
data class FichierModule<T>(
    val module: String,
    @SerialName("version_schema") val versionSchema: Int = VERSION_SCHEMA,
    val meta: Meta,
    val donnees: T,
)

@Serializable
data class EntreeManifest(
    val fichier: String,
    val version: String,
    @SerialName("recupere_le") val recupereLe: Horodatage,
    @SerialName("valide_jusqu_a") val valideJusquA: Horodatage,
    val etat: Etat,
)

@Serializable
data class Manifest(
    @SerialName("version_schema") val versionSchema: Int = VERSION_SCHEMA,
    @SerialName("genere_le") val genereLe: Horodatage,
    val modules: Map<String, EntreeManifest>,
)
