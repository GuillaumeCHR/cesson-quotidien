package com.yogistudio.cesson.environnement

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.LocalFlorist
import androidx.compose.material.icons.rounded.Masks
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yogistudio.cesson.core.donnees.EtatModule
import com.yogistudio.cesson.core.ui.CarteDonnee
import com.yogistudio.cesson.core.ui.FuseauCesson
import com.yogistudio.cesson.core.ui.LocalCompact
import com.yogistudio.cesson.core.ui.Palette
import com.yogistudio.cesson.core.ui.Pastille
import com.yogistudio.cesson.core.ui.TexteBientot
import com.yogistudio.cesson.core.ui.Ton
import com.yogistudio.cesson.core.ui.rememberMaintenant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val FormatJourSemaine = DateTimeFormatter.ofPattern("EEEE", Locale.FRANCE)

/** « Aujourd'hui », « Demain », puis le jour de la semaine (« Mercredi »). */
private fun nomJour(d: LocalDate, aujourdhui: LocalDate): String = when (d) {
    aujourdhui -> "Aujourd'hui"
    aujourdhui.plusDays(1) -> "Demain"
    aujourdhui.minusDays(1) -> "Hier"
    else -> FormatJourSemaine.format(d).replaceFirstChar { it.titlecase(Locale.FRANCE) }
}

@Composable
private fun LienAirBreizh(texte: String) {
    val uri = LocalUriHandler.current
    TextButton(onClick = { uri.openUri(URL_AIR_BREIZH) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
        Text(texte)
        Spacer(Modifier.width(4.dp))
        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun TexteSecondaire(texte: String) =
    Text(texte, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

// --- Qualité de l'air --------------------------------------------------------------------------------

/** Pastille aux couleurs officielles de l'indice ATMO ; le niveau est toujours écrit. */
@Composable
private fun PastilleAtmo(libelle: String, argb: Long, code: Int, modifier: Modifier = Modifier) {
    Surface(
        color = Color(argb),
        contentColor = if (texteClairSurAtmo(code)) Color.White else Color(0xFF14211C),
        shape = RoundedCornerShape(50),
        modifier = modifier,
    ) {
        Text(libelle, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp), maxLines = 1)
    }
}

@Composable
fun CarteQualiteAir(etat: EtatModule<DonneesQualiteAir>, modifier: Modifier = Modifier) {
    val maintenant = rememberMaintenant()
    val titre = "Qualité de l'air"
    when (etat) {
        EtatModule.Absent -> CarteDonnee(titre, Icons.Rounded.Air, modifier, maintenant = maintenant) {
            TexteBientot("Indice ATMO pas encore reçu. Air Breizh le publie chaque jour vers 16h.")
        }
        is EtatModule.Disponible -> {
            val d = etat.fichier.donnees
            val aujourdhui = maintenant.atZone(FuseauCesson).toLocalDate()
            val jours = d.jours.filter { !it.date.isBefore(aujourdhui) }
            val compact = LocalCompact.current
            CarteDonnee(titre, Icons.Rounded.Air, modifier, fraicheur = etat.fraicheur, maintenant = maintenant) {
                if (jours.isEmpty()) {
                    TexteBientot("Pas d'indice pour aujourd'hui : Air Breizh ne l'a pas encore publié.")
                    return@CarteDonnee
                }
                Column(verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp)) {
                    jours.forEachIndexed { i, j -> JourQualiteAir(j, nomJour(j.date, aujourdhui), principal = i == 0 && !compact) }
                    if (!compact) {
                        jours.firstOrNull()?.takeIf { it.sousIndices.isNotEmpty() }?.let { SousIndices(it) }
                        TexteSecondaire("Indice calculé pour ${d.zone}, qui vaut pour Cesson.")
                        LienAirBreizh("Conseils et cartes sur Air Breizh")
                    }
                }
            }
        }
    }
}

@Composable
private fun JourQualiteAir(j: JourAtmo, nom: String, principal: Boolean) {
    val cause = j.responsables.takeIf { j.code >= 2 && it.isNotEmpty() }?.joinToString(" et ") { nomPolluant(it) }
    val description = "$nom : qualité de l'air ${j.libelle.lowercase(Locale.FRANCE)}" + (cause?.let { ", à cause de $it" } ?: "")
    if (principal) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = description
        }) {
            Box(Modifier.size(44.dp).background(Color(j.argb()), CircleShape))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(j.libelle, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold))
                TexteSecondaire(if (cause != null) "$nom · dû à $cause" else "$nom · indice ATMO")
            }
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = description
        }) {
            Text("$nom ", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
            PastilleAtmo(j.libelle, j.argb(), j.code)
            cause?.let {
                Spacer(Modifier.width(8.dp))
                Text(it.replaceFirstChar { c -> c.uppercase() }, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

/** Les cinq sous-indices du jour, chacun avec son niveau écrit. */
@Composable
private fun SousIndices(j: JourAtmo) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        TexteSecondaire("Par polluant, aujourd'hui")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            j.sousIndices.forEach { s ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.semantics(mergeDescendants = true) {
                        contentDescription = "${s.nom} : ${libelleAtmo(s.code).lowercase(Locale.FRANCE)}"
                    },
                ) {
                    Text(s.nom, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                    Box(Modifier.width(40.dp).height(6.dp).background(Color(couleurAtmo(s.code)), RoundedCornerShape(3.dp)))
                    Text(libelleAtmo(s.code), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }
    }
}

// --- Pollens -----------------------------------------------------------------------------------------

private fun tonPollen(code: Int): Ton = when (code) {
    1, 2 -> Ton.OUVERT
    3 -> Ton.ALERTE
    in 4..6 -> Ton.FERME
    else -> Ton.NEUTRE
}

@Composable
fun CartePollens(etat: EtatModule<DonneesPollens>, modifier: Modifier = Modifier) {
    val maintenant = rememberMaintenant()
    val titre = "Indice pollen"
    when (etat) {
        EtatModule.Absent -> CarteDonnee(titre, Icons.Rounded.LocalFlorist, modifier, maintenant = maintenant) {
            TexteBientot("Indice pollen pas encore reçu. Air Breizh le publie chaque jour vers 16h.")
        }
        is EtatModule.Disponible -> {
            val d = etat.fichier.donnees
            val aujourdhui = maintenant.atZone(FuseauCesson).toLocalDate()
            val jours = d.jours.filter { !it.date.isBefore(aujourdhui) }
            val compact = LocalCompact.current
            CarteDonnee(titre, Icons.Rounded.LocalFlorist, modifier, fraicheur = etat.fraicheur, maintenant = maintenant) {
                val j = jours.firstOrNull()
                if (j == null) {
                    TexteBientot("Pas d'indice pour aujourd'hui : Air Breizh ne l'a pas encore publié.")
                    return@CarteDonnee
                }
                Column(verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp)) {
                    LigneJourPollen(j, nomJour(j.date, aujourdhui))
                    if (j.alerte) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Warning, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Alerte pollens d'Air Breizh", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                    val suivants = jours.drop(1).let { if (compact) it.take(1) else it }
                    if (suivants.isNotEmpty() && suivants.all { it.horsSaison } && j.horsSaison) {
                        TexteSecondaire(if (compact) "Très faible aussi demain." else "Très faible aussi les jours suivants : pollens hors saison.")
                    } else {
                        suivants.forEach { LigneJourPollen(it, nomJour(it.date, aujourdhui), secondaire = true) }
                    }
                    if (!compact && !j.horsSaison) Especes(j)
                    if (!compact) TexteSecondaire("Indice calculé pour ${d.commune}.")
                }
            }
        }
    }
}

@Composable
private fun LigneJourPollen(j: JourPollen, nom: String, secondaire: Boolean = false) {
    val especes = j.nomsResponsables()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = "$nom : risque pollinique ${j.libelle.lowercase(Locale.FRANCE)}" +
                (if (especes.isNotEmpty()) ", surtout ${especes.joinToString(", ")}" else "")
        },
    ) {
        Text("$nom ", style = (if (secondaire) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge)
            .copy(fontWeight = FontWeight.Bold))
        Pastille(j.libelle, tonPollen(j.code))
        if (especes.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            Text(especes.joinToString(", "), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

/** Détail par espèce, du plus fort au plus faible ; seulement en saison. */
@Composable
private fun Especes(j: JourPollen) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        TexteSecondaire("Par espèce, aujourd'hui")
        j.especes.sortedByDescending { it.code }.forEach { e ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(e.nom, style = MaterialTheme.typography.bodyMedium)
                Text(libellePollen(e.code), style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (e.code >= 3) FontWeight.Bold else FontWeight.Normal))
            }
        }
    }
}

// --- Épisodes de pollution ---------------------------------------------------------------------------

@Composable
private fun PastilleEpisode(e: EtatEpisode) = when (e) {
    EtatEpisode.AUCUN -> Pastille("Aucun épisode", Ton.OUVERT)
    EtatEpisode.INFORMATION -> Pastille("Information", Ton.ALERTE)
    EtatEpisode.ALERTE_PERSISTANCE, EtatEpisode.ALERTE ->
        Surface(color = Palette.VigilanceOrange, contentColor = Color(0xFF2B1600), shape = RoundedCornerShape(50)) {
            Text("Alerte", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp))
        }
    EtatEpisode.INCONNU -> Pastille("À vérifier", Ton.NEUTRE)
}

private fun couleurEpisode(e: EtatEpisode): Color = when (e) {
    EtatEpisode.INFORMATION -> Palette.VigilanceJaune
    EtatEpisode.ALERTE_PERSISTANCE, EtatEpisode.ALERTE -> Palette.VigilanceOrange
    else -> Color.Transparent
}

@Composable
fun CarteEpisodes(etat: EtatModule<DonneesEpisodes>, modifier: Modifier = Modifier) {
    val maintenant = rememberMaintenant()
    val titre = "Épisodes de pollution"
    when (etat) {
        EtatModule.Absent -> CarteDonnee(titre, Icons.Rounded.Masks, modifier, maintenant = maintenant) {
            TexteBientot("Épisodes de pollution pas encore reçus. Tirez vers le bas pour actualiser.")
        }
        is EtatModule.Disponible -> {
            val d = etat.fichier.donnees
            val aujourdhui = maintenant.atZone(FuseauCesson).toLocalDate()
            val aVenir = d.aVenir(aujourdhui)
            val compact = LocalCompact.current
            CarteDonnee(titre, Icons.Rounded.Masks, modifier, fraicheur = etat.fraicheur, maintenant = maintenant) {
                Column(verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp)) {
                    if (aVenir.isEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PastilleEpisode(EtatEpisode.AUCUN)
                            Spacer(Modifier.width(8.dp))
                            Text("ni aujourd'hui ni demain", style = MaterialTheme.typography.bodyMedium)
                        }
                        if (!compact) TexteSecondaire("Aucune procédure préfectorale en ${d.zone}.")
                    } else {
                        aVenir.forEach { j ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.semantics(mergeDescendants = true) {
                                    contentDescription = "${nomJour(j.date, aujourdhui)} : ${j.etat.libelle()}, ${j.polluantsVises()}"
                                },
                            ) {
                                Text("${nomJour(j.date, aujourdhui)} ", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
                                PastilleEpisode(j.etat)
                                Spacer(Modifier.width(8.dp))
                                Text(j.polluantsVises(), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                            }
                            if (!compact) TexteSecondaire("${j.etat.libelle()} · ${j.polluants.filter { it.etat == j.etat }.joinToString(", ") { it.nom }}")
                        }
                        if (!compact) {
                            Text(aVenir.maxBy { it.etat.ordinal }.etat.definition(), style = MaterialTheme.typography.bodyMedium)
                            LienAirBreizh("Recommandations sur Air Breizh")
                        }
                    }
                }
            }
        }
    }
}

/**
 * Bandeau en tête de l'accueil dès qu'une procédure vise aujourd'hui ou demain. Comme celui des crues,
 * il ne fait pas partie des tuiles et renvoie vers la source officielle.
 */
@Composable
fun BandeauPollution(etat: EtatModule<DonneesEpisodes>) {
    val d = (etat as? EtatModule.Disponible)?.fichier?.donnees ?: return
    val maintenant = rememberMaintenant()
    val aujourdhui = maintenant.atZone(FuseauCesson).toLocalDate()
    val pire = d.aVenir(aujourdhui).maxByOrNull { it.etat.ordinal } ?: return
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(8.dp).fillMaxHeight().background(couleurEpisode(pire.etat)))
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.width(8.dp))
                    Text("Pollution ${pire.polluantsVises()} ${nomJour(pire.date, aujourdhui).lowercase(Locale.FRANCE)}",
                        style = MaterialTheme.typography.titleMedium)
                }
                Text("${pire.etat.libelle()} en ${d.zone}. ${pire.etat.definition()}", style = MaterialTheme.typography.bodyMedium)
                LienAirBreizh("Recommandations sur Air Breizh")
            }
        }
    }
}
