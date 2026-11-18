package com.yogistudio.cesson.actus

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZonedDateTime
import com.yogistudio.cesson.core.ui.FuseauCesson

class RappelsSpectaclesTest {
    private val suzane = Evenement(id = "openagenda:1", titre = "Suzane", du = LocalDate.of(2026, 11, 19),
        au = LocalDate.of(2026, 11, 19), horaires = "20h00 – 22h00", lieu = "Carré Sévigné", lien = "https://x", saison = true)
    private val d = DonneesAgenda(listOf(suzane))
    private fun le(h: Int) = ZonedDateTime.of(2026, 11, 18, h, 0, 0, 0, FuseauCesson)

    @Test
    fun laVeilleA18h() {
        assertEquals(emptyList<RappelSpectacle>(), rappelsSpectacles(d, setOf(suzane.id), le(13), emptySet()))
        val r = rappelsSpectacles(d, setOf(suzane.id), le(18), emptySet()).single()
        assertEquals("Demain 20h00 : Suzane", r.titre)
        assertEquals("Spectacle au Carré Sévigné. Bon spectacle !", r.texte)
        assertEquals(emptyList<RappelSpectacle>(), rappelsSpectacles(d, setOf(suzane.id), le(19), setOf(r.cle)))
        assertEquals(emptyList<RappelSpectacle>(), rappelsSpectacles(d, emptySet(), le(18), emptySet()))
    }

    @Test
    fun annuleAussitot() {
        val annule = DonneesAgenda(listOf(suzane.copy(statut = "Annulé")))
        assertEquals("Suzane : annulé", rappelsSpectacles(annule, setOf(suzane.id), le(9), emptySet()).single().titre)
    }
}
