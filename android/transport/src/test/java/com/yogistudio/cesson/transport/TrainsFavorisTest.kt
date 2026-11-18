package com.yogistudio.cesson.transport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class TrainsFavorisTest {
    private val f = TrainFavori("07:08", "rennes", "Rennes")
    private val jeudi = LocalDate.of(2026, 11, 19)
    private val horaires = listOf(HoraireTrain("07:08", "rennes", "Rennes",
        listOf(16, 17, 18, 19, 20).map { LocalDate.of(2026, 11, it) }))

    private fun train(statut: StatutTrain, retard: Int? = null) = DepartTrain(
        ligne = "C4", destination = "Rennes", heureTheorique = Instant.parse("2026-11-19T06:08:00Z"),
        heureReelle = retard?.let { Instant.parse("2026-11-19T06:08:00Z").plusSeconds(it * 60L) },
        statut = statut, numero = "855207", direction = "rennes", retardMin = retard,
    )

    @Test
    fun reconnuParSonHeureEtSaDirection() {
        assertEquals(true, f.estLe(train(StatutTrain.A_L_HEURE)))
        assertEquals(false, f.copy(direction = "est").estLe(train(StatutTrain.A_L_HEURE)))
    }

    @Test
    fun prochainDepartJoursChoisisSansFerieNiWeekEnd() {
        // Jeudi 19/11 à 7h30 : le train est parti, le prochain est vendredi.
        val apres = f.prochainDepart(Instant.parse("2026-11-19T06:30:00Z"), horaires) { false }
        assertEquals(LocalDate.of(2026, 11, 20), apres?.toLocalDate())
        // Vendredi soir : le samedi et le dimanche ne comptent pas ; lundi est au-delà des dates connues, on suppose qu'il circule.
        val lundi = f.prochainDepart(Instant.parse("2026-11-20T19:00:00Z"), horaires) { false }
        assertEquals(LocalDate.of(2026, 11, 23), lundi?.toLocalDate())
        // Un jour férié est sauté.
        val ferie = f.prochainDepart(Instant.parse("2026-11-18T20:00:00Z"), horaires) { it == jeudi }
        assertEquals(LocalDate.of(2026, 11, 20), ferie?.toLocalDate())
    }

    @Test
    fun alerteSeulementSiProbleme() {
        assertNull(alerteTrain(f, jeudi, train(StatutTrain.A_L_HEURE), emptySet()))
        assertNull(alerteTrain(f, jeudi, train(StatutTrain.RETARD, 3), emptySet()))
        val retard = alerteTrain(f, jeudi, train(StatutTrain.RETARD, 8), emptySet())
        assertEquals("Train de 7h08 : +8 min", retard?.titre)
        // Même retard, ou à peine plus : pas de seconde alerte ; retard aggravé de 5 min : nouvelle alerte.
        assertNull(alerteTrain(f, jeudi, train(StatutTrain.RETARD, 10), setOf(retard!!.cle)))
        assertNotNull(alerteTrain(f, jeudi, train(StatutTrain.RETARD, 13), setOf(retard.cle)))
        val supprime = alerteTrain(f, jeudi, train(StatutTrain.SUPPRIME), setOf(retard.cle))
        assertEquals("Train de 7h08 supprimé", supprime?.titre)
        assertNull(alerteTrain(f, jeudi, train(StatutTrain.SUPPRIME), setOf(supprime!!.cle)))
    }

    @Test
    fun libelles() {
        assertEquals("du lundi au vendredi", libelleJours(JOURS_OUVRES))
        assertEquals("lundi, jeudi", libelleJours(listOf(4, 1)))
        assertEquals("tous les jours", libelleJours((1..7).toList()))
        assertEquals("7h08", heureLisible("07:08"))
        assertEquals(listOf(f), lireTrainsFavoris(ecrireTrainsFavoris(listOf(f))))
    }
}
