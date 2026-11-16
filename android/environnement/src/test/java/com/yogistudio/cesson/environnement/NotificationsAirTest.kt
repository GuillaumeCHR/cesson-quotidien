package com.yogistudio.cesson.environnement

import com.yogistudio.cesson.core.modele.FichierModule
import com.yogistudio.cesson.core.modele.JsonCesson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.LocalDate

class NotificationsAirTest {
    private val pollens = JsonCesson.decodeFromString(FichierModule.serializer(DonneesPollens.serializer()),
        File("src/test/resources/pollens.json").readText()).donnees
    private val episodes = JsonCesson.decodeFromString(FichierModule.serializer(DonneesEpisodes.serializer()),
        File("src/test/resources/episodes_pollution.json").readText()).donnees
    private val le16 = LocalDate.parse("2026-11-16")

    /** Pollens de demain (17/11) passés à [code], graminées en tête. */
    private fun pollensDemain(code: Int, alerte: Boolean = false) = pollens.copy(jours = pollens.jours.map { j ->
        if (j.date != le16.plusDays(1)) j
        else j.copy(code = code, libelle = libellePollen(code), alerte = alerte, responsables = listOf("gram"),
            especes = j.especes.map { if (it.espece == "gram") it.copy(code = code) else it })
    })

    @Test
    fun rienEnNovembre() {
        assertTrue(notificationsPollens(pollens, le16, 3, emptySet()).isEmpty())
        assertTrue(notificationsEpisodes(episodes, le16, emptySet()).isEmpty())
    }

    @Test
    fun pollensEleveDemainAuSeuilParDefaut() {
        val n = notificationsPollens(pollensDemain(4), le16, 4, emptySet()).single()
        assertEquals("pollens:2026-11-17", n.cle)
        assertEquals("Pollens : risque élevé demain à Cesson-Sévigné", n.titre)
        assertEquals("Indice élevé, surtout graminées. Source : Atmo France / AASQA.", n.texte)
    }

    @Test
    fun seuilRespecte() {
        assertTrue(notificationsPollens(pollensDemain(3), le16, 4, emptySet()).isEmpty())
        assertEquals(1, notificationsPollens(pollensDemain(3), le16, 3, emptySet()).size)
    }

    @Test
    fun alerteAirBreizhSousLeSeuil() {
        val n = notificationsPollens(pollensDemain(3, alerte = true), le16, 5, emptySet()).single()
        assertEquals("Alerte pollens demain à Cesson-Sévigné", n.titre)
    }

    @Test
    fun uneSeuleFoisParJour() {
        assertTrue(notificationsPollens(pollensDemain(5), le16, 4, setOf("pollens:2026-11-17")).isEmpty())
    }

    @Test
    fun episodeDemainPuisAggravation() {
        fun avec(etat: EtatEpisode) = episodes.copy(jours = episodes.jours.map { j ->
            if (j.date != le16.plusDays(1)) j
            else j.copy(etat = etat, polluants = j.polluants.map { if (it.polluant == "O₃") it.copy(etat = etat) else it })
        })
        val info = notificationsEpisodes(avec(EtatEpisode.INFORMATION), le16, emptySet()).single()
        assertEquals("episode:2026-11-17:information", info.cle)
        assertEquals("Pollution O₃ demain en Ille-et-Vilaine", info.titre)
        assertTrue(notificationsEpisodes(avec(EtatEpisode.INFORMATION), le16, setOf(info.cle)).isEmpty())
        // L'information devient une alerte : on prévient de nouveau.
        assertEquals("episode:2026-11-17:alerte", notificationsEpisodes(avec(EtatEpisode.ALERTE), le16, setOf(info.cle)).single().cle)
    }

    @Test
    fun controlesAuxHeuresPrevues() {
        // 16/11 à 12h00 à Paris (11h00 UTC) : prochain contrôle à 16h30.
        assertEquals(Instant.parse("2026-11-16T15:30:00Z"), prochainControleAir(Instant.parse("2026-11-16T11:00:00Z")))
        // Après 18h30 : le lendemain à 7h30.
        assertEquals(Instant.parse("2026-11-17T06:30:00Z"), prochainControleAir(Instant.parse("2026-11-16T17:30:00Z")))
        // Pile à l'heure : le contrôle suivant, pas le même.
        assertEquals(Instant.parse("2026-11-16T10:30:00Z"), prochainControleAir(Instant.parse("2026-11-16T06:30:00Z")))
    }

    @Test
    fun anciennesClesOubliees() {
        val cles = setOf("pollens:2026-11-01", "pollens:2026-11-10", "episode:2026-11-16:information", "illisible")
        assertEquals(setOf("pollens:2026-11-10", "episode:2026-11-16:information"), clesAirAGarder(cles, le16))
    }
}
