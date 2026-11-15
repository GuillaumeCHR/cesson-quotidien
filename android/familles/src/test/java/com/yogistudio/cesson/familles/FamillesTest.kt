package com.yogistudio.cesson.familles

import com.yogistudio.cesson.core.modele.FichierModule
import com.yogistudio.cesson.core.modele.JsonCesson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.LocalTime

/** Fichier publié par le backend le 15 nov. 2026 : menus de novembre, actualités publiques du portail. */
class FamillesTest {
    private val d = JsonCesson.decodeFromString(
        FichierModule.serializer(DonneesFamilles.serializer()), File("src/test/resources/familles.json").readText()).donnees

    @Test
    fun semaineAVenir() {
        val jours = joursDeMenu(d, LocalDate.of(2026, 11, 15)) // un dimanche
        assertEquals(LocalDate.of(2026, 11, 16), jours.first().jour)
        assertEquals(5, jours.size)
        assertEquals(listOf("Entrée", "Plat", "Laitage", "Dessert", "Goûter"), jours.first().rubriques().map { it.first })
    }

    @Test
    fun apresLeDejeunerLeJourSuivant() {
        val lundi = LocalDate.of(2026, 11, 16)
        val jours = joursDeMenu(d, lundi)
        assertEquals(0, jourParDefaut(jours, lundi, LocalTime.of(9, 0)))
        assertEquals(1, jourParDefaut(jours, lundi, LocalTime.of(15, 0)))
    }

    @Test
    fun actualitesPubliques() {
        assertTrue(d.actualites.isNotEmpty())
        assertTrue(d.actualites.none { it.titre.startsWith("Menu", ignoreCase = true) })
    }
}
