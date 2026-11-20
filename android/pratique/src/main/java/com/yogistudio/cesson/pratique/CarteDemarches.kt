package com.yogistudio.cesson.pratique

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.yogistudio.cesson.core.ui.LocalCompact

// La prise de rendez-vous se fait dans un module intégré à cette page de la ville (Synbird).
const val URL_RDV_IDENTITE = "https://www.ville-cesson-sevigne.fr/carte-nationale-didentite-cni-et-passeport/"
const val URL_ANTS = "https://passeport.ants.gouv.fr/demarches-en-ligne"
const val URL_SIGNALEMENT_VILLE = "https://www.ville-cesson-sevigne.fr/faire-un-signalement/"
const val URL_SIGNALEMENT_METROPOLE =
    "https://demarches.rennes.fr/RENNES.Guichet/workflow_url?ECITIZ_ACTIVITY_PATH=Citoyen&IDCOLLECTIVITE=RENNES&TS=SVE&TASK=CREATE_SVE&THEMATIQUE=SIGNAL"

@Composable
private fun Lien(texte: String, principal: Boolean, onClick: () -> Unit) {
    val contenu: @Composable () -> Unit = {
        Text(texte)
        Spacer(Modifier.width(6.dp))
        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
    }
    if (principal) Button(onClick = onClick) { contenu() } else OutlinedButton(onClick = onClick) { contenu() }
}

/** Carte d'identité et passeport : pré-demande sur le site de l'État, puis rendez-vous en mairie. */
@Composable
fun ContenuIdentite(modifier: Modifier = Modifier) {
    val uri = LocalUriHandler.current
    Column(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val compact = LocalCompact.current  // compact : les deux boutons seulement
            if (!compact) Text("1. Faites la pré-demande en ligne, gratuite, sur le seul site officiel (ANTS).\n" +
                "2. Prenez rendez-vous à la mairie (Espace Citoyen) pour déposer le dossier et les empreintes.",
                style = MaterialTheme.typography.bodyMedium)
            Lien("Prendre rendez-vous en mairie", principal = true) { runCatching { uri.openUri(URL_RDV_IDENTITE) } }
            Lien("Pré-demande sur ants.gouv.fr", principal = false) { runCatching { uri.openUri(URL_ANTS) } }
            if (!compact) Text("Les mineurs viennent au rendez-vous ; dès 12 ans, aussi au retrait. Délais plus longs avant l'été. " +
                "Méfiez-vous des sites payants : la démarche est gratuite.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Signaler un problème sur l'espace public : à la ville ou à Rennes Métropole selon le sujet. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ContenuSignalement(modifier: Modifier = Modifier) {
    val uri = LocalUriHandler.current
    fun ouvrir(lien: String) { runCatching { uri.openUri(lien) } }
    Column(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Compact : qui fait quoi en une ligne chacun, et les deux formulaires.
            val compact = LocalCompact.current
            if (!compact) Text("À la ville", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
            Text(if (compact) "Ville : parcs, aires de jeux, propreté, plaques de rue." else
                "Parcs, espaces verts et aires de jeux, élagage, chemins piétons, bâtiments publics, plaques de rue, propreté.",
                style = MaterialTheme.typography.bodySmall)
            Lien("Signaler à la ville", principal = true) { ouvrir(URL_SIGNALEMENT_VILLE) }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            if (!compact) Text("À Rennes Métropole", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
            Text(if (compact) "Métropole : voirie, éclairage, eau, déchets." else
                "Voirie (trou, barrière, ralentisseur), éclairage public, eau et assainissement, déchets.",
                style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Lien("Formulaire de la Métropole", principal = false) { ouvrir(URL_SIGNALEMENT_METROPOLE) }
                TextButton(onClick = { ouvrir("tel:0223621010") }) {
                    Icon(Icons.Rounded.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("02 23 62 10 10")
                }
            }
            if (!compact) Text("Par téléphone : 8h30-17h30 en semaine, 9h-12h30 le samedi. Aussi par l'application « Ici Rennes ».",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
