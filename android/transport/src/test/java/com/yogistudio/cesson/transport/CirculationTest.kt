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

    @Test
    fun tracesDuPlanDansLeCadreDeCesson() {
        assertEquals(316, d.troncons.size)
        val xy = d.troncons.flatMap { t -> t.points.map { com.yogistudio.cesson.core.carte.CarteCesson.projeter(it[0], it[1]) } }
        // La rocade est longe la commune : tout tient dans le cadre de la carte, à peine débordé.
        assertTrue(xy.all { (x, y) -> x in -0.05f..(com.yogistudio.cesson.core.carte.CarteCesson.LARGEUR + 0.05f) && y in -0.05f..1.05f })
        // Le plus grave en dernier, pour être dessiné par-dessus.
        val gravite = listOf(EtatTrafic.INCONNU, EtatTrafic.FLUIDE, EtatTrafic.DENSE, EtatTrafic.SATURE, EtatTrafic.BLOQUE)
        val rangs = d.troncons.map { gravite.indexOf(it.etat) }
        assertEquals(rangs.sorted(), rangs)
    }
}
