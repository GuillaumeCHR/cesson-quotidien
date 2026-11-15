package com.yogistudio.cesson.transport

import com.yogistudio.cesson.core.modele.FichierModule
import com.yogistudio.cesson.core.modele.JsonCesson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant

/** Fichier publié par le backend le dimanche 15 nov. 2026 vers 9h45 (horaires prévus seulement). */
class TrainsTest {
    private val texte = File("src/test/resources/trains.json").readText()
    private fun decoder(t: String = texte) =
        JsonCesson.decodeFromString(FichierModule.serializer(DonneesTrains.serializer()), t).donnees

    @Test
    fun decodeLeFichierDuBackend() {
        val d = decoder()
        assertEquals("Halte de Cesson-Sévigné", d.halte.nom)
        assertEquals(listOf("rennes", "est"), d.directions.map { it.id })
        assertTrue(d.departs.all { it.statut == StatutTrain.THEORIQUE })
    }

    @Test
    fun seulementLesTrainsDuJour() {
        val d = decoder()
        // Dimanche 15 nov., 10h à Cesson : les trains de lundi ne sont pas montrés.
        val dimanche = departsDeLaDirection(d, "rennes", Instant.parse("2026-11-15T09:00:00Z"))
        assertEquals(listOf("2026-11-15T12:51:00Z", "2026-11-15T17:55:00Z"), dimanche.map { it.heureTheorique.toString() })
        // Dimanche 21h : plus de train aujourd'hui, même si lundi en compte.
        assertTrue(departsDeLaDirection(d, "rennes", Instant.parse("2026-11-15T20:00:00Z")).isEmpty())
        assertTrue(d.departs.any { it.heureTheorique.toString().startsWith("2026-11-16") })
    }

    @Test
    fun departsParDirectionSansLesTrainsPartis() {
        val d = decoder()
        val apres = d.departs.first { it.direction == "rennes" }.heureTheorique.plusSeconds(300)
        val rennes = departsDeLaDirection(d, "rennes", apres)
        assertTrue(rennes.all { it.direction == "rennes" && it.heureTheorique.isAfter(apres.minusSeconds(120)) })
        assertEquals(rennes.sortedBy { it.heureTheorique }, rennes)
    }

    @Test
    fun statutsEnToutesLettres() {
        val t = decoder().departs.first()
        assertEquals("+7 min", libelleStatut(t.copy(statut = StatutTrain.RETARD, retardMin = 7)))
        assertEquals("supprimé", libelleStatut(t.copy(statut = StatutTrain.SUPPRIME)))
        assertEquals("horaire prévu", libelleStatut(t))
        // Un statut inconnu d'une future version du backend retombe sur « horaire prévu ».
        val inconnu = decoder(texte.replaceFirst("\"theorique\"", "\"detourne\""))
        assertEquals(StatutTrain.THEORIQUE, inconnu.departs.first().statut)
    }
}
