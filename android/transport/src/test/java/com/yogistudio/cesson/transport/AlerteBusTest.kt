package com.yogistudio.cesson.transport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class AlerteBusTest {
    private val t0 = Instant.parse("2026-11-16T07:00:00Z")
    private fun bus(min: Long, ligne: String = "C6", dest: String = "Rennes") =
        Depart("Bourgchevreuil", ligne, Mode.BUS, dest, t0.plusSeconds(min * 60), null, StatutDepart.TEMPS_REEL)
    private fun alerte(min: Long, delai: Int = 5) = AlerteBus("Bourgchevreuil", "C6", "Rennes", t0.plusSeconds(min * 60).epochSecond, delai)

    @Test
    fun viseLePremierBusAuDelaDuDelai() {
        val d = departAViser(listOf(bus(3), bus(12), bus(25), bus(4, "C4")), "C6", "Rennes", 5, t0)
        assertEquals(t0.plusSeconds(12 * 60), d!!.heure)
    }

    @Test
    fun revientUnPeuAvantLHeureDeLAlerte() {
        val d = decider(alerte(12), listOf(bus(12)), t0) as Decision.Revoir
        // 12 min - 5 min - 90 s de marge
        assertEquals(t0.plusSeconds(12 * 60 - 5 * 60 - 90), d.quand)
    }

    @Test
    fun suitLeRetardDuBus() {
        // Le bus visé à 12 min annonce maintenant 16 min : c'est le même, l'alerte le suit.
        val d = decider(alerte(12), listOf(bus(16), bus(40)), t0.plusSeconds(400)) as Decision.Revoir
        assertEquals(t0.plusSeconds(16 * 60), d.heure)
    }

    @Test
    fun previentADelaiAtteint() {
        val d = decider(alerte(12), listOf(bus(12)), t0.plusSeconds(7 * 60 + 10)) as Decision.Prevenir
        assertEquals(5, d.minutes)
        assertEquals("Le C6 vers Rennes passe dans 5 min", texteAlerte("C6", "Rennes", d.minutes))
    }

    @Test
    fun relitAuMoinsToutesLesDixMinutes() {
        val d = decider(alerte(60), listOf(bus(60)), t0) as Decision.Revoir
        assertEquals(t0.plusSeconds(600), d.quand)
    }

    @Test
    fun abandonneSiLeBusADisparuEtQueLHeureEstPassee() {
        assertEquals(Decision.Abandonner, decider(alerte(12), emptyList(), t0.plusSeconds(15 * 60)))
        assertTrue(decider(alerte(12), emptyList(), t0) is Decision.Revoir)
    }
}
