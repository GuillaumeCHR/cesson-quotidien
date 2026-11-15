package com.yogistudio.cesson.ui

import com.yogistudio.cesson.core.preferences.ConfigAccueil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TuilesTest {
    @Test
    fun ordreChoisiPuisNouvellesCartesSansLesMasquees() {
        val config = ConfigAccueil(ordre = listOf("mon_arret", "arrets"), masquees = setOf("carburants"))
        // « trains » n'existait pas quand l'ordre a été enregistré : il arrive à sa place par défaut, après.
        assertEquals(listOf("mon_arret", "arrets", "trains"), cartesVisibles(ONGLET_BOUGER, config).map { it.id })
    }

    @Test
    fun chaqueOngletASesCartesSansDoublon() {
        ONGLETS_PERSONNALISABLES.forEach { o ->
            assertTrue(o.id, o.tuiles.isNotEmpty())
            assertEquals(o.id, o.tuiles.size, o.tuiles.map { it.id }.toSet().size)
        }
        assertEquals(listOf("accueil", "bouger", "sortir", "pratique"), ONGLETS_PERSONNALISABLES.map { it.id })
    }
}
