package com.yogistudio.cesson.familles

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.BeachAccess
import androidx.compose.material.icons.rounded.FamilyRestroom
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
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

/** Vacances scolaires : où on en est (en vacances, ou les prochaines dans combien de jours), puis l'année. */
@Composable
fun CarteVacances(etat: EtatModule<DonneesVacances>, modifier: Modifier = Modifier) {
    val maintenant = rememberMaintenant()
    val titre = "Vacances scolaires"
    when (etat) {
        EtatModule.Absent -> CarteDonnee(titre, Icons.Rounded.BeachAccess, modifier, maintenant = maintenant) {
            TexteBientot("Calendrier pas encore reçu. Tirez vers le bas pour actualiser.")
        }
        is EtatModule.Disponible -> {
            val d = etat.fichier.donnees
            val aujourdhui = maintenant.atZone(FuseauCesson).toLocalDate()
            CarteDonnee(titre, Icons.Rounded.BeachAccess, modifier, etat.fraicheur, maintenant) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    when (val s = situation(d, aujourdhui)) {
                        is SituationScolaire.EnVacances -> {
                            Pastille("En vacances", Ton.OUVERT)
                            Text(s.periode.nom, style = MaterialTheme.typography.titleMedium)
                            Text(
                                s.periode.reprise?.let { "Reprise ${jourLong(it)}" } ?: "Jusqu'au ${jourLong(s.periode.dernierJour())}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        is SituationScolaire.AVenir -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Pastille(dansJours(s.dansJours).replaceFirstChar { it.uppercase() }, Ton.NEUTRE)
                            }
                            Text(s.periode.nom, style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (s.periode.pont) "Pas de classe le ${s.periode.dates()}"
                                else "Dernier jour de classe ${jourLong(s.periode.du.minusDays(1))}, " +
                                    (s.periode.reprise?.let { "reprise ${jourLong(it)}" } ?: "fin pas encore publiée"),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        SituationScolaire.Inconnue -> TexteBientot("Calendrier de l'année suivante pas encore publié.")
                    }
                    if (LocalCompact.current) return@Column  // compact : où on en est, sans la liste de l'année
                    val suivantes = d.periodes.filter { !it.dernierJour().isBefore(aujourdhui) }.drop(1)
                    if (suivantes.isNotEmpty()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        suivantes.forEach { p ->
                            Row(Modifier.fillMaxWidth()) {
                                Text(p.nom, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                Text(p.dates(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                    Text("${d.zone}, académie de ${d.academie}. Dernier jour de classe : la veille du premier jour indiqué.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

const val URL_PORTAIL_FAMILLES = "https://portail-familles.ville-cesson-sevigne.fr/"

private val LIENS_FAMILLES = listOf(
    "Démarches scolaires (inscriptions)" to "https://www.ville-cesson-sevigne.fr/demarches-scolaires/",
    "Accueil de loisirs et périscolaire (2-11 ans)" to "https://www.ville-cesson-sevigne.fr/accueil-de-loisirs-et-periscolaire-2-11ans/",
    "Stages pendant les vacances" to "https://www.ville-cesson-sevigne.fr/stages-vacances-scolaires/",
    "Relais petite enfance et modes de garde" to
        "https://www.ville-cesson-sevigne.fr/relais-petite-enfance-structures-collectives-assistants-maternels-et-garde-denfants-a-domicile/",
)

/** Portail familles (inscriptions, réservations, factures) et pages utiles de la ville. */
@Composable
fun CartePortailFamilles(modifier: Modifier = Modifier) {
    val uri = LocalUriHandler.current
    fun ouvrir(lien: String) { runCatching { uri.openUri(lien) } }
    CarteDonnee("Portail familles", Icons.Rounded.FamilyRestroom, modifier) {
        val compact = LocalCompact.current  // compact : le bouton du portail seulement
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (!compact) Text("Inscriptions à la cantine, au périscolaire et à l'accueil de loisirs, réservations et factures : " +
                "tout se fait sur le portail de la ville, avec votre compte.", style = MaterialTheme.typography.bodyMedium)
            Button(onClick = { ouvrir(URL_PORTAIL_FAMILLES) }) {
                Text("Ouvrir le portail familles")
                Spacer(Modifier.width(6.dp))
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
            }
            if (!compact) LIENS_FAMILLES.forEach { (nom, lien) ->
                Row(
                    Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "Ouvrir") { ouvrir(lien) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(nom, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
