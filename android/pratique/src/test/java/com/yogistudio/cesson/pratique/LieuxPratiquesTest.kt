package com.yogistudio.cesson.pratique

import com.yogistudio.cesson.core.modele.Coord
import com.yogistudio.cesson.core.modele.FichierModule
import com.yogistudio.cesson.core.modele.JsonCesson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Fichiers publiés par le backend le 15 nov. 2026. */
class LieuxPratiquesTest {
    private val dae = JsonCesson.decodeFromString(FichierModule.serializer(DonneesDefibrillateurs.serializer()),
        File("src/test/resources/defibrillateurs.json").readText()).donnees.defibrillateurs
    private val bornes = JsonCesson.decodeFromString(FichierModule.serializer(DonneesBornes.serializer()),
        File("src/test/resources/bornes.json").readText()).donnees.stations

    @Test
    fun accessiblesATousMomentsEnTete() {
        assertTrue(dae.first().toujoursAccessible)
        assertTrue(dae.size > 90)
    }

    @Test
    fun triParProximite() {
        // À la mairie de Cesson : le premier de la liste est le plus proche.
        val mairie = Coord(48.1198, -1.6032)
        val tries = parProximite(dae, mairie) { it.lieu }
        val distances = tries.map { it.second!! }
        assertEquals(distances.sorted(), distances)
        assertTrue(distances.first() < 500)
    }

    @Test
    fun sansPositionOrdreDuBackend() {
        assertEquals(bornes.map { it.lieu.id }, parProximite(bornes, null) { it.lieu }.map { it.first.lieu.id })
    }

    @Test
    fun formats() {
        assertEquals("350 m", formaterDistance(347.0))
        assertEquals("1,2 km", formaterDistance(1234.0))
        assertEquals("300 kW", formaterPuissance(300.0))
        assertEquals("22 kW", formaterPuissance(22.0))
        assertEquals("22,1 kW", formaterPuissance(22.08))
    }
}
