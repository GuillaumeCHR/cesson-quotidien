package com.yogistudio.cesson.pratique

import com.yogistudio.cesson.core.modele.FichierModule
import com.yogistudio.cesson.core.modele.JsonCesson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant

class CarburantsTest {
    private val fichier = JsonCesson.decodeFromString(
        FichierModule.serializer(DonneesCarburants.serializer()),
        File("src/test/resources/carburants.json").readText(),
    )
    private val d = fichier.donnees
    // Le 15 nov. 2026 à 9h : prix du Carrefour Market relevés le 5 nov., donc anciens.
    private val maintenant = Instant.parse("2026-11-15T08:00:00Z")

    /** Le fichier publié par le backend se décode tel quel : le contrat est tenu des deux côtés. */
    @Test
    fun decodeLeFichierDuBackend() {
        assertEquals("carburants", fichier.module)
        assertEquals(listOf("Carrefour Rigourdière", "Carrefour Market Bourgchevreuil", "Total route de Rennes"), d.stations.map { it.lieu.nom })
        assertEquals(listOf("gazole", "e10", "sp95", "sp98", "e85", "gplc"), carburantsProposes(d).map { it.first })
    }

    @Test
    fun gazoleDuMoinsCherAuPlusCherPrixAnciensALaFin() {
        val l = classer(d, "gazole", maintenant).map { it as LigneCarburant.Prix }
        assertEquals(listOf("Carrefour Rigourdière", "Total route de Rennes", "Carrefour Market Bourgchevreuil"), l.map { it.station.lieu.nom })
        assertTrue(l[0].moinsCher)
        assertFalse(l[1].moinsCher)
        assertTrue(l[2].ancien)
        // Le prix ancien du Carrefour Market n'entre pas dans la comparaison, même s'il était plus bas.
        assertFalse(l[2].moinsCher)
    }

    @Test
    fun ruptureApresLesPrix() {
        val l = classer(d, "sp98", maintenant)
        assertEquals(listOf("Carrefour Rigourdière", "Carrefour Market Bourgchevreuil", "Total route de Rennes"), l.map { it.station.lieu.nom })
        assertTrue(l.last() is LigneCarburant.Rupture)
        // Un seul prix récent : pas de badge « le moins cher ».
        assertTrue(l.filterIsInstance<LigneCarburant.Prix>().none { it.moinsCher })
    }

    @Test
    fun stationQuiNeVendPasLeCarburantOmise() {
        assertEquals(listOf("Carrefour Rigourdière"), classer(d, "e85", maintenant).map { it.station.lieu.nom })
    }

    @Test
    fun prixALaFrancaise() {
        assertEquals("1,918 €", formaterPrix(1.918).replace(' ', ' '))
    }
}
