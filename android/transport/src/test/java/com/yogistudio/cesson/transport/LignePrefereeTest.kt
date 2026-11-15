package com.yogistudio.cesson.transport

import org.junit.Assert.assertEquals
import org.junit.Test

class LignePrefereeTest {
    private fun dir(ligne: String, dest: String) = Direction(ligne, Mode.BUS, dest, emptyList())
    private val directions = listOf(dir("C2", "Haut Sancé"), dir("64", "Acigné"), dir("50", "Thorigné"), dir("64", "Rennes"))

    @Test
    fun lignePrefereeEnTeteOrdreConserve() {
        assertEquals(listOf("64", "64", "C2", "50"), prioriser(directions, "64").map { it.ligne })
        assertEquals(listOf("Acigné", "Rennes"), prioriser(directions, "64").take(2).map { it.destination })
    }

    @Test
    fun sansPreferenceRienNeChange() {
        assertEquals(directions, prioriser(directions, null))
        assertEquals(directions, prioriser(directions, "C4"))
    }
}
