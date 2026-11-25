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

    // Jeudi 19/11, le 7h08 vers Rennes : 06:08 UTC.
    private val prevu = Instant.parse("2026-11-19T06:08:00Z")
    private fun publie(statut: StatutTrain, retard: Int? = null, fin: Instant? = null) =
        train(statut, retard).copy(visibleJusquA = fin)
    private fun surveille(a: Instant, vararg departs: DepartTrain) = f.departSurveille(a, horaires, departs.toList()) { false }

    @Test
    fun surveilleDUneHeureAvantJusquAuDepartPrevu() {
        assertNull(surveille(prevu.minusSeconds(61 * 60), publie(StatutTrain.A_L_HEURE)))
        assertNotNull(surveille(prevu.minusSeconds(60 * 60), publie(StatutTrain.A_L_HEURE)))
        // À l'heure : la surveillance s'arrête 2 min après le départ.
        assertNotNull(surveille(prevu.plusSeconds(120), publie(StatutTrain.A_L_HEURE)))
        assertNull(surveille(prevu.plusSeconds(121), publie(StatutTrain.A_L_HEURE)))
    }

    @Test
    fun trainEnRetardSurveilleJusquASonDepartReel() {
        // +25 min : encore surveillé 20 min après l'heure prévue, et le retard aggravé déclenche une nouvelle alerte.
        val retard = publie(StatutTrain.RETARD, 25, fin = prevu.plusSeconds(27 * 60))
        val suivi = surveille(prevu.plusSeconds(20 * 60), retard)
        assertEquals(retard, suivi?.publie)
        val premiere = alerteTrain(f, jeudi, train(StatutTrain.RETARD, 10), emptySet())!!
        assertEquals("Train de 7h08 : +25 min", alerteTrain(f, jeudi, suivi!!.publie!!, setOf(premiere.cle))?.titre)
        // Parti : plus rien.
        assertNull(surveille(prevu.plusSeconds(28 * 60), retard))
    }

    @Test
    fun suppressionAnnonceeApresLHeurePrevue() {
        val supprime = publie(StatutTrain.SUPPRIME, fin = prevu.plusSeconds(32 * 60))
        val suivi = surveille(prevu.plusSeconds(10 * 60), supprime)
        assertEquals("Train de 7h08 supprimé", alerteTrain(f, jeudi, suivi!!.publie!!, emptySet())?.titre)
    }

    @Test
    fun jamaisPlusDe90MinApresLHeurePrevue() {
        val enorme = publie(StatutTrain.RETARD, 170, fin = prevu.plusSeconds(172 * 60))
        assertNotNull(surveille(prevu.plusSeconds(90 * 60), enorme))
        assertNull(surveille(prevu.plusSeconds(91 * 60), enorme))
        // Sans train publié (serveur muet) : 2 min après l'heure prévue.
        assertEquals(prevu.plusSeconds(120), finSurveillance(prevu, null))
    }

    @Test
    fun reveils() {
        val retard = publie(StatutTrain.RETARD, 25, fin = prevu.plusSeconds(27 * 60))
        // La veille au soir : une heure avant le départ.
        assertEquals(prevu.minusSeconds(3600),
            prochainReveil(listOf(f), Instant.parse("2026-11-18T20:00:00Z"), horaires, listOf(retard)) { false })
        // Après l'heure prévue, train en retard pas parti : dans 5 min.
        val apres = prevu.plusSeconds(15 * 60)
        assertEquals(apres.plusSeconds(300), prochainReveil(listOf(f), apres, horaires, listOf(retard)) { false })
        // Train parti : prochain réveil vendredi, une heure avant.
        assertEquals(Instant.parse("2026-11-20T05:08:00Z"),
            prochainReveil(listOf(f), prevu.plusSeconds(30 * 60), horaires, listOf(retard)) { false })
        assertNull(prochainReveil(emptyList(), apres, horaires, listOf(retard)) { false })
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
