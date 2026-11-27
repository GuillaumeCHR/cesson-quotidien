package com.yogistudio.cesson.familles

import com.yogistudio.cesson.core.modele.FichierModule
import com.yogistudio.cesson.core.modele.JsonCesson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.LocalTime

/** Fichier publié par le backend le 15 nov. 2026 : menus de novembre, actualités publiques du portail. */
class FamillesTest {
    private val d = JsonCesson.decodeFromString(
        FichierModule.serializer(DonneesFamilles.serializer()), File("src/test/resources/familles.json").readText()).donnees

    @Test
    fun semaineAVenir() {
        val jours = joursDeMenu(d, LocalDate.of(2026, 11, 15)) // un dimanche
        assertEquals(LocalDate.of(2026, 11, 16), jours.first().jour)
        assertEquals(5, jours.size)
        assertEquals(listOf("Entrée", "Plat", "Laitage", "Dessert", "Goûter"), jours.first().rubriques().map { it.first })
    }

    @Test
    fun apresLeDejeunerLeJourSuivant() {
        val lundi = LocalDate.of(2026, 11, 16)
        val jours = joursDeMenu(d, lundi)
        assertEquals(0, jourParDefaut(jours, lundi, LocalTime.of(9, 0)))
        assertEquals(1, jourParDefaut(jours, lundi, LocalTime.of(15, 0)))
    }

    @Test
    fun actualitesPubliques() {
        assertTrue(d.actualites.isNotEmpty())
        assertTrue(d.actualites.none { it.titre.startsWith("Menu", ignoreCase = true) })
    }

    // --- Vacances scolaires -------------------------------------------------------------------------

    private val vacances = JsonCesson.decodeFromString(
        FichierModule.serializer(DonneesVacances.serializer()), File("src/test/resources/vacances_scolaires.json").readText()).donnees

    /** Menus de décembre (14-18) et de la reprise (4-8 janvier), plus une mention « vacances » le 21/12. */
    private fun menu(j: String, note: String? = null) =
        MenuDuJour(jour = LocalDate.parse(j), plat = if (note == null) listOf("Plat") else emptyList(), note = note)
    private val autourDeNoel = DonneesFamilles(menus = listOf("2026-12-14", "2026-12-15", "2026-12-16", "2026-12-17",
        "2026-12-18").map { menu(it) } + menu("2026-12-21", "Vacances scolaires") +
        listOf("2027-01-04", "2027-01-05", "2027-01-06", "2027-01-07", "2027-01-08").map { menu(it) })

    @Test
    fun avantLesVacancesOnCoupeEtOnAnnonce() {
        val vue = vueMenus(autourDeNoel, vacances, LocalDate.of(2026, 12, 16))
        assertEquals(listOf(16, 17, 18), vue.jours.map { it.jour.dayOfMonth })
        assertEquals(null, vue.enCours)
        assertEquals("Puis vacances de Noël à partir du samedi 19 décembre, reprise le lundi 4 janvier.",
            texteVacancesEnsuite(vue.ensuite!!))
    }

    @Test
    fun pendantLesVacancesLaReprise() {
        val noel = LocalDate.of(2026, 12, 22)
        val vue = vueMenus(autourDeNoel, vacances, noel)
        assertEquals("Vacances de Noël", vue.enCours!!.nom)
        assertEquals(LocalDate.of(2027, 1, 4), vue.jours.first().jour) // la mention du 21/12 est écartée
        assertEquals(5, vue.jours.size)
        assertEquals("Pas de cantine jusqu'au dimanche 3 janvier, reprise le lundi 4 janvier.",
            texteVacancesEnCours(vue.enCours!!, noel, vue.menusPendant))
        assertTrue(pucePorteLeMois(vue.jours.first().jour, noel))
    }

    @Test
    fun pendantLesVacancesRepriseNonPubliee() {
        val sansJanvier = autourDeNoel.copy(menus = autourDeNoel.menus.filter { it.jour.year == 2026 })
        val vue = vueMenus(sansJanvier, vacances, LocalDate.of(2026, 12, 22))
        assertTrue(vue.jours.isEmpty())
        assertEquals("Vacances de Noël", vue.enCours!!.nom)
    }

    @Test
    fun vraiMenuPendantLesVacancesGarde() {
        val avecCentre = autourDeNoel.copy(menus = autourDeNoel.menus + menu("2026-12-23"))
        val vue = vueMenus(avecCentre, vacances, LocalDate.of(2026, 12, 22))
        assertEquals(LocalDate.of(2026, 12, 23), vue.jours.first().jour)
        assertTrue(vue.menusPendant)
    }

    @Test
    fun semaineOrdinaireSansMois() {
        val vue = vueMenus(d, vacances, LocalDate.of(2026, 11, 16))
        assertEquals(5, vue.jours.size)
        assertEquals(null, vue.ensuite)
        assertTrue(vue.jours.none { pucePorteLeMois(it.jour, LocalDate.of(2026, 11, 16)) })
    }
}
