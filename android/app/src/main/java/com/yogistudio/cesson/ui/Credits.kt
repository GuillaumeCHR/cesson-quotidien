package com.yogistudio.cesson.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yogistudio.cesson.BuildConfig
import com.yogistudio.cesson.ui.intro.NOM_APP
import java.util.Locale

/**
 * Crédits de l'écran À propos, façon générique de film, sur le modèle de Molkky Board et de VYLIA, adaptés à une app
 * d'information locale : la vedette, ce sont les sources de données publiques. Ton léger, mais chaque affirmation est
 * vraie (comme la promesse de l'app).
 */
@Composable
fun Credits() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Column(
            Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Générique", style = MaterialTheme.typography.titleLarge, modifier = Modifier.fillMaxWidth().semantics { heading() })
            Terminal()
            Text(NOM_APP, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
            Text("Version ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})", style = MaterialTheme.typography.titleMedium)
            Note("Compilée avec soin, café et ./gradlew ${if (BuildConfig.DEBUG) "assembleDebug" else "assembleRelease"}.")
            CREDITS.forEach { Credit(it) }
            Rubrique("Statistiques de production")
            Statistiques()
            Rubrique("Remerciements")
            REMERCIEMENTS.forEach { Text(it, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center) }
            Spacer(Modifier.height(12.dp))
            Note("Aucune donnée personnelle n'a été collectée pendant ce générique. Nous avons vérifié.")
            Note("Scène post-générique : un bac jaune attend sagement au bord du trottoir. Le camion passe mercredi, semaine paire.")
        }
    }
}

private class Bloc(val role: String, val nom: String, val note: String?)

private val CREDITS = listOf(
    Bloc("Production", "Yogi Studio",
        "Studio indépendant, effectif total : une personne et un smartphone en posture du lotus."),
    Bloc("Développement, design, tests et service après-vente", "Yogi",
        "A aussi tenu le rôle de stagiaire, de chef de projet, de premier usager et de testeur au doigt."),
    Bloc("Pair programming", "Claude (Anthropic), via Claude Code",
        "A lu le calendrier des collectes scanné, case par case, et retrouvé ses 50 dates par le calcul."),
    Bloc("Transports", "STAR (Rennes Métropole, Keolis Rennes) et SNCF",
        "Métro, bus et trains en temps réel, sous licence ODbL et Licence Ouverte. Quand le temps réel manque, l'app le dit."),
    Bloc("La Vilaine", "Hub'Eau et Vigicrues (SCHAPI)",
        "Ils surveillent la rivière à Pont Briand. Nous ne faisons que relayer, et renvoyer vers eux."),
    Bloc("Météo", "Open-Meteo, modèles de Météo-France",
        "Sous CC BY 4.0. Le parapluie reste à votre appréciation."),
    Bloc("Vigilance météo", "Source : Météo-France",
        "Vigilance météorologique et bulletins de Météo-France, sous Licence Ouverte Etalab 2.0, reproduits sans " +
            "modification avec la date de leur dernière mise à jour. Jaune, orange ou rouge : c'est Météo-France qui " +
            "décide, l'app ne fait que relayer."),
    Bloc("Qualité de l'air et pollens", "Source : Atmo France / AASQA",
        "Qualité de l'air, pollens et épisodes de pollution mesurés et prévus par Air Breizh, l'observatoire de l'air en Bretagne, " +
            "sous licence ODbL. Ils surveillent l'air de Cesson ; nous, on se contente de vous le dire."),
    Bloc("Accessibilité des lieux", "acceslibre (ministère de la Transition écologique)",
        "Entrées, marches, places réservées, sanitaires : décrits par les établissements et par des contributeurs, " +
            "sous Licence Ouverte. Une fiche incomplète se complète chez eux, en deux minutes."),
    Bloc("Prix à la pompe", "Ministère de l'Économie (data.economie.gouv.fr)",
        "Chaque prix garde sa date : un prix d'il y a dix jours n'est jamais « le moins cher »."),
    Bloc("Fiches des films", "The Movie Database (TMDB) et Wikidata",
        "Affiches, synopsis, réalisation et distribution des films du Sévigné viennent de TMDB ; les liens Wikipédia " +
            "et Allociné, de Wikidata (CC0). This product uses the TMDB API but is not endorsed or certified by TMDB. " +
            "Le serveur de l'app les récupère : votre téléphone ne contacte jamais TMDB."),
    Bloc("Photos des spectacles", "Pont des Arts et Carré Sévigné, via OpenAgenda",
        "Les vignettes de la saison culturelle sont celles que la Ville publie sur OpenAgenda, chacune avec le crédit " +
            "de son photographe. Le serveur de l'app les récupère : votre téléphone ne contacte jamais OpenAgenda."),
    Bloc("Cartographie", "Les contributrices et contributeurs d'OpenStreetMap",
        "La carte de l'intro et la carte de Cesson, c'est leur travail bénévole, sous ODbL. Les tuiles viennent des " +
            "serveurs de la Fondation OpenStreetMap, relayées par le serveur de l'app ; affichées avec osmdroid (Apache 2.0)."),
    Bloc("Horaires et calendriers", "Ville de Cesson-Sévigné et Rennes Métropole",
        "Recopiés à la main depuis leurs sites, avec la date de vérification. La page officielle fait foi."),
    Bloc("Figurants du générique d'ouverture", "Les épingles, les horaires et les « ouvert, fermé »",
        "Ils apparaissent, informent et s'effacent : c'est tout ce qu'on attend d'une bonne information locale."),
    Bloc("Typographie", "Atkinson Hyperlegible et Roboto Flex — SIL Open Font License 1.1",
        "Choisies pour être lues d'un coup d'œil, même à l'arrêt de bus, sous la pluie."),
    Bloc("Matériel de test", "Un Fairphone en mode débogage, un PC portable sous Linux et un Raspberry Pi 4",
        "Le Pi dormait dans un tiroir. Il a repris du service : c'est lui qui publiera les données de l'app."),
)

private val REMERCIEMENTS = listOf(
    "Aux bénévoles d'OpenStreetMap, qui ont dessiné Cesson-Sévigné bien avant nous.",
    "Aux services de la Ville et de Rennes Métropole, qui publient horaires, calendriers et données ouvertes.",
    "À STAR, à la SNCF, à Hub'Eau, à Vigicrues, à Météo-France, à Open-Meteo, à Air Breizh, à acceslibre, à TMDB et à Wikidata, dont les " +
        "données ouvertes font tout le travail.",
    "À celles et ceux qui sortent leur bac la veille au soir.",
)

@Composable
private fun Credit(b: Bloc) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Rubrique(b.role)
        Text(b.nom, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        b.note?.let { Note(it) }
    }
}

@Composable
private fun Rubrique(texte: String) {
    Text(
        texte.uppercase(Locale.FRANCE),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 12.dp).semantics { heading() },
    )
}

@Composable
private fun Note(texte: String) {
    Text(texte, style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic,
        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
}

private val Ardoise = Color(0xFF23302B)
private val Sable = Color(0xFFE5E2D9)
private val Vert = Color(0xFF93D2B0)

/** Faux terminal, ardoise et vert dans les deux thèmes ; TalkBack le lit comme une seule phrase. */
@Composable
private fun Terminal() {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Ardoise,
        contentColor = Sable,
        modifier = Modifier.fillMaxWidth().clearAndSetSemantics {
            contentDescription = "Démarrage terminé : Vilaine surveillée, horaires à jour, aucun traqueur, aucun compte."
        },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("$ cessonapp --generique", style = StyleTerminal(), color = Vert)
            listOf(
                "Tracé de la Vilaine" to "OK",
                "Horaires des équipements" to "OK",
                "Bacs gris et jaunes" to "à jour",
                "Traqueurs dans $NOM_APP" to "0 trouvé",
                "Compte à créer" to "aucun",
            ).forEach { (l, v) -> LigneTerminal(l, v) }
        }
    }
}

@Composable
private fun StyleTerminal() = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)

@Composable
private fun LigneTerminal(libelle: String, valeur: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("> $libelle", style = StyleTerminal(), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(4f, fill = false))
        Text(".".repeat(60), style = StyleTerminal(), maxLines = 1, overflow = TextOverflow.Clip, modifier = Modifier.weight(1f))
        Text(" $valeur", style = StyleTerminal(), maxLines = 1, color = Vert)
    }
}

/** Les chiffres qui comptent. Les zéros sont vrais. */
@Composable
private fun Statistiques() {
    val lignes = listOf(
        "Publicités" to "0",
        "Traqueurs" to "0",
        "Comptes à créer" to "0",
        "Données personnelles collectées" to "0",
        "Sources de données publiques" to "9",
        "Serveurs Cesson'App" to "un Raspberry Pi, qui ne publie que des données publiques",
    )
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        lignes.forEach { (libelle, valeur) ->
            if (valeur.all { it.isDigit() }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(libelle, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(valeur, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 12.dp))
                }
            } else {
                Column(Modifier.fillMaxWidth()) {
                    Text(libelle, style = MaterialTheme.typography.bodyLarge)
                    Text(valeur, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
