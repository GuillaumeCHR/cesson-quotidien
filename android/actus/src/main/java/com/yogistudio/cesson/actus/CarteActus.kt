package com.yogistudio.cesson.actus

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yogistudio.cesson.core.donnees.EtatModule
import com.yogistudio.cesson.core.ui.CarteDonnee
import com.yogistudio.cesson.core.ui.LocalCompact
import com.yogistudio.cesson.core.ui.Pastille
import com.yogistudio.cesson.core.ui.TexteBientot
import com.yogistudio.cesson.core.ui.Ton
import com.yogistudio.cesson.core.ui.rememberMaintenant
import java.time.Instant

private const val NB_REPLIE = 4

/**
 * Fil d'actualités de la ville : date, titre, extrait. Un appui ouvre l'article sur le site
 * de son éditeur ; l'app ne le recopie pas.
 */
@Composable
fun CarteActus(etat: EtatModule<DonneesActus>, flux: String, modifier: Modifier = Modifier) {
    val maintenant = rememberMaintenant()
    val titre = "Actualités de la ville"
    val icone = Icons.Rounded.Newspaper
    var tout by rememberSaveable(flux) { mutableStateOf(false) }
    when (etat) {
        EtatModule.Absent -> CarteDonnee(titre, icone, modifier, maintenant = maintenant) {
            TexteBientot("Actualités pas encore reçues. Tirez vers le bas pour actualiser.")
        }
        is EtatModule.Disponible -> {
            val articles = etat.fichier.donnees.flux.firstOrNull { it.id == flux }?.articles.orEmpty()
            CarteDonnee(titre, icone, modifier, etat.fraicheur, maintenant) {
                // Compact : les 3 derniers titres, sans extrait.
                val compact = LocalCompact.current
                Column {
                    if (articles.isEmpty()) TexteBientot("Aucune actualité pour le moment.")
                    val visibles = if (compact) articles.take(3) else if (tout) articles else articles.take(NB_REPLIE)
                    visibles.forEachIndexed { i, a ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        LigneArticle(a, maintenant, extrait = !compact)
                    }
                    if (!compact && articles.size > NB_REPLIE) {
                        TextButton(onClick = { tout = !tout }) {
                            Text(if (tout) "Afficher moins" else "Afficher les ${articles.size} articles")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LigneArticle(a: Article, maintenant: Instant, extrait: Boolean = true) {
    val uri = LocalUriHandler.current
    val date = formaterDateArticle(a.date, maintenant)
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = "Lire l'article sur le site") { runCatching { uri.openUri(a.lien) } }
            .padding(vertical = if (extrait) 10.dp else 6.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = buildString {
                    append("$date. ${a.titre}.")
                    if (a.concerneCesson) append(" Concerne Cesson.")
                    a.extrait?.let { append(" $it") }
                }
            },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(date, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (estRecent(a, maintenant)) {
                Spacer(Modifier.width(8.dp))
                Pastille("Nouveau", Ton.INFO)
            }
            if (a.concerneCesson) {
                Spacer(Modifier.width(8.dp))
                Pastille("Cesson", Ton.OUVERT)
            }
        }
        Text(a.titre, style = MaterialTheme.typography.titleSmall, maxLines = 3,
            overflow = TextOverflow.Ellipsis)
        if (extrait) a.extrait?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
