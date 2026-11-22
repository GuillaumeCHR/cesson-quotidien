package com.yogistudio.cesson.pratique

import com.yogistudio.cesson.core.modele.FichierModule
import com.yogistudio.cesson.core.modele.JsonCesson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.LocalDate

/** Fichier publié par le backend le dimanche 15 nov. 2026 (8 semaines de passages). */
class CollecteTest {
    private val d = JsonCesson.decodeFromString(
        FichierModule.serializer(DonneesCollecte.serializer()),
        File("src/test/resources/collecte.json").readText(),
    ).donnees

    private fun a(iso: String) = Instant.parse(iso)

    @Test
    fun prochainsPassages() {
        val p = prochaines(d, a("2026-11-15T09:00:00Z"))
        assertEquals(listOf("gris", "jaune"), p.map { it.flux.bac })
        assertEquals(LocalDate.of(2026, 11, 23), p[0].passage!!.date)
        assertEquals(LocalDate.of(2026, 11, 25), p[1].passage!!.date)
    }

    @Test
    fun rappelLaVeilleApresMidi() {
        // Dimanche 22 nov. : rien le matin, rappel du bac gris à partir de midi.
        assertTrue(aSortirCeSoir(d, a("2026-11-22T09:00:00Z")).isEmpty())
        assertEquals(listOf("gris"), aSortirCeSoir(d, a("2026-11-22T17:00:00Z")).map { it.bac })
    }

    @Test
    fun collecteDuJourPasseApresMidi() {
        // Lundi 23 nov. : « aujourd'hui » le matin, la suivante (7 déc.) l'après-midi.
        assertEquals(LocalDate.of(2026, 11, 23), prochaines(d, a("2026-11-23T07:00:00Z"))[0].passage!!.date)
        assertEquals(LocalDate.of(2026, 12, 7), prochaines(d, a("2026-11-23T14:00:00Z"))[0].passage!!.date)
    }

    @Test
    fun avisDeFinDAnnee() {
        assertTrue(avisEnCours(d, a("2026-11-15T09:00:00Z")).isEmpty())
        assertTrue(avisEnCours(d, a("2026-12-20T09:00:00Z")).single().texte.contains("semaines 53 et 1"))
    }

    @Test
    fun libelles() {
        val maintenant = a("2026-11-15T09:00:00Z")
        assertEquals("demain (lundi)", formaterJourCollecte(LocalDate.of(2026, 11, 16), maintenant))
        assertEquals("lundi 23 nov.", formaterJourCollecte(LocalDate.of(2026, 11, 23), maintenant))
    }

    @Test
    fun rappelLaVeilleA17h30() {
        // Dimanche 15 nov. : premier passage lundi 23 (bac gris), rappel dimanche 22 à 17h30.
        assertEquals(a("2026-11-22T16:30:00Z"), prochainRappel(d, a("2026-11-15T09:00:00Z")))
        // Juste après ce rappel, le suivant est mardi 24 à 17h30 (bac jaune du mercredi 25).
        assertEquals(a("2026-11-24T16:30:00Z"), prochainRappel(d, a("2026-11-22T16:30:00Z")))
    }

    @Test
    fun bacsDuRappel() {
        assertTrue(bacsARappeler(d, a("2026-11-22T16:25:00Z")).isEmpty()) // 17h25 : trop tôt
        val bacs = bacsARappeler(d, a("2026-11-22T16:35:00Z"))
        assertEquals(listOf("gris"), bacs.map { it.bac })
        assertEquals("Sortez le bac gris ce soir" to "Collecte demain matin dès 5h30 (ordures ménagères).", texteRappel(bacs))
        assertTrue(bacsARappeler(d, a("2026-11-23T16:35:00Z")).isEmpty()) // lundi soir : rien mardi
    }
}
