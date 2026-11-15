package com.yogistudio.cesson.transport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Réponse réelle de la STAR, 15 nov. 2026 au soir. */
class ParcsRelaisTest {
    private val parcs = decoderParcsRelais(File("src/test/resources/parcs_relais.json").readText())

    @Test
    fun viaSilva() {
        val p = parcs.first { it.idparc == PARC_VIASILVA }
        assertEquals("Cesson-Viasilva", p.nom)
        assertTrue(p.ouvert && !p.complet)
        assertEquals(648, p.jrdinfosoliste)
        assertEquals(757, p.capacitesoliste)
        assertEquals(0.144f, p.occupation, 0.001f)
        assertTrue(p.misAJour != null)
    }

    @Test
    fun parcFermeNiOuvertNiComplet() {
        val f = parcs.first { !it.ouvert }
        assertFalse(f.complet)
        assertTrue(PARCS_VOISINS.all { (id, _) -> parcs.any { it.idparc == id } })
    }
}
