package com.yogistudio.cesson.actus

import com.yogistudio.cesson.core.modele.FichierModule
import com.yogistudio.cesson.core.modele.JsonCesson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/** Fichier publié par le backend le 15 nov. 2026 (OpenAgenda et site de la ville). */
class AgendaTest {
    private val d = JsonCesson.decodeFromString(FichierModule.serializer(DonneesAgenda.serializer()),
        File("src/test/resources/agenda.json").readText()).donnees
    private val aujourdhui = LocalDate.of(2026, 11, 15)

    @Test
    fun parJourEnCommencantParAujourdhui() {
        val j = agendaParJour(d, null, aujourdhui)
        assertEquals(aujourdhui, j.first().first)
        assertEquals(j.map { it.first }.sorted(), j.map { it.first })
    }

    @Test
    fun saisonCulturelleAPart() {
        val saison = saisonParJour(d, aujourdhui).flatMap { it.second }
        assertTrue(saison.any { it.titre == "Suzane" } && saison.all { it.saison })
        // L'agenda ne montre plus les spectacles de la saison.
        assertTrue(agendaParJour(d, null, aujourdhui).flatMap { it.second }.none { it.saison })
    }

    @Test
    fun filtreParFamille() {
        val assos = agendaParJour(d, "associations", aujourdhui).flatMap { it.second }
        assertTrue(assos.isNotEmpty() && assos.all { it.famille == "associations" })
        assertTrue(agendaParJour(d, "sport", aujourdhui).flatMap { it.second }.any { it.titre.startsWith("Glaz Arena") })
    }

    @Test
    fun titresEnCapitalesRemisEnCasseDePhrase() {
        assertEquals("La folle journée", titreSpectacle("LA FOLLE JOURNÉE"))
        assertEquals("Suzane", titreSpectacle("Suzane"))
    }

    @Test
    fun libelles() {
        assertEquals("Aujourd'hui", libelleJour(aujourdhui, aujourdhui))
        assertEquals("Jeudi 19 novembre", libelleJour(LocalDate.of(2026, 11, 19), aujourdhui))
        val expo = d.evenements.first { it.titre == "Exposition Clémentine Carsberg" }
        assertEquals("jusqu'au 16 déc.", libelleJusquau(expo))
    }
}
