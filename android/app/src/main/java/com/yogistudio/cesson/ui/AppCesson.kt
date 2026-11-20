package com.yogistudio.cesson.ui

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material.icons.rounded.UnfoldLess
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.FamilyRestroom
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Source
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlinx.coroutines.flow.first
import com.yogistudio.cesson.CessonApp
import androidx.compose.material.icons.rounded.Map
import com.yogistudio.cesson.ui.intro.CessonAppLogotype
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

@Serializable object RouteAujourdhui
@Serializable object RouteBouger
@Serializable object RouteSortir
@Serializable object RoutePratique
@Serializable object RouteEnvironnement
@Serializable object RouteFamilles
@Serializable data class RoutePersonnaliser(val onglet: String)
@Serializable object RouteGuide
@Serializable object RouteCarte
@Serializable object RouteReglages
@Serializable object RouteSources
@Serializable object RouteAPropos
@Serializable object RouteVosIdees

private data class Onglet(
    val route: Any, val classe: KClass<*>, val libelle: String, val icone: ImageVector,
    val tuiles: OngletTuiles? = null,
    /** Libellé de la barre du bas, quand le titre complet n'y tient pas à six onglets. */
    val court: String = libelle,
)

private val ONGLETS = listOf(
    Onglet(RouteAujourdhui, RouteAujourdhui::class, "Aujourd'hui", Icons.Rounded.Today, ONGLET_ACCUEIL),
    Onglet(RouteBouger, RouteBouger::class, "Bouger", Icons.Rounded.DirectionsBus, ONGLET_BOUGER),
    Onglet(RouteSortir, RouteSortir::class, "Sortir", Icons.Rounded.Celebration, ONGLET_SORTIR),
    Onglet(RouteFamilles, RouteFamilles::class, "Familles", Icons.Rounded.FamilyRestroom, ONGLET_FAMILLES),
    Onglet(RouteEnvironnement, RouteEnvironnement::class, "Environnement", Icons.Rounded.Eco, ONGLET_ENVIRONNEMENT, court = "Env."),
    Onglet(RoutePratique, RoutePratique::class, "Pratique", Icons.Rounded.VolunteerActivism, ONGLET_PRATIQUE),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppCesson(app: CessonApp) {
    val nav = rememberNavController()
    val entree by nav.currentBackStackEntryAsState()
    val destination = entree?.destination
    val onglet = ONGLETS.firstOrNull { o -> destination?.hierarchy?.any { it.hasRoute(o.classe) } == true }
    val titre = onglet?.libelle ?: when {
        destination?.hasRoute(RoutePersonnaliser::class) == true -> {
            val id = entree?.toRoute<RoutePersonnaliser>()?.onglet
            "Personnaliser " + (ONGLETS_PERSONNALISABLES.firstOrNull { it.id == id }?.nom ?: "")
        }
        destination?.hasRoute(RouteGuide::class) == true -> "Guide de démarrage"
        destination?.hasRoute(RouteReglages::class) == true -> "Réglages"
        destination?.hasRoute(RouteCarte::class) == true -> "Carte de Cesson"
        destination?.hasRoute(RouteSources::class) == true -> "Sources et licences"
        destination?.hasRoute(RouteAPropos::class) == true -> "À propos"
        destination?.hasRoute(RouteVosIdees::class) == true -> "Vos idées"
        else -> ""
    }

    // Premier lancement : le guide de démarrage, une seule fois (il reste ensuite dans le menu).
    LaunchedEffect(Unit) {
        if (!app.preferences.guideDemarrageVu.first()) nav.navigate(RouteGuide)
    }

    val detaillees by app.preferences.cartesDetaillees.collectAsStateWithLifecycle(false)
    val portee = rememberCoroutineScope()
    Scaffold(
        topBar = {
            TopAppBar(
                // L'accueil porte le logotype : l'intro le fait glisser jusqu'ici.
                title = { if (onglet?.route == RouteAujourdhui) CessonAppLogotype() else Text(titre) },
                navigationIcon = {
                    if (onglet == null) {
                        IconButton(onClick = { nav.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Retour")
                        }
                    }
                },
                actions = {
                    // Bascule compactes / détaillées : le même réglage que Réglages → Cartes, à portée de pouce.
                    if (onglet != null) IconButton(onClick = { portee.launch { app.preferences.enregistrerCartesDetaillees(!detaillees) } }) {
                        Icon(if (detaillees) Icons.Rounded.UnfoldLess else Icons.Rounded.UnfoldMore,
                            contentDescription = if (detaillees) "Passer aux cartes compactes" else "Passer aux cartes détaillées")
                    }
                    if (onglet != null) IconButton(onClick = { nav.navigate(RouteCarte) }) {
                        Icon(Icons.Rounded.Map, contentDescription = "Carte de Cesson-Sévigné")
                    }
                    onglet?.tuiles?.let { t ->
                        IconButton(onClick = { nav.navigate(RoutePersonnaliser(t.id)) }) {
                            Icon(Icons.Rounded.Tune, contentDescription = "Personnaliser ${t.nom}")
                        }
                    }
                    if (onglet != null) MenuPlus(nav)
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            if (onglet != null) BoxWithConstraints {
                // Une seule taille de libellé pour les six onglets : la plus grande (12 sp au plus) qui fait tenir
                // le plus long (« Aujourd'hui ») sur une ligne, selon la largeur de l'écran et la taille du texte.
                val style = MaterialTheme.typography.labelMedium
                val mesure = rememberTextMeasurer()
                val place = with(LocalDensity.current) { (maxWidth / ONGLETS.size - 4.dp).roundToPx() }
                val taille = remember(place, style) {
                    generateSequence(12f) { it - 0.5f }.takeWhile { it >= 8f }.firstOrNull { t ->
                        ONGLETS.all { o -> mesure.measure(o.court, style.copy(fontSize = t.sp), maxLines = 1).size.width <= place }
                    } ?: 8f
                }
                NavigationBar {
                    ONGLETS.forEach { o ->
                        NavigationBarItem(
                            selected = o == onglet,
                            onClick = {
                                nav.navigate(o.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(o.icone, contentDescription = null) },
                            // TalkBack lit le nom complet, pas « Env. ».
                            label = { Text(o.court, maxLines = 1, softWrap = false, style = style.copy(fontSize = taille.sp),
                                modifier = Modifier.clearAndSetSemantics { contentDescription = o.libelle }) },
                        )
                    }
                }
            }
        },
    ) { marges ->
        NavHost(nav, startDestination = RouteAujourdhui, modifier = Modifier.padding(marges)) {
            composable<RouteAujourdhui> { EcranAujourdhui(app) { nav.navigate(RoutePersonnaliser(ONGLET_ACCUEIL.id)) } }
            composable<RouteBouger> { EcranBouger(app) { nav.navigate(RoutePersonnaliser(ONGLET_BOUGER.id)) } }
            composable<RouteSortir> { EcranSortir(app) { nav.navigate(RoutePersonnaliser(ONGLET_SORTIR.id)) } }
            composable<RoutePratique> { EcranPratique(app) { nav.navigate(RoutePersonnaliser(ONGLET_PRATIQUE.id)) } }
            composable<RouteEnvironnement> { EcranEnvironnement(app) { nav.navigate(RoutePersonnaliser(ONGLET_ENVIRONNEMENT.id)) } }
            composable<RouteFamilles> { EcranFamilles(app) { nav.navigate(RoutePersonnaliser(ONGLET_FAMILLES.id)) } }
            composable<RoutePersonnaliser> { e ->
                val id = e.toRoute<RoutePersonnaliser>().onglet
                EcranPersonnaliser(app, ONGLETS_PERSONNALISABLES.firstOrNull { it.id == id } ?: ONGLET_ACCUEIL)
            }
            composable<RouteReglages> { EcranReglages(app) }
            composable<RouteCarte> { EcranCarte(app) }
            composable<RouteGuide> { EcranGuide(app, onTerminer = { nav.popBackStack() }) }
            composable<RouteSources> { EcranSources(app) }
            composable<RouteAPropos> { EcranAPropos(onVosIdees = { nav.navigate(RouteVosIdees) }) }
            composable<RouteVosIdees> { EcranVosIdees() }
        }
    }
}

@Composable
private fun MenuPlus(nav: NavHostController) {
    var ouvert by remember { mutableStateOf(false) }
    IconButton(onClick = { ouvert = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "Plus d'options") }
    DropdownMenu(expanded = ouvert, onDismissRequest = { ouvert = false }) {
        listOf(
            Triple("Réglages", Icons.Rounded.Settings, RouteReglages),
            Triple("Vos idées / signaler une erreur", Icons.Rounded.Lightbulb, RouteVosIdees),
            Triple("Guide de démarrage", Icons.Rounded.Explore, RouteGuide),
            Triple("Sources et licences", Icons.Rounded.Source, RouteSources),
            Triple("À propos", Icons.Rounded.Info, RouteAPropos),
        ).forEach { (libelle, icone, route) ->
            DropdownMenuItem(
                text = { Text(libelle) },
                leadingIcon = { Icon(icone, contentDescription = null) },
                onClick = { ouvert = false; nav.navigate(route) },
            )
        }
    }
}
