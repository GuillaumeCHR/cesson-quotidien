package com.yogistudio.cesson.environnement

import com.yogistudio.cesson.core.modele.FichierModule
import com.yogistudio.cesson.core.modele.JsonCesson
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.LocalDate

class MeteoTest {
    private val fichier = JsonCesson.decodeFromString(
        FichierModule.serializer(DonneesMeteo.serializer()),
        File("src/test/resources/meteo.json").readText(),
    )

    /** Le fichier publié par le backend se décode tel quel. */
    @Test
    fun decodeLeFichierDuBackend() {
        assertEquals("meteo", fichier.module)
        assertEquals(LocalDate.parse("2026-11-15"), fichier.donnees.jours.first().date)
        assertEquals(24, fichier.donnees.heures.size)
    }

    @Test
    fun creneauxAPartirDeLHeureEnCoursUneHeureSurDeux() {
        val heures = fichier.donnees.heures
        val maintenant = heures[3].horodatage.plusSeconds(20 * 60)
        val c = prochainsCreneaux(heures, maintenant)
        assertEquals(6, c.size)
        assertEquals(heures[3].horodatage, c[0].horodatage)
        assertEquals(heures[5].horodatage, c[1].horodatage)
    }

    @Test
    fun formats() {
        assertEquals("4°", degres(3.6))
        assertEquals("-1°", degres(-0.6))
        assertEquals("8,6 mm", millimetres(8.6))
        assertEquals(Instant.parse("2026-11-15T07:13:00Z"), fichier.donnees.jours.first().lever)
    }
}
