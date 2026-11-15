package com.yogistudio.cesson.pratique

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yogistudio.cesson.core.donnees.EtatModule
import com.yogistudio.cesson.core.ui.CarteDonnee
import com.yogistudio.cesson.core.ui.Palette
import com.yogistudio.cesson.core.ui.TexteBientot
import com.yogistudio.cesson.core.ui.rememberMaintenant
import java.time.Instant

private val BacGris = Color(0xFF7D8480)

/** « Ma collecte » : prochain passage de chaque bac, et rappel la veille. */
@Composable
fun CarteCollecte(etat: EtatModule<DonneesCollecte>, modifier: Modifier = Modifier) {
    val maintenant = rememberMaintenant()
    when (etat) {
        EtatModule.Absent -> CarteDonnee("Ma collecte", Icons.Rounded.DeleteOutline, modifier, maintenant = maintenant) {
            TexteBientot("Calendrier pas encore reçu. Tirez vers le bas pour actualiser.")
        }
        is EtatModule.Disponible -> {
            val d = etat.fichier.donnees
            CarteDonnee("Ma collecte", Icons.Rounded.DeleteOutline, modifier, etat.fraicheur, maintenant) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val ceSoir = aSortirCeSoir(d, maintenant)
                    if (ceSoir.isNotEmpty()) RappelCeSoir(ceSoir)
                    avisEnCours(d, maintenant).forEach { Note(it.texte) }
                    val prochaines = prochaines(d, maintenant)
                    if (prochaines.all { it.passage == null }) {
                        TexteBientot("Le calendrier suivant n'est pas encore dans l'app. Il est distribué chaque septembre.")
                    }
                    prochaines.forEach { LigneBac(it, maintenant) }
                    Text(
                        "${d.public}. En immeuble, les jours peuvent différer." + (d.conseil?.let { "\n$it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun Bac(bac: String) {
    val couleur = if (bac == "jaune") Palette.VigilanceJaune else BacGris
    Box(Modifier.size(width = 18.dp, height = 22.dp).background(couleur, MaterialTheme.shapes.extraSmall)
        .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.extraSmall))
}

@Composable
private fun LigneBac(p: ProchaineCollecte, maintenant: Instant) {
    val quand = p.passage?.let { formaterJourCollecte(it.date, maintenant) } ?: "date non connue"
    val decale = p.passage?.decalePar
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
            contentDescription = "Bac ${p.flux.bac}, ${p.flux.nom} : $quand" +
                (decale?.let { ", décalé en raison du jour férié, $it" } ?: "")
        },
    ) {
        Bac(p.flux.bac)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Bac ${p.flux.bac} · ${p.flux.nom}", style = MaterialTheme.typography.bodyMedium)
            Text(
                decale?.let { "Décalé d'un jour : $it" } ?: p.flux.regle.replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.bodySmall,
                color = if (decale != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(quand.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
    }
}

@Composable
private fun RappelCeSoir(bacs: List<FluxCollecte>) {
    val noms = bacs.joinToString(" et ") { "bac ${it.bac}" }
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.medium) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.width(10.dp))
            Text("Ce soir, sortez le $noms : collecte demain matin dès 5h30.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun Note(texte: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(Icons.Rounded.Info, contentDescription = null, modifier = Modifier.size(16.dp).padding(top = 2.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(6.dp))
        Text(texte, style = MaterialTheme.typography.bodySmall)
    }
}
