package com.yogistudio.cesson.pratique

import android.content.ActivityNotFoundException
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yogistudio.cesson.core.donnees.EtatModule
import com.yogistudio.cesson.core.ui.CarteDonnee
import com.yogistudio.cesson.core.ui.StyleChiffres
import com.yogistudio.cesson.core.ui.TexteBientot
import com.yogistudio.cesson.core.ui.formaterHeure
import com.yogistudio.cesson.core.ui.rememberMaintenant
import java.net.URLEncoder
import java.time.Instant

/**
 * Prix d'un carburant, choisi par l'utilisateur (le choix reste sur le téléphone), dans les
 * stations de Cesson. Un appui sur une station ouvre l'itinéraire dans l'app de cartes.
 */
@Composable
fun CarteCarburants(
    etat: EtatModule<DonneesCarburants>,
    carburantSuivi: String?,
    onChoisir: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val maintenant = rememberMaintenant()
    when (etat) {
        EtatModule.Absent -> CarteDonnee("Carburants", Icons.Rounded.LocalGasStation, modifier, maintenant = maintenant) {
            TexteBientot("Prix pas encore reçus. Tirez vers le bas pour actualiser.")
        }
        is EtatModule.Disponible -> {
            val d = etat.fichier.donnees
            val proposes = carburantsProposes(d)
            val choisi = carburantSuivi?.takeIf { c -> proposes.any { it.first == c } }
                ?: CARBURANT_PAR_DEFAUT.takeIf { c -> proposes.any { it.first == c } }
                ?: proposes.firstOrNull()?.first
            val libelle = proposes.firstOrNull { it.first == choisi }?.second ?: ""
            val lignes = choisi?.let { classer(d, it, maintenant) } ?: emptyList()
            CarteDonnee(
                titre = "Carburants à Cesson",
                icone = Icons.Rounded.LocalGasStation,
                modifier = modifier,
                fraicheur = etat.fraicheur,
                maintenant = maintenant,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        proposes.forEach { (code, nom) ->
                            FilterChip(selected = code == choisi, onClick = { onChoisir(code) }, label = { Text(nom) })
                        }
                    }
                    if (lignes.isEmpty()) {
                        TexteBientot("Aucune station de Cesson ne vend ce carburant.")
                    }
                    lignes.forEach { LigneStation(it, libelle, maintenant) }
                }
            }
        }
    }
}

@Composable
private fun LigneStation(ligne: LigneCarburant, libelle: String, maintenant: Instant) {
    val uri = LocalUriHandler.current
    val lieu = ligne.station.lieu
    val (valeur, detail) = when (ligne) {
        is LigneCarburant.Prix -> formaterPrix(ligne.prix.prix) to buildList {
            if (ligne.ancien) add("prix relevé le ${formaterHeure(ligne.prix.maj, maintenant)}")
            if (ligne.station.automate2424) add("automate 24 h/24")
        }.joinToString(" · ")
        is LigneCarburant.Rupture -> "Rupture" to (ligne.rupture.debut?.let { "depuis le ${formaterHeure(it, maintenant)}" } ?: "")
    }
    val itineraire = lieu.coord?.let { c ->
        "geo:${c.lat},${c.lon}?q=${c.lat},${c.lon}(${URLEncoder.encode(lieu.nom, "UTF-8")})"
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (itineraire != null) Modifier.clickable(role = Role.Button, onClickLabel = "Itinéraire vers ${lieu.nom}") {
                    try { uri.openUri(itineraire) } catch (_: ActivityNotFoundException) { /* aucune app de cartes */ }
                } else Modifier,
            )
            .padding(vertical = 4.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = buildString {
                    append("${lieu.nom}, $libelle ")
                    append(if (ligne is LigneCarburant.Rupture) "en rupture" else valeur.replace(" €", " euros"))
                    if (ligne is LigneCarburant.Prix && ligne.moinsCher) append(", le moins cher")
                    if (detail.isNotEmpty()) append(", $detail")
                }
            },
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(lieu.nom, style = MaterialTheme.typography.bodyLarge)
                if (ligne is LigneCarburant.Prix && ligne.moinsCher) {
                    Spacer(Modifier.width(8.dp))
                    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.small) {
                        Text("Le moins cher", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
            }
            if (detail.isNotEmpty()) {
                Text(detail, style = MaterialTheme.typography.bodySmall,
                    color = if (ligne is LigneCarburant.Prix && ligne.ancien) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            valeur,
            style = if (ligne is LigneCarburant.Prix) StyleChiffres.copy(fontWeight = FontWeight.SemiBold)
            else MaterialTheme.typography.bodyMedium,
            color = if (ligne is LigneCarburant.Prix && !ligne.ancien) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
