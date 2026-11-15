package com.yogistudio.cesson.actus

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.yogistudio.cesson.core.ui.CarteDonnee

/** Un canal d'information extérieur : on y renvoie, on n'en reprend rien (WhatsApp, Facebook l'interdisent). */
data class Canal(val nom: String, val detail: String, val lien: String)

val CANAUX = listOf(
    Canal(
        "Chaîne WhatsApp de la ville",
        "Les informations officielles de la mairie, directement dans WhatsApp.",
        "https://whatsapp.com/channel/0029VanQ89PHgZWkYAaXKN2Z",
    ),
    Canal(
        "Cesson Entraide, groupe Facebook",
        "Groupe d'habitants pour s'entraider entre voisins. Non officiel.",
        "https://www.facebook.com/groups/648693025948534",
    ),
)

@Composable
fun CarteCanaux(canaux: List<Canal> = CANAUX) {
    val uri = LocalUriHandler.current
    CarteDonnee("Suivre Cesson ailleurs", Icons.Rounded.Forum) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            canaux.forEach { c ->
                Row(
                    Modifier.fillMaxWidth()
                        .clickable(role = Role.Button, onClickLabel = "Ouvrir") { runCatching { uri.openUri(c.lien) } }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(c.nom, style = MaterialTheme.typography.bodyLarge)
                        Text(c.detail, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
