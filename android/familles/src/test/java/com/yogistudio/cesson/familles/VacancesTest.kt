package com.yogistudio.cesson.familles

import com.yogistudio.cesson.core.modele.FichierModule
import com.yogistudio.cesson.core.modele.JsonCesson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/** Fichier publié par le backend le 15 nov. 2026 (zone B). */
class VacancesTest {
    private val d = JsonCesson.decodeFromString(
        FichierModule.serializer(DonneesVacances.serializer()), File("src/test/resources/vacances_scolaires.json").readText()).donnees

    @Test
    fun prochainesVacances() {
        val s = situation(d, LocalDate.of(2026, 11, 15)) as SituationScolaire.AVenir
        assertEquals("Vacances de Noël", s.periode.nom)
        assertEquals(34L, s.dansJours)
        assertEquals("dans 4 semaines", dansJours(s.dansJours))
    }

    @Test
    fun enVacancesJusquAuDimanche() {
        val s = situation(d, LocalDate.of(2027, 1, 3)) as SituationScolaire.EnVacances
        assertEquals("Vacances de Noël", s.periode.nom)
        assertEquals(0L, s.joursRestants)
        // Le jour de la reprise, on n'est plus en vacances.
        assertTrue(situation(d, LocalDate.of(2027, 1, 4)) is SituationScolaire.AVenir)
    }

    @Test
    fun pont() {
        val pont = d.periodes.first { it.pont }
        assertEquals(LocalDate.of(2027, 5, 7), pont.dernierJour())
        assertEquals("vendredi 7 mai", pont.dates())
        assertTrue(situation(d, LocalDate.of(2027, 5, 7)) is SituationScolaire.EnVacances)
    }
}
