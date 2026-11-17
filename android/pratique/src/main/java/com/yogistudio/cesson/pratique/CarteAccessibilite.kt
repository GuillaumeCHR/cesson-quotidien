package com.yogistudio.cesson.pratique

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Accessible
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Directions
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yogistudio.cesson.core.donnees.EtatModule
import com.yogistudio.cesson.core.modele.Coord
import com.yogistudio.cesson.core.ui.CarteDonnee
import com.yogistudio.cesson.core.ui.LocalCompact
import com.yogistudio.cesson.core.ui.Pastille
import com.yogistudio.cesson.core.ui.TexteBientot
import com.yogistudio.cesson.core.ui.rememberMaintenant

private const val NB_LIEUX = 5

/**
 * Accessibilité des lieux recevant du public (acceslibre) : chercher un lieu, ou parcourir par famille,
 * services publics en tête. Chaque ligne se déplie sur le détail de la fiche, l'itinéraire et la fiche complète.
 * La carte gère elle-même sa longueur (5 lieux, puis « Tous les lieux ») : elle n'est pas repliable.
 */
@Composable
fun CarteAccessibilite(
    etat: EtatModule<DonneesAccessibilite>,
    position: Coord?,
    rechercheEnCours: Boolean,
    onDemanderPosition: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val maintenant = rememberMaintenant()
    val compact = LocalCompact.current
    CarteDonnee("Accessibilité des lieux", Icons.Rounded.Accessible, modifier,
        (etat as? EtatModule.Disponible)?.fraicheur, maintenant, repliable = false) {
        when (etat) {
            EtatModule.Absent -> TexteBientot("Liste pas encore reçue. Tirez vers le bas pour actualiser.")
            is EtatModule.Disponible -> {
                ContenuAccessibilite(etat.fichier.donnees.etablissements, position, rechercheEnCours, onDemanderPosition, compact)
            }
        }
    }
}

@Composable
private fun ContenuAccessibilite(
    tous: List<Etablissement>,
    position: Coord?,
    rechercheEnCours: Boolean,
    onDemanderPosition: () -> Unit,
    compact: Boolean,
) {
    var recherche by rememberSaveable { mutableStateOf("") }
    var famille by rememberSaveable { mutableStateOf(FamilleLieu.Tous) }
    var tout by rememberSaveable { mutableStateOf(false) }
    var ouvert by rememberSaveable { mutableStateOf<String?>(null) }
    val focus = LocalFocusManager.current
    val filtres = remember(tous, recherche, famille) { filtrerEtablissements(tous, recherche, famille) }
    val liste = remember(filtres, position) { parProximite(filtres, position) { it.lieu } }
    // Compact : 3 lieux tant qu'on ne cherche rien, comme les autres cartes ; la recherche reste toujours là.
    val replie = if (compact && recherche.isBlank()) 3 else NB_LIEUX
    val nb = if (tout) liste.size else replie

    Column(Modifier.animateContentSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (!compact) Text("Entrée, stationnement, accueil, sanitaires : ce que disent les fiches acceslibre, remplies par les " +
            "établissements et par des contributeurs.", style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = recherche,
            onValueChange = { recherche = it; tout = false },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = if (compact) null else ({ Text("Chercher un lieu") }),
            placeholder = { Text(if (compact) "Chercher parmi ${tous.size} lieux" else "Mairie, piscine, boulangerie…") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = if (recherche.isNotEmpty()) ({
                IconButton(onClick = { recherche = "" }) { Icon(Icons.Rounded.Close, contentDescription = "Effacer la recherche") }
            }) else null,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { focus.clearFocus() }),
        )
        if (!compact) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FamilleLieu.entries.forEach { f ->
                    FilterChip(selected = f == famille, onClick = { famille = f; tout = false }, label = { Text(f.libelle) })
                }
            }
            BoutonProximite(position, rechercheEnCours, onDemanderPosition)
        }
        if (liste.isEmpty()) {
            TexteBientot(if (recherche.isNotBlank()) "Aucun lieu trouvé pour « ${recherche.trim()} »." else "Aucun lieu dans cette catégorie.")
        }
        liste.take(nb).forEachIndexed { i, (e, dist) ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            LigneEtablissement(e, dist, ouvert == e.lieu.id) { ouvert = if (ouvert == e.lieu.id) null else e.lieu.id }
        }
        if (liste.size > replie) {
            TextButton(onClick = { tout = !tout }) {
                Text(if (tout) "Afficher moins" else "Tous les lieux (${liste.size})")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LigneEtablissement(e: Etablissement, distance: Double?, deplie: Boolean, onBasculer: () -> Unit) {
    val uri = LocalUriHandler.current
    val itineraire = ouvrirItineraire()
    val pastilles = e.acces.pastilles()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClickLabel = if (deplie) "Masquer le détail" else "Voir le détail", onClick = onBasculer)
                .padding(vertical = 8.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = listOfNotNull(e.lieu.nom, distance?.let { "à ${formaterDistance(it)}" }, e.activite,
                        e.lieu.adresse, pastilles.joinToString(", ") { it.first }).joinToString(", ")
                    stateDescription = if (deplie) "Détail affiché" else "Détail masqué"
                },
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(e.lieu.nom, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                distance?.let {
                    Text(formaterDistance(it), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(4.dp))
                }
                Icon(if (deplie) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            listOfNotNull(e.activite, e.lieu.adresse).joinToString(" · ").takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                pastilles.forEach { (t, ton) -> Pastille(t, ton) }
            }
        }
        if (deplie) {
            Column(Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val details = e.details()
                if (details.none { it.first != "Fiche" }) {
                    Text("La fiche ne décrit pas encore l'accessibilité de ce lieu.", style = MaterialTheme.typography.bodyMedium)
                }
                details.forEach { (titre, texte) ->
                    Column(Modifier.semantics(mergeDescendants = true) {}) {
                        Text(titre, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        Text(texte, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (e.lieu.coord != null) {
                        OutlinedButton(onClick = { itineraire(e.lieu) }) {
                            Icon(Icons.Rounded.Directions, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Itinéraire")
                        }
                    }
                    e.lieu.url?.let { url ->
                        OutlinedButton(onClick = { runCatching { uri.openUri(url) } }) {
                            Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Fiche acceslibre")
                        }
                    }
                }
                Text("Une information manque ou a changé ? Elle se complète sur la fiche acceslibre, ouverte à tous.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
