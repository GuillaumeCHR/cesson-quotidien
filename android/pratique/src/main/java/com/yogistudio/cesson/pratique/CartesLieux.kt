package com.yogistudio.cesson.pratique

import android.content.ActivityNotFoundException
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ElectricCar
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yogistudio.cesson.core.donnees.EtatModule
import com.yogistudio.cesson.core.modele.Coord
import com.yogistudio.cesson.core.modele.Lieu
import com.yogistudio.cesson.core.ui.CarteDonnee
import com.yogistudio.cesson.core.ui.LocalCompact
import com.yogistudio.cesson.core.ui.Pastille
import com.yogistudio.cesson.core.ui.TexteBientot
import com.yogistudio.cesson.core.ui.Ton
import com.yogistudio.cesson.core.ui.rememberMaintenant
import java.net.URLEncoder

private const val NB_REPLIE = 5

/** Compact : les 3 premiers, sans adresse ni détail. */
@Composable
private fun nbReplie() = if (LocalCompact.current) 3 else NB_REPLIE

/** Bouton « Les plus proches de moi » : la position reste sur le téléphone et ne sert qu'à trier la liste. */
@Composable
private fun BoutonProximite(position: Coord?, enCours: Boolean, onDemander: () -> Unit) {
    if (position != null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(6.dp))
            Text("Du plus proche au plus loin de vous", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        OutlinedButton(onClick = onDemander, enabled = !enCours) {
            Icon(Icons.Rounded.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (enCours) "Recherche de votre position…" else "Les plus proches de moi")
        }
    }
}

@Composable
private fun ouvrirItineraire(): (Lieu) -> Unit {
    val uri = LocalUriHandler.current
    return { lieu ->
        lieu.coord?.let { c ->
            try { uri.openUri("geo:${c.lat},${c.lon}?q=${c.lat},${c.lon}(${URLEncoder.encode(lieu.nom, "UTF-8")})") }
            catch (_: ActivityNotFoundException) { } catch (_: IllegalArgumentException) { }
        }
    }
}

/**
 * Défibrillateurs : le réflexe d'abord (appeler le 15), puis les appareils, ceux accessibles à tout moment en tête,
 * ou du plus proche au plus loin si l'utilisateur partage sa position.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CarteDefibrillateurs(
    etat: EtatModule<DonneesDefibrillateurs>,
    position: Coord?,
    rechercheEnCours: Boolean,
    onDemanderPosition: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val maintenant = rememberMaintenant()
    val uri = LocalUriHandler.current
    val itineraire = ouvrirItineraire()
    var tout by rememberSaveable { mutableStateOf(false) }
    CarteDonnee("Défibrillateurs", Icons.Rounded.Favorite, modifier,
        (etat as? EtatModule.Disponible)?.fraicheur, maintenant) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer,
                shape = MaterialTheme.shapes.medium) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (LocalCompact.current) "Arrêt cardiaque : appelez le 15, massez." else
                        "Arrêt cardiaque : appelez le 15 (ou le 112), massez, et faites apporter un défibrillateur. " +
                        "Il vous guide pas à pas.", style = MaterialTheme.typography.bodyMedium)
                    Button(
                        onClick = { runCatching { uri.openUri("tel:15") } },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError),
                    ) {
                        Icon(Icons.Rounded.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Appeler le 15")
                    }
                }
            }
            when (etat) {
                EtatModule.Absent -> TexteBientot("Liste pas encore reçue. Tirez vers le bas pour actualiser.")
                is EtatModule.Disponible -> {
                    val liste = parProximite(etat.fichier.donnees.defibrillateurs, position) { it.lieu }
                    BoutonProximite(position, rechercheEnCours, onDemanderPosition)
                    (if (tout) liste else liste.take(nbReplie())).forEachIndexed { i, (d, dist) ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        LigneLieu(d.lieu, dist, itineraire) {
                            when {
                                d.toujoursAccessible -> Pastille("Dehors, accès libre", Ton.OUVERT)
                                d.libreAcces -> Pastille("Accès libre aux heures du lieu", Ton.INFO)
                                else -> Pastille("Accès restreint", Ton.NEUTRE)
                            }
                        }
                    }
                    if (liste.size > nbReplie()) {
                        TextButton(onClick = { tout = !tout }) {
                            Text(if (tout) "Afficher moins" else "Tous les défibrillateurs (${liste.size})")
                        }
                    }
                }
            }
        }
    }
}

/** Bornes de recharge : par puissance, ou du plus proche au plus loin si la position est partagée. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CarteBornes(
    etat: EtatModule<DonneesBornes>,
    position: Coord?,
    rechercheEnCours: Boolean,
    onDemanderPosition: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val maintenant = rememberMaintenant()
    val itineraire = ouvrirItineraire()
    var tout by rememberSaveable { mutableStateOf(false) }
    when (etat) {
        EtatModule.Absent -> CarteDonnee("Bornes de recharge", Icons.Rounded.ElectricCar, modifier, maintenant = maintenant) {
            TexteBientot("Liste pas encore reçue. Tirez vers le bas pour actualiser.")
        }
        is EtatModule.Disponible -> {
            val liste = parProximite(etat.fichier.donnees.stations, position) { it.lieu }
            CarteDonnee("Bornes de recharge", Icons.Rounded.ElectricCar, modifier, etat.fraicheur, maintenant) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    BoutonProximite(position, rechercheEnCours, onDemanderPosition)
                    (if (tout) liste else liste.take(nbReplie())).forEachIndexed { i, (s, dist) ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        LigneLieu(s.lieu, dist, itineraire, sousTitre = listOfNotNull(
                            s.enseigne?.takeIf { e -> !s.lieu.nom.contains(e, ignoreCase = true) },
                            "${s.points} point${if (s.points > 1) "s" else ""}",
                            s.prises.joinToString(", "),
                        ).joinToString(" · ")) {
                            Pastille(formaterPuissance(s.puissanceMax), if (s.puissanceMax >= 50) Ton.INFO else Ton.NEUTRE)
                            if (s.h24) Pastille("24 h/24", Ton.OUVERT)
                            if (s.gratuit) Pastille("Gratuit", Ton.OUVERT)
                        }
                    }
                    if (liste.size > nbReplie()) {
                        TextButton(onClick = { tout = !tout }) {
                            Text(if (tout) "Afficher moins" else "Toutes les stations (${liste.size})")
                        }
                    }
                    if (!LocalCompact.current) Text("Disponibilité en temps réel non publiée en open data : vérifiez dans l'app de l'opérateur.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LigneLieu(
    lieu: Lieu,
    distance: Double?,
    itineraire: (Lieu) -> Unit,
    sousTitre: String? = null,
    pastilles: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = "Itinéraire vers ${lieu.nom}") { itineraire(lieu) }
            .padding(vertical = 8.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = listOfNotNull(lieu.nom, distance?.let { "à ${formaterDistance(it)}" }, lieu.adresse, sousTitre)
                    .joinToString(", ")
            },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(lieu.nom, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            distance?.let { Text(formaterDistance(it), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
        }
        val compact = LocalCompact.current
        if (!compact) lieu.adresse?.takeIf { it != lieu.nom }?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!compact) sousTitre?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { pastilles() }
    }
}
