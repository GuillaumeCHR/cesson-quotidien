package com.yogistudio.cesson.actus

import com.yogistudio.cesson.core.modele.FichierModule
import com.yogistudio.cesson.core.modele.JsonCesson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant

/** Fichier publié par le backend le 15 nov. 2026. */
class ActusTest {
    private val d = JsonCesson.decodeFromString(
        FichierModule.serializer(DonneesActus.serializer()), File("src/test/resources/actus.json").readText()).donnees
    private val maintenant = Instant.parse("2026-11-15T10:00:00Z")

    @Test
    fun fluxDeLaVille() {
        assertEquals(listOf(FLUX_VILLE), d.flux.map { it.id })
        assertEquals("Ordre du jour du Conseil municipal du 18 novembre", d.flux[0].articles[0].titre)
    }

    @Test
    fun datesEtNouveautes() {
        val a = d.flux[0].articles[0] // 10 nov.
        assertEquals("10 nov.", formaterDateArticle(a.date, maintenant))
        assertTrue(estRecent(a, maintenant))
        assertFalse(estRecent(d.flux[0].articles[1], maintenant)) // 28 sept.
        assertEquals("Hier", formaterDateArticle(Instant.parse("2026-11-14T15:00:00Z"), maintenant))
    }
}
