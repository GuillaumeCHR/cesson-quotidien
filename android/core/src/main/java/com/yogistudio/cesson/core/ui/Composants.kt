package com.yogistudio.cesson.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.yogistudio.cesson.core.donnees.Fraicheur
import java.time.Instant

/**
 * Cartes compactes (réglage « Cartes », par défaut) : chaque carte ne garde que l'essentiel, et la ligne
 * « Mis à jour… » ne s'affiche que si les données sont anciennes. Fourni par les écrans à onglets (EcranTuiles).
 */
val LocalCompact = staticCompositionLocalOf { false }

/**
 * Carte de donnée commune à tous les modules : titre, contenu, puis la ligne de fraîcheur
 * (« Mis à jour il y a 12 min · source »). Les signalements passent par le menu « Vos idées ».
 */
@Composable
fun CarteDonnee(
    titre: String,
    icone: ImageVector,
    modifier: Modifier = Modifier,
    fraicheur: Fraicheur? = null,
    maintenant: Instant = rememberMaintenant(),
    repliable: Boolean = true,
    contenu: @Composable () -> Unit,
) {
    val fond = MaterialTheme.colorScheme.surfaceContainerLowest
    val compact = LocalCompact.current
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = fond),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            Modifier.padding(start = 18.dp, end = 18.dp, top = if (compact) 12.dp else 16.dp, bottom = if (compact) 12.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Pastille d'icône, comme les épingles de l'intro.
                Box(
                    Modifier.size(34.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icone, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(titre, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            }
            if (repliable) ContenuRepliable(fond, contenu = contenu) else contenu()
            if (fraicheur != null && (!compact || fraicheur.estPerimee(maintenant))) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                LigneFraicheur(fraicheur, maintenant)
            }
        }
    }
}

/** Une donnée périmée est signalée comme telle, jamais masquée. */
@Composable
fun LigneFraicheur(fraicheur: Fraicheur, maintenant: Instant, modifier: Modifier = Modifier) {
    val perimee = fraicheur.estPerimee(maintenant)
    Column(modifier) {
        if (perimee) {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.History, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Données anciennes", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Text(
            "Mis à jour ${formaterDepuis(fraicheur.recupereLe, maintenant)} · ${fraicheur.source}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Contenu d'attente d'une tuile dont le module arrive à une étape ultérieure. */
@Composable
fun TexteBientot(texte: String) {
    Text(texte, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Tons des pastilles d'état, les mêmes que dans l'intro. */
enum class Ton { OUVERT, FERME, ALERTE, INFO, NEUTRE }

/**
 * Pastille d'état pleine et contrastée : « Ouvert », « Fermé », « +7 min », « Supprimé »… Le texte porte toujours
 * l'information ; la couleur la souligne.
 */
@Composable
fun Pastille(texte: String, ton: Ton, modifier: Modifier = Modifier) {
    val sombre = LocalSombre.current
    val (fond, encre) = when (ton) {
        Ton.OUVERT -> (if (sombre) Color(0xFF93D2B0) else Color(0xFF2F8A5B)) to (if (sombre) Color(0xFF0E2E20) else Color.White)
        Ton.FERME -> (if (sombre) Color(0xFFFFB4AB) else Color(0xFFC0392B)) to (if (sombre) Color(0xFF5C1111) else Color.White)
        Ton.ALERTE -> Palette.VigilanceJaune to Color(0xFF2B2100)
        Ton.INFO -> (if (sombre) Color(0xFF9DCAF0) else Palette.BleuVilaine) to (if (sombre) Color(0xFF00324F) else Color.White)
        Ton.NEUTRE -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(color = fond, contentColor = encre, shape = RoundedCornerShape(50), modifier = modifier) {
        Text(texte, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp), maxLines = 1)
    }
}
