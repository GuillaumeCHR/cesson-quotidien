package com.yogistudio.cesson.environnement

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yogistudio.cesson.core.donnees.EtatModule
import com.yogistudio.cesson.core.ui.FuseauCesson
import com.yogistudio.cesson.core.ui.Palette
import com.yogistudio.cesson.core.ui.Pastille
import com.yogistudio.cesson.core.ui.Ton
import com.yogistudio.cesson.core.ui.formaterHeure
import com.yogistudio.cesson.core.ui.rememberMaintenant

fun couleurVigilanceMeteo(c: CouleurVigilance): Color = when (c) {
    CouleurVigilance.JAUNE -> Palette.VigilanceJaune
    CouleurVigilance.ORANGE -> Palette.VigilanceOrange
    CouleurVigilance.ROUGE -> Palette.VigilanceRouge
    else -> Color.Transparent
}

/** Pastille de vigilance météo, toujours écrite en toutes lettres. */
@Composable
fun PastilleVigilanceMeteo(c: CouleurVigilance) {
    when (c) {
        CouleurVigilance.VERT -> Pastille("Pas de vigilance", Ton.OUVERT)
        CouleurVigilance.JAUNE -> Pastille("Vigilance jaune", Ton.ALERTE)
        CouleurVigilance.ORANGE -> Surface(color = Palette.VigilanceOrange, contentColor = Color(0xFF2B1600), shape = RoundedCornerShape(50)) {
            Text("Vigilance orange", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp), maxLines = 1)
        }
        CouleurVigilance.ROUGE -> Pastille("Vigilance rouge", Ton.FERME)
        CouleurVigilance.INCONNUE -> Pastille("Vigilance : à vérifier", Ton.NEUTRE)
    }
}

/**
 * Ligne « vigilance météo » en tête de la carte Météo : la vigilance d'aujourd'hui et de demain, ou « Pas de
 * vigilance ». La carte Météo reste informative ; seule cette ligne, venue de Météo-France, porte une couleur.
 */
@Composable
fun LigneVigilanceMeteo(etat: EtatModule<DonneesVigilanceMeteo>) {
    val d = (etat as? EtatModule.Disponible)?.fichier?.donnees ?: return
    val maintenant = rememberMaintenant()
    val aujourdhui = maintenant.atZone(FuseauCesson).toLocalDate()
    val ancienne = etat.fraicheur.estPerimee(maintenant)
    val jours = d.aSignaler(maintenant)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (jours.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics(mergeDescendants = true) {
                contentDescription = "Pas de vigilance météo aujourd'hui ni demain en ${d.departement}"
            }) {
                PastilleVigilanceMeteo(CouleurVigilance.VERT)
                Spacer(Modifier.width(8.dp))
                Text("aujourd'hui ni demain", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        jours.forEach { j ->
            val phenomenes = j.aSignaler()
            val quand = nomJourVigilance(j.date(), aujourdhui)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics(mergeDescendants = true) {
                contentDescription = "Vigilance ${phenomenes.couleurMax().nom()} $quand : ${phenomenes.joinToString(", ") { it.nom }}"
            }) {
                PastilleVigilanceMeteo(phenomenes.couleurMax())
                Spacer(Modifier.width(8.dp))
                Text("$quand · ${phenomenes.joinToString(", ") { it.nom }}", style = MaterialTheme.typography.bodySmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (ancienne) {
            Text("Vigilance connue à ${formaterHeure(etat.fraicheur.recupereLe, maintenant)} : vérifiez sur Météo-France.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Bandeau en tête de l'accueil dès la vigilance jaune (hors crues, qui ont le leur), pour aujourd'hui ou demain.
 * Il ne fait pas partie des tuiles et renvoie toujours vers Météo-France.
 */
@Composable
fun BandeauVigilanceMeteo(etat: EtatModule<DonneesVigilanceMeteo>) {
    val d = (etat as? EtatModule.Disponible)?.fichier?.donnees ?: return
    val maintenant = rememberMaintenant()
    val jours = d.aSignaler(maintenant)
    if (jours.isEmpty()) return
    val aujourdhui = maintenant.atZone(FuseauCesson).toLocalDate()
    val pire = jours.flatMap { it.aSignaler() }.couleurMax()
    val uri = LocalUriHandler.current
    var details by remember { mutableStateOf(false) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(8.dp).fillMaxHeight().background(couleurVigilanceMeteo(pire)))
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.width(8.dp))
                    Text("Vigilance ${pire.nom()} · ${d.departement}", style = MaterialTheme.typography.titleMedium)
                }
                jours.forEach { j ->
                    Text(
                        nomJourVigilance(j.date(), aujourdhui).replaceFirstChar { it.uppercase() } + " : " +
                            j.aSignaler().joinToString(" ; ") { "${it.description()} (${it.couleur.nom()})" } + ".",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Text(pire.consigne(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (d.textes.isNotEmpty()) {
                    if (details) {
                        d.textes.forEach { t ->
                            Text(t.titre, style = MaterialTheme.typography.labelLarge)
                            Text(t.texte, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    TextButton(onClick = { details = !details }, contentPadding = PaddingValues(0.dp)) {
                        Text(if (details) "Masquer le bulletin" else "Lire le bulletin de Météo-France")
                    }
                }
                TextButton(onClick = { uri.openUri(URL_VIGILANCE_METEO) }, contentPadding = PaddingValues(0.dp)) {
                    Text("Vigilance officielle sur Météo-France")
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
