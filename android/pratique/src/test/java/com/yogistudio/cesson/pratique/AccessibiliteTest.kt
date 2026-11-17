package com.yogistudio.cesson.pratique

import com.yogistudio.cesson.core.modele.FichierModule
import com.yogistudio.cesson.core.modele.JsonCesson
import com.yogistudio.cesson.core.ui.Ton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AccessibiliteTest {
    /** Fichier produit par le backend à partir de 120 fiches acceslibre réelles (17 nov. 2026). */
    private val lieux = JsonCesson.decodeFromString(FichierModule.serializer(DonneesAccessibilite.serializer()),
        File("src/test/resources/accessibilite.json").readText()).donnees.etablissements

    private fun lieu(nom: String) = lieux.first { it.lieu.nom == nom }

    @Test
    fun rechercheSansAccentsNiCasse() {
        assertEquals(listOf("Médiathèque"), filtrerEtablissements(lieux, "mediatheque", FamilleLieu.Tous).map { it.lieu.nom })
        // Sur l'activité et sur l'adresse aussi, chaque mot devant se retrouver.
        assertTrue(filtrerEtablissements(lieux, "piscine", FamilleLieu.Tous).size >= 2)
        assertTrue(filtrerEtablissements(lieux, "rue de rennes pharmacie", FamilleLieu.Tous).any { it.lieu.nom == "Pharmacie Le Brun" })
        assertTrue(filtrerEtablissements(lieux, "piscine", FamilleLieu.Restauration).isEmpty())
        assertEquals(lieux.size, filtrerEtablissements(lieux, "  ", FamilleLieu.Tous).size)
    }

    @Test
    fun familles() {
        val services = filtrerEtablissements(lieux, "", FamilleLieu.ServicesPublics)
        assertTrue(services.isNotEmpty() && services.all { it.famille == "services_publics" })
        assertTrue(services.any { it.lieu.nom == "La Poste" })
    }

    @Test
    fun entreeAvecMarchesEtRampe() {
        val p = lieu("Pharmacie Le Brun")
        assertEquals("1 marche, rampe fixe", p.acces.entree())
        assertEquals("Rampe à l'entrée" to Ton.INFO, p.acces.pastilles().first())
        assertTrue("Place réservée à proximité" to Ton.INFO in p.acces.pastilles())
        val d = p.details().toMap()
        assertEquals("1 marche, rampe fixe, porte d'au moins 100 cm.".replaceFirstChar { it.uppercase() }, d["Entrée"])
        assertEquals("Place réservée à moins de 200 m.", d["Stationnement"])
        assertTrue(d.getValue("Accueil").contains("personnel non formé"))
    }

    @Test
    fun plainPiedEtMalentendants() {
        val poste = lieu("La Poste")
        assertEquals("Entrée de plain-pied" to Ton.OUVERT, poste.acces.pastilles().first())
        assertEquals("Boucle à induction magnétique.", poste.details().toMap()["Malentendants"])
    }

    @Test
    fun entreeAccessiblePourLaCarte() {
        assertTrue(lieu("La Poste").acces.entreeAccessible)
        assertTrue(lieu("Pharmacie Le Brun").acces.entreeAccessible)  // 1 marche, mais rampe fixe
        assertTrue(!Acces(entreePlainPied = false, entreeMarches = 10).entreeAccessible)
        assertTrue(!Acces().entreeAccessible)
    }

    @Test
    fun ficheVide() {
        val vide = Etablissement(lieu = lieux.first().lieu)
        assertEquals(listOf("Peu renseigné" to Ton.NEUTRE), vide.acces.pastilles())
        assertTrue(vide.details().isEmpty())
    }
}
