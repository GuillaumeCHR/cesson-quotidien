package com.yogistudio.cesson.eau

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.yogistudio.cesson.core.donnees.EtatModule
import com.yogistudio.cesson.core.ui.Palette
import com.yogistudio.cesson.core.ui.formaterHeure
import com.yogistudio.cesson.core.ui.rememberMaintenant

@Composable
fun couleurVigilance(v: Vigilance): Color = when (v) {
    Vigilance.VERT -> MaterialTheme.colorScheme.primary
    Vigilance.JAUNE -> Palette.VigilanceJaune
    Vigilance.ORANGE -> Palette.VigilanceOrange
    Vigilance.ROUGE -> Palette.VigilanceRouge
    Vigilance.INCONNUE -> MaterialTheme.colorScheme.outline
}

/**
 * Ligne « vigilance crues » en tête de la carte Vilaine. La couleur n'est jamais seule porteuse
 * du sens : le niveau est toujours écrit en toutes lettres.
 */
@Composable
fun LigneVigilance(etat: EtatModule<DonneesVigicrues>) {
    val maintenant = rememberMaintenant()
    when (etat) {
        EtatModule.Absent -> Text("Vigilance crues pas encore reçue.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        is EtatModule.Disponible -> {
            val d = etat.fichier.donnees
            val ancienne = etat.fraicheur.estPerimee(maintenant)
            val precision = if (ancienne) " (niveau connu à ${formaterHeure(etat.fraicheur.recupereLe, maintenant)})" else ""
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.semantics(mergeDescendants = true) {
                    contentDescription = "${d.vigilance.libelleCourt()} sur la ${d.troncon.nom}$precision"
                },
            ) {
                Box(Modifier.size(12.dp).background(couleurVigilance(d.vigilance), CircleShape))
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(d.vigilance.libelleCourt(), style = MaterialTheme.typography.titleSmall)
                    Text("Tronçon ${d.troncon.nom}$precision", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/**
 * Bandeau en tête de l'accueil dès la vigilance jaune. Il ne fait pas partie des tuiles : on ne
 * peut ni le masquer ni le déplacer. Il renvoie toujours vers la source officielle.
 */
@Composable
fun BandeauCrue(etat: EtatModule<DonneesVigicrues>) {
    val d = (etat as? EtatModule.Disponible)?.fichier?.donnees ?: return
    if (!d.vigilance.estUneAlerte) return
    val maintenant = rememberMaintenant()
    val ancienne = etat.fraicheur.estPerimee(maintenant)
    val uri = LocalUriHandler.current
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(8.dp).fillMaxHeight().background(couleurVigilance(d.vigilance)))
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.width(8.dp))
                    Text("${d.vigilance.libelleCourt()} · ${d.troncon.nom}", style = MaterialTheme.typography.titleMedium)
                }
                Text(d.vigilance.definition(), style = MaterialTheme.typography.bodyMedium)
                Text(
                    if (ancienne) "Niveau connu à ${formaterHeure(etat.fraicheur.recupereLe, maintenant)} : " +
                        "il a pu changer depuis. Vérifiez sur Vigicrues."
                    else "Signalée depuis ${formaterHeure(d.depuis, maintenant)} au moins.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { uri.openUri(d.troncon.url ?: URL_VIGICRUES) }) {
                    Text("Bulletin officiel sur Vigicrues")
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
