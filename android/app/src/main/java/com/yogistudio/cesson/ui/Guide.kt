package com.yogistudio.cesson.ui

import androidx.compose.foundation.horizontalScroll
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
import com.yogistudio.cesson.equipements.equipements
import com.yogistudio.cesson.pratique.CARBURANT_PAR_DEFAUT
import com.yogistudio.cesson.pratique.carburants
import com.yogistudio.cesson.pratique.carburantsProposes
import com.yogistudio.cesson.rappels.ReglageRappelCollecte
import com.yogistudio.cesson.transport.ChoixArret
import com.yogistudio.cesson.transport.lignesStar
import kotlinx.coroutines.launch

private const val NB_ETAPES = 4

/**
 * Guide de démarrage, au premier lancement puis depuis le menu : bienvenue, cartes de l'accueil,
 * réglages personnels. Chaque choix est enregistré aussitôt (sur le téléphone) : « Passer » à
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
                1 -> EtapeAccueil(app)
                2 -> EtapeReglages(app)
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
            if (etape < NB_ETAPES - 1) Button(onClick = { etape++ }) { Text("Suivant") }
            else Button(onClick = { terminer() }) { Text("C'est parti") }
        }
    }
}

@Composable
private fun TitreEtape(texte: String) =
    Text(texte, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })

@Composable
private fun EtapeBienvenue() {
    TitreEtape("Bienvenue à Cesson au quotidien")
    Text("Cette application non officielle rassemble l'information pratique de Cesson-Sévigné à partir de " +
        "données publiques : transports, collecte des déchets, équipements, Vilaine, météo…",
        style = MaterialTheme.typography.bodyLarge)
    Text("Elle n'est éditée ni par la Ville ni par Rennes Métropole.", style = MaterialTheme.typography.bodyLarge)
    Text(PROMESSE, style = MaterialTheme.typography.titleMedium)
    Text("En deux minutes, choisissez ce que vous voulez voir. Tout se change ensuite à tout moment.",
        style = MaterialTheme.typography.bodyLarge)
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

    // Mon arrêt
    val lignes by remember { app.depot.lignesStar() }.collectAsStateWithLifecycle(EtatModule.Absent)
    val donneesLignes = (lignes as? EtatModule.Disponible)?.fichier?.donnees
    val arret by app.preferences.arretSuivi.collectAsStateWithLifecycle(null)
    var choixArret by rememberSaveable { mutableStateOf(false) }
    SousTitre("Mon arrêt de bus")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(arret ?: "Aucun arrêt choisi", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
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

    // Mon carburant
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

    // Mes équipements
    val equipements by remember { app.depot.equipements() }.collectAsStateWithLifecycle(EtatModule.Absent)
    val liste = (equipements as? EtatModule.Disponible)?.fichier?.donnees?.equipements.orEmpty()
    val masques by app.preferences.equipementsMasques.collectAsStateWithLifecycle(emptySet())
    if (liste.isNotEmpty()) {
        SousTitre("Mes équipements")
        liste.forEach { e ->
            LigneInterrupteur(e.lieu.nom, e.lieu.id !in masques) { garder ->
                portee.launch {
                    app.preferences.enregistrerEquipementsMasques(if (garder) masques - e.lieu.id else masques + e.lieu.id)
                }
            }
        }
    }

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
