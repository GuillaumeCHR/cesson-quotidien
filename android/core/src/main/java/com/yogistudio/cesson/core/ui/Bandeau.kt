package com.yogistudio.cesson.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Bandeau d'alerte en tête de l'accueil (crues, vigilance météo, pollution) : filet de couleur, titre, puis le détail.
 *
 * En mode compact, le bandeau se réduit à son titre (deux lignes au plus) et à la [mention] de source, quand une
 * licence l'exige ; « Détails » déplie le reste. Le titre porte toujours l'essentiel : le niveau en toutes lettres.
 */
@Composable
fun BandeauAlerte(
    couleur: Color,
    titre: String,
    mention: String? = null,
    details: @Composable () -> Unit,
) {
    val compact = LocalCompact.current
    var deplie by rememberSaveable(titre) { mutableStateOf(false) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(if (compact) 6.dp else 8.dp).fillMaxHeight().background(couleur))
            Column(
                Modifier.padding(horizontal = 16.dp, vertical = if (compact) 10.dp else 16.dp),
                verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(if (compact) 18.dp else 24.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        titre,
                        style = if (compact) MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        else MaterialTheme.typography.titleMedium,
                        maxLines = if (compact) 2 else Int.MAX_VALUE,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                if (!compact || deplie) details()
                mention?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (compact) {
                    TextButton(onClick = { deplie = !deplie }, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                        modifier = Modifier.height(32.dp)) {
                        Text(if (deplie) "Masquer" else "Détails", style = MaterialTheme.typography.labelLarge)
                        Icon(if (deplie) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null,
                            modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
