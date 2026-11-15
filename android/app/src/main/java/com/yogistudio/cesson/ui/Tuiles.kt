package com.yogistudio.cesson.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yogistudio.cesson.CessonApp
import com.yogistudio.cesson.core.preferences.ConfigAccueil
import com.yogistudio.cesson.core.preferences.deplacer
import com.yogistudio.cesson.core.preferences.ordonnerTuiles
import kotlinx.coroutines.launch

/**
 * Une carte d'un onglet. L'utilisateur ordonne et masque les cartes de chaque onglet ; le choix
 * reste sur le téléphone. Une carte ajoutée par une mise à jour apparaît à la fin de l'onglet,
 * sans effacer l'ordre choisi.
 */
data class Tuile(
    val id: String,
    val titre: String,
    val icone: ImageVector,
    val contenu: @Composable (CessonApp) -> Unit,
)

/** Un onglet personnalisable : identifiant des préférences, nom pour les libellés, cartes. */
data class OngletTuiles(val id: String, val nom: String, val tuiles: List<Tuile>)

fun cartesVisibles(onglet: OngletTuiles, config: ConfigAccueil): List<Tuile> =
    ordonnerTuiles(onglet.tuiles.map { it.id }, config.ordre)
        .filter { it !in config.masquees }
        .mapNotNull { id -> onglet.tuiles.firstOrNull { it.id == id } }

/**
 * Écran commun aux onglets : glisser vers le bas pour actualiser, cartes dans l'ordre choisi,
 * appui long pour masquer. [entete] et [pied] accueillent ce qui ne se personnalise pas (bandeau
 * crue en tête de l'accueil, liste « bientôt » en bas des onglets).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcranTuiles(
    app: CessonApp,
    onglet: OngletTuiles,
    onPersonnaliser: () -> Unit,
    entete: LazyListScope.() -> Unit = {},
    pied: LazyListScope.() -> Unit = {},
) {
    val portee = rememberCoroutineScope()
    val config by remember(onglet.id) { app.preferences.configOnglet(onglet.id) }.collectAsState(initial = null)
    val enCours by app.depot.rafraichissementEnCours.collectAsStateWithLifecycle()
    val erreur by app.depot.derniereErreur.collectAsStateWithLifecycle()
    val messages = remember { SnackbarHostState() }
    val cfg = config ?: return
    val visibles = cartesVisibles(onglet, cfg)

    PullToRefreshBox(
        isRefreshing = enCours,
        onRefresh = { portee.launch { app.depot.rafraichir() } },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            entete()
            if (erreur != null && !enCours) {
                item("hors-ligne") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        Text("Hors connexion : dernières données reçues affichées.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            items(visibles, key = { it.id }) { tuile ->
                TuileMasquable(
                    tuile = tuile,
                    app = app,
                    nomOnglet = onglet.nom,
                    onMasquer = {
                        val avant = cfg
                        val ordre = ordonnerTuiles(onglet.tuiles.map { it.id }, cfg.ordre)
                        portee.launch {
                            app.preferences.enregistrerOnglet(onglet.id, ConfigAccueil(ordre, cfg.masquees + tuile.id))
                            val r = messages.showSnackbar("Carte « ${tuile.titre} » masquée", actionLabel = "Annuler",
                                duration = SnackbarDuration.Long)
                            if (r == SnackbarResult.ActionPerformed) app.preferences.enregistrerOnglet(onglet.id, avant)
                        }
                    },
                    onPersonnaliser = onPersonnaliser,
                )
            }
            if (visibles.isEmpty()) {
                item("vide") {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text("Toutes les cartes sont masquées.", style = MaterialTheme.typography.bodyLarge)
                        TextButton(onClick = onPersonnaliser) { Text("Personnaliser ${onglet.nom}") }
                    }
                }
            }
            pied()
        }
        SnackbarHost(messages, Modifier.align(Alignment.BottomCenter))
    }
}

/**
 * Appui long sur une carte : « Masquer cette carte » / « Personnaliser… ». Les mêmes actions sont
 * proposées à TalkBack (menu des actions), l'appui long n'y étant pas découvrable.
 */
@Composable
private fun TuileMasquable(
    tuile: Tuile,
    app: CessonApp,
    nomOnglet: String,
    onMasquer: () -> Unit,
    onPersonnaliser: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val haptique = LocalHapticFeedback.current
    Box(
        Modifier
            .pointerInput(tuile.id) {
                detectTapGestures(onLongPress = {
                    haptique.performHapticFeedback(HapticFeedbackType.LongPress)
                    menu = true
                })
            }
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction("Masquer cette carte") { onMasquer(); true },
                    CustomAccessibilityAction("Personnaliser $nomOnglet") { onPersonnaliser(); true },
                )
            },
    ) {
        tuile.contenu(app)
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text("Masquer cette carte") },
                leadingIcon = { Icon(Icons.Rounded.VisibilityOff, contentDescription = null) },
                onClick = { menu = false; onMasquer() },
            )
            DropdownMenuItem(
                text = { Text("Personnaliser $nomOnglet") },
                leadingIcon = { Icon(Icons.Rounded.Tune, contentDescription = null) },
                onClick = { menu = false; onPersonnaliser() },
            )
        }
    }
}

/**
 * Personnaliser un onglet : monter, descendre, afficher ou masquer chaque carte. Des boutons
 * plutôt qu'un glisser-déposer : ils fonctionnent tels quels avec TalkBack et au clavier.
 */
@Composable
fun EcranPersonnaliser(app: CessonApp, onglet: OngletTuiles) {
    val portee = rememberCoroutineScope()
    val config by remember(onglet.id) { app.preferences.configOnglet(onglet.id) }.collectAsState(initial = null)
    val cfg = config ?: return
    val ordre = ordonnerTuiles(onglet.tuiles.map { it.id }, cfg.ordre)

    fun enregistrer(c: ConfigAccueil) = portee.launch { app.preferences.enregistrerOnglet(onglet.id, c) }

    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
        item {
            Text(
                "Choisissez les cartes affichées dans ${onglet.nom} et leur ordre. Ce réglage reste sur votre téléphone.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        items(ordre, key = { it }) { id ->
            val tuile = onglet.tuiles.first { it.id == id }
            val visible = id !in cfg.masquees
            val index = ordre.indexOf(id)
            ListItem(
                headlineContent = { Text(tuile.titre) },
                supportingContent = { Text(if (visible) "Affichée" else "Masquée") },
                leadingContent = { Icon(tuile.icone, contentDescription = null) },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(enabled = index > 0, onClick = { enregistrer(cfg.copy(ordre = deplacer(ordre, id, -1))) }) {
                            Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Monter ${tuile.titre}")
                        }
                        IconButton(enabled = index < ordre.lastIndex,
                            onClick = { enregistrer(cfg.copy(ordre = deplacer(ordre, id, +1))) }) {
                            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Descendre ${tuile.titre}")
                        }
                        Switch(
                            checked = visible,
                            onCheckedChange = { afficher ->
                                val m = if (afficher) cfg.masquees - id else cfg.masquees + id
                                enregistrer(ConfigAccueil(ordre, m))
                            },
                            modifier = Modifier.semantics {
                                stateDescription = if (visible) "${tuile.titre} affichée" else "${tuile.titre} masquée"
                            },
                        )
                    }
                },
            )
        }
        item {
            TextButton(onClick = { enregistrer(ConfigAccueil(emptyList(), emptySet())) }, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text("Rétablir l'ordre par défaut")
            }
        }
    }
}
