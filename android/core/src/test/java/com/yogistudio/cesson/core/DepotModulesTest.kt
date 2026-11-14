package com.yogistudio.cesson.core

import com.yogistudio.cesson.core.donnees.DepotModules
import com.yogistudio.cesson.core.donnees.EtatModule
import com.yogistudio.cesson.core.donnees.ResultatRafraichissement
import com.yogistudio.cesson.core.modele.Etat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DepotModulesTest {
    @get:Rule val dossier = TemporaryFolder()
    private val serveur = MockWebServer()

    @Serializable data class D(val n: Int)

    @Before fun demarrer() = serveur.start()
    @After fun arreter() = serveur.close()

    private fun manifest(version: String, etat: String = "ok", recupere: String = "2026-11-14T22:56:00Z") = """
        {"version_schema":1,"genere_le":"$recupere","modules":{"m":{"fichier":"m.json","version":"$version",
         "recupere_le":"$recupere","valide_jusqu_a":"2026-11-15T23:30:00Z","etat":"$etat"}}}"""

    private fun module(n: Int) = """
        {"module":"m","version_schema":1,"champ_futur":true,
         "meta":{"source":"S","licence":"L","recupere_le":"2026-11-14T22:56:00Z","valide_jusqu_a":"2026-11-15T23:30:00Z","etat":"ok"},
         "donnees":{"n":$n}}"""

    private fun ok(body: String) = MockResponse.Builder().code(200).body(body).build()

    private fun depot() = DepotModules(serveur.url("/").toString(), dossier.root, OkHttpClient())

    @Test
    fun telechargeSeulementLesModulesQuiOntChange() = runTest {
        val depot = depot()
        serveur.enqueue(ok(manifest("v1")))
        serveur.enqueue(ok(module(1)))
        assertEquals(ResultatRafraichissement.Reussi(listOf("m")), depot.rafraichir())

        // Même version, collecte plus récente : seul le manifest est relu, la fraîcheur suit.
        serveur.enqueue(ok(manifest("v1", recupere = "2026-11-15T06:05:00Z")))
        assertEquals(ResultatRafraichissement.Reussi(emptyList()), depot.rafraichir())
        assertEquals(3, serveur.requestCount)
        val e = depot.suivre("m", D.serializer()).first() as EtatModule.Disponible
        assertEquals(1, e.fichier.donnees.n)
        assertEquals("2026-11-15T06:05:00Z", e.fraicheur.recupereLe.toString())
    }

    @Test
    fun horsConnexionLeCacheResteDisponibleEtMarquePerime() = runTest {
        serveur.enqueue(ok(manifest("v1", etat = "perime")))
        serveur.enqueue(ok(module(7)))
        depot().rafraichir()

        // Nouveau démarrage de l'app, sans réseau.
        serveur.close()
        val depot = depot()
        depot.chargerCache()
        assertTrue(depot.rafraichir() is ResultatRafraichissement.Echec)
        val e = depot.suivre("m", D.serializer()).first() as EtatModule.Disponible
        assertEquals(7, e.fichier.donnees.n)
        assertEquals(Etat.PERIME, e.fraicheur.etat)
    }

    @Test
    fun unFichierIllisibleNEcrasePasLaVersionValide() = runTest {
        val depot = depot()
        serveur.enqueue(ok(manifest("v1")))
        serveur.enqueue(ok(module(1)))
        depot.rafraichir()
        serveur.enqueue(ok(manifest("v2")))
        serveur.enqueue(ok("<html>erreur</html>"))
        depot.rafraichir()
        val e = depot.suivre("m", D.serializer()).first() as EtatModule.Disponible
        assertEquals(1, e.fichier.donnees.n)
    }
}
