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
    fun filtreCarreSevigne() {
        val j = agendaParJour(d, "carre_sevigne", aujourdhui)
        assertTrue(j.flatMap { it.second }.all { it.theme == "carre_sevigne" })
        assertTrue(j.flatMap { it.second }.any { it.titre == "Suzane" })
    }

    @Test
    fun libelles() {
        assertEquals("Aujourd'hui", libelleJour(aujourdhui, aujourdhui))
        assertEquals("Jeudi 19 novembre", libelleJour(LocalDate.of(2026, 11, 19), aujourdhui))
        val expo = d.evenements.first { it.titre == "Exposition Clémentine Carsberg" }
        assertEquals("jusqu'au 16 déc.", libelleJusquau(expo))
    }
}
