package com.yogistudio.cesson.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.yogistudio.cesson.core.donnees.Fraicheur
import com.yogistudio.cesson.core.signalement.ContexteSignalement
import com.yogistudio.cesson.core.signalement.LocalSignaleur
import java.time.Instant

/**
 * Carte de donnée commune à tous les modules : titre, contenu, puis la ligne de fraîcheur
 * (« Mis à jour il y a 12 min · source ») et le lien « Signaler une erreur ».
 */
@Composable
fun CarteDonnee(
    titre: String,
    icone: ImageVector,
    modifier: Modifier = Modifier,
    fraicheur: Fraicheur? = null,
    signalement: ContexteSignalement? = null,
    maintenant: Instant = rememberMaintenant(),
    contenu: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text(titre, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            }
            contenu()
            if (fraicheur != null || signalement != null) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (fraicheur != null) {
                        LigneFraicheur(fraicheur, maintenant, Modifier.weight(1f))
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    if (signalement != null) {
                        val signaleur = LocalSignaleur.current
                        TextButton(onClick = { signaleur.signaler(signalement) }) {
                            Icon(Icons.Rounded.Flag, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Signaler une erreur")
                        }
                    }
                }
            }
        }
    }
}

/** Une donnée périmée est signalée comme telle, jamais masquée. */
@Composable
fun LigneFraicheur(fraicheur: Fraicheur, maintenant: Instant, modifier: Modifier = Modifier) {
    val perimee = fraicheur.estPerimee(maintenant)
    Column(modifier) {
        if (perimee) {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.History, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Données anciennes", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Text(
            "Mis à jour ${formaterDepuis(fraicheur.recupereLe, maintenant)} · ${fraicheur.source}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Contenu d'attente d'une tuile dont le module arrive à une étape ultérieure. */
@Composable
fun TexteBientot(texte: String) {
    Text(texte, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
