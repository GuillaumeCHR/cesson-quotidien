package com.yogistudio.cesson.eau

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yogistudio.cesson.core.donnees.EtatModule
import com.yogistudio.cesson.core.ui.CarteDonnee
import com.yogistudio.cesson.core.ui.Palette
import com.yogistudio.cesson.core.ui.Pastille
import com.yogistudio.cesson.core.ui.TexteBientot
import com.yogistudio.cesson.core.ui.Ton
import com.yogistudio.cesson.core.ui.FuseauCesson
import com.yogistudio.cesson.core.ui.rememberMaintenant
import java.time.Instant
import java.time.format.TextStyle
import java.util.Locale

private const val THEMES_REPLIES = 2

/**
 * Usage de l'eau : niveau de restriction sécheresse à Cesson (VigiEau, d'après l'arrêté préfectoral), les
 * restrictions pour les particuliers, et les consignes d'Eau du Bassin Rennais. L'arrêté fait foi.
 */
@Composable
fun CarteUsagesEau(etat: EtatModule<DonneesUsagesEau>, modifier: Modifier = Modifier) {
    val maintenant = rememberMaintenant()
    val uri = LocalUriHandler.current
    var tout by rememberSaveable { mutableStateOf(false) }
    when (etat) {
        EtatModule.Absent -> CarteDonnee("Usage de l'eau", Icons.Rounded.WaterDrop, modifier, maintenant = maintenant) {
            TexteBientot("Restrictions pas encore reçues. Tirez vers le bas pour actualiser.")
        }
        is EtatModule.Disponible -> {
            val d = etat.fichier.donnees
            CarteDonnee("Usage de l'eau", Icons.Rounded.WaterDrop, modifier, etat.fraicheur, maintenant) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PastilleSecheresse(d.niveau)
                        d.depuis?.let {
                            Spacer(Modifier.width(8.dp))
                            Text("depuis le ${jour(it)}" + (d.jusquAu?.let { f -> " jusqu'au ${jour(f)}" } ?: ""),
                                style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (d.niveau == NiveauSecheresse.AUCUNE) {
                        Text("Aucune restriction d'eau en vigueur à Cesson-Sévigné.", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Text("Restrictions pour les particuliers (eau du robinet) :", style = MaterialTheme.typography.bodyMedium)
                        val themes = parTheme(d.usages)
                        (if (tout) themes else themes.take(THEMES_REPLIES)).forEach { (theme, usages) ->
                            Text(theme, style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                            usages.forEach { LigneUsage(it) }
                        }
                        if (themes.size > THEMES_REPLIES) {
                            TextButton(onClick = { tout = !tout }) {
                                Text(if (tout) "Afficher moins" else "Toutes les restrictions (${d.usages.size})")
                            }
                        }
                    }
                    if (d.consignes.isNotEmpty()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Text("Consignes d'Eau du Bassin Rennais", style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.semantics { heading() })
                        d.consignes.forEach { c ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                                    .clickable(role = Role.Button, onClickLabel = "Lire sur le site d'Eau du Bassin Rennais") {
                                        runCatching { uri.openUri(c.lien) }
                                    }
                                    .padding(vertical = 4.dp),
                            ) {
                                Text(c.titre, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Row {
                        d.arrete?.let { a ->
                            TextButton(onClick = { runCatching { uri.openUri(a) } }) { Text("Arrêté préfectoral") }
                        }
                        TextButton(onClick = { runCatching { uri.openUri("https://vigieau.gouv.fr/") } }) { Text("VigiEau") }
                    }
                }
            }
        }
    }
}

@Composable
private fun LigneUsage(u: UsageEau) {
    Column(Modifier.fillMaxWidth().padding(start = 4.dp).semantics(mergeDescendants = true) { }) {
        Text(u.nom, style = MaterialTheme.typography.bodyMedium)
        Text(u.regle, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = if (u.regle.startsWith("Interdit")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun PastilleSecheresse(n: NiveauSecheresse) {
    when (n) {
        NiveauSecheresse.AUCUNE -> Pastille(n.libelle(), Ton.OUVERT)
        NiveauSecheresse.VIGILANCE -> Pastille(n.libelle(), Ton.INFO)
        NiveauSecheresse.ALERTE -> Pastille(n.libelle(), Ton.ALERTE)
        NiveauSecheresse.ALERTE_RENFORCEE -> Surface(color = Palette.VigilanceOrange, contentColor = Color(0xFF2B1600),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(50)) {
            Text(n.libelle(), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp))
        }
        NiveauSecheresse.CRISE -> Pastille(n.libelle(), Ton.FERME)
    }
}

private fun jour(i: Instant): String {
    val d = i.atZone(FuseauCesson).toLocalDate()
    return "${d.dayOfMonth} ${d.month.getDisplayName(TextStyle.FULL, Locale.FRANCE)}"
}
