package com.yogistudio.cesson.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import com.yogistudio.cesson.core.ui.Pastille
import com.yogistudio.cesson.core.ui.Ton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Train
import androidx.compose.ui.graphics.vector.ImageVector
import com.yogistudio.cesson.core.preferences.MODE_STAR
import com.yogistudio.cesson.core.preferences.MODE_TRAIN
import com.yogistudio.cesson.core.preferences.MODE_VOITURE
import com.yogistudio.cesson.core.preferences.MOTORISATION_ELECTRIQUE
import com.yogistudio.cesson.core.preferences.MOTORISATION_HYBRIDE
import com.yogistudio.cesson.core.preferences.MOTORISATION_THERMIQUE
import com.yogistudio.cesson.core.preferences.configSelonDeplacements
import com.yogistudio.cesson.core.preferences.tuilesUtiles
import com.yogistudio.cesson.rappels.ReglageTrainsFavoris
import kotlinx.coroutines.flow.first
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yogistudio.cesson.CessonApp
import com.yogistudio.cesson.core.donnees.EtatModule
import com.yogistudio.cesson.core.preferences.ACCUEIL
import com.yogistudio.cesson.core.preferences.ConfigAccueil
import com.yogistudio.cesson.core.preferences.ordonnerTuiles
import com.yogistudio.cesson.pratique.CARBURANT_PAR_DEFAUT
import com.yogistudio.cesson.pratique.carburants
import com.yogistudio.cesson.pratique.carburantsProposes
import com.yogistudio.cesson.rappels.ReglageRappelCollecte
import com.yogistudio.cesson.transport.ChoixArret
import com.yogistudio.cesson.transport.ChoixDestination
import com.yogistudio.cesson.transport.lignesStar
import kotlinx.coroutines.launch

private const val NB_ETAPES = 6

/**
 * Guide de démarrage, au premier lancement puis depuis le menu : bienvenue, déplacements (voiture, bus et métro,
 * train, avec leurs réglages), cartes compactes ou détaillées, cartes de l'accueil, réglages personnels. Chaque choix est enregistré aussitôt (sur le téléphone) : « Passer » à
 * n'importe quelle étape garde ce qui a déjà été choisi.
 */
@Composable
fun EcranGuide(app: CessonApp, onTerminer: () -> Unit) {
    var etape by rememberSaveable { mutableIntStateOf(0) }
    val portee = rememberCoroutineScope()
    fun terminer() = portee.launch { app.preferences.marquerGuideVu(); onTerminer() }

    Column(Modifier.fillMaxSize()) {
        LinearProgressIndicator(progress = { (etape + 1f) / NB_ETAPES }, modifier = Modifier.fillMaxWidth())
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Étape ${etape + 1} sur $NB_ETAPES", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            when (etape) {
                0 -> EtapeBienvenue()
                1 -> EtapeDeplacements(app)
                2 -> EtapeCartes(app)
                3 -> EtapeAccueil(app)
                4 -> EtapeReglages(app)
                else -> EtapeFin()
            }
        }
        HorizontalDivider()
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (etape < NB_ETAPES - 1) TextButton(onClick = { terminer() }) { Text("Passer") }
            Spacer(Modifier.weight(1f))
            if (etape > 0) {
                OutlinedButton(onClick = { etape-- }) { Text("Retour") }
                Spacer(Modifier.width(8.dp))
            }
            if (etape < NB_ETAPES - 1) Button(onClick = {
                // En quittant « Vos déplacements » : l'accueil et l'onglet Bouger s'adaptent aux choix.
                if (etape == 1) portee.launch { appliquerDeplacements(app) }
                etape++
            }) { Text("Suivant") }
            else Button(onClick = { terminer() }) { Text("C'est parti") }
        }
    }
}

@Composable
private fun TitreEtape(texte: String) =
    Text(texte, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })

@Composable
private fun EtapeBienvenue() {
    TitreEtape("Bienvenue dans Cesson'App")
    Text("Cette application non officielle rassemble l'information pratique de Cesson-Sévigné à partir de " +
        "données publiques : transports, collecte des déchets, équipements, Vilaine, météo…",
        style = MaterialTheme.typography.bodyLarge)
    Text("Elle n'est éditée ni par la Ville ni par Rennes Métropole.", style = MaterialTheme.typography.bodyLarge)
    Text(PROMESSE, style = MaterialTheme.typography.titleMedium)
    Text("En deux minutes, choisissez ce que vous voulez voir. Tout se change ensuite à tout moment.",
        style = MaterialTheme.typography.bodyLarge)
}

/**
 * Vos déplacements : voiture, bus et métro, train (plusieurs choix), et pour chacun ses réglages (motorisation et
 * carburant, mon arrêt, mes trains). En quittant l'étape, l'accueil et l'onglet Bouger s'adaptent (appliquerDeplacements).
 */
@Composable
private fun EtapeDeplacements(app: CessonApp) {
    val portee = rememberCoroutineScope()
    val modes by app.preferences.deplacements.collectAsStateWithLifecycle(emptySet())
    val motorisation by app.preferences.motorisation.collectAsStateWithLifecycle(null)
    fun basculer(mode: String) = portee.launch {
        app.preferences.enregistrerDeplacements(if (mode in modes) modes - mode else modes + mode)
    }
    TitreEtape("Vos déplacements")
    Text("Comment vous déplacez-vous ? Plusieurs choix possibles : l'accueil et l'onglet Bouger s'adaptent.",
        style = MaterialTheme.typography.bodyLarge)

    ChoixDeplacement("En voiture", "Circulation, prix des carburants ou bornes de recharge, parc relais.",
        Icons.Rounded.DirectionsCar, MODE_VOITURE in modes) { basculer(MODE_VOITURE) }
    if (MODE_VOITURE in modes) Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SousTitre("Votre voiture")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(MOTORISATION_THERMIQUE to "Essence ou diesel", MOTORISATION_HYBRIDE to "Hybride", MOTORISATION_ELECTRIQUE to "Électrique")
                .forEach { (code, nom) ->
                    FilterChip(selected = motorisation == code,
                        onClick = { portee.launch { app.preferences.enregistrerMotorisation(code) } }, label = { Text(nom) })
                }
        }
        if (motorisation != MOTORISATION_ELECTRIQUE) MonCarburant(app)
    }

    ChoixDeplacement("En bus ou en métro", "Métro b et bus à ViaSilva, prochains passages à votre arrêt.",
        Icons.Rounded.DirectionsBus, MODE_STAR in modes) { basculer(MODE_STAR) }
    if (MODE_STAR in modes) Column(Modifier.padding(start = 12.dp)) { MonArretGuide(app) }

    ChoixDeplacement("En train", "Trains à la halte de Cesson, retards et suppressions en temps réel.",
        Icons.Rounded.Train, MODE_TRAIN in modes) { basculer(MODE_TRAIN) }
    if (MODE_TRAIN in modes) Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SousTitre("Mon trajet en train")
        Text("Au retour, la carte montre les trains de votre gare de destination vers Rennes ou Cesson uniquement.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        com.yogistudio.cesson.rappels.ReglageGareDestination(app)
        SousTitre("Mon train habituel")
        ReglageTrainsFavoris(app)
    }
}

/** Accueil : cartes de transport utiles affichées, les autres masquées ; Bouger : cartes utiles en tête. */
private suspend fun appliquerDeplacements(app: CessonApp) {
    val modes = app.preferences.deplacements.first()
    if (modes.isEmpty()) return // rien de choisi : on ne touche à rien
    val utiles = tuilesUtiles(modes, app.preferences.motorisation.first())
    listOf(ONGLET_ACCUEIL to true, ONGLET_BOUGER to false).forEach { (onglet, masquer) ->
        val actuelle = app.preferences.configOnglet(onglet.id).first()
        app.preferences.enregistrerOnglet(onglet.id, configSelonDeplacements(onglet.tuiles.map { it.id }, actuelle, utiles, masquer))
    }
}

@Composable
private fun ChoixDeplacement(titre: String, explication: String, icone: ImageVector, choisi: Boolean, onBasculer: () -> Unit) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth().toggleable(value = choisi, role = Role.Checkbox, onValueChange = { onBasculer() }),
        border = BorderStroke(if (choisi) 2.dp else 1.dp,
            if (choisi) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = choisi, onCheckedChange = null)
            Spacer(Modifier.width(8.dp))
            Icon(icone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(titre, style = MaterialTheme.typography.titleMedium)
                Text(explication, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun MonArretGuide(app: CessonApp) {
    val portee = rememberCoroutineScope()
    val lignes by remember { app.depot.lignesStar() }.collectAsStateWithLifecycle(EtatModule.Absent)
    val donneesLignes = (lignes as? EtatModule.Disponible)?.fichier?.donnees
    val arret by app.preferences.arretSuivi.collectAsStateWithLifecycle(null)
    val destination by app.preferences.destinationSuivie.collectAsStateWithLifecycle(null)
    var choixArret by rememberSaveable { mutableStateOf(false) }
    var choixDestination by rememberSaveable { mutableStateOf(false) }
    SousTitre("Mon trajet réseau Star")
    Text("Votre trajet habituel : la carte montre les bus et métros en approche de votre arrêt à Cesson à " +
        "l'aller, et de votre destination au retour.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Mon arrêt, à Cesson", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(arret ?: "Aucun arrêt choisi", style = MaterialTheme.typography.bodyLarge)
        }
        OutlinedButton(onClick = { choixArret = true }, enabled = !donneesLignes?.arrets.isNullOrEmpty()) {
            Text(if (arret == null) "Choisir" else "Changer")
        }
    }
    if (choixArret && donneesLignes != null) {
        ChoixArret(donneesLignes.arrets, donneesLignes, actuel = arret, onFermer = { choixArret = false }) {
            portee.launch { app.preferences.enregistrerArret(it) }
            choixArret = false
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Ma destination, sur tout le réseau STAR", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(destination ?: "Facultatif : pour le retour", style = MaterialTheme.typography.bodyLarge)
        }
        OutlinedButton(onClick = { choixDestination = true }, enabled = !donneesLignes?.reseau.isNullOrEmpty()) {
            Text(if (destination == null) "Choisir" else "Changer")
        }
    }
    if (choixDestination && donneesLignes != null) {
        ChoixDestination(donneesLignes, destination, onFermer = { choixDestination = false }) {
            portee.launch { app.preferences.enregistrerDestination(it) }
            choixDestination = false
        }
    }

}

@Composable
private fun MonCarburant(app: CessonApp) {
    val portee = rememberCoroutineScope()
    val carburants by remember { app.depot.carburants() }.collectAsStateWithLifecycle(EtatModule.Absent)
    val proposes = (carburants as? EtatModule.Disponible)?.fichier?.donnees?.let(::carburantsProposes).orEmpty()
    val suivi by app.preferences.carburantSuivi.collectAsStateWithLifecycle(null)
    if (proposes.isNotEmpty()) {
        SousTitre("Mon carburant")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            proposes.forEach { (code, nom) ->
                FilterChip(selected = code == (suivi ?: CARBURANT_PAR_DEFAUT),
                    onClick = { portee.launch { app.preferences.enregistrerCarburant(code) } }, label = { Text(nom) })
            }
        }
    }

}

/** Cartes compactes ou détaillées : deux choix illustrés par ce qu'affichent la météo et les trains. */
@Composable
private fun EtapeCartes(app: CessonApp) {
    val portee = rememberCoroutineScope()
    val detaillees by app.preferences.cartesDetaillees.collectAsStateWithLifecycle(false)
    TitreEtape("Vos cartes")
    Text("Deux façons d'afficher les informations, sur tous les écrans.", style = MaterialTheme.typography.bodyLarge)
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ChoixCartes(
            titre = "Compactes", recommande = true, choisi = !detaillees,
            explication = "L'essentiel d'un coup d'œil, pour consulter vite et faire moins défiler.",
            exemples = listOf("Météo : 11°, pluie faible, 3° / 14° aujourd'hui", "Trains : le prochain vers Rennes et vers Vitré"),
        ) { portee.launch { app.preferences.enregistrerCartesDetaillees(false) } }
        ChoixCartes(
            titre = "Détaillées", recommande = false, choisi = detaillees,
            explication = "Toutes les informations : prévisions heure par heure, plusieurs passages, conseils, " +
                "liens vers les sources et heure de mise à jour.",
            exemples = listOf("Météo : + heure par heure, demain, lever et coucher du soleil",
                "Trains : 3 trains par sens, n° de TER, temps réel SNCF"),
        ) { portee.launch { app.preferences.enregistrerCartesDetaillees(true) } }
    }
    Text("Une carte trop longue se replie aussi avec « Étendre ». Ce choix se change dans Réglages → Cartes.",
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ChoixCartes(
    titre: String,
    recommande: Boolean,
    choisi: Boolean,
    explication: String,
    exemples: List<String>,
    onChoisir: () -> Unit,
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth().selectable(selected = choisi, role = Role.RadioButton, onClick = onChoisir),
        border = BorderStroke(if (choisi) 2.dp else 1.dp,
            if (choisi) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            RadioButton(selected = choisi, onClick = null)
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(titre, style = MaterialTheme.typography.titleMedium)
                    if (recommande) {
                        Spacer(Modifier.width(8.dp))
                        Pastille("Par défaut", Ton.INFO)
                    }
                }
                Text(explication, style = MaterialTheme.typography.bodyMedium)
                exemples.forEach {
                    Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun EtapeAccueil(app: CessonApp) {
    val portee = rememberCoroutineScope()
    val config by remember { app.preferences.configOnglet(ACCUEIL) }.collectAsState(initial = null)
    val cfg = config ?: return
    val ordre = ordonnerTuiles(ONGLET_ACCUEIL.tuiles.map { it.id }, cfg.ordre)
    TitreEtape("Votre accueil")
    Text("Gardez les cartes qui vous servent. Les autres restent disponibles dans les onglets.",
        style = MaterialTheme.typography.bodyLarge)
    ordre.mapNotNull { id -> ONGLET_ACCUEIL.tuiles.firstOrNull { it.id == id } }.forEach { tuile ->
        val visible = tuile.id !in cfg.masquees
        LigneInterrupteur(tuile.titre, visible) { afficher ->
            val m = if (afficher) cfg.masquees - tuile.id else cfg.masquees + tuile.id
            portee.launch { app.preferences.enregistrerOnglet(ACCUEIL, ConfigAccueil(ordre, m)) }
        }
    }
}

@Composable
private fun EtapeReglages(app: CessonApp) {
    val portee = rememberCoroutineScope()
    TitreEtape("Vos réglages")
    Text("Facultatif : ils personnalisent les cartes. Ils restent sur votre téléphone.",
        style = MaterialTheme.typography.bodyLarge)

    // Mes équipements
    SousTitre("Mes équipements")
    Text("Ceux que vous gardez s'affichent dans la carte Équipements de l'onglet Sortir.",
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    ReglageEquipements(app)

    // Rappel de collecte
    SousTitre("Ma collecte de déchets")
    ReglageRappelCollecte(app)
}

@Composable
private fun EtapeFin() {
    TitreEtape("C'est prêt")
    Text("Pour changer vos choix plus tard :", style = MaterialTheme.typography.bodyLarge)
    Text("• le bouton de réglage en haut de chaque onglet ordonne et masque ses cartes ;", style = MaterialTheme.typography.bodyLarge)
    Text("• un appui long sur une carte la masque ;", style = MaterialTheme.typography.bodyLarge)
    Text("• cartes compactes ou détaillées : Réglages → Cartes ;", style = MaterialTheme.typography.bodyLarge)
    Text("• vos trains habituels, vos équipements et les notifications : Réglages ;", style = MaterialTheme.typography.bodyLarge)
    Text("• ce guide reste dans le menu, en haut à droite.", style = MaterialTheme.typography.bodyLarge)
    Text("Une erreur, une idée ? « Vos idées / signaler une erreur », dans le même menu.",
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SousTitre(texte: String) {
    Spacer(Modifier.width(0.dp))
    Text(texte, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp).semantics { heading() })
}

@Composable
private fun LigneInterrupteur(libelle: String, actif: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().toggleable(value = actif, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 4.dp),
    ) {
        Text(libelle, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = actif, onCheckedChange = null)
    }
}
