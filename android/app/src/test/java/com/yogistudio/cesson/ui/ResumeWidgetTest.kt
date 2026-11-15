package com.yogistudio.cesson.ui

import com.yogistudio.cesson.core.modele.FichierModule
import com.yogistudio.cesson.core.modele.JsonCesson
import com.yogistudio.cesson.pratique.DonneesCollecte
import com.yogistudio.cesson.transport.DonneesTrains
import com.yogistudio.cesson.widget.resumer
import kotlinx.serialization.KSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant

/** Contenu du widget, sur les fichiers réels publiés par le backend (ressources de test des autres modules). */
class ResumeWidgetTest {
    private fun <T> lire(chemin: String, s: KSerializer<T>): T =
        JsonCesson.decodeFromString(FichierModule.serializer(s), File(chemin).readText()).donnees

    private val collecte = lire("../pratique/src/test/resources/collecte.json", DonneesCollecte.serializer())
    private val trains = lire("../transport/src/test/resources/trains.json", DonneesTrains.serializer())

    @Test
    fun veilleDeCollecteEnAlerte() {
        // Dimanche 22 nov. 2026, 18h : bac gris lundi, à sortir ce soir.
        val r = resumer(Instant.parse("2026-11-22T17:00:00Z"), null, null, null, collecte, "Bourgchevreuil", null, null)
        val l = r.lignes.single { it.etiquette == "Collecte" }
        assertTrue(l.alerte && l.texte.startsWith("Bac gris demain") && l.texte.endsWith("à sortir ce soir"))
    }

    @Test
    fun prochainsTrainsEnHeureDHorloge() {
        // Dimanche 15 nov. 2026, 10h : prochains trains de la journée dans chaque direction.
        val r = resumer(Instant.parse("2026-11-15T09:00:00Z"), null, null, null, null, "ViaSilva", null, trains)
        val t = r.lignes.filter { it.etiquette == "Train" }.map { it.texte }
        assertEquals(2, t.size)
        assertTrue(t.any { it.startsWith("Rennes · ") } && t.all { Regex("\\d{1,2}h\\d{2}").containsMatchIn(it) })
    }

    @Test
    fun sansDonneesAucuneLigne() {
        assertTrue(resumer(Instant.EPOCH, null, null, null, null, "x", null, null).lignes.isEmpty())
    }
}
