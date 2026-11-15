package com.yogistudio.cesson.equipements

import com.yogistudio.cesson.core.modele.FichierModule
import com.yogistudio.cesson.core.modele.JsonCesson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.LocalDate

/** Fichier publié par le backend le dimanche 15 nov. 2026 (14 jours à partir de ce jour). */
class EquipementsTest {
    private val d = JsonCesson.decodeFromString(
        FichierModule.serializer(DonneesEquipements.serializer()),
        File("src/test/resources/equipements.json").readText(),
    ).donnees

    private fun espace(e: String, s: String) =
        d.equipements.first { it.lieu.id == "equipement:$e" }.espaces.first { it.id == s }

    private fun a(iso: String) = Instant.parse(iso)

    @Test
    fun decodeLeFichierDuBackend() {
        assertEquals(5, d.equipements.size) // dont la déchèterie
        assertEquals(LocalDate.of(2026, 11, 15), d.equipements.first().verifieLe)
    }

    @Test
    fun ouvertJusquA() {
        // Dimanche 10h à Paris : bassin ludique ouvert 9h – 13h30.
        val o = ouverture(espace("piscine", "bassin_ludique"), a("2026-11-15T09:00:00Z"))
        assertEquals("Ouvert jusqu'à 13h30", libelleOuverture(o, a("2026-11-15T09:00:00Z")))
    }

    @Test
    fun fermeEtProchaineOuverture() {
        // Dimanche 15h : fermé, rouvre lundi à 16h, c'est-à-dire « demain ».
        val maintenant = a("2026-11-15T14:00:00Z")
        val o = ouverture(espace("piscine", "bassin_ludique"), maintenant)
        assertEquals("Fermé · ouvre demain à 16h", libelleOuverture(o, maintenant))
        // Médiathèque fermée le dimanche et le lundi : ouvre mardi à 14h30.
        val m = ouverture(espace("mediatheque", "mediatheque"), maintenant)
        assertEquals("Fermé · ouvre mardi à 14h30", libelleOuverture(m, maintenant))
    }

    @Test
    fun horsSaisonNEstPasFerme() {
        val o = ouverture(espace("base_sports_nature", "eau_calme"), a("2026-11-15T14:00:00Z"))
        assertEquals(Ouverture.Inconnu(), o)
        assertEquals("Horaires non communiqués", libelleOuverture(o, a("2026-11-15T14:00:00Z")))
    }

    @Test
    fun jourFerieAVerifier() {
        val ferie = JourHoraire(java.time.LocalDate.of(2027, 3, 29), connu = false, motif = "Jour férié (Lundi de Pâques)")
        assertEquals("jour férié (Lundi de Pâques) : à vérifier", libelleJour(ferie))
        assertEquals("Jour férié (Lundi de Pâques) : horaires à vérifier",
            libelleOuverture(Ouverture.Inconnu(ferie.motif), a("2027-03-29T08:00:00Z")))
    }

    @Test
    fun libellesDuDetail() {
        val lundi = espace("piscine", "bassin_ludique").jours[1]
        assertEquals("16h – 19h45", libelleJour(lundi))
        val mardi = espace("piscine", "bassin_ludique").jours[2]
        assertEquals("7h45 – 8h45, 12h15 – 13h45", libelleJour(mardi))
        assertEquals("fermé", libelleJour(espace("mediatheque", "mediatheque").jours[1]))
        assertTrue(nomDuJour(LocalDate.of(2026, 11, 18), LocalDate.of(2026, 11, 15)).startsWith("Mercredi 18"))
    }
}
