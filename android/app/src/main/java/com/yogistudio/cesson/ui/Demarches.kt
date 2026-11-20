package com.yogistudio.cesson.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.HomeWork
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.LocalPolice
import androidx.compose.material.icons.rounded.ReportProblem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yogistudio.cesson.CessonApp
import com.yogistudio.cesson.core.donnees.EtatModule
import com.yogistudio.cesson.core.ui.CarteDonnee
import com.yogistudio.cesson.equipements.ContenuPolice
import com.yogistudio.cesson.equipements.ContenuUrbanisme
import com.yogistudio.cesson.equipements.equipements
import com.yogistudio.cesson.pratique.ContenuIdentite
import com.yogistudio.cesson.pratique.ContenuSignalement

private class Rubrique(val id: String, val titre: String, val icone: ImageVector, val contenu: @Composable () -> Unit)

/**
 * Démarches : carte d'identité et passeport, signalement, urbanisme, police municipale, réunis en une carte.
 * Chaque rubrique se déplie (une seule à la fois) et garde le contenu de son ancienne carte.
 */
@Composable
fun CarteDemarches(app: CessonApp) {
    val etat by remember(app) { app.depot.equipements() }.collectAsStateWithLifecycle(EtatModule.Absent)
    var ouverte by rememberSaveable { mutableStateOf<String?>(null) }
    val rubriques = listOf(
        Rubrique("identite", "Carte d'identité et passeport", Icons.Rounded.Badge) { ContenuIdentite() },
        Rubrique("signalement", "Faire un signalement", Icons.Rounded.ReportProblem) { ContenuSignalement() },
        Rubrique("urbanisme", "Urbanisme", Icons.Rounded.HomeWork) { ContenuUrbanisme(etat) },
        Rubrique("police", "Police municipale", Icons.Rounded.LocalPolice) { ContenuPolice(etat) },
    )
    CarteDonnee("Démarches", Icons.Rounded.AccountBalance, repliable = false) {
        Column {
            rubriques.forEachIndexed { i, r ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                val active = ouverte == r.id
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                        .toggleable(value = active, role = Role.Button) { ouverte = if (it) r.id else null }
                        .padding(vertical = 12.dp)
                        .semantics { heading() },
                ) {
                    Icon(r.icone, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(r.titre, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Icon(if (active) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                        contentDescription = if (active) "Replier" else "Déplier")
                }
                AnimatedVisibility(active) {
                    Column(Modifier.padding(bottom = 12.dp)) { r.contenu() }
                }
            }
        }
    }
}
