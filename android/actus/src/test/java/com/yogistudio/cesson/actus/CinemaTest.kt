package com.yogistudio.cesson.actus

import com.yogistudio.cesson.core.modele.FichierModule
import com.yogistudio.cesson.core.modele.JsonCesson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.LocalDate

/** Fichier publié par le backend le dimanche 15 nov. 2026, d'après la grille de novembre du cinéma. */
class CinemaTest {
    private val d = JsonCesson.decodeFromString(FichierModule.serializer(DonneesCinema.serializer()),
        File("src/test/resources/cinema.json").readText()).donnees
    private val maintenant = Instant.parse("2026-11-15T09:00:00Z")

    @Test
    fun joursAvecSeances() {
        val j = joursDeSeance(d, maintenant)
        assertEquals(LocalDate.of(2026, 11, 15), j.first())
        assertEquals(j.sorted(), j)
    }

    @Test
    fun seancesDuDimancheDansLOrdre() {
        val s = seancesDuJour(d, LocalDate.of(2026, 11, 15), maintenant)
        assertEquals(listOf("La bataille de Gaulle – Partie 2 : J’écris ton nom", "15/18", "La gradiva"), s.map { it.first.titre })
        assertTrue(s.first { it.first.titre == "15/18" }.first.sortieNationale)
    }

    @Test
    fun seanceDejaPasseeRetiree() {
        // Dimanche 15h : la séance de 14h00 est passée depuis plus d'un quart d'heure.
        val s = seancesDuJour(d, LocalDate.of(2026, 11, 15), Instant.parse("2026-11-15T14:00:00Z"))
        assertTrue(s.none { it.first.titre.startsWith("La bataille de Gaulle") })
    }
}
