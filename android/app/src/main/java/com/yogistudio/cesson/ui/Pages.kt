package com.yogistudio.cesson.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yogistudio.cesson.BuildConfig
import com.yogistudio.cesson.CessonApp
import com.yogistudio.cesson.ui.intro.CessonAppLogotype
import com.yogistudio.cesson.ui.intro.YogiStudioLogo
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.sp
import com.yogistudio.cesson.core.donnees.EtatModule
import com.yogistudio.cesson.core.signalement.ContexteSignalement
import com.yogistudio.cesson.core.signalement.LocalSignaleur
import com.yogistudio.cesson.core.ui.CarteDonnee
import com.yogistudio.cesson.core.ui.LigneFraicheur
import com.yogistudio.cesson.core.ui.TexteBientot
import com.yogistudio.cesson.core.ui.rememberMaintenant
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.json.JsonElement

// --- Onglets dont les modules arrivent aux étapes suivantes ----------------------------------



// --- Sources et licences ---------------------------------------------------------------------

/** Liste les jeux de données réellement reçus, avec leur source, leur licence et leur fraîcheur. */
@Composable
fun EcranSources(app: CessonApp) {
    val manifest by app.depot.manifest.collectAsStateWithLifecycle()
    val modules = manifest?.modules?.keys?.sorted().orEmpty()
    val etats by remember(modules) {
        if (modules.isEmpty()) flowOf(emptyList())
        else combine(modules.map { app.depot.suivre(it, JsonElement.serializer()) }) { it.toList() }
    }.collectAsStateWithLifecycle(emptyList())
    val maintenant = rememberMaintenant()
    val uri = LocalUriHandler.current

    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
        item {
            Text(
                "Toutes les données proviennent de sources publiques. Chacune est citée avec sa licence. " +
                    "Les fichiers dérivés de données sous ODbL sont eux-mêmes diffusés sous ODbL.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        if (etats.isEmpty()) {
            item { Text("Aucune donnée reçue pour l'instant.", Modifier.padding(16.dp)) }
        }
        items(etats.filterIsInstance<EtatModule.Disponible<JsonElement>>(), key = { it.fichier.module }) { e ->
            val meta = e.fichier.meta
            ListItem(
                headlineContent = { Text(meta.source) },
                supportingContent = {
                    Column {
                        Text("Licence : ${meta.licence}")
                        LigneFraicheur(e.fraicheur, maintenant)
                    }
                },
                trailingContent = meta.urlSource?.let { url ->
                    { IconButtonLien(url) { uri.openUri(url) } }
                },
            )
            HorizontalDivider()
        }
        item {
            Text(
                "Prochains passages du métro et des bus : STAR (Rennes Métropole, Keolis Rennes), en temps réel, " +
                    "sous licence ODbL. L'app les demande directement à STAR.\n\n" +
                    "Polices Atkinson Hyperlegible et Roboto Flex, sous licence SIL Open Font License 1.1.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Composable
private fun IconButtonLien(url: String, onClick: () -> Unit) {
    androidx.compose.material3.IconButton(onClick = onClick) {
        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = "Ouvrir la source $url")
    }
}

// --- À propos --------------------------------------------------------------------------------

private val CANAUX_OFFICIELS = listOf(
    "Ville de Cesson-Sévigné" to "https://www.ville-cesson-sevigne.fr/",
    "Vigicrues" to "https://www.vigicrues.gouv.fr/",
    "FR-Alert" to "https://fr-alert.gouv.fr/",
    "STAR" to "https://www.star.fr/",
    "SNCF / TER BreizhGo" to "https://www.ter.sncf.com/bretagne",
)

@Composable
fun EcranAPropos(onVosIdees: () -> Unit) {
    val uri = LocalUriHandler.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CessonAppLogotype(fontSize = 34.sp, modifier = Modifier.semantics { heading() })
        Text("Cesson-Sévigné, au quotidien · version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium)
        YogiStudioLogo(markSize = 48.dp)
        Text(PROMESSE, style = MaterialTheme.typography.titleMedium)
        Text(
            "Les préférences (tuiles de l'accueil, favoris) restent sur votre téléphone. L'app télécharge " +
                "uniquement des fichiers de données publiques, sans identifiant ni statistique d'usage.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Titre("Application non officielle")
        Text(
            "Cette application n'est éditée ni par la Ville de Cesson-Sévigné ni par Rennes Métropole. Elle ne " +
                "remplace ni les canaux officiels de la ville, ni Vigicrues, ni FR-Alert. Son contenu est strictement " +
                "informatif et factuel.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Titre("Canaux officiels")
        CANAUX_OFFICIELS.forEach { (nom, url) ->
            OutlinedButton(onClick = { uri.openUri(url) }, modifier = Modifier.fillMaxWidth()) {
                Text(nom, Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null)
            }
        }
        Titre("Contact")
        Text("Une idée, une erreur ? ${BuildConfig.CONTACT}", style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onVosIdees) { Text("Vos idées / signaler une erreur") }
        Spacer(Modifier.height(8.dp))
        Credits()
    }
}

@Composable
private fun Titre(t: String) =
    Text(t, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp).semantics { heading() })

// --- Vos idées / signaler une erreur ---------------------------------------------------------

@Composable
fun EcranVosIdees() {
    val signaleur = LocalSignaleur.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "Un horaire faux, une fermeture oubliée, une idée de rubrique ? Écrivez-nous : c'est le moyen le " +
                "plus rapide de corriger l'application.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            "Votre application mail s'ouvre avec un message prêt à compléter. Rien n'est envoyé sans votre accord, " +
                "et vous pouvez retirer le contexte technique joint.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = { signaleur.signaler(ContexteSignalement("signaler une erreur")) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.Flag, contentDescription = null)
            Text("  Signaler une erreur")
        }
        OutlinedButton(onClick = { signaleur.signaler(ContexteSignalement("une idée")) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.Lightbulb, contentDescription = null)
            Text("  Proposer une idée")
        }
        Text(
            "Astuce : le lien « Signaler une erreur » sous chaque carte joint automatiquement la donnée concernée.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
