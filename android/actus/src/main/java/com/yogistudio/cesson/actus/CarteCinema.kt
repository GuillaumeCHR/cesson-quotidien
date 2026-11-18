package com.yogistudio.cesson.actus

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yogistudio.cesson.core.donnees.EtatModule
import com.yogistudio.cesson.core.ui.CarteDonnee
import com.yogistudio.cesson.core.ui.LocalCompact
import com.yogistudio.cesson.core.ui.FuseauCesson
import com.yogistudio.cesson.core.ui.Pastille
import com.yogistudio.cesson.core.ui.TexteBientot
import com.yogistudio.cesson.core.ui.Ton
import com.yogistudio.cesson.core.ui.rememberMaintenant

private const val JOURS_PROPOSES = 7

/** « 20h30 », « 14h00 ». */
private fun heure(s: Seance) = s.debut.atZone(FuseauCesson).let { "${it.hour}h%02d".format(it.minute) }

/**
 * Cinéma Le Sévigné : séances jour par jour (aujourd'hui d'abord), d'après la grille mensuelle de la salle.
 * Étiquettes VO et Sortie nationale ; tarif particulier et note de séance quand la grille en donne.
 * Fiche du film quand le serveur l'a reconnu (TMDB) : affiche, « réalisateur · durée · genre », résumé d'une ligne,
 * et un dépliant pour le synopsis, la distribution et les liens. Compact : affiche et ligne seulement.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CarteCinema(etat: EtatModule<DonneesCinema>, modifier: Modifier = Modifier, affiche: suspend (String) -> ByteArray? = { null }) {
    val maintenant = rememberMaintenant()
    val uri = LocalUriHandler.current
    var choix by rememberSaveable { mutableIntStateOf(0) }
    when (etat) {
        EtatModule.Absent -> CarteDonnee("Cinéma Le Sévigné", Icons.Rounded.Movie, modifier, maintenant = maintenant) {
            TexteBientot("Séances pas encore reçues. Tirez vers le bas pour actualiser.")
        }
        is EtatModule.Disponible -> {
            val d = etat.fichier.donnees
            val aujourdhui = maintenant.atZone(FuseauCesson).toLocalDate()
            val jours = joursDeSeance(d, maintenant).take(JOURS_PROPOSES)
            val jour = jours.getOrNull(choix.coerceAtMost(jours.lastIndex.coerceAtLeast(0)))
            CarteDonnee(d.salle, Icons.Rounded.Movie, modifier, etat.fraicheur, maintenant) {
                // Compact : les jours et, pour le jour choisi, chaque film et ses horaires ; ni notes, ni annonces, ni tarifs.
                val compact = LocalCompact.current
                Column(verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)) {
                    if (jours.isEmpty()) {
                        TexteBientot("Pas de séance annoncée pour l'instant : la grille du mois suivant arrive en fin de mois.")
                    } else {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            jours.forEachIndexed { i, j ->
                                FilterChip(selected = j == jour, onClick = { choix = i },
                                    label = { Text(libelleJour(j, aujourdhui).replace(Regex(" \\S+$"), "")) })
                            }
                        }
                        jour?.let { j ->
                            seancesDuJour(d, j, maintenant).forEach { (film, seances) ->
                                Column(
                                    Modifier.fillMaxWidth().padding(vertical = 4.dp).semantics(mergeDescendants = true) {
                                        contentDescription = listOfNotNull(
                                            film.titre, film.fiche?.let(::ligneFiche), film.fiche?.resume.takeIf { !compact },
                                            "version originale".takeIf { film.vo }, "sortie nationale".takeIf { film.sortieNationale },
                                            "séances à " + seances.joinToString(", ") { heure(it) },
                                            seances.mapNotNull { it.tarif }.firstOrNull()?.let { "tarif $it" },
                                            seances.mapNotNull { it.note }.firstOrNull(),
                                        ).joinToString(", ")
                                    },
                                    verticalArrangement = Arrangement.spacedBy(3.dp),
                                ) {
                                  Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    film.fiche?.affiche?.let { AfficheFilm(it, compact, affiche) }
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(film.titre, style = MaterialTheme.typography.titleSmall)
                                    film.fiche?.let(::ligneFiche)?.takeIf { it.isNotEmpty() }?.let {
                                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (!compact) film.fiche?.resume?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        seances.forEach { s ->
                                            Pastille(heure(s) + (s.tarif?.takeIf { !compact }?.let { " · $it" } ?: ""), Ton.INFO)
                                        }
                                        if (film.vo) Pastille("VO", Ton.NEUTRE)
                                        if (film.sortieNationale) Pastille("Sortie nationale", Ton.OUVERT)
                                    }
                                    if (!compact) seances.mapNotNull { it.note }.firstOrNull()?.let {
                                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    }
                                  }
                                  if (!compact) film.fiche?.let { DetailFiche(it) }
                                }
                            }
                        }
                    }
                    if (compact) return@Column
                    d.annonces.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                    d.tarifs?.let {
                        Text(it.replaceFirstChar { c -> c.uppercase() }, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row {
                        TextButton(onClick = { runCatching { uri.openUri(d.grille) } }) { Text("Grille du mois") }
                    }
                }
            }
        }
    }
}

private val LARGEUR_AFFICHE = 56.dp
private val LARGEUR_AFFICHE_COMPACTE = 40.dp

/** Affiche du film, lue sur le serveur de l'app (jamais chez TMDB) ; un aplat le temps qu'elle arrive. */
@Composable
private fun AfficheFilm(chemin: String, compact: Boolean, charger: suspend (String) -> ByteArray?) {
    val image by produceState<ImageBitmap?>(null, chemin) {
        value = charger(chemin)?.let { o -> runCatching { BitmapFactory.decodeByteArray(o, 0, o.size)?.asImageBitmap() }.getOrNull() }
    }
    val largeur = if (compact) LARGEUR_AFFICHE_COMPACTE else LARGEUR_AFFICHE
    val forme = RoundedCornerShape(6.dp)
    Box(Modifier.size(largeur, largeur * 1.5f).clip(forme).background(MaterialTheme.colorScheme.surfaceVariant)) {
        image?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(largeur, largeur * 1.5f)) }
    }
}

/** Dépliant « À propos du film » : synopsis, réalisation, distribution, classification, liens. */
@Composable
private fun DetailFiche(f: Fiche) {
    val uri = LocalUriHandler.current
    var deplie by rememberSaveable(f.tmdb) { mutableStateOf(false) }
    TextButton(onClick = { deplie = !deplie }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 0.dp)) {
        Text(if (deplie) "Masquer la fiche" else "À propos du film")
    }
    if (!deplie) return
    val discret = MaterialTheme.colorScheme.onSurfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        f.synopsis?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        listOfNotNull(
            f.realisateurs.takeIf { it.isNotEmpty() }?.let { "Réalisation : " + it.joinToString(", ") },
            f.distribution.takeIf { it.isNotEmpty() }?.let { "Avec " + it.joinToString(", ") },
            listOfNotNull(f.annee?.toString(), f.titreOriginal?.let { "titre original : $it" },
                f.genres.takeIf { it.size > 1 }?.joinToString(", ")).joinToString(" · ").takeIf { it.isNotEmpty() },
            f.classification?.let { "Interdit aux moins de $it ans" },
        ).forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = discret) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            f.wikipedia?.let { l -> TextButton(onClick = { runCatching { uri.openUri(l) } }) { Text("Wikipédia") } }
            f.allocine?.let { l -> TextButton(onClick = { runCatching { uri.openUri(l) } }) { Text("Allociné") } }
            TextButton(onClick = { runCatching { uri.openUri(f.pageTmdb) } }) { Text("TMDB") }
        }
    }
}
