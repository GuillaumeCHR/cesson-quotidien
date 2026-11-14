package com.yogistudio.cesson.core

import com.yogistudio.cesson.core.preferences.deplacer
import com.yogistudio.cesson.core.preferences.ordonnerTuiles
import com.yogistudio.cesson.core.ui.formaterDepuis
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class AccueilEtTempsTest {
    private val defaut = listOf("vilaine", "collecte", "arrets", "air")

    @Test
    fun ordreParDefautSansPreference() = assertEquals(defaut, ordonnerTuiles(defaut, emptyList()))

    @Test
    fun ordreEnregistreRespecteEtNouvellesTuilesAjouteesALaFin() =
        assertEquals(listOf("air", "vilaine", "collecte", "arrets"),
            ordonnerTuiles(defaut, listOf("air", "vilaine", "tuile-supprimee")))

    @Test
    fun deplacerAuxExtremitesEstSansEffet() {
        assertEquals(defaut, deplacer(defaut, "vilaine", -1))
        assertEquals(defaut, deplacer(defaut, "air", +1))
        assertEquals(listOf("collecte", "vilaine", "arrets", "air"), deplacer(defaut, "vilaine", +1))
    }

    @Test
    fun formatageRelatif() {
        val t = Instant.parse("2026-11-14T22:30:00Z")
        assertEquals("à l'instant", formaterDepuis(t, t.plusSeconds(30)))
        assertEquals("il y a 12 min", formaterDepuis(t, t.plusSeconds(12 * 60)))
        assertEquals("il y a 3 h", formaterDepuis(t, t.plusSeconds(3 * 3600 + 120)))
        assertEquals("le 14 nov. à 23h30", formaterDepuis(t, t.plusSeconds(30 * 3600)))
    }
}
