package com.yogistudio.cesson.ui

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.material3.Switch
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import com.yogistudio.cesson.actus.cinema
import com.yogistudio.cesson.actus.CarteCinema
import com.yogistudio.cesson.transport.CarteParcRelais
import androidx.compose.material.icons.rounded.LocalParking
import com.yogistudio.cesson.actus.CarteSaison
import com.yogistudio.cesson.pratique.CartePharmacieGarde
import com.yogistudio.cesson.pratique.pharmacies
import com.yogistudio.cesson.pratique.CarteUrgences
import com.yogistudio.cesson.pratique.CarteAccessibilite
import com.yogistudio.cesson.pratique.InfosDecheterie
import com.yogistudio.cesson.pratique.decheterie
import com.yogistudio.cesson.pratique.accessibilite
import androidx.compose.material.icons.rounded.Accessible
import androidx.compose.material.icons.rounded.LocalPharmacy
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.TheaterComedy
import com.yogistudio.cesson.familles.CarteVacances
import com.yogistudio.cesson.familles.CartePortailFamilles
import com.yogistudio.cesson.familles.vacancesScolaires
import com.yogistudio.cesson.familles.familles
import com.yogistudio.cesson.familles.CarteMenus
import com.yogistudio.cesson.familles.CarteActusFamilles
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.BeachAccess
import androidx.compose.material.icons.rounded.FamilyRestroom
import androidx.compose.material.icons.rounded.Movie
import com.yogistudio.cesson.equipements.SERVICES_A_PART
import com.yogistudio.cesson.actus.agenda
import com.yogistudio.cesson.actus.CarteAgenda
import androidx.compose.material.icons.rounded.Event
import com.yogistudio.cesson.pratique.defibrillateurs
import com.yogistudio.cesson.pratique.bornes
import com.yogistudio.cesson.pratique.CarteDefibrillateurs
import com.yogistudio.cesson.pratique.CarteBornes
import com.yogistudio.cesson.position.rememberDemandePosition
import com.yogistudio.cesson.position.Position
import androidx.compose.material.icons.rounded.ElectricCar
import androidx.compose.material.icons.rounded.Favorite
import com.yogistudio.cesson.transport.circulation
import com.yogistudio.cesson.transport.CarteCirculation
import androidx.compose.material.icons.rounded.DirectionsCar
import com.yogistudio.cesson.eau.usagesEau
import com.yogistudio.cesson.eau.CarteUsagesEau
import com.yogistudio.cesson.actus.actus
import com.yogistudio.cesson.actus.FLUX_VILLE
import com.yogistudio.cesson.actus.CarteActus
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Newspaper
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
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.Pool
import androidx.compose.material.icons.rounded.Recycling
import androidx.compose.material.icons.rounded.Train
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
import com.yogistudio.cesson.environnement.BandeauPollution
import com.yogistudio.cesson.environnement.BandeauVigilanceMeteo
import com.yogistudio.cesson.environnement.enVigilance
import com.yogistudio.cesson.environnement.vigilanceMeteo
import com.yogistudio.cesson.rappels.ReglageAlerteVigilance
import com.yogistudio.cesson.environnement.CarteEpisodes
import com.yogistudio.cesson.environnement.CartePollens
import com.yogistudio.cesson.environnement.CarteQualiteAir
import com.yogistudio.cesson.environnement.episodesPollution
import com.yogistudio.cesson.environnement.pollens
import com.yogistudio.cesson.environnement.qualiteAir
import com.yogistudio.cesson.environnement.enAlerte as episodeEnAlerte
import com.yogistudio.cesson.core.ui.FuseauCesson
import com.yogistudio.cesson.core.ui.rememberMaintenant
import androidx.compose.material.icons.rounded.LocalFlorist
import androidx.compose.material.icons.rounded.Masks
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
import com.yogistudio.cesson.rappels.ReglageAlerteEpisodes
import com.yogistudio.cesson.rappels.ReglageAlertePollens
import com.yogistudio.cesson.transport.CarteDeparts
import com.yogistudio.cesson.transport.CarteMonArret
import com.yogistudio.cesson.transport.CarteTrains
import com.yogistudio.cesson.transport.trainsRetour
import com.yogistudio.cesson.transport.lignesStar
import com.yogistudio.cesson.transport.trains
import kotlinx.coroutines.launch

// --- Cartes, partagées entre les onglets -----------------------------------------------------

private val METEO = Tuile("meteo", "Météo", Icons.Rounded.WbCloudy) { app ->
    val etat by remember(app) { app.depot.meteo() }.collectAsStateWithLifecycle(EtatModule.Absent)
    val vigilance by remember(app) { app.depot.vigilanceMeteo() }.collectAsStateWithLifecycle(EtatModule.Absent)
    CarteMeteo(etat, vigilance = vigilance, reglageAlerte = { ReglageAlerteVigilance(app) })
}
private val VILAINE = Tuile("vilaine", "La Vilaine et les crues", Icons.Rounded.Water) { app ->
    val etat by remember(app) { app.depot.vilaine() }.collectAsStateWithLifecycle(EtatModule.Absent)
    val vigilance by remember(app) { app.depot.vigicrues() }.collectAsStateWithLifecycle(EtatModule.Absent)
    CarteVilaine(etat, vigilance)
}
private val VIASILVA = Tuile("arrets", "Métro et bus ViaSilva", Icons.Rounded.DirectionsBus) { DepartsViaSilva(it) }
private val PARC_RELAIS = Tuile("parc_relais", "Parc relais ViaSilva", Icons.Rounded.LocalParking) { CarteParcRelais(it.star) }
private val MON_ARRET = Tuile("mon_arret", "Mon trajet réseau Star", Icons.Rounded.DirectionsBus) { MonArret(it) }
private val TRAINS = Tuile("trains", "Mon trajet en train", Icons.Rounded.Train) { TrainsHalte(it) }
private val COLLECTE = Tuile("collecte", "Ma collecte de déchets", Icons.Rounded.DeleteOutline) { CollecteCesson(it) }
private val CARBURANTS = Tuile("carburants", "Prix des carburants", Icons.Rounded.LocalGasStation) { CarburantsCesson(it) }
private val CIRCULATION = Tuile("circulation", "Circulation", Icons.Rounded.DirectionsCar) { app ->
    val etat by remember(app) { app.depot.circulation() }.collectAsStateWithLifecycle(EtatModule.Absent)
    CarteCirculation(etat)
}
private val AIR = Tuile("air", "Qualité de l'air", Icons.Rounded.Air) { app ->
    val etat by remember(app) { app.depot.qualiteAir() }.collectAsStateWithLifecycle(EtatModule.Absent)
    CarteQualiteAir(etat)
}
private val POLLENS = Tuile("pollens", "Indice pollen", Icons.Rounded.LocalFlorist) { app ->
    val etat by remember(app) { app.depot.pollens() }.collectAsStateWithLifecycle(EtatModule.Absent)
    CartePollens(etat, reglageAlerte = { ReglageAlertePollens(app) })
}
private val EPISODES = Tuile("episodes_pollution", "Épisodes de pollution", Icons.Rounded.Masks) { app ->
    val etat by remember(app) { app.depot.episodesPollution() }.collectAsStateWithLifecycle(EtatModule.Absent)
    CarteEpisodes(etat, reglageAlerte = { ReglageAlerteEpisodes(app) })
}
private val EQUIPEMENTS_LOISIRS = Tuile("equipements", "Équipements", Icons.Rounded.Pool) { MesEquipements(it) }
private val DECHETERIE_TUILE = Tuile("decheterie", "Déchetterie", Icons.Rounded.Recycling) { app ->
    val dechets by remember(app) { app.depot.decheterie() }.collectAsStateWithLifecycle(EtatModule.Absent)
    EquipementsCesson(app, titre = "Déchetterie", icone = Icons.Rounded.Recycling, garder = { it.lieu.id == DECHETERIE },
        pied = (dechets as? EtatModule.Disponible)?.let { d -> { InfosDecheterie(d.fichier.donnees) } })
}

private val DEFIBRILLATEURS = Tuile("defibrillateurs", "Défibrillateurs", Icons.Rounded.Favorite) { app ->
    val etat by remember(app) { app.depot.defibrillateurs() }.collectAsStateWithLifecycle(EtatModule.Absent)
    val position by Position.actuelle.collectAsStateWithLifecycle()
    val demande = rememberDemandePosition()
    CarteDefibrillateurs(etat, position, demande.enCours, demande.demander)
}
private val BORNES = Tuile("bornes", "Bornes de recharge", Icons.Rounded.ElectricCar) { app ->
    val etat by remember(app) { app.depot.bornes() }.collectAsStateWithLifecycle(EtatModule.Absent)
    val position by Position.actuelle.collectAsStateWithLifecycle()
    val demande = rememberDemandePosition()
    CarteBornes(etat, position, demande.enCours, demande.demander)
}
private val ACTUS_VILLE = Tuile("actus_ville", "Actualités de la ville", Icons.Rounded.Newspaper) { ActusCesson(it, FLUX_VILLE) }
// Les actualités de la ville sont à l'accueil : c'est ce qui se passe aujourd'hui à Cesson. Les épisodes de pollution
// et la vigilance crues y restent par leurs bandeaux ; leurs cartes sont dans l'onglet Environnement.
val ONGLET_ACCUEIL = OngletTuiles(ACCUEIL, "l'accueil",
    listOf(METEO, MON_ARRET, TRAINS, CIRCULATION, AIR, ACTUS_VILLE))
val ONGLET_BOUGER = OngletTuiles("bouger", "l'onglet Bouger", listOf(VIASILVA, PARC_RELAIS, TRAINS, MON_ARRET, CIRCULATION, CARBURANTS, BORNES))
private val AGENDA = Tuile("agenda", "Agenda", Icons.Rounded.Event) { app ->
    val etat by remember(app) { app.depot.agenda() }.collectAsStateWithLifecycle(EtatModule.Absent)
    CarteAgenda(etat)
}
private val CINEMA = Tuile("cinema", "Cinéma Le Sévigné", Icons.Rounded.Movie) { app ->
    val etat by remember(app) { app.depot.cinema() }.collectAsStateWithLifecycle(EtatModule.Absent)
    CarteCinema(etat, affiche = { app.depot.image(it) })
}
private val SAISON = Tuile("saison", "Saison culturelle", Icons.Rounded.TheaterComedy) { app ->
    val etat by remember(app) { app.depot.agenda() }.collectAsStateWithLifecycle(EtatModule.Absent)
    val favoris by app.preferences.spectaclesFavoris.collectAsStateWithLifecycle(emptySet())
    val context = androidx.compose.ui.platform.LocalContext.current
    val portee = androidx.compose.runtime.rememberCoroutineScope()
    CarteSaison(etat, image = { app.depot.image(it) }, favoris = favoris,
        basculerFavori = { id -> portee.launch { com.yogistudio.cesson.rappels.RappelsSpectacles.basculer(context, id) } })
}
val ONGLET_SORTIR = OngletTuiles("sortir", "l'onglet Sortir", listOf(SAISON, AGENDA, CINEMA, EQUIPEMENTS_LOISIRS))
private val USAGES_EAU = Tuile("usages_eau", "Usage de l'eau", Icons.Rounded.WaterDrop) { app ->
    val etat by remember(app) { app.depot.usagesEau() }.collectAsStateWithLifecycle(EtatModule.Absent)
    CarteUsagesEau(etat)
}
private val DEMARCHES = Tuile("demarches", "Démarches", Icons.Rounded.AccountBalance) { CarteDemarches(it) }
private val PHARMACIE_GARDE = Tuile("pharmacie_garde", "Pharmacies", Icons.Rounded.LocalPharmacy) { app ->
    val etat by remember(app) { app.depot.pharmacies() }.collectAsStateWithLifecycle(EtatModule.Absent)
    CartePharmacieGarde(etat)
}
private val URGENCES = Tuile("urgences", "Urgences", Icons.Rounded.LocalHospital) { CarteUrgences() }
private val ACCESSIBILITE = Tuile("accessibilite", "Accessibilité des lieux", Icons.Rounded.Accessible) { app ->
    val etat by remember(app) { app.depot.accessibilite() }.collectAsStateWithLifecycle(EtatModule.Absent)
    val position by Position.actuelle.collectAsStateWithLifecycle()
    val demande = rememberDemandePosition()
    CarteAccessibilite(etat, position, demande.enCours, demande.demander)
}
val ONGLET_PRATIQUE = OngletTuiles("pratique", "l'onglet Pratique",
    listOf(DEMARCHES, PHARMACIE_GARDE, URGENCES, DEFIBRILLATEURS, ACCESSIBILITE))
// Le cadre de vie : air, pollens, pollution, Vilaine, eau et déchets. Après les cartes qu'il réunit (ordre d'initialisation).
val ONGLET_ENVIRONNEMENT = OngletTuiles("environnement", "l'onglet Environnement",
    listOf(AIR, POLLENS, EPISODES, VILAINE, USAGES_EAU, COLLECTE, DECHETERIE_TUILE))
private val VACANCES = Tuile("vacances", "Vacances scolaires", Icons.Rounded.BeachAccess) { app ->
    val etat by remember(app) { app.depot.vacancesScolaires() }.collectAsStateWithLifecycle(EtatModule.Absent)
    CarteVacances(etat)
}
private val PORTAIL_FAMILLES = Tuile("portail_familles", "Portail familles", Icons.Rounded.FamilyRestroom) { CartePortailFamilles() }
private val MENUS = Tuile("menus", "Menus de la cantine", Icons.Rounded.Restaurant) { app ->
    val etat by remember(app) { app.depot.familles() }.collectAsStateWithLifecycle(EtatModule.Absent)
    CarteMenus(etat)
}
private val ACTUS_FAMILLES = Tuile("actus_familles", "Infos familles", Icons.Rounded.Campaign) { app ->
    val etat by remember(app) { app.depot.familles() }.collectAsStateWithLifecycle(EtatModule.Absent)
    CarteActusFamilles(etat)
}
val ONGLET_FAMILLES = OngletTuiles("familles", "l'onglet Familles", listOf(MENUS, VACANCES, ACTUS_FAMILLES, PORTAIL_FAMILLES))

val ONGLETS_PERSONNALISABLES = listOf(ONGLET_ACCUEIL, ONGLET_BOUGER, ONGLET_SORTIR, ONGLET_FAMILLES, ONGLET_ENVIRONNEMENT, ONGLET_PRATIQUE)

@Composable
fun ActusCesson(app: CessonApp, flux: String) {
    val etat by remember(app) { app.depot.actus() }.collectAsStateWithLifecycle(EtatModule.Absent)
    CarteActus(etat, flux)
}

// --- Branchement des cartes sur les données et les préférences --------------------------------

/** Carte des prochains départs, avec les couleurs des lignes publiées par notre backend. */
@Composable
fun DepartsViaSilva(app: CessonApp) {
    val lignes by remember(app) { app.depot.lignesStar() }.collectAsStateWithLifecycle(EtatModule.Absent)
    CarteDeparts(app.star, (lignes as? EtatModule.Disponible)?.fichier?.donnees)
}

@Composable
fun EquipementsCesson(
    app: CessonApp,
    titre: String = "Mes équipements",
    icone: ImageVector = Icons.Rounded.Pool,
    garder: (Equipement) -> Boolean = { true },
    pied: (@Composable () -> Unit)? = null,
) {
    val etat by remember(app) { app.depot.equipements() }.collectAsStateWithLifecycle(EtatModule.Absent)
    CarteEquipements(etat, titre = titre, icone = icone, garder = garder, pied = pied)
}

/**
 * Équipements de loisirs (onglet Sortir) : sans ceux que l'utilisateur a retirés, dans le guide de démarrage ou dans
 * Réglages → Mes équipements.
 */
@Composable
fun MesEquipements(app: CessonApp, titre: String = "Équipements") {
    val masques by app.preferences.equipementsMasques.collectAsStateWithLifecycle(emptySet())
    EquipementsCesson(app, titre = titre, garder = { it.lieu.id !in masques && it.lieu.id !in SERVICES_A_PART })
}

/** Choix des équipements affichés (guide de démarrage et Réglages) : un interrupteur par équipement. */
@Composable
fun ReglageEquipements(app: CessonApp) {
    val portee = rememberCoroutineScope()
    val equipements by remember(app) { app.depot.equipements() }.collectAsStateWithLifecycle(EtatModule.Absent)
    val liste = (equipements as? EtatModule.Disponible)?.fichier?.donnees?.equipements.orEmpty()
        .filter { it.lieu.id !in SERVICES_A_PART }
    val masques by app.preferences.equipementsMasques.collectAsStateWithLifecycle(emptySet())
    if (liste.isEmpty()) {
        Text("Liste des équipements pas encore reçue.", style = MaterialTheme.typography.bodyMedium)
        return
    }
    Column {
        liste.forEach { e ->
            val affiche = e.lieu.id !in masques
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().toggleable(value = affiche, role = Role.Switch) { garder ->
                    portee.launch { app.preferences.enregistrerEquipementsMasques(if (garder) masques - e.lieu.id else masques + e.lieu.id) }
                }.padding(vertical = 4.dp),
            ) {
                Text(e.lieu.nom, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(checked = affiche, onCheckedChange = null)
            }
        }
    }
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

/**
 * « Mon trajet en train » : l'aller depuis la halte de Cesson, le retour depuis la gare de destination choisie. Données
 * du backend, relues toutes les 2 min tant que la carte est visible ; la gare reste dans les préférences du téléphone.
 */
@Composable
fun TrainsHalte(app: CessonApp) {
    val etat by remember(app) { app.depot.trains() }.collectAsStateWithLifecycle(EtatModule.Absent)
    val retour by remember(app) { app.depot.trainsRetour() }.collectAsStateWithLifecycle(EtatModule.Absent)
    val favoris by app.preferences.trainsFavoris.collectAsStateWithLifecycle(null)
    val gare by app.preferences.gareDestination.collectAsStateWithLifecycle(null)
    val portee = rememberCoroutineScope()
    CarteTrains(etat, rafraichir = { app.depot.rafraichir() }, favoris = com.yogistudio.cesson.transport.lireTrainsFavoris(favoris),
        reglageFavoris = { com.yogistudio.cesson.rappels.ReglageTrainsFavoris(app) },
        retour = (retour as? EtatModule.Disponible)?.fichier?.donnees, gare = gare,
        onChoisirGare = { g -> portee.launch { app.preferences.enregistrerGareDestination(g) } })
}

/**
 * Carte « Mon trajet réseau Star » : l'arrêt choisi et l'alerte de passage restent dans les préférences du téléphone.
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
    val lignePreferee by app.preferences.lignePreferee.collectAsStateWithLifecycle(null)
    val destination by app.preferences.destinationSuivie.collectAsStateWithLifecycle(null)
    val portee = rememberCoroutineScope()
    val messages = LocalMessages.current
    var enAttente by remember { mutableStateOf<Direction?>(null) }

    fun armer(d: Direction) {
        // Au retour, l'alerte vise l'arrêt de destination d'où part le bus.
        val a = d.departs.firstOrNull()?.arret ?: arret ?: return
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
        lignePreferee = lignePreferee,
        onLignePreferee = { l -> portee.launch { app.preferences.enregistrerLignePreferee(l) } },
        destination = destination,
        onDestination = { d -> portee.launch { app.preferences.enregistrerDestination(d) } },
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
fun EcranFamilles(app: CessonApp, onPersonnaliser: () -> Unit) =
    EcranTuiles(app, ONGLET_FAMILLES, onPersonnaliser)

@Composable
fun EcranAujourdhui(app: CessonApp, onPersonnaliser: () -> Unit) {
    val vigilance by remember(app) { app.depot.vigicrues() }.collectAsStateWithLifecycle(EtatModule.Absent)
    val pollution by remember(app) { app.depot.episodesPollution() }.collectAsStateWithLifecycle(EtatModule.Absent)
    val meteo by remember(app) { app.depot.vigilanceMeteo() }.collectAsStateWithLifecycle(EtatModule.Absent)
    val maintenant = rememberMaintenant()
    val aujourdhui = maintenant.atZone(FuseauCesson).toLocalDate()
    val crue = vigilance.enAlerte()
    val vigilanceMeteo = meteo.enVigilance(maintenant)
    val episode = pollution.episodeEnAlerte(aujourdhui)
    // Vigilance crues ou météo jaune ou plus, procédure de pollution aujourd'hui ou demain : toujours en tête,
    // hors des cartes personnalisables.
    EcranTuiles(app, ONGLET_ACCUEIL, onPersonnaliser,
        entete = if (crue || vigilanceMeteo || episode) ({
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (crue) BandeauCrue(vigilance)
                if (vigilanceMeteo) BandeauVigilanceMeteo(meteo)
                if (episode) BandeauPollution(pollution)
            }
        }) else null)
}

@Composable
fun EcranBouger(app: CessonApp, onPersonnaliser: () -> Unit) =
    EcranTuiles(app, ONGLET_BOUGER, onPersonnaliser)

@Composable
fun EcranSortir(app: CessonApp, onPersonnaliser: () -> Unit) =
    EcranTuiles(app, ONGLET_SORTIR, onPersonnaliser, pied = {
        CarteAVenir(Icons.Rounded.Celebration, listOf("Programme de la médiathèque"))
    })

@Composable
fun EcranEnvironnement(app: CessonApp, onPersonnaliser: () -> Unit) =
    EcranTuiles(app, ONGLET_ENVIRONNEMENT, onPersonnaliser)

@Composable
fun EcranPratique(app: CessonApp, onPersonnaliser: () -> Unit) =
    EcranTuiles(app, ONGLET_PRATIQUE, onPersonnaliser)

const val PROMESSE = "Aucun compte, aucune publicité, aucune donnée collectée."
