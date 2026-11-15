package com.yogistudio.cesson.ui

import java.time.Instant
import com.yogistudio.cesson.transport.departAViser
import com.yogistudio.cesson.transport.Direction
import com.yogistudio.cesson.transport.DELAI_ALERTE_DEFAUT
import com.yogistudio.cesson.transport.AlerteBus
import com.yogistudio.cesson.rappels.RappelsCollecte
import com.yogistudio.cesson.rappels.AlertesBus
import com.yogistudio.cesson.core.ui.formaterHeure
import com.yogistudio.cesson.core.modele.JsonCesson
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.os.Build
import android.Manifest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.Pool
import androidx.compose.material.icons.rounded.Recycling
import androidx.compose.material.icons.rounded.Train
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material.icons.rounded.Water
import androidx.compose.material.icons.rounded.WbCloudy
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yogistudio.cesson.CessonApp
import com.yogistudio.cesson.core.donnees.EtatModule
import com.yogistudio.cesson.core.preferences.ACCUEIL
import com.yogistudio.cesson.core.ui.CarteDonnee
import com.yogistudio.cesson.core.ui.TexteBientot
import com.yogistudio.cesson.eau.BandeauCrue
import com.yogistudio.cesson.eau.CarteVilaine
import com.yogistudio.cesson.eau.enAlerte
import com.yogistudio.cesson.eau.vigicrues
import com.yogistudio.cesson.eau.vilaine
import com.yogistudio.cesson.environnement.CarteMeteo
import com.yogistudio.cesson.environnement.meteo
import com.yogistudio.cesson.equipements.CarteEquipements
import com.yogistudio.cesson.equipements.DECHETERIE
import com.yogistudio.cesson.equipements.Equipement
import com.yogistudio.cesson.equipements.equipements
import com.yogistudio.cesson.pratique.CarteCarburants
import com.yogistudio.cesson.pratique.CarteCollecte
import com.yogistudio.cesson.pratique.carburants
import com.yogistudio.cesson.pratique.collecte
import com.yogistudio.cesson.rappels.ReglageRappelCollecte
import com.yogistudio.cesson.transport.CarteDeparts
import com.yogistudio.cesson.transport.CarteMonArret
import com.yogistudio.cesson.transport.CarteTrains
import com.yogistudio.cesson.transport.lignesStar
import com.yogistudio.cesson.transport.trains
import kotlinx.coroutines.launch

// --- Cartes, partagées entre les onglets -----------------------------------------------------

private val METEO = Tuile("meteo", "Météo", Icons.Rounded.WbCloudy) { app ->
    val etat by remember(app) { app.depot.meteo() }.collectAsStateWithLifecycle(EtatModule.Absent)
    CarteMeteo(etat)
}
private val VILAINE = Tuile("vilaine", "La Vilaine et les crues", Icons.Rounded.Water) { app ->
    val etat by remember(app) { app.depot.vilaine() }.collectAsStateWithLifecycle(EtatModule.Absent)
    val vigilance by remember(app) { app.depot.vigicrues() }.collectAsStateWithLifecycle(EtatModule.Absent)
    CarteVilaine(etat, vigilance)
}
private val VIASILVA = Tuile("arrets", "Métro et bus ViaSilva", Icons.Rounded.DirectionsBus) { DepartsViaSilva(it) }
private val MON_ARRET = Tuile("mon_arret", "Mon arrêt de bus", Icons.Rounded.DirectionsBus) { MonArret(it) }
private val TRAINS = Tuile("trains", "Trains à Halte-Cesson", Icons.Rounded.Train) { TrainsHalte(it) }
private val COLLECTE = Tuile("collecte", "Ma collecte de déchets", Icons.Rounded.DeleteOutline) { CollecteCesson(it) }
private val MES_EQUIPEMENTS = Tuile("equipements", "Mes équipements", Icons.Rounded.Pool) { MesEquipements(it) }
private val CARBURANTS = Tuile("carburants", "Prix des carburants", Icons.Rounded.LocalGasStation) { CarburantsCesson(it) }
private val AIR = Tuile("air", "Qualité de l'air", Icons.Rounded.Air) {
    CarteBientot("Qualité de l'air", Icons.Rounded.Air, "Indice ATMO du jour, bientôt ici.")
}
private val EQUIPEMENTS_LOISIRS = Tuile("equipements", "Équipements", Icons.Rounded.Pool) { app ->
    EquipementsCesson(app, titre = "Équipements") { it.lieu.id != DECHETERIE }
}
private val DECHETERIE_TUILE = Tuile("decheterie", "Déchèterie", Icons.Rounded.Recycling) { app ->
    EquipementsCesson(app, titre = "Déchèterie") { it.lieu.id == DECHETERIE }
}

val ONGLET_ACCUEIL = OngletTuiles(ACCUEIL, "l'accueil",
    listOf(METEO, VILAINE, VIASILVA, MON_ARRET, TRAINS, COLLECTE, MES_EQUIPEMENTS, CARBURANTS, AIR))
val ONGLET_BOUGER = OngletTuiles("bouger", "l'onglet Bouger", listOf(VIASILVA, TRAINS, MON_ARRET, CARBURANTS))
val ONGLET_SORTIR = OngletTuiles("sortir", "l'onglet Sortir", listOf(EQUIPEMENTS_LOISIRS))
val ONGLET_PRATIQUE = OngletTuiles("pratique", "l'onglet Pratique", listOf(COLLECTE, DECHETERIE_TUILE))

val ONGLETS_PERSONNALISABLES = listOf(ONGLET_ACCUEIL, ONGLET_BOUGER, ONGLET_SORTIR, ONGLET_PRATIQUE)

// --- Branchement des cartes sur les données et les préférences --------------------------------

/** Carte des prochains départs, avec les couleurs des lignes publiées par notre backend. */
@Composable
fun DepartsViaSilva(app: CessonApp) {
    val lignes by remember(app) { app.depot.lignesStar() }.collectAsStateWithLifecycle(EtatModule.Absent)
    CarteDeparts(app.star, (lignes as? EtatModule.Disponible)?.fichier?.donnees)
}

@Composable
fun EquipementsCesson(app: CessonApp, titre: String = "Mes équipements", garder: (Equipement) -> Boolean = { true }) {
    val etat by remember(app) { app.depot.equipements() }.collectAsStateWithLifecycle(EtatModule.Absent)
    CarteEquipements(etat, titre = titre, garder = garder)
}

/** « Mes équipements » de l'accueil : sans ceux que l'utilisateur a retirés (guide de démarrage). */
@Composable
fun MesEquipements(app: CessonApp) {
    val masques by app.preferences.equipementsMasques.collectAsStateWithLifecycle(emptySet())
    EquipementsCesson(app) { it.lieu.id !in masques }
}

@Composable
fun CollecteCesson(app: CessonApp) {
    val etat by remember(app) { app.depot.collecte() }.collectAsStateWithLifecycle(EtatModule.Absent)
    CarteCollecte(etat, reglageRappel = { ReglageRappelCollecte(app) })
}

@Composable
fun CarburantsCesson(app: CessonApp) {
    val etat by remember(app) { app.depot.carburants() }.collectAsStateWithLifecycle(EtatModule.Absent)
    val suivi by app.preferences.carburantSuivi.collectAsStateWithLifecycle(null)
    val portee = rememberCoroutineScope()
    CarteCarburants(etat, suivi, onChoisir = { portee.launch { app.preferences.enregistrerCarburant(it) } })
}

/** Trains à la halte : données du backend, relues toutes les 2 min tant que la carte est visible. */
@Composable
fun TrainsHalte(app: CessonApp) {
    val etat by remember(app) { app.depot.trains() }.collectAsStateWithLifecycle(EtatModule.Absent)
    CarteTrains(etat, rafraichir = { app.depot.rafraichir() })
}

/**
 * Carte « Mon arrêt » : l'arrêt choisi et l'alerte de passage restent dans les préférences du téléphone.
 * Armer une alerte demande la permission de notification (Android 13+) la première fois.
 */
@Composable
fun MonArret(app: CessonApp) {
    val context = LocalContext.current
    val lignes by remember(app) { app.depot.lignesStar() }.collectAsStateWithLifecycle(EtatModule.Absent)
    val arret by app.preferences.arretSuivi.collectAsStateWithLifecycle(null)
    val alerteJson by app.preferences.alerteBus.collectAsStateWithLifecycle(null)
    val alerte = remember(alerteJson) { alerteJson?.let { runCatching { JsonCesson.decodeFromString(AlerteBus.serializer(), it) }.getOrNull() } }
    val delai by app.preferences.delaiAlerteBus.collectAsStateWithLifecycle(DELAI_ALERTE_DEFAUT)
    val portee = rememberCoroutineScope()
    val messages = LocalMessages.current
    var enAttente by remember { mutableStateOf<Direction?>(null) }

    fun armer(d: Direction) {
        val a = arret ?: return
        val bus = departAViser(d.departs, d.ligne, d.destination, delai, Instant.now())
        portee.launch {
            if (bus == null) {
                messages?.showSnackbar("Aucun ${d.ligne} vers ${d.destination} à plus de $delai min pour l'instant.")
                return@launch
            }
            AlertesBus.armer(context, AlerteBus(a, d.ligne, d.destination, bus.heure.epochSecond, delai))
            messages?.showSnackbar("Alerte armée : $delai min avant le ${d.ligne} de ${formaterHeure(bus.heure, Instant.now())}.")
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { accordee ->
        val d = enAttente
        enAttente = null
        if (accordee && d != null) armer(d)
        else if (!accordee) portee.launch { messages?.showSnackbar("Notifications refusées : l'alerte ne peut pas vous prévenir.") }
    }
    CarteMonArret(
        app.star, (lignes as? EtatModule.Disponible)?.fichier?.donnees, arret,
        onChoisir = { nouveau ->
            portee.launch {
                app.preferences.enregistrerArret(nouveau)
                if (alerte != null && alerte.arret != nouveau) AlertesBus.annuler(context) // l'alerte visait l'ancien arrêt
            }
        },
        alerte = alerte,
        delaiAlerte = delai,
        onAlerter = { d ->
            when {
                alerte?.ligne == d.ligne && alerte.destination == d.destination -> portee.launch { AlertesBus.annuler(context) }
                RappelsCollecte.notificationsAutorisees(context) -> armer(d)
                Build.VERSION.SDK_INT >= 33 -> { enAttente = d; permission.launch(Manifest.permission.POST_NOTIFICATIONS) }
            }
        },
        onAnnulerAlerte = { portee.launch { AlertesBus.annuler(context) } },
        onDelaiAlerte = { portee.launch { app.preferences.enregistrerDelaiAlerteBus(it) } },
    )
}

@Composable
private fun CarteBientot(titre: String, icone: ImageVector, texte: String) =
    CarteDonnee(titre, icone) { TexteBientot(texte) }

/** Liste « bientôt dans cet onglet », en bas des onglets dont les modules arrivent plus tard. */
@Composable
fun CarteAVenir(icone: ImageVector, lignes: List<String>) {
    CarteDonnee("Bientôt dans cet onglet", icone) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            lignes.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

// --- Écrans des onglets ----------------------------------------------------------------------

@Composable
fun EcranAujourdhui(app: CessonApp, onPersonnaliser: () -> Unit) {
    val vigilance by remember(app) { app.depot.vigicrues() }.collectAsStateWithLifecycle(EtatModule.Absent)
    EcranTuiles(app, ONGLET_ACCUEIL, onPersonnaliser, entete = {
        // Vigilance crues jaune ou plus : toujours en tête, hors des cartes personnalisables.
        if (vigilance.enAlerte()) item("crue") { BandeauCrue(vigilance) }
    })
}

@Composable
fun EcranBouger(app: CessonApp, onPersonnaliser: () -> Unit) =
    EcranTuiles(app, ONGLET_BOUGER, onPersonnaliser, pied = {
        item("a-venir") { CarteAVenir(Icons.Rounded.DirectionsBus, listOf("Vélos en libre-service et parcs-relais", "Bornes de recharge")) }
    })

@Composable
fun EcranSortir(app: CessonApp, onPersonnaliser: () -> Unit) =
    EcranTuiles(app, ONGLET_SORTIR, onPersonnaliser, pied = {
        item("a-venir") {
            CarteAVenir(Icons.Rounded.Celebration, listOf("Spectacles du Carré Sévigné", "Cinéma Le Sévigné",
                "Programme de la médiathèque", "Pont des Arts et Galerie Pictura"))
        }
    })

@Composable
fun EcranPratique(app: CessonApp, onPersonnaliser: () -> Unit) =
    EcranTuiles(app, ONGLET_PRATIQUE, onPersonnaliser, pied = {
        item("a-venir") {
            CarteAVenir(Icons.Rounded.VolunteerActivism, listOf("Demandes d'urbanisme", "Défibrillateurs",
                "Accessibilité des lieux publics", "Bornes de recharge"))
        }
    })

const val PROMESSE = "Aucun compte, aucune publicité, aucune donnée collectée."
