package com.yogistudio.cesson.core.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Préférences qui restent sur le téléphone : aucune n'est envoyée nulle part.
 * Accueil personnalisable (ordre et tuiles masquées) et bandeau de premier lancement.
 */
private val Context.preferencesCesson by preferencesDataStore(name = "preferences")

data class ConfigAccueil(val ordre: List<String>, val masquees: Set<String>)

class PreferencesLocales(private val context: Context) {
    private val ordre = stringPreferencesKey("accueil_ordre")
    private val masquees = stringSetPreferencesKey("accueil_masquees")
    private val bandeauVu = booleanPreferencesKey("bandeau_non_officiel_vu")

    val configAccueil: Flow<ConfigAccueil> = context.preferencesCesson.data.map { p ->
        ConfigAccueil(
            ordre = p[ordre]?.split(',')?.filter { it.isNotBlank() } ?: emptyList(),
            masquees = p[masquees] ?: emptySet(),
        )
    }

    val bandeauNonOfficielVu: Flow<Boolean> = context.preferencesCesson.data.map { it[bandeauVu] ?: false }

    suspend fun enregistrerAccueil(config: ConfigAccueil) {
        context.preferencesCesson.edit {
            it[ordre] = config.ordre.joinToString(",")
            it[masquees] = config.masquees
        }
    }

    suspend fun marquerBandeauVu() {
        context.preferencesCesson.edit { it[bandeauVu] = true }
    }
}

/**
 * Ordre effectif des tuiles : l'ordre enregistré d'abord (sans les tuiles qui n'existent plus),
 * puis les tuiles apparues depuis, dans leur ordre par défaut. Une nouvelle tuile ajoutée par une
 * mise à jour apparaît donc sans effacer les choix de l'utilisateur.
 */
fun ordonnerTuiles(parDefaut: List<String>, enregistre: List<String>): List<String> {
    val connues = parDefaut.toSet()
    val retenues = enregistre.filter { it in connues }.distinct()
    return retenues + parDefaut.filter { it !in retenues }
}

/** Déplace une tuile d'un cran (-1 vers le haut, +1 vers le bas) ; sans effet aux extrémités. */
fun deplacer(ordre: List<String>, id: String, sens: Int): List<String> {
    val i = ordre.indexOf(id)
    val j = i + sens
    if (i < 0 || j !in ordre.indices) return ordre
    return ordre.toMutableList().apply { add(j, removeAt(i)) }
}
