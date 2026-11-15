package com.yogistudio.cesson.transport

import com.yogistudio.cesson.core.modele.FichierModule
import com.yogistudio.cesson.core.modele.JsonCesson
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant

/** Réponses réelles de data.explore.star.fr, dimanche 15 nov. 2026 vers 8h30 à Cesson-ViaSilva. */
class StarTest {
    private val bus = File("src/test/resources/star_bus.json").readText()
    private val metro = File("src/test/resources/star_metro.json").readText()
    private val maintenant = Instant.parse("2026-11-15T07:31:39Z")

    @Test
    fun decodeLeBusEtLeMetro() {
        val b = decoderBus(bus)
        assertEquals(15, b.size)
        assertEquals(2, b.count { it.statut == StatutDepart.TEMPS_REEL })
        val m = decoderMetro(metro)
        assertEquals(20, m.size)
        assertTrue(m.all { it.mode == Mode.METRO && it.ligne == "b" })
        assertEquals(StatutDepart.TEMPS_REEL, m.first().statut)
        assertEquals(StatutDepart.THEORIQUE, m[2].statut)
    }

    @Test
    fun arriveesAuTerminusEtDepartsPassesEcartes() {
        val utiles = departsUtiles(decoderBus(bus), maintenant)
        // Les C2 « vers Cesson-Viasilva » arrivent ici : personne n'y monte.
        assertFalse(utiles.any { normaliser(it.destination) == "cessonviasilva" })
        assertTrue(utiles.all { !it.heure.isBefore(maintenant.minusSeconds(30)) })
        assertEquals(listOf("C2", "C2", "C2", "64", "50", "C2", "64", "C2"), utiles.map { it.ligne })
    }

    @Test
    fun metroEnTetePuisParProchainDepart() {
        val d = regrouper(departsUtiles(decoderMetro(metro) + decoderBus(bus), maintenant))
        assertEquals("b", d.first().ligne)
        assertEquals(3, d.first().departs.size)
        assertEquals(listOf("b", "C2", "64", "50"), d.map { it.ligne })
    }

    @Test
    fun attente() {
        val h: (Instant) -> String = { "10h21" }
        assertEquals("maintenant", formaterAttente(maintenant.plusSeconds(40), maintenant, h))
        assertEquals("12 min", formaterAttente(maintenant.plusSeconds(12 * 60 + 5), maintenant, h))
        assertEquals("10h21", formaterAttente(maintenant.plusSeconds(3 * 3600), maintenant, h))
    }

    @Test
    fun lignesDuBackend() {
        val f = JsonCesson.decodeFromString(FichierModule.serializer(DonneesLignesStar.serializer()),
            File("src/test/resources/lignes_star.json").readText())
        val b = f.donnees.lignes.first()
        assertEquals("b", b.nomCourt)
        assertEquals(Mode.METRO, b.mode)
        assertEquals("#00893e", b.couleur)
    }

    @Test
    fun clientInterrogeLesDeuxJeuxEtSignaleLesPannes() = runTest {
        val serveur = MockWebServer()
        var enPanne = false
        val chemins = mutableListOf<String>()
        serveur.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val chemin = request.url.encodedPath
                synchronized(chemins) { chemins += chemin }
                if (enPanne) return MockResponse.Builder().code(503).build()
                return MockResponse.Builder().body(if ("metro" in chemin) metro else bus).build()
            }
        }
        serveur.start()
        val client = ClientStar(OkHttpClient(), serveur.url("/d").toString().removeSuffix("/"))
        assertEquals(35, client.departs(POLE_VIASILVA).size)
        assertEquals(setOf("/d/tco-bus-circulation-passages-tr/records", "/d/tco-metro-circulation-passages-tr/records"),
            chemins.toSet())

        enPanne = true
        assertTrue(runCatching { client.departs(POLE_VIASILVA) }.exceptionOrNull() is ErreurStar)
        serveur.close()
    }

    @Test
    fun rechercheDArret() {
        val f = JsonCesson.decodeFromString(FichierModule.serializer(DonneesLignesStar.serializer()),
            File("src/test/resources/lignes_star.json").readText())
        val arrets = f.donnees.arrets
        assertEquals(listOf("Chêne Germain", "Chêne Morand"), filtrerArrets(arrets, "chene").map { it.nom })
        assertEquals(listOf("Cesson - Viasilva", "star't Cesson - Viasilva"), filtrerArrets(arrets, "cesson-via").map { it.nom })
        // Une ligne tapée telle quelle liste ses arrêts.
        assertTrue(filtrerArrets(arrets, "C6").all { "C6" in it.lignes })
        assertEquals(arrets, filtrerArrets(arrets, "  "))
    }
}
