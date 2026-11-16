package com.yogistudio.cesson.environnement

import com.yogistudio.cesson.core.modele.FichierModule
import com.yogistudio.cesson.core.modele.JsonCesson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.LocalTime

/** Fichier réel publié par le backend le 16 nov. 2026 au soir : vigilance jaune le 17 en Ille-et-Vilaine. */
class VigilanceMeteoTest {
    private val json = File("src/test/resources/vigilance_meteo.json").readText()
    private val d = JsonCesson.decodeFromString(FichierModule.serializer(DonneesVigilanceMeteo.serializer()), json).donnees
    private val soirDu16 = Instant.parse("2026-11-16T21:00:00Z")

    @Test
    fun jauneDemainPourTroisPhenomenes() {
        val j = d.aSignaler(soirDu16).single()
        assertEquals("2026-11-17", j.date().toString())
        assertEquals(CouleurVigilance.JAUNE, j.aSignaler().couleurMax())
        assertEquals(listOf("vent violent", "pluie-inondation", "orages"), j.aSignaler().map { it.nom })
    }

    @Test
    fun plagesEnHeureDeParis() {
        val j = d.aSignaler(soirDu16).single()
        assertEquals("vent violent jusqu'à 8h et de 15h à 20h", j.aSignaler()[0].description())
        assertEquals("pluie-inondation jusqu'à 6h", j.aSignaler()[1].description())
        assertEquals("orages de 14h à 20h", j.aSignaler()[2].description())
    }

    @Test
    fun notificationDesJaune() {
        val n = notificationsVigilance(d, soirDu16, CouleurVigilance.JAUNE, emptySet()).single()
        assertEquals("vigilance:2026-11-17:jaune:1-2-3", n.cle)
        assertEquals("Vigilance jaune demain en Ille-et-Vilaine", n.titre)
        assertTrue(n.texte.startsWith("Vent violent jusqu'à 8h et de 15h à 20h ; pluie-inondation jusqu'à 6h ; orages de 14h à 20h. Soyez attentif"))
        assertTrue(n.texte.endsWith("Source : Météo-France, bulletin du 16/11 à 16h02."))
        assertTrue(notificationsVigilance(d, soirDu16, CouleurVigilance.JAUNE, setOf(n.cle)).isEmpty())
    }

    @Test
    fun seuilOrangeNeDeclenchePasSurDuJaune() {
        assertTrue(notificationsVigilance(d, soirDu16, CouleurVigilance.ORANGE, emptySet()).isEmpty())
    }

    @Test
    fun aggravationEnOrangePrevientDeNouveau() {
        val orange = d.copy(jours = d.jours.map { j ->
            j.copy(phenomenes = j.phenomenes.map { if (it.nom == "orages") it.copy(couleur = CouleurVigilance.ORANGE) else it })
        })
        val n = notificationsVigilance(orange, soirDu16, CouleurVigilance.ORANGE, setOf("vigilance:2026-11-17:jaune:1-2-3")).single()
        assertEquals("vigilance:2026-11-17:orange:3", n.cle)
        assertEquals("Vigilance orange demain en Ille-et-Vilaine", n.titre)
    }

    @Test
    fun finieApresLeDernierJour() {
        assertTrue(d.aSignaler(Instant.parse("2026-11-17T23:30:00Z")).isEmpty())
    }

    @Test
    fun couleurInconnueNeCassePasLeDecodage() {
        val f = JsonCesson.decodeFromString(FichierModule.serializer(DonneesVigilanceMeteo.serializer()),
            json.replace(Regex("\"couleur\": ?\"jaune\""), "\"couleur\": \"violette\""))
        assertTrue(f.donnees.aSignaler(soirDu16).isEmpty())
    }

    @Test
    fun controlesHorairesQuandLaVigilanceEstSuivie() {
        // 16/11 à 12h00 à Paris : avec la vigilance, prochain contrôle à 12h35 au lieu de 16h30.
        val midi = Instant.parse("2026-11-16T11:00:00Z")
        assertEquals(Instant.parse("2026-11-16T11:35:00Z"), prochainControleAir(midi, CONTROLES_AIR + CONTROLES_VIGILANCE))
        // Après 22h35 : le lendemain à 7h30.
        assertEquals(Instant.parse("2026-11-17T06:30:00Z"), prochainControleAir(Instant.parse("2026-11-16T21:40:00Z"),
            CONTROLES_AIR + CONTROLES_VIGILANCE))
        assertEquals(LocalTime.of(22, 35), CONTROLES_VIGILANCE.last())
    }
}
