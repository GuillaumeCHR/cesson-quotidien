package com.yogistudio.cesson.transport

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class TrainsRetourTest {
    private val gares = listOf(
        GareRetour("87471300", "Vitré", "35"),
        GareRetour("87391003", "Paris Montparnasse", "75"),
        GareRetour("87481002", "Nantes", "44"),
    )
    private val maintenant = Instant.parse("2026-11-23T16:00:00Z")

    private fun depart(gare: String, minutes: Long) = DepartRetour(
        arret = gare, ligne = "TER", destination = "Rennes", heureTheorique = maintenant.plusSeconds(minutes * 60),
        numero = "8$minutes", arrivee = "Rennes", heureArrivee = maintenant.plusSeconds((minutes + 30) * 60),
    )

    @Test
    fun rechercheParNomOuDepartement() {
        assertEquals(listOf("Vitré"), filtrerGares(gares, "vitre").map { it.nom })
        assertEquals(listOf("Paris Montparnasse"), filtrerGares(gares, "Paris").map { it.nom })
        assertEquals(listOf("Nantes"), filtrerGares(gares, "loire").map { it.nom })
        assertEquals(listOf("Vitré"), filtrerGares(gares, "35").map { it.nom })
    }

    @Test
    fun seulementLaGareChoisieEtPasLesTrainsPartis() {
        val d = DonneesTrainsRetour(gares, listOf(depart("87471300", 40), depart("87471300", -5), depart("87481002", 10),
            depart("87471300", 20)))
        assertEquals(listOf("820", "840"), departsDeLaGare(d, "87471300", maintenant).map { it.numero })
    }
}
