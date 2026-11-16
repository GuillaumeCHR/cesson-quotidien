package com.yogistudio.cesson.environnement

import com.yogistudio.cesson.core.modele.FichierModule
import com.yogistudio.cesson.core.modele.JsonCesson
import kotlinx.serialization.KSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/** Fichiers réels publiés par le backend le 16 nov. 2026 (Atmo Data, Air Breizh). */
class AirTest {
    private fun <T> lire(module: String, s: KSerializer<T>): FichierModule<T> =
        JsonCesson.decodeFromString(FichierModule.serializer(s), File("src/test/resources/$module.json").readText())

    private val air = lire(MODULE_QUALITE_AIR, DonneesQualiteAir.serializer())
    private val pollens = lire(MODULE_POLLENS, DonneesPollens.serializer())
    private val episodes = lire(MODULE_EPISODES, DonneesEpisodes.serializer())
    private val le16 = LocalDate.parse("2026-11-16")

    @Test
    fun indiceAtmoDeRennesMetropole() {
        val j = air.donnees.jours.first()
        assertEquals("Rennes Métropole", air.donnees.zone)
        assertEquals("Moyen", j.libelle)
        assertEquals(0xFF50CCAA, j.argb())
        assertEquals(listOf("o3"), j.responsables)
        assertEquals("l'ozone (O₃)", nomPolluant(j.responsables.first()))
        assertEquals(5, j.sousIndices.size)
    }

    @Test
    fun couleurMalFormeeRetombeSurLEchelle() {
        assertEquals(couleurAtmo(3), air.donnees.jours.first().copy(code = 3, couleur = "jaune").argb())
    }

    @Test
    fun pollensHorsSaisonEnNovembre() {
        val j = pollens.donnees.jours.first()
        assertEquals("Cesson-Sévigné", pollens.donnees.commune)
        assertTrue(j.horsSaison)
        assertTrue(j.nomsResponsables().isEmpty())
        val enSaison = j.copy(code = 4, responsables = listOf("gram"),
            especes = j.especes.map { if (it.espece == "gram") it.copy(code = 4) else it })
        assertFalse(enSaison.horsSaison)
        assertEquals(listOf("Graminées"), enSaison.nomsResponsables())
    }

    @Test
    fun aucunEpisodeLe16() {
        assertEquals(3, episodes.donnees.jours.size)
        assertTrue(episodes.donnees.aVenir(le16).isEmpty())
    }

    @Test
    fun episodeDeDemainSeulementSiAVenir() {
        val avecEpisode = episodes.donnees.copy(jours = episodes.donnees.jours.map { j ->
            if (j.date == le16.plusDays(1)) j.copy(etat = EtatEpisode.INFORMATION,
                polluants = j.polluants.map { if (it.polluant == "PM10") it.copy(etat = EtatEpisode.INFORMATION) else it })
            else j
        })
        assertEquals(listOf(le16.plusDays(1)), avecEpisode.aVenir(le16).map { it.date })
        assertEquals("PM10", avecEpisode.aVenir(le16).first().polluantsVises())
        // Le surlendemain, l'épisode du 17 est passé.
        assertTrue(avecEpisode.aVenir(le16.plusDays(2)).isEmpty())
    }

    @Test
    fun etatInconnuNeCassePasLeDecodage() {
        val json = File("src/test/resources/$MODULE_EPISODES.json").readText().replace("\"aucun\"", "\"vigilance\"")
        val f = JsonCesson.decodeFromString(FichierModule.serializer(DonneesEpisodes.serializer()), json)
        assertEquals(EtatEpisode.INCONNU, f.donnees.jours.first().etat)
    }
}
