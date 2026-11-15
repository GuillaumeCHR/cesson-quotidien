package com.yogistudio.cesson.transport

import com.yogistudio.cesson.core.modele.FichierModule
import com.yogistudio.cesson.core.modele.JsonCesson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Fichier publié par le backend le dimanche 15 nov. 2026 vers midi. */
class CirculationTest {
    private val texte = File("src/test/resources/circulation.json").readText()
    private val d = JsonCesson.decodeFromString(FichierModule.serializer(DonneesCirculation.serializer()), texte).donnees

    @Test
    fun rocadeDansLesDeuxSens() {
        assertEquals(listOf("Rocade intérieure", "Rocade extérieure"), d.rocadeEst.map { it.libelle })
        assertTrue(d.rocadeEst.all { it.etat == EtatTrafic.FLUIDE })
    }

    @Test
    fun seulsLesAxesChargesSontSignales() {
        assertTrue(axesASignaler(d).none { it.etat == EtatTrafic.FLUIDE || it.etat == EtatTrafic.INCONNU })
        assertEquals("Saturé", EtatTrafic.SATURE.libelle())
    }
}
