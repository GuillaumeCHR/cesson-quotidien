package com.yogistudio.cesson.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Pool
import androidx.compose.material.icons.rounded.Train
import androidx.compose.material.icons.rounded.Water
import androidx.compose.material.icons.rounded.WbCloudy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yogistudio.cesson.CessonApp
import com.yogistudio.cesson.core.donnees.EtatModule
import com.yogistudio.cesson.core.preferences.ConfigAccueil
import com.yogistudio.cesson.core.preferences.deplacer
import com.yogistudio.cesson.core.preferences.ordonnerTuiles
import com.yogistudio.cesson.core.ui.CarteDonnee
import com.yogistudio.cesson.core.ui.TexteBientot
import com.yogistudio.cesson.eau.BandeauCrue
import com.yogistudio.cesson.eau.CarteVilaine
import com.yogistudio.cesson.eau.enAlerte
import com.yogistudio.cesson.eau.vigicrues
import com.yogistudio.cesson.eau.vilaine
import com.yogistudio.cesson.environnement.CarteMeteo
import com.yogistudio.cesson.environnement.meteo
import kotlinx.coroutines.launch

/**
 * Tuiles de l'accueil. L'utilisateur les ordonne et les masque (écran Personnaliser) ; le choix
 * reste sur le téléphone. Chaque nouveau module ajoute sa tuile ici : elle apparaît à la fin de
 * l'accueil des utilisateurs existants, sans effacer leur ordre.
 */
data class Tuile(
    val id: String,
    val titre: String,
    val icone: ImageVector,
    val contenu: @Composable (CessonApp) -> Unit,
)

val TUILES: List<Tuile> = listOf(
    Tuile("meteo", "Météo", Icons.Rounded.WbCloudy) { app ->
        val etat by remember(app) { app.depot.meteo() }.collectAsStateWithLifecycle(EtatModule.Absent)
        CarteMeteo(etat)
    },
    Tuile("vilaine", "La Vilaine", Icons.Rounded.Water) { app ->
        val etat by remember(app) { app.depot.vilaine() }.collectAsStateWithLifecycle(EtatModule.Absent)
        val vigilance by remember(app) { app.depot.vigicrues() }.collectAsStateWithLifecycle(EtatModule.Absent)
        CarteVilaine(etat, vigilance)
    },
    Tuile("arrets", "Prochains départs à Cesson-ViaSilva", Icons.Rounded.DirectionsBus) {
        CarteBientot("Prochains départs à Cesson-ViaSilva", Icons.Rounded.DirectionsBus,
            "Métro b et bus STAR en temps réel, bientôt ici.")
    },
    Tuile("trains", "Halte de Cesson-Sévigné", Icons.Rounded.Train) {
        CarteBientot("Halte de Cesson-Sévigné", Icons.Rounded.Train,
            "Prochains trains vers Rennes et Vitré, bientôt ici.")
    },
    Tuile("collecte", "Ma collecte", Icons.Rounded.DeleteOutline) {
        CarteBientot("Ma collecte", Icons.Rounded.DeleteOutline,
            "Choisissez bientôt votre secteur pour connaître la prochaine collecte.")
    },
    Tuile("equipements", "Mes équipements", Icons.Rounded.Pool) {
        CarteBientot("Mes équipements", Icons.Rounded.Pool,
            "Piscine, médiathèque, Pont des Arts : ouvert ou fermé, bientôt ici.")
    },
    Tuile("air", "Qualité de l'air", Icons.Rounded.Air) {
        CarteBientot("Qualité de l'air", Icons.Rounded.Air, "Indice ATMO du jour, bientôt ici.")
    },
)

@Composable
private fun CarteBientot(titre: String, icone: ImageVector, texte: String) =
    CarteDonnee(titre, icone) { TexteBientot(texte) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcranAujourdhui(app: CessonApp, onPersonnaliser: () -> Unit) {
    val portee = rememberCoroutineScope()
    val config by app.preferences.configAccueil.collectAsState(initial = null)
    val bandeauVu by app.preferences.bandeauNonOfficielVu.collectAsState(initial = true)
    val enCours by app.depot.rafraichissementEnCours.collectAsStateWithLifecycle()
    val erreur by app.depot.derniereErreur.collectAsStateWithLifecycle()
    val messages = remember { SnackbarHostState() }
    val vigilance by remember(app) { app.depot.vigicrues() }.collectAsStateWithLifecycle(EtatModule.Absent)
    val cfg = config ?: return
    val visibles = ordonnerTuiles(TUILES.map { it.id }, cfg.ordre)
        .filter { it !in cfg.masquees }
        .mapNotNull { id -> TUILES.firstOrNull { it.id == id } }

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
            // Vigilance crues jaune ou plus : toujours en tête, hors des tuiles personnalisables.
            if (vigilance.enAlerte()) item("crue") { BandeauCrue(vigilance) }
            if (!bandeauVu) {
                item("bandeau") { BandeauNonOfficiel { portee.launch { app.preferences.marquerBandeauVu() } } }
            }
            if (erreur != null && !enCours) {
                item("hors-ligne") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CloudOff, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        Text("Hors connexion : dernières données reçues affichées.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            items(visibles, key = { it.id }) { tuile ->
                TuileMasquable(
                    tuile = tuile,
                    app = app,
                    onMasquer = {
                        val avant = cfg
                        val ordre = ordonnerTuiles(TUILES.map { it.id }, cfg.ordre)
                        portee.launch {
                            app.preferences.enregistrerAccueil(ConfigAccueil(ordre, cfg.masquees + tuile.id))
                            val r = messages.showSnackbar("Carte « ${tuile.titre} » masquée", actionLabel = "Annuler",
                                duration = SnackbarDuration.Long)
                            if (r == SnackbarResult.ActionPerformed) app.preferences.enregistrerAccueil(avant)
                        }
                    },
                    onPersonnaliser = onPersonnaliser,
                )
            }
            if (visibles.isEmpty()) {
                item("vide") {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text("Toutes les tuiles sont masquées.", style = MaterialTheme.typography.bodyLarge)
                        TextButton(onClick = onPersonnaliser) { Text("Personnaliser l'accueil") }
                    }
                }
            }
        }
        SnackbarHost(messages, Modifier.align(Alignment.BottomCenter))
    }
}

/**
 * Appui long sur une carte : menu « Masquer cette carte » / « Personnaliser l'accueil ».
 * Les mêmes actions sont proposées à TalkBack (menu des actions), l'appui long n'y étant pas
 * découvrable. Les boutons de la carte (« Signaler une erreur »…) gardent leur propre clic.
 */
@Composable
private fun TuileMasquable(tuile: Tuile, app: CessonApp, onMasquer: () -> Unit, onPersonnaliser: () -> Unit) {
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
                    CustomAccessibilityAction("Personnaliser l'accueil") { onPersonnaliser(); true },
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
                text = { Text("Personnaliser l'accueil") },
                leadingIcon = { Icon(Icons.Rounded.Tune, contentDescription = null) },
                onClick = { menu = false; onPersonnaliser() },
            )
        }
    }
}

/** Montré au premier lancement : statut non officiel et promesse Yogi Studio. */
@Composable
private fun BandeauNonOfficiel(onCompris: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Bienvenue", style = MaterialTheme.typography.titleMedium)
            Text(
                "Cette application non officielle rassemble des données publiques sur Cesson-Sévigné. " +
                    "Elle n'est éditée ni par la Ville ni par Rennes Métropole.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(PROMESSE, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = MaterialTheme.typography.titleSmall.fontWeight))
            Text(
                "Réorganisez l'accueil avec le bouton de réglage, en haut à droite.",
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onCompris, modifier = Modifier.align(Alignment.End)) { Text("J'ai compris") }
        }
    }
}

const val PROMESSE = "Aucun compte, aucune publicité, aucune donnée collectée."

/**
 * Personnaliser l'accueil : monter, descendre, afficher ou masquer chaque tuile. Des boutons
 * plutôt qu'un glisser-déposer : ils fonctionnent tels quels avec TalkBack et au clavier.
 */
@Composable
fun EcranPersonnaliser(app: CessonApp) {
    val portee = rememberCoroutineScope()
    val config by app.preferences.configAccueil.collectAsState(initial = null)
    val cfg = config ?: return
    val ordre = ordonnerTuiles(TUILES.map { it.id }, cfg.ordre)

    fun enregistrer(c: ConfigAccueil) = portee.launch { app.preferences.enregistrerAccueil(c) }

    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
        item {
            Text(
                "Choisissez les tuiles affichées sur l'accueil et leur ordre. Ce réglage reste sur votre téléphone.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        items(ordre, key = { it }) { id ->
            val tuile = TUILES.first { it.id == id }
            val visible = id !in cfg.masquees
            val index = ordre.indexOf(id)
            ListItem(
                headlineContent = { Text(tuile.titre) },
                supportingContent = { Text(if (visible) "Affichée" else "Masquée") },
                leadingContent = { Icon(tuile.icone, contentDescription = null) },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(enabled = index > 0,
                            onClick = { enregistrer(cfg.copy(ordre = deplacer(ordre, id, -1))) }) {
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
            TextButton(
                onClick = { enregistrer(ConfigAccueil(emptyList(), emptySet())) },
                modifier = Modifier.padding(horizontal = 8.dp),
            ) { Text("Rétablir l'ordre par défaut") }
        }
    }
}
