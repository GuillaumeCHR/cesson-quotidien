package com.yogistudio.cesson.familles

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yogistudio.cesson.core.donnees.EtatModule
import com.yogistudio.cesson.core.ui.CarteDonnee
import com.yogistudio.cesson.core.ui.LocalCompact
import com.yogistudio.cesson.core.ui.FuseauCesson
import com.yogistudio.cesson.core.ui.Pastille
import com.yogistudio.cesson.core.ui.TexteBientot
import com.yogistudio.cesson.core.ui.Ton
import com.yogistudio.cesson.core.ui.rememberMaintenant
import java.time.format.DateTimeFormatter
import java.util.Locale

private val PUCE_JOUR = DateTimeFormatter.ofPattern("EEE d", Locale.FRENCH)

/** Menus de la cantine : un jour à la fois (puces de la semaine), rubrique par rubrique. */
@Composable
fun CarteMenus(etat: EtatModule<DonneesFamilles>, modifier: Modifier = Modifier) {
    val maintenant = rememberMaintenant()
    val uri = LocalUriHandler.current
    val titre = "Menus de la cantine"
    when (etat) {
        EtatModule.Absent -> CarteDonnee(titre, Icons.Rounded.Restaurant, modifier, maintenant = maintenant) {
            TexteBientot("Menus pas encore reçus. Tirez vers le bas pour actualiser.")
        }
        is EtatModule.Disponible -> {
            val d = etat.fichier.donnees
            val ici = maintenant.atZone(FuseauCesson)
            val jours = joursDeMenu(d, ici.toLocalDate())
            var choisi by rememberSaveable(jours.firstOrNull()?.jour) {
                mutableIntStateOf(jourParDefaut(jours, ici.toLocalDate(), ici.toLocalTime()))
            }
            CarteDonnee(titre, Icons.Rounded.Restaurant, modifier, etat.fraicheur, maintenant) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (jours.isEmpty()) {
                        TexteBientot("Pas de menu publié pour les prochains jours (vacances, ou menu du mois pas encore en ligne).")
                    } else {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            jours.forEachIndexed { i, m ->
                                FilterChip(
                                    selected = i == choisi, onClick = { choisi = i },
                                    label = { Text(if (m.jour == ici.toLocalDate()) "Aujourd'hui" else PUCE_JOUR.format(m.jour).replaceFirstChar { it.uppercase() }) },
                                )
                            }
                        }
                        val m = jours[choisi.coerceIn(jours.indices)]
                        if (m.note != null) {
                            Pastille("Pas de cantine", Ton.NEUTRE)
                            Text(m.note, style = MaterialTheme.typography.bodyMedium)
                        } else {
                            m.rubriques().forEach { (rubrique, plats) ->
                                Row {
                                    Text(rubrique, style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(76.dp).padding(top = 2.dp))
                                    Column(Modifier.weight(1f)) {
                                        plats.forEachIndexed { i, p ->
                                            Text(p, style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (rubrique == "Plat" && i == 0) FontWeight.SemiBold else FontWeight.Normal)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (LocalCompact.current) return@Column  // compact : le menu du jour, sans le pied
                    d.restaurants?.let {
                        Text("Restaurants scolaires de $it. Menu susceptible de changer selon les livraisons.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    d.menu_pdf?.let { pdf -> TextButton(onClick = { runCatching { uri.openUri(pdf) } }) { Text("Menu du mois (PDF)") } }
                }
            }
        }
    }
}

/** Actualités publiques du portail familles : catégorie, titre, résumé ; l'actualité se lit sur le portail. */
@Composable
fun CarteActusFamilles(etat: EtatModule<DonneesFamilles>, modifier: Modifier = Modifier) {
    val maintenant = rememberMaintenant()
    val uri = LocalUriHandler.current
    val titre = "Infos du portail familles"
    when (etat) {
        EtatModule.Absent -> CarteDonnee(titre, Icons.Rounded.Campaign, modifier, maintenant = maintenant) {
            TexteBientot("Actualités pas encore reçues. Tirez vers le bas pour actualiser.")
        }
        is EtatModule.Disponible -> CarteDonnee(titre, Icons.Rounded.Campaign, modifier, etat.fraicheur, maintenant) {
            // Compact : les 3 premiers titres, sans catégorie ni résumé.
            val compact = LocalCompact.current
            val actus = etat.fichier.donnees.actualites.let { if (compact) it.take(3) else it }
            Column(verticalArrangement = Arrangement.spacedBy(if (compact) 0.dp else 4.dp)) {
                if (actus.isEmpty()) TexteBientot("Aucune actualité publique en ce moment.")
                actus.forEach { a ->
                    Column(
                        Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "Lire sur le portail") {
                            runCatching { uri.openUri(a.lien) }
                        }.padding(vertical = if (compact) 6.dp else 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        if (!compact) a.categories.firstOrNull()?.let {
                            Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        Text(a.titre, style = MaterialTheme.typography.titleSmall)
                        if (!compact) a.resume?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3)
                        }
                    }
                }
            }
        }
    }
}
