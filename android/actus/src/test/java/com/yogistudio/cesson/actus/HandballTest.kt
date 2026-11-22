package com.yogistudio.cesson.actus

import com.yogistudio.cesson.core.modele.FichierModule
import com.yogistudio.cesson.core.modele.JsonCesson
import com.yogistudio.cesson.core.ui.FuseauCesson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime
import java.io.File

class HandballTest {
    // Publication réelle du 22 nov. 2026.
    private val d = JsonCesson.decodeFromString(FichierModule.serializer(DonneesHandball.serializer()),
        File("src/test/resources/handball.json").readText()).donnees

    @Test
    fun prochainsMatchsALaGlazArena() {
        val m = prochainsMatchs(d, Instant.parse("2026-11-22T10:00:00Z"))
        assertEquals("Saran", m.first().adversaire)
        assertEquals("20h30", heureMatch(m.first()))
        assertTrue(m.all { it.salle == "Glaz Arena" })
        // Pendant le match, il reste affiché ; le lendemain, c'est le suivant.
        assertEquals("Saran", prochainsMatchs(d, Instant.parse("2026-12-03T20:30:00Z")).first().adversaire)
        assertEquals("Paris", prochainsMatchs(d, Instant.parse("2026-12-04T08:00:00Z")).first().adversaire)
    }

    @Test
    fun dernierResultat() {
        assertEquals("Défaite 29-37 contre Toulouse", resultat(d.dernier!!))
    }

    @Test
    fun rappelLaVeilleA18h() {
        val saran = d.aVenir.first()
        val favoris = setOf(saran.cleFavori())
        fun le(h: Int) = ZonedDateTime.of(2026, 12, 2, h, 0, 0, 0, FuseauCesson)
        assertTrue(rappelsMatchs(d, favoris, le(13), emptySet()).isEmpty())
        val r = rappelsMatchs(d, favoris, le(18), emptySet()).single()
        assertEquals("Demain 20h30 : Cesson – Saran", r.titre)
        assertTrue(r.cle.startsWith("handball:liqui-moly-starligue-13e-journee-saran|veille|"))
        assertTrue(rappelsMatchs(d, favoris, le(19), setOf(r.cle)).isEmpty())
        assertTrue(rappelsMatchs(d, emptySet(), le(18), emptySet()).isEmpty())
    }

    @Test
    fun matchsDuClubHorsDeLAgenda() {
        val jour = LocalDate.of(2026, 12, 3)
        val match = Evenement(id = "ville:m", titre = "Cesson Rennes Métropole Handball : match", du = jour, au = jour,
            lien = "https://x", famille = "sport")
        val autre = match.copy(id = "ville:a", titre = "Glaz Arena : Fête du sport")
        val jours = agendaParJour(DonneesAgenda(listOf(match, autre)), null, jour)
        assertEquals(listOf("ville:a"), jours.flatMap { it.second }.map { it.id })
        assertFalse(estMatchDuClub(autre))
    }
}
