package com.yogistudio.cesson.transport

import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yogistudio.cesson.core.ui.CarteDonnee
import com.yogistudio.cesson.core.ui.LocalCompact
import com.yogistudio.cesson.core.ui.TexteBientot

/**
 * « Mon arrêt » : un arrêt de Cesson choisi par l'utilisateur, affiché comme le pôle ViaSilva.
 * Le choix reste sur le téléphone ; STAR ne reçoit que le nom de l'arrêt interrogé.
 */
@Composable
fun CarteMonArret(
    client: ClientStar,
    lignes: DonneesLignesStar?,
    arretSuivi: String?,
    onChoisir: (String) -> Unit,
    modifier: Modifier = Modifier,
    alerte: AlerteBus? = null,
    delaiAlerte: Int = DELAI_ALERTE_DEFAUT,
    onAlerter: (Direction) -> Unit = {},
    onAnnulerAlerte: () -> Unit = {},
    onDelaiAlerte: (Int) -> Unit = {},
) {
    var choix by rememberSaveable { mutableStateOf(false) }
    val arrets = lignes?.arrets.orEmpty()
    if (arretSuivi == null) {
        CarteDonnee("Mon arrêt de bus", Icons.Rounded.DirectionsBus, modifier) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TexteBientot("Choisissez l'arrêt que vous prenez le plus souvent pour voir ses prochains départs ici.")
                OutlinedButton(onClick = { choix = true }, enabled = arrets.isNotEmpty()) {
                    Text(if (arrets.isEmpty()) "Liste des arrêts en cours de chargement…" else "Choisir mon arrêt de bus")
                }
            }
        }
    } else {
        val armee = alerte?.takeIf { it.arret == arretSuivi }
        CarteDeparts(
            client, lignes, nomArret = arretSuivi, titre = "Mon arrêt de bus : $arretSuivi", modifier = modifier,
            onAlerter = onAlerter, alerteArmee = armee?.let { it.ligne to it.destination },
        ) {
            // Compact : l'alerte (cloches, délai, alerte armée) reste complète ; seul le changement d'arrêt est réservé
            // à la version détaillée.
            val compact = LocalCompact.current
            PiedAlerte(armee, delaiAlerte, onAnnulerAlerte, onDelaiAlerte)
            if (!compact) TextButton(onClick = { choix = true }, enabled = arrets.isNotEmpty()) { Text("Changer d'arrêt") }
        }
    }
    if (choix) {
        ChoixArret(arrets, lignes, actuel = arretSuivi, onFermer = { choix = false }) {
            onChoisir(it)
            choix = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ChoixArret(
    arrets: List<ArretStar>,
    lignes: DonneesLignesStar?,
    actuel: String?,
    onFermer: () -> Unit,
    onChoisir: (String) -> Unit,
) {
    var recherche by rememberSaveable { mutableStateOf("") }
    val couleurs = remember(lignes) { lignes?.lignes?.associateBy { it.nomCourt } ?: emptyMap() }
    val resultats = remember(arrets, recherche) { filtrerArrets(arrets, recherche) }
    ModalBottomSheet(onDismissRequest = onFermer, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text("Choisir mon arrêt de bus", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = recherche,
                onValueChange = { recherche = it },
                label = { Text("Nom de l'arrêt ou ligne") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )
        }
        LazyColumn(Modifier.heightIn(max = 520.dp)) {
            if (resultats.isEmpty()) {
                item { Column(Modifier.padding(16.dp)) { TexteBientot("Aucun arrêt de Cesson ne correspond.") } }
            }
            items(resultats, key = { it.nom }) { a ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button, onClickLabel = "Choisir ${a.nom}") { onChoisir(a.nom) }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .semantics(mergeDescendants = true) {
                            contentDescription = buildString {
                                append(a.nom)
                                if (a.nom == actuel) append(", arrêt actuel")
                                append(". Lignes ${a.lignes.joinToString(", ")}")
                            }
                        },
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        if (a.nom == actuel) "${a.nom} (actuel)" else a.nom,
                        style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        a.lignes.forEach { PastilleLigne(it, couleurs[it]) }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

/**
 * Proposition d'alerte : délai de prévenance réglable (2 à 15 min), et l'alerte en cours avec « Annuler ».
 * Pour armer, une cloche sur chaque direction.
 */
@Composable
private fun PiedAlerte(alerte: AlerteBus?, delai: Int, onAnnuler: () -> Unit, onDelai: (Int) -> Unit, reglage: Boolean = true) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (alerte != null) {
            val heure = com.yogistudio.cesson.core.ui.formaterHeure(java.time.Instant.ofEpochSecond(alerte.heureVisee), java.time.Instant.now())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Alerte ${alerte.delaiMin} min avant le ${alerte.ligne} vers ${alerte.destination} (passage prévu à $heure)",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                TextButton(onClick = onAnnuler) { Text("Annuler") }
            }
        }
        if (reglage) Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Touchez une cloche pour être prévenu", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            IconButton(onClick = { onDelai((delai - 1).coerceIn(DELAIS_ALERTE)) }, enabled = delai > DELAIS_ALERTE.first) {
                Icon(Icons.Rounded.Remove, contentDescription = "Prévenir moins tôt")
            }
            Text("$delai min avant", style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.semantics { contentDescription = "Prévenir $delai minutes avant le passage" })
            IconButton(onClick = { onDelai((delai + 1).coerceIn(DELAIS_ALERTE)) }, enabled = delai < DELAIS_ALERTE.last) {
                Icon(Icons.Rounded.Add, contentDescription = "Prévenir plus tôt")
            }
        }
    }
}
