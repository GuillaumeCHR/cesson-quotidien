package com.yogistudio.cesson.transport

import androidx.compose.material3.FilterChip
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.TripOrigin
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
 * « Mon trajet réseau Star » : un arrêt de Cesson et, facultative, une destination sur tout le réseau. À l'aller, les
 * départs en approche de l'arrêt de Cesson ; au retour, ceux de la destination.
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
    lignePreferee: String? = null,
    onLignePreferee: (String?) -> Unit = {},
    destination: String? = null,
    onDestination: (String?) -> Unit = {},
) {
    var choixDestination by rememberSaveable { mutableStateOf(false) }
    var choix by rememberSaveable { mutableStateOf(false) }
    // Trajet pendulaire : l'aller part de Cesson, le retour de la destination. Le matin l'aller, l'après-midi le retour.
    var retour by rememberSaveable(destination) { mutableStateOf(destination != null && java.time.LocalTime.now().hour >= 13) }
    val arrets = lignes?.arrets.orEmpty()
    val reseauPret = !lignes?.reseau.isNullOrEmpty()
    val compact = LocalCompact.current
    // Les deux arrêts du trajet, chacun modifiable d'un toucher (version détaillée, ou tant que rien n'est choisi).
    val trajet: @Composable () -> Unit = {
        Column {
            LigneTrajet(Icons.Rounded.TripOrigin, "À Cesson", arretSuivi ?: "Choisir mon arrêt",
                choisi = arretSuivi != null, actif = arrets.isNotEmpty()) { choix = true }
            LigneTrajet(Icons.Rounded.Place, "Destination", destination ?: "Tout le réseau STAR (facultatif)",
                choisi = destination != null, actif = reseauPret) { choixDestination = true }
        }
    }
    if (arretSuivi == null) {
        CarteDonnee(TITRE_TRAJET, Icons.Rounded.DirectionsBus, modifier) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TexteBientot("Choisissez votre arrêt à Cesson et, si vous voulez, votre destination n'importe où sur le " +
                    "réseau : la carte montre les bus et métros en approche de l'un, à l'aller, et de l'autre, au retour.")
                trajet()
                if (arrets.isEmpty()) TexteBientot("Liste des arrêts en cours de chargement…")
            }
        }
    } else {
        val origine = if (retour && destination != null) destination else arretSuivi
        val armee = alerte?.takeIf { it.arret == origine }
        CarteDeparts(
            client, lignes, nomArret = origine, titre = TITRE_TRAJET, modifier = modifier,
            onAlerter = onAlerter, alerteArmee = armee?.let { it.ligne to it.destination }, lignePreferee = lignePreferee,
            entete = {
                if (!compact) trajet()
                if (destination != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !retour, onClick = { retour = false }, label = { Text("Aller") })
                        FilterChip(selected = retour, onClick = { retour = true }, label = { Text("Retour") })
                        Text("Départs de $origine", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f))
                    }
                }
            },
        ) {
            // Compact : l'alerte (cloches, délai, alerte armée) reste complète ; seul le changement d'arrêt est réservé
            // à la version détaillée.
            PiedAlerte(armee, delaiAlerte, onAnnulerAlerte, onDelaiAlerte)
            if (!compact) {
                // Détaillé : la ligne préférée se choisit aussi ici (facultatif : « Aucune » garde l'ordre habituel).
                val desservies = (arrets + lignes?.reseau.orEmpty()).firstOrNull { it.nom == origine }?.lignes.orEmpty()
                if (desservies.size > 1) {
                    Text("Ma ligne préférée", style = MaterialTheme.typography.labelLarge)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = lignePreferee == null || lignePreferee !in desservies,
                            onClick = { onLignePreferee(null) }, label = { Text("Aucune") })
                        desservies.forEach { l ->
                            FilterChip(selected = l == lignePreferee, onClick = { onLignePreferee(l) }, label = { Text(l) })
                        }
                    }
                }
            }
        }
    }
    if (choix) {
        ChoixArret(arrets, lignes, actuel = arretSuivi, onFermer = { choix = false }) {
            onChoisir(it)
            choix = false
        }
    }
    if (choixDestination) {
        ChoixDestination(lignes, destination, onFermer = { choixDestination = false }) {
            onDestination(it)
            choixDestination = false
        }
    }
}

const val TITRE_TRAJET = "Mon trajet réseau Star"

/** Une extrémité du trajet : icône, « Départ » ou « Arrivée », l'arrêt, et « Changer » ou « Choisir ». */
@Composable
internal fun LigneTrajet(
    icone: androidx.compose.ui.graphics.vector.ImageVector,
    libelle: String,
    arret: String,
    choisi: Boolean,
    actif: Boolean,
    onToucher: () -> Unit,
) {
    val action = if (choisi) "Changer" else "Choisir"
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clickable(enabled = actif, role = Role.Button, onClickLabel = "$action l'arrêt ${libelle.lowercase()}") { onToucher() }
            .semantics(mergeDescendants = true) { contentDescription = "$libelle : $arret" },
    ) {
        Icon(icone, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(libelle, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(92.dp))
        Text(arret, style = if (choisi) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
            color = if (choisi) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (actif) Text(action, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}

/**
 * Destination de « Mon trajet réseau Star » : n'importe quel arrêt du réseau STAR, recherché par nom ou par ligne.
 * « Aucune destination » retire le retour.
 */
@Composable
fun ChoixDestination(lignes: DonneesLignesStar?, actuel: String?, onFermer: () -> Unit, onChoisir: (String?) -> Unit) {
    ChoixArret(
        lignes?.reseau.orEmpty(), lignes, actuel = actuel, onFermer = onFermer, onChoisir = { onChoisir(it) },
        onRetirer = { onChoisir(null) },
        titre = "Ma destination",
        aucunResultat = "Aucun arrêt du réseau STAR ne correspond.",
        retirer = if (actuel != null) "Aucune destination" else null,
    )
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ChoixArret(
    arrets: List<ArretStar>,
    lignes: DonneesLignesStar?,
    actuel: String?,
    onFermer: () -> Unit,
    titre: String = "Choisir mon arrêt de bus",
    aucunResultat: String = "Aucun arrêt de Cesson ne correspond.",
    /** Si fourni (avec [onRetirer]), une première ligne qui retire le choix. */
    retirer: String? = null,
    onRetirer: () -> Unit = {},
    onChoisir: (String) -> Unit,
) {
    var recherche by rememberSaveable { mutableStateOf("") }
    val couleurs = remember(lignes) { lignes?.lignes?.associateBy { it.nomCourt } ?: emptyMap() }
    val resultats = remember(arrets, recherche) { filtrerArrets(arrets, recherche) }
    ModalBottomSheet(onDismissRequest = onFermer, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(titre, style = MaterialTheme.typography.titleLarge)
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
            if (retirer != null && recherche.isBlank()) item {
                Text(retirer, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { onRetirer() }
                        .padding(horizontal = 16.dp, vertical = 14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            if (resultats.isEmpty()) {
                item { Column(Modifier.padding(16.dp)) { TexteBientot(aucunResultat) } }
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
