package com.yogistudio.cesson.actus

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * Un canal d'information extérieur : on y renvoie, on n'en reprend rien (WhatsApp, Facebook l'interdisent).
 * Chaque ligne a une icône Material neutre, jamais un logo : ni marque, ni logo d'organisme public dans une app
 * non officielle.
 */
data class Canal(val nom: String, val detail: String, val lien: String, val icone: ImageVector)

/** Canal officiel de la mairie. */
val CANAL_WHATSAPP_VILLE = Canal(
    "Chaîne WhatsApp de la ville",
    "Les informations officielles de la mairie, directement dans WhatsApp.",
    "https://whatsapp.com/channel/0029VanQ89PHgZWkYAaXKN2Z",
    icone = Icons.Rounded.Forum,
)

/** Groupe d'habitants : jamais rangé avec les canaux officiels. */
val CANAL_CESSON_ENTRAIDE = Canal(
    "Cesson Entraide, groupe Facebook",
    "Groupe d'habitants pour s'entraider entre voisins. Non officiel.",
    "https://www.facebook.com/groups/648693025948534",
    icone = Icons.Rounded.Groups,
)

/** Liens vers les canaux, dans l'écran À propos : icône, nom, une ligne d'explication. */
@Composable
fun LiensCanaux(canaux: List<Canal>) {
    val uri = LocalUriHandler.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        canaux.forEach { c ->
            Row(
                Modifier.fillMaxWidth()
                    .clickable(role = Role.Button, onClickLabel = "Ouvrir") { runCatching { uri.openUri(c.lien) } }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(c.icone, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(c.nom, style = MaterialTheme.typography.bodyLarge)
                    Text(c.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
        }
    }
}
