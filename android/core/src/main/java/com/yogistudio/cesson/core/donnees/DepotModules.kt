package com.yogistudio.cesson.core.donnees

import com.yogistudio.cesson.core.modele.Etat
import com.yogistudio.cesson.core.modele.FichierModule
import com.yogistudio.cesson.core.modele.JsonCesson
import com.yogistudio.cesson.core.modele.Manifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.time.Instant

/** Ce que l'interface sait d'un module à un instant donné. */
sealed interface EtatModule<out T> {
    /** Rien en cache, première récupération en cours ou jamais réussie. */
    data object Absent : EtatModule<Nothing>

    data class Disponible<T>(val fichier: FichierModule<T>, val fraicheur: Fraicheur) : EtatModule<T>
}

/** Fraîcheur affichée sous chaque carte : « mis à jour il y a 12 min, source … ». */
data class Fraicheur(
    val source: String,
    val licence: String,
    val recupereLe: Instant,
    val valideJusquA: Instant,
    val etat: Etat,
) {
    fun estPerimee(maintenant: Instant): Boolean = etat != Etat.OK || maintenant.isAfter(valideJusquA)
}

sealed interface ResultatRafraichissement {
    data class Reussi(val modulesTelecharges: List<String>) : ResultatRafraichissement
    data class Echec(val cause: String) : ResultatRafraichissement
}

/**
 * Lit le manifest, ne télécharge que les modules dont la version a changé et garde la dernière
 * version de chaque module sur le disque : l'app reste utilisable sans réseau, y compris
 * pendant une crue.
 *
 * Une donnée périmée n'est jamais masquée : elle reste affichée, signalée comme telle.
 */
class DepotModules(
    baseUrl: String,
    private val dossier: File,
    private val client: OkHttpClient,
) {
    private val base = baseUrl.toHttpUrl()
    private val verrou = Mutex()

    private val _manifest = MutableStateFlow<Manifest?>(null)
    val manifest: StateFlow<Manifest?> = _manifest.asStateFlow()

    /** Contenu JSON brut de chaque module en cache, et la version correspondante. */
    private val bruts = MutableStateFlow<Map<String, String>>(emptyMap())
    private var versions: Map<String, String> = emptyMap()

    private val _rafraichissementEnCours = MutableStateFlow(false)
    val rafraichissementEnCours: StateFlow<Boolean> = _rafraichissementEnCours.asStateFlow()

    private val _derniereErreur = MutableStateFlow<String?>(null)
    val derniereErreur: StateFlow<String?> = _derniereErreur.asStateFlow()

    private var charge = false

    /** Charge le cache disque. Appelée une fois au démarrage, avant tout rafraîchissement. */
    suspend fun chargerCache() = verrou.withLock { chargerCacheSansVerrou() }

    private suspend fun chargerCacheSansVerrou() = withContext(Dispatchers.IO) {
        if (charge) return@withContext
        charge = true
        dossier.mkdirs()
        _manifest.value = lire(FICHIER_MANIFEST)?.let { decoder(it, Manifest.serializer()) }
        versions = lire(FICHIER_VERSIONS)?.let { decoder(it, serialiseurVersions) } ?: emptyMap()
        bruts.value = versions.keys.mapNotNull { m -> lire("$m.json")?.let { m to it } }.toMap()
    }

    suspend fun rafraichir(): ResultatRafraichissement = verrou.withLock {
        chargerCacheSansVerrou()
        _rafraichissementEnCours.value = true
        try {
            withContext(Dispatchers.IO) { rafraichirSansVerrou() }
        } finally {
            _rafraichissementEnCours.value = false
        }
    }

    private fun rafraichirSansVerrou(): ResultatRafraichissement {
        val texteManifest = try {
            telecharger(FICHIER_MANIFEST)
        } catch (e: IOException) {
            return echec("Pas de connexion aux données (${e.message ?: e.javaClass.simpleName})")
        }
        val manifest = decoder(texteManifest, Manifest.serializer())
            ?: return echec("Manifest illisible")

        val telecharges = mutableListOf<String>()
        val nouvellesVersions = versions.toMutableMap()
        val nouveauxBruts = bruts.value.toMutableMap()
        var erreurModule: String? = null
        for ((module, entree) in manifest.modules) {
            if (versions[module] == entree.version && module in nouveauxBruts) continue
            try {
                val texte = telecharger(entree.fichier)
                // On n'écrase jamais une version valide par un fichier inexploitable.
                if (decoder(texte, enveloppeMinimale) == null) {
                    erreurModule = "$module : fichier illisible"
                    continue
                }
                ecrire("$module.json", texte)
                nouveauxBruts[module] = texte
                nouvellesVersions[module] = entree.version
                telecharges += module
            } catch (e: IOException) {
                erreurModule = "$module : ${e.message}"
            }
        }
        versions = nouvellesVersions
        ecrire(FICHIER_VERSIONS, JsonCesson.encodeToString(serialiseurVersions, nouvellesVersions))
        ecrire(FICHIER_MANIFEST, texteManifest)
        bruts.value = nouveauxBruts
        _manifest.value = manifest
        _derniereErreur.value = erreurModule
        return ResultatRafraichissement.Reussi(telecharges)
    }

    /**
     * Suit un module. La fraîcheur vient du manifest quand il décrit la version en cache (le
     * backend y note chaque collecte, même sans changement de données), sinon du bloc Meta.
     */
    fun <T> suivre(module: String, serialiseurDonnees: KSerializer<T>): Flow<EtatModule<T>> {
        val serialiseur = FichierModule.serializer(serialiseurDonnees)
        return combine(bruts, _manifest) { b, manifest ->
            val texte = b[module] ?: return@combine EtatModule.Absent
            val fichier = decoder(texte, serialiseur) ?: return@combine EtatModule.Absent
            val entree = manifest?.modules?.get(module)?.takeIf { it.version == versions[module] }
            val meta = fichier.meta
            EtatModule.Disponible(
                fichier,
                Fraicheur(
                    source = meta.source,
                    licence = meta.licence,
                    recupereLe = entree?.recupereLe ?: meta.recupereLe,
                    valideJusquA = entree?.valideJusquA ?: meta.valideJusquA,
                    etat = entree?.etat ?: meta.etat,
                ),
            )
        }
    }

    /**
     * Image publiée avec les données (affiche d'un film : « affiches/103.jpg »), gardée sur le disque : une affiche
     * ne change pas, elle n'est téléchargée qu'une fois. Null sans réseau ni copie locale. Les images qu'on n'a pas
     * affichées depuis 90 jours sont effacées.
     */
    suspend fun image(chemin: String): ByteArray? = withContext(Dispatchers.IO) {
        if (!Regex("[a-z0-9_]+/[0-9]+\\.jpg").matches(chemin)) return@withContext null
        val dossierImages = File(dossier, "images").apply { mkdirs() }
        val fichier = File(dossierImages, chemin.replace('/', '_'))
        if (fichier.isFile) {
            fichier.setLastModified(System.currentTimeMillis())
            return@withContext fichier.readBytes()
        }
        val octets = try {
            val url = base.resolve(chemin) ?: return@withContext null
            client.newCall(Request.Builder().url(url).build()).execute().use { r ->
                if (!r.isSuccessful) return@withContext null
                r.body.bytes()
            }
        } catch (_: IOException) {
            return@withContext null
        }
        val limite = System.currentTimeMillis() - 90L * 24 * 3600 * 1000
        dossierImages.listFiles()?.filter { it.lastModified() < limite }?.forEach { it.delete() }
        val tmp = File(dossierImages, ".${fichier.name}.tmp")
        runCatching { tmp.writeBytes(octets); tmp.renameTo(fichier) }
        octets
    }

    private fun echec(cause: String): ResultatRafraichissement {
        _derniereErreur.value = cause
        return ResultatRafraichissement.Echec(cause)
    }

    private fun telecharger(chemin: String): String {
        val url = base.resolve(chemin) ?: throw IOException("adresse invalide : $chemin")
        client.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code} sur $chemin")
            return r.body.string()
        }
    }

    private fun lire(nom: String): String? = File(dossier, nom).takeIf { it.isFile }?.readText()

    private fun ecrire(nom: String, texte: String) {
        val tmp = File(dossier, ".$nom.tmp")
        tmp.writeText(texte)
        if (!tmp.renameTo(File(dossier, nom))) {
            tmp.delete()
            throw IOException("écriture impossible : $nom")
        }
    }

    private fun <T> decoder(texte: String, serialiseur: KSerializer<T>): T? =
        try {
            JsonCesson.decodeFromString(serialiseur, texte)
        } catch (_: IllegalArgumentException) { // SerializationException en hérite
            null
        }

    private companion object {
        const val FICHIER_MANIFEST = "manifest.json"
        const val FICHIER_VERSIONS = "versions.json"
        val serialiseurVersions = MapSerializer(String.serializer(), String.serializer())
        val enveloppeMinimale = FichierModule.serializer(kotlinx.serialization.json.JsonElement.serializer())
    }
}
