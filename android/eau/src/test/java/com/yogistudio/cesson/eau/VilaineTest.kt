package com.yogistudio.cesson.eau

import com.yogistudio.cesson.core.modele.FichierModule
import com.yogistudio.cesson.core.modele.JsonCesson
import com.yogistudio.cesson.core.modele.Tendance
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.time.Instant

class VilaineTest {
    /** Le fichier publié par le backend se décode tel quel : le contrat est tenu des deux côtés. */
    @Test
    fun decodeLeFichierDuBackend() {
        val texte = File("src/test/resources/vilaine.json").readText()
        val f = JsonCesson.decodeFromString(FichierModule.serializer(DonneesVilaine.serializer()), texte)
        assertEquals("vilaine", f.module)
        assertEquals("mm", f.donnees.mesure.unite)
        assertEquals(Instant.parse("2026-11-14T22:30:00Z"), f.donnees.mesure.horodatage)
        assertEquals(Tendance.STABLE, f.donnees.mesure.tendance)
    }

    @Test
    fun hauteurEnMetres() {
        val m = com.yogistudio.cesson.core.modele.Mesure("s", "H", 215.0, "mm", Instant.EPOCH)
        assertEquals("0,22 m", formaterHauteur(m).replace(' ', ' '))
        assertEquals("3,73 m", formaterHauteur(m.copy(valeur = 3730.0)))
    }
}
