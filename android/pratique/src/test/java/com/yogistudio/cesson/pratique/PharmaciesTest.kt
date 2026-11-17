package com.yogistudio.cesson.pratique

import com.yogistudio.cesson.core.modele.FichierModule
import com.yogistudio.cesson.core.modele.JsonCesson
import com.yogistudio.cesson.equipements.Ouverture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class PharmaciesTest {
    private fun a(texte: String) = LocalDateTime.parse(texte).atZone(ZoneId.of("Europe/Paris")).toInstant()

    @Test
    fun paques() {
        assertEquals(LocalDate.of(2026, 4, 5), dimancheDePaques(2026))
        assertEquals(LocalDate.of(2027, 3, 28), dimancheDePaques(2027))
        assertEquals(LocalDate.of(2025, 4, 20), dimancheDePaques(2025))
    }

    @Test
    fun joursFeries() {
        assertTrue(estFerie(LocalDate.of(2026, 11, 11)))
        assertTrue(estFerie(LocalDate.of(2026, 4, 6)))   // lundi de Pâques
        assertTrue(estFerie(LocalDate.of(2026, 5, 14)))  // Ascension
        assertTrue(estFerie(LocalDate.of(2026, 5, 25)))  // lundi de Pentecôte
        assertFalse(estFerie(LocalDate.of(2026, 11, 17)))
    }

    @Test
    fun periodes() {
        assertEquals(PeriodePharmacie.Ouvertes, periodePharmacie(a("2026-11-17T10:00")))       // mardi
        assertEquals(PeriodePharmacie.Ouvertes, periodePharmacie(a("2026-11-21T19:59")))       // samedi
        assertEquals(PeriodePharmacie.GardeNuit, periodePharmacie(a("2026-11-17T20:00")))
        assertEquals(PeriodePharmacie.GardeNuit, periodePharmacie(a("2026-11-18T07:59")))
        assertEquals(PeriodePharmacie.GardeJour, periodePharmacie(a("2026-11-22T08:00")))      // dimanche
        assertEquals(PeriodePharmacie.GardeJour, periodePharmacie(a("2026-11-11T15:00")))      // 11 novembre, un mercredi
        assertEquals(PeriodePharmacie.GardeNuit, periodePharmacie(a("2026-11-22T21:00")))
    }

    /** Fichier publié par le backend le 17 nov. 2026 (5 officines, horaires OpenStreetMap). */
    private val pharmacies = JsonCesson.decodeFromString(FichierModule.serializer(DonneesPharmacies.serializer()),
        File("src/test/resources/pharmacies.json").readText()).donnees.pharmacies

    @Test
    fun ouvertesDAbordPuisParProchaineOuverture() {
        assertEquals(5, pharmacies.size)
        // Mardi 17 à 8 h 45 : Beausoleil et Le Brun ouvrent à 8 h 30, les autres à 9 h.
        val matin = pharmaciesParOuverture(pharmacies, a("2026-11-17T08:45"))
        assertEquals(2, matin.count { it.second is Ouverture.Ouvert })
        assertTrue(matin.take(2).all { it.second is Ouverture.Ouvert })
        // Le Brun ferme à 19 h 30, Beausoleil à 12 h 30 : celle qui ferme le plus tard d'abord.
        assertEquals("Pharmacie Le Brun", matin.first().first.lieu.nom)
        // Mardi 13 h 30 : Bouriel et Salmon (9 h-20 h) et Le Brun (8 h 30-19 h 30) sans pause de midi.
        val midi = pharmaciesParOuverture(pharmacies, a("2026-11-17T13:30"))
        assertEquals(setOf("Pharmacie Bouriel et Salmon", "Pharmacie Le Brun"),
            midi.filter { it.second is Ouverture.Ouvert }.map { it.first.lieu.nom }.toSet())
        val nuit = pharmaciesParOuverture(pharmacies, a("2026-11-17T22:00"))
        assertTrue(nuit.none { it.second is Ouverture.Ouvert })
    }
}
