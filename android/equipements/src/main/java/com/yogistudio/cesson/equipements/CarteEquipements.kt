package com.yogistudio.cesson.equipements

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Pool
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yogistudio.cesson.core.donnees.EtatModule
import com.yogistudio.cesson.core.ui.CarteDonnee
import com.yogistudio.cesson.core.ui.LocalCompact
import com.yogistudio.cesson.core.ui.FuseauCesson
import com.yogistudio.cesson.core.ui.Pastille
import com.yogistudio.cesson.core.ui.Ton
import com.yogistudio.cesson.core.ui.TexteBientot
import com.yogistudio.cesson.core.ui.rememberMaintenant
import java.time.Instant

/**
 * « Mes équipements » : ouvert ou fermé maintenant, et jusqu'à quand. Un appui sur un équipement
 * ouvre la semaine détaillée, avec la date de dernière vérification et le lien vers la source.
 */
@Composable
fun CarteEquipements(
    etat: EtatModule<DonneesEquipements>,
    modifier: Modifier = Modifier,
    titre: String = "Mes équipements",
    garder: (Equipement) -> Boolean = { true },
) {
    val maintenant = rememberMaintenant()
    var detail by rememberSaveable { mutableStateOf<String?>(null) }
    when (etat) {
        EtatModule.Absent -> CarteDonnee(titre, Icons.Rounded.Pool, modifier, maintenant = maintenant) {
            TexteBientot("Horaires pas encore reçus. Tirez vers le bas pour actualiser.")
        }
        is EtatModule.Disponible -> {
            val d = etat.fichier.donnees.copy(equipements = etat.fichier.donnees.equipements.filter(garder))
            CarteDonnee(titre, Icons.Rounded.Pool, modifier, etat.fraicheur, maintenant) {
                Column {
                    d.equipements.forEachIndexed { i, e ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        if (LocalCompact.current) LigneEquipementCompacte(e, maintenant) { detail = e.lieu.id }
                        else LigneEquipement(e, maintenant) { detail = e.lieu.id }
                    }
                }
            }
            d.equipements.firstOrNull { it.lieu.id == detail }?.let { e ->
                DetailEquipement(e, maintenant) { detail = null }
            }
        }
    }
}

@Composable
private fun LigneEquipement(e: Equipement, maintenant: Instant, onOuvrir: () -> Unit) {
    val etats = e.espaces.map { it to ouverture(it, maintenant) }
    val libelles = etats.map { (_, o) -> libelleOuverture(o, maintenant) }
    // Tous les espaces dans le même état : une seule ligne (« Hors saison », « Fermé »…).
    val regroupe = libelles.distinct().size == 1
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = "Voir les horaires de la semaine", onClick = onOuvrir)
            .padding(vertical = 10.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = e.lieu.nom + " : " + if (regroupe) libelles.first()
                else etats.zip(libelles).joinToString(" ; ") { (eo, l) -> "${eo.first.nom}, $l" }
            },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(e.lieu.nom, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
        if (regroupe) {
            LigneEtat(null, etats.first().second, maintenant)
        } else {
            etats.forEach { (espace, o) -> LigneEtat(espace.nom, o, maintenant) }
        }
    }
}

/** Compact : une ligne par équipement, son nom et une pastille (ouvert si l'un de ses espaces l'est). */
@Composable
private fun LigneEquipementCompacte(e: Equipement, maintenant: Instant, onOuvrir: () -> Unit) {
    val ouvertures = e.espaces.map { ouverture(it, maintenant) }
    val o = ouvertures.firstOrNull { it is Ouverture.Ouvert } ?: ouvertures.firstOrNull { it is Ouverture.Ferme } ?: ouvertures.firstOrNull()
    Row(
        Modifier.fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = "Voir les horaires de la semaine", onClick = onOuvrir)
            .padding(vertical = 8.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(e.lieu.nom, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1)
        if (o != null) {
            val (pastille, precision) = etiquetteOuverture(o, maintenant)
            precision?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                Spacer(Modifier.width(8.dp))
            }
            Pastille(pastille, when (o) { is Ouverture.Ouvert -> Ton.OUVERT; is Ouverture.Ferme -> Ton.FERME; else -> Ton.NEUTRE })
        }
    }
}

@Composable
private fun LigneEtat(nom: String?, o: Ouverture, maintenant: Instant) {
    val (pastille, precision) = etiquetteOuverture(o, maintenant)
    val ton = when (o) {
        is Ouverture.Ouvert -> Ton.OUVERT
        is Ouverture.Ferme -> Ton.FERME
        is Ouverture.Inconnu -> Ton.NEUTRE
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (nom != null) {
            Text(nom, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f), maxLines = 1)
            Spacer(Modifier.width(8.dp))
        }
        Pastille(pastille, ton)
        precision?.let {
            Spacer(Modifier.width(8.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 1,
                modifier = if (nom == null) Modifier.weight(1f) else Modifier)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailEquipement(e: Equipement, maintenant: Instant, onFermer: () -> Unit) {
    val uri = LocalUriHandler.current
    val aujourdhui = maintenant.atZone(FuseauCesson).toLocalDate()
    ModalBottomSheet(onDismissRequest = onFermer, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(e.lieu.nom, style = MaterialTheme.typography.titleLarge)
            e.lieu.adresse?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            e.espaces.forEach { s ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (e.espaces.size > 1 || s.nom != e.lieu.nom) {
                        Text(s.nom, style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                    }
                    s.jours.filter { it.date.isBefore(aujourdhui.plusDays(7)) && !it.date.isBefore(aujourdhui) }.forEach { j ->
                        Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { }) {
                            Text(nomDuJour(j.date, aujourdhui), style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (j.date == aujourdhui) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier.width(120.dp))
                            Text(libelleJour(j), style = MaterialTheme.typography.bodyMedium,
                                color = if (j.connu) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    s.remarque?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            e.remarque?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Text(
                "Horaires recopiés du site de la ville, vérifiés le ${e.verifieLe.dayOfMonth} " +
                    "${e.verifieLe.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.FRANCE)} " +
                    "${e.verifieLe.year}. En cas de doute, la page officielle fait foi.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row {
                e.lieu.url?.let { url ->
                    TextButton(onClick = { uri.openUri(url) }) {
                        Text("Page officielle")
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                }
                e.telephone?.let { tel ->
                    TextButton(onClick = { runCatching { uri.openUri("tel:" + tel.filter { it.isDigit() || it == '+' }) } }) {
                        Icon(Icons.Rounded.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(tel)
                    }
                }
            }
        }
    }
}
