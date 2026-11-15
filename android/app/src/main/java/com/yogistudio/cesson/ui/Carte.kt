package com.yogistudio.cesson.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.graphics.drawable.GradientDrawable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yogistudio.cesson.BuildConfig
import com.yogistudio.cesson.CessonApp
import com.yogistudio.cesson.core.donnees.EtatModule
import com.yogistudio.cesson.core.modele.Coord
import com.yogistudio.cesson.core.ui.rememberMaintenant
import com.yogistudio.cesson.equipements.equipements
import com.yogistudio.cesson.equipements.etiquetteOuverture
import com.yogistudio.cesson.equipements.ouverture
import com.yogistudio.cesson.position.Position
import com.yogistudio.cesson.position.rememberDemandePosition
import com.yogistudio.cesson.pratique.bornes
import com.yogistudio.cesson.pratique.carburants
import com.yogistudio.cesson.pratique.defibrillateurs
import com.yogistudio.cesson.pratique.formaterPuissance
import com.yogistudio.cesson.pratique.toujoursAccessible
import com.yogistudio.cesson.transport.ErreurStar
import com.yogistudio.cesson.transport.ParcRelais
import com.yogistudio.cesson.transport.parcsRelais
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker
import java.io.File
import java.net.URLEncoder
import java.time.Instant

/** Emprise de la commune (geo.api.gouv.fr) avec une marge : la même que les tuiles servies par le Pi (tuiles.py). */
private val EMPRISE = BoundingBox(48.166, -1.541, 48.081, -1.645)
private val COMMUNE = BoundingBox(48.16041, -1.550313, 48.0866, -1.636682)
private val MAIRIE = GeoPoint(48.1213, -1.6027)

fun dansCesson(c: Coord): Boolean = COMMUNE.contains(c.lat, c.lon)

/** Catégories d'épingles, avec leur couleur et leur filtre. */
enum class Categorie(val libelle: String, val couleur: Color) {
    EQUIPEMENTS("Équipements", Color(0xFF2E7D5B)),
    DEFIBRILLATEURS("Défibrillateurs", Color(0xFFC62828)),
    CARBURANTS("Carburants", Color(0xFFE08A00)),
    BORNES("Bornes de recharge", Color(0xFF1565C0)),
    PARC_RELAIS("Parc relais", Color(0xFF6A3FA0)),
}

/** Une épingle : ce que la fiche affiche quand on la touche. */
data class Epingle(val categorie: Categorie, val nom: String, val detail: String?, val coord: Coord)

/**
 * Carte de Cesson-Sévigné : tuiles OpenStreetMap relayées par le serveur de l'app (OpenStreetMap ne voit pas
 * les utilisateurs), lieux de l'app en épingles filtrables, position à la demande. La position reste sur le
 * téléphone ; hors de Cesson, la carte reste sur la commune, sans point « vous êtes ici ».
 */
@Composable
fun EcranCarte(app: CessonApp) {
    val context = LocalContext.current
    val uri = LocalUriHandler.current
    val maintenant = rememberMaintenant()
    val equipements by remember(app) { app.depot.equipements() }.collectAsStateWithLifecycle(EtatModule.Absent)
    val defibs by remember(app) { app.depot.defibrillateurs() }.collectAsStateWithLifecycle(EtatModule.Absent)
    val bornes by remember(app) { app.depot.bornes() }.collectAsStateWithLifecycle(EtatModule.Absent)
    val carburants by remember(app) { app.depot.carburants() }.collectAsStateWithLifecycle(EtatModule.Absent)
    var parcs by remember { mutableStateOf<List<ParcRelais>>(emptyList()) }
    LaunchedEffect(Unit) { parcs = try { app.star.parcsRelais() } catch (_: ErreurStar) { emptyList() } }
    val position by Position.actuelle.collectAsStateWithLifecycle()
    val demande = rememberDemandePosition()
    var actives by rememberSaveable { mutableStateOf(Categorie.entries.map { it.name }.toSet() - Categorie.BORNES.name) }
    var choisie by remember { mutableStateOf<Epingle?>(null) }
    var horsCesson by remember { mutableStateOf(false) }
    var suivre by remember { mutableStateOf(false) }

    val epingles = buildList {
        (equipements as? EtatModule.Disponible)?.fichier?.donnees?.equipements?.forEach { e ->
            val c = e.lieu.coord ?: return@forEach
            val etat = e.espaces.firstOrNull()?.let { etiquetteOuverture(ouverture(it, maintenant), maintenant) }
            add(Epingle(Categorie.EQUIPEMENTS, e.lieu.nom, etat?.let { (p, d) -> listOfNotNull(p, d).joinToString(" · ") }, c))
        }
        (defibs as? EtatModule.Disponible)?.fichier?.donnees?.defibrillateurs?.forEach { d ->
            val c = d.lieu.coord ?: return@forEach
            add(Epingle(Categorie.DEFIBRILLATEURS, d.lieu.nom,
                if (d.toujoursAccessible) "Dehors, accès libre" else if (d.libreAcces) "Accès libre aux heures du lieu" else "Accès restreint", c))
        }
        (carburants as? EtatModule.Disponible)?.fichier?.donnees?.stations?.forEach { s ->
            val c = s.lieu.coord ?: return@forEach
            add(Epingle(Categorie.CARBURANTS, s.lieu.nom, if (s.automate2424) "Automate 24 h/24" else null, c))
        }
        (bornes as? EtatModule.Disponible)?.fichier?.donnees?.stations?.forEach { s ->
            val c = s.lieu.coord ?: return@forEach
            add(Epingle(Categorie.BORNES, s.lieu.nom, "${formaterPuissance(s.puissanceMax)} · ${s.points} point${if (s.points > 1) "s" else ""}", c))
        }
        parcs.filter { it.idparc == "CVI" }.forEach { p ->
            val c = p.coordonnees ?: return@forEach
            add(Epingle(Categorie.PARC_RELAIS, "Parc relais ViaSilva",
                if (!p.ouvert) "Fermé" else "${p.jrdinfosoliste} places libres sur ${p.capacitesoliste}", Coord(c.lat, c.lon)))
        }
    }.filter { it.categorie.name in actives }

    val carte = remember { creerCarte(context) }
    val cycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(cycle) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> carte.onResume()
                Lifecycle.Event.ON_PAUSE -> carte.onPause()
                else -> {}
            }
        }
        cycle.addObserver(obs)
        onDispose { cycle.removeObserver(obs); carte.onDetach() }
    }
    // Position reçue après un appui sur « Me localiser » : dans Cesson, on y va ; dehors, on le dit et on reste.
    LaunchedEffect(position, suivre) {
        val p = position ?: return@LaunchedEffect
        if (!suivre) return@LaunchedEffect
        if (dansCesson(p)) carte.controller.animateTo(GeoPoint(p.lat, p.lon), 17.0, 600L) else horsCesson = true
        suivre = false
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { carte }, modifier = Modifier.fillMaxSize(), update = { m ->
            m.overlays.removeAll { it is Marker }
            epingles.forEach { e ->
                m.overlays.add(Marker(m).apply {
                    this.position = GeoPoint(e.coord.lat, e.coord.lon)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    icon = pastille(context, e.categorie.couleur, 13)
                    title = e.nom
                    setOnMarkerClickListener { _, _ -> choisie = e; true }
                })
            }
            position?.takeIf { dansCesson(it) }?.let { p ->
                m.overlays.add(Marker(m).apply {
                    this.position = GeoPoint(p.lat, p.lon)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    icon = pastille(context, Color(0xFF1A73E8), 9, halo = true)
                    title = "Vous êtes ici"
                    setOnMarkerClickListener { _, _ -> true }
                })
            }
            m.invalidate()
        })

        // Filtres
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Categorie.entries.forEach { c ->
                val active = c.name in actives
                FilterChip(
                    selected = active,
                    onClick = { actives = if (active) actives - c.name else actives + c.name },
                    label = { Text(c.libelle) },
                    leadingIcon = { Box(Modifier.padding(2.dp)) { Text("●", color = c.couleur) } },
                    colors = FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.surface),
                )
            }
        }

        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (horsCesson) {
                Bandeau("Vous êtes en dehors de Cesson-Sévigné : la carte reste sur la commune.") { horsCesson = false }
            }
            choisie?.let { e ->
                Surface(shape = MaterialTheme.shapes.large, tonalElevation = 3.dp, shadowElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text(e.categorie.libelle, style = MaterialTheme.typography.labelMedium, color = e.categorie.couleur)
                            Text(e.nom, style = MaterialTheme.typography.titleMedium)
                            e.detail?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                            TextButton(onClick = {
                                try {
                                    uri.openUri("geo:${e.coord.lat},${e.coord.lon}?q=${e.coord.lat},${e.coord.lon}(${URLEncoder.encode(e.nom, "UTF-8")})")
                                } catch (_: ActivityNotFoundException) { } catch (_: IllegalArgumentException) { }
                            }) { Text("Itinéraire") }
                        }
                        IconButton(onClick = { choisie = null }) { Icon(Icons.Rounded.Close, contentDescription = "Fermer la fiche") }
                    }
                }
            }
            Row(Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(1f))
                FloatingActionButton(onClick = { suivre = true; if (position == null) demande.demander() }) {
                    Icon(Icons.Rounded.MyLocation, contentDescription = "Me localiser")
                }
            }
        }
    }
}

@Composable
private fun Bandeau(texte: String, onFermer: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(texte, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(vertical = 10.dp))
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = onFermer) { Icon(Icons.Rounded.Close, contentDescription = "Fermer") }
        }
    }
}

/** Carte osmdroid réglée pour Cesson : tuiles du Pi, emprise et zooms bornés, attribution OpenStreetMap. */
private fun creerCarte(context: Context): MapView {
    Configuration.getInstance().apply {
        userAgentValue = "CessonApp/${BuildConfig.VERSION_NAME}"
        osmdroidBasePath = File(context.cacheDir, "osmdroid")
        osmdroidTileCache = File(context.cacheDir, "osmdroid/tuiles")
    }
    val source = XYTileSource("CessonPi", 12, 18, 256, ".png", arrayOf("${BuildConfig.DONNEES_URL}tuiles/"),
        "© les contributeurs d'OpenStreetMap")
    return MapView(context).apply {
        setTileSource(source)
        setMultiTouchControls(true)
        zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        // Tuiles du Pi : zooms 12 à 18. Au-delà de 18, osmdroid agrandit les tuiles du 18 : rien de plus à télécharger.
        minZoomLevel = 12.0
        maxZoomLevel = 20.0
        setScrollableAreaLimitDouble(EMPRISE)
        isVerticalMapRepetitionEnabled = false
        isHorizontalMapRepetitionEnabled = false
        controller.setZoom(14.0)
        controller.setCenter(MAIRIE)
        overlays.add(CopyrightOverlay(context))
    }
}

/** Pastille ronde de couleur, bordée de blanc, pour une épingle (ou la position, avec un halo). */
private fun pastille(context: Context, couleur: Color, rayonDp: Int, halo: Boolean = false): GradientDrawable {
    val d = context.resources.displayMetrics.density
    return GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(couleur.toArgb())
        setStroke(((if (halo) 4 else 2) * d).toInt(), if (halo) Color(0x551A73E8).toArgb() else android.graphics.Color.WHITE)
        setSize((2 * rayonDp * d).toInt(), (2 * rayonDp * d).toInt())
    }
}
