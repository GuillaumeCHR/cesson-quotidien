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
    fun trainSupprimeGardeJusquAFinFixeeParLeServeur() {
        val premier = decoder().departs.first { it.direction == "rennes" }
        val fin = premier.heureTheorique.plusSeconds(40 * 60)
        val supprime = premier.copy(statut = StatutTrain.SUPPRIME, visibleJusquA = fin)
        val d = decoder().let { it.copy(departs = listOf(supprime) + it.departs.drop(1)) }
        // 20 min après son horaire : toujours là, barré « supprimé ».
        val apres20 = departsDeLaDirection(d, "rennes", premier.heureTheorique.plusSeconds(20 * 60))
        assertEquals(StatutTrain.SUPPRIME, apres20.first().statut)
        // Fin d'affichage dépassée : retiré.
        assertTrue(departsDeLaDirection(d, "rennes", fin.plusSeconds(1)).none { it.numero == premier.numero })
    }

    @Test
    fun sansFinDuServeurDeuxMinutesApresLeDepart() {
        val t = Instant.parse("2026-11-15T12:00:00Z")
        assertEquals(t.plusSeconds(120), finAffichage(null, null, t))
        assertEquals(t.plusSeconds(600 + 120), finAffichage(null, t.plusSeconds(600), t))
        assertEquals(t.plusSeconds(3600), finAffichage(t.plusSeconds(3600), null, t))
    }

    @Test
    fun lesTrainsPassesSAjoutentAuxProchains() {
        val maintenant = Instant.parse("2026-11-15T12:00:00Z")
        val heures = listOf(-600L, 300L, 900L, 1500L).map { maintenant.plusSeconds(it) }
        assertEquals(heures.take(3), prochainsEtPasses(heures, 2, maintenant) { it })
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
