package com.yogistudio.cesson.eau

import com.yogistudio.cesson.core.modele.FichierModule
import com.yogistudio.cesson.core.modele.JsonCesson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class VigicruesTest {
    private val serialiseur = FichierModule.serializer(DonneesVigicrues.serializer())

    /** Le fichier publié par le backend se décode tel quel : le contrat est tenu des deux côtés. */
    @Test
    fun decodeLeFichierDuBackend() {
        val f = JsonCesson.decodeFromString(serialiseur, File("src/test/resources/vigicrues.json").readText())
        assertEquals("vigicrues", f.module)
        assertEquals(Vigilance.VERT, f.donnees.vigilance)
        assertEquals("Vilaine amont", f.donnees.troncon.nom)
        assertNull(f.donnees.alerte)
        assertFalse(f.donnees.vigilance.estUneAlerte)
    }

    @Test
    fun niveauInconnuNeCassePasLApp() {
        val texte = File("src/test/resources/vigicrues.json").readText().replace("\"vert\"", "\"violet\"")
        val f = JsonCesson.decodeFromString(serialiseur, texte)
        assertEquals(Vigilance.INCONNUE, f.donnees.vigilance)
    }

    @Test
    fun jauneEtPlusSontDesAlertes() {
        assertTrue(listOf(Vigilance.JAUNE, Vigilance.ORANGE, Vigilance.ROUGE).all { it.estUneAlerte })
        assertFalse(Vigilance.INCONNUE.estUneAlerte)
    }
}
