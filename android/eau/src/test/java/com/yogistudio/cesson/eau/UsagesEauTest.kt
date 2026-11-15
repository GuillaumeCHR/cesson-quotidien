package com.yogistudio.cesson.eau

import com.yogistudio.cesson.core.modele.FichierModule
import com.yogistudio.cesson.core.modele.JsonCesson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Fichier publié par le backend le 15 nov. 2026 : Cesson en crise sécheresse. */
class UsagesEauTest {
    private val d = JsonCesson.decodeFromString(
        FichierModule.serializer(DonneesUsagesEau.serializer()), File("src/test/resources/usages_eau.json").readText()).donnees

    @Test
    fun crise() {
        assertEquals(NiveauSecheresse.CRISE, d.niveau)
        assertEquals("Crise sécheresse", d.niveau.libelle())
        assertTrue(d.arrete!!.endsWith(".pdf"))
    }

    @Test
    fun usagesParTheme() {
        val themes = parTheme(d.usages).map { it.first }
        assertTrue(themes.containsAll(listOf("Arroser", "Nettoyer", "Remplir ou vidanger")))
        assertEquals(d.usages.size, parTheme(d.usages).sumOf { it.second.size })
    }

    @Test
    fun niveauInconnuRetombeSurAucune() {
        val t = File("src/test/resources/usages_eau.json").readText().replace("\"crise\"", "\"extreme\"")
        val x = JsonCesson.decodeFromString(FichierModule.serializer(DonneesUsagesEau.serializer()), t).donnees
        assertEquals(NiveauSecheresse.AUCUNE, x.niveau)
    }
}
