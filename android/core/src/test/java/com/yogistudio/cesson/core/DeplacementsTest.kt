package com.yogistudio.cesson.core

import com.yogistudio.cesson.core.preferences.ConfigAccueil
import com.yogistudio.cesson.core.preferences.MODE_STAR
import com.yogistudio.cesson.core.preferences.MODE_TRAIN
import com.yogistudio.cesson.core.preferences.MODE_VOITURE
import com.yogistudio.cesson.core.preferences.MOTORISATION_ELECTRIQUE
import com.yogistudio.cesson.core.preferences.MOTORISATION_HYBRIDE
import com.yogistudio.cesson.core.preferences.configSelonDeplacements
import com.yogistudio.cesson.core.preferences.tuilesUtiles
import org.junit.Assert.assertEquals
import org.junit.Test

class DeplacementsTest {
    private val accueil = listOf("meteo", "vilaine", "arrets", "mon_arret", "trains", "circulation", "collecte", "carburants", "air")
    private val bouger = listOf("arrets", "parc_relais", "trains", "mon_arret", "circulation", "carburants", "bornes")

    @Test
    fun cartesUtiles() {
        assertEquals(setOf("trains"), tuilesUtiles(setOf(MODE_TRAIN), null))
        assertEquals(setOf("circulation", "parc_relais", "bornes"), tuilesUtiles(setOf(MODE_VOITURE), MOTORISATION_ELECTRIQUE))
        assertEquals(setOf("circulation", "parc_relais", "carburants", "bornes"), tuilesUtiles(setOf(MODE_VOITURE), MOTORISATION_HYBRIDE))
        assertEquals(setOf("arrets", "mon_arret", "trains"), tuilesUtiles(setOf(MODE_STAR, MODE_TRAIN), null))
    }

    @Test
    fun accueilTrainEtVoitureThermique() {
        val utiles = tuilesUtiles(setOf(MODE_TRAIN, MODE_VOITURE), "thermique")
        val c = configSelonDeplacements(accueil, ConfigAccueil(emptyList(), setOf("air")), utiles, masquerInutiles = true)
        // Les cartes de transport utiles d'abord, à la place des cartes de transport ; le reste ne bouge pas.
        assertEquals(listOf("meteo", "vilaine", "trains", "circulation", "carburants", "arrets", "mon_arret", "collecte", "air"), c.ordre)
        assertEquals(setOf("air", "arrets", "mon_arret"), c.masquees)
    }

    @Test
    fun bougerReordonneSansMasquer() {
        val c = configSelonDeplacements(bouger, ConfigAccueil(emptyList(), setOf("trains")), tuilesUtiles(setOf(MODE_TRAIN), null), false)
        assertEquals("trains", c.ordre.first())
        assertEquals(emptySet<String>(), c.masquees) // une carte utile masquée auparavant réapparaît
    }
}
