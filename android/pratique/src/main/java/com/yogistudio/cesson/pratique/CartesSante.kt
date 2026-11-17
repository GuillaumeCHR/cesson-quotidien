package com.yogistudio.cesson.pratique

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Directions
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.LocalPharmacy
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yogistudio.cesson.core.ui.CarteDonnee
import com.yogistudio.cesson.core.ui.LocalCompact
import com.yogistudio.cesson.core.ui.rememberMaintenant
import androidx.compose.material3.HorizontalDivider
import com.yogistudio.cesson.core.donnees.EtatModule
import com.yogistudio.cesson.core.ui.Pastille
import com.yogistudio.cesson.core.ui.Ton
import com.yogistudio.cesson.equipements.Ouverture
import com.yogistudio.cesson.equipements.etiquetteOuverture
import java.net.URLEncoder
import java.time.Instant

// Garde de jour (dimanche, jours fériés) : la liste de l'Assurance maladie. La nuit, seulement le 3237.
const val URL_PHARMACIE_GARDE = "https://pharmacie-de-garde.ameli.fr/"
// Hôpitaux Privés Rennais – Cesson-Sévigné (Vivalto Santé), anciennement Hôpital privé Sévigné.
const val URL_URGENCES_HOPITAL_PRIVE = "https://www.vivalto-sante.fr/hopitaux-prives-rennais-cesson-sevigne/urgences"
const val ADRESSE_HOPITAL_PRIVE = "3 rue du Chêne Germain, 35510 Cesson-Sévigné"

@Composable
private fun BoutonAppel(texte: String, icone: ImageVector, principal: Boolean, onClick: () -> Unit) {
    val contenu: @Composable () -> Unit = {
        Icon(icone, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(texte)
    }
    if (principal) Button(onClick = onClick) { contenu() } else OutlinedButton(onClick = onClick) { contenu() }
}

/**
 * Pharmacies : la carte suit l'heure. En semaine, les officines sont ouvertes ; le dimanche et les jours fériés,
 * ameli publie la garde de jour ; la nuit, il ne publie rien (sécurité des pharmaciens), on renvoie au 3237.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CartePharmacieGarde(
    etat: EtatModule<DonneesPharmacies>,
    modifier: Modifier = Modifier,
    maintenant: Instant = rememberMaintenant(),
) {
    val uri = LocalUriHandler.current
    fun ouvrir(lien: String) { runCatching { uri.openUri(lien) } }
    val periode = periodePharmacie(maintenant)
    val titre = if (periode == PeriodePharmacie.Ouvertes) "Pharmacies" else "Pharmacie de garde"
    // L'heure de mise à jour n'a de sens que pour la liste des officines, montrée en journée.
    val fraicheur = (etat as? EtatModule.Disponible)?.fraicheur?.takeIf { periode == PeriodePharmacie.Ouvertes }
    CarteDonnee(titre, Icons.Rounded.LocalPharmacy, modifier, fraicheur, maintenant) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val compact = LocalCompact.current
            val note: @Composable (String) -> Unit = {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            when (periode) {
                PeriodePharmacie.Ouvertes -> {
                    val liste = (etat as? EtatModule.Disponible)?.fichier?.donnees?.pharmacies.orEmpty()
                    if (liste.isEmpty()) {
                        Text("Les pharmacies sont ouvertes en journée, du lundi au samedi, chacune avec ses horaires.",
                            style = MaterialTheme.typography.bodyMedium)
                        BoutonAppel("Pharmacies autour de moi", Icons.Rounded.Directions, principal = true) {
                            ouvrir("geo:0,0?q=${URLEncoder.encode("pharmacie, Cesson-Sévigné", "UTF-8")}")
                        }
                    } else {
                        ListePharmacies(pharmaciesParOuverture(liste, maintenant), maintenant, compact, ::ouvrir)
                    }
                    if (!compact) note("Horaires relevés dans OpenStreetMap : en cas de doute, appelez avant de vous déplacer. " +
                        "Le dimanche, les jours fériés et à partir de 20 h, cette carte affiche la pharmacie de garde.")
                }
                PeriodePharmacie.GardeJour -> {
                    Text("Garde de jour, de 8 h à 20 h : la liste est tenue à jour en temps réel par l'Assurance maladie.",
                        style = MaterialTheme.typography.bodyMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { ouvrir(URL_PHARMACIE_GARDE) }) {
                            Text("Trouver la pharmacie de garde")
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        BoutonAppel("3237", Icons.Rounded.Call, principal = false) { ouvrir("tel:3237") }
                    }
                    if (!compact) note("Recherche gratuite sur ameli.fr (un captcha est demandé) ; l'appel au 3237 est payant (0,35 € la minute).")
                }
                PeriodePharmacie.GardeNuit -> {
                    Text("Garde de nuit, de 20 h à 8 h : pour la sécurité des pharmaciens, leurs coordonnées ne sont pas publiées en ligne.",
                        style = MaterialTheme.typography.bodyMedium)
                    BoutonAppel("Appeler le 3237", Icons.Rounded.Call, principal = true) { ouvrir("tel:3237") }
                    if (!compact) note("Appel payant (0,35 € la minute). Téléphonez avant de vous déplacer et venez avec votre ordonnance. " +
                        "Urgence médicale : le 15.")
                }
            }
        }
    }
}

/** Urgences des Hôpitaux Privés Rennais à Cesson : ouvertes jour et nuit, mais le 15 d'abord le soir et la nuit. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CarteUrgences(modifier: Modifier = Modifier) {
    val uri = LocalUriHandler.current
    fun ouvrir(lien: String) { runCatching { uri.openUri(lien) } }
    CarteDonnee("Urgences de l'hôpital privé", Icons.Rounded.LocalHospital, modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val compact = LocalCompact.current
            Text("Ouvertes 24 h/24, 7 j/7 · $ADRESSE_HOPITAL_PRIVE", style = MaterialTheme.typography.bodyMedium)
            Text("De 18 h 30 à 8 h, appelez le 15 avant de vous déplacer : c'est obligatoire dans la métropole rennaise.",
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BoutonAppel("Appeler le 15", Icons.Rounded.Call, principal = true) { ouvrir("tel:15") }
                BoutonAppel("Itinéraire", Icons.Rounded.Directions, principal = false) {
                    ouvrir("geo:0,0?q=${URLEncoder.encode("Hôpital privé, $ADRESSE_HOPITAL_PRIVE", "UTF-8")}")
                }
            }
            if (!compact) {
                Text("En journée, appelez d'abord votre médecin traitant ; s'il n'est pas joignable, le 116 117 (médecin de garde). " +
                    "On entre par l'accueil principal ; un dépose-minute est prévu à côté.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { ouvrir("tel:116117") }) { Text("116 117") }
                    TextButton(onClick = { ouvrir("tel:0299255050") }) { Text("Standard 02 99 25 50 50") }
                    TextButton(onClick = { ouvrir(URL_URGENCES_HOPITAL_PRIVE) }) { Text("Site de l'hôpital") }
                }
            }
        }
    }
}

/** Officines de Cesson, ouvertes d'abord. Appui sur une ligne : itinéraire ; le numéro s'appelle directement. */
@Composable
private fun ListePharmacies(
    liste: List<Pair<Pharmacie, Ouverture>>,
    maintenant: Instant,
    compact: Boolean,
    ouvrir: (String) -> Unit,
) {
    val itineraire = ouvrirItineraire()
    val ouvertes = liste.count { it.second is Ouverture.Ouvert }
    Text(when (ouvertes) {
        0 -> "Aucune pharmacie de Cesson n'est ouverte en ce moment."
        1 -> "1 pharmacie de Cesson ouverte en ce moment."
        else -> "$ouvertes pharmacies de Cesson ouvertes en ce moment."
    }, style = MaterialTheme.typography.bodyMedium)
    Column {
        (if (compact) liste.take(3) else liste).forEachIndexed { i, (p, o) ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            val (pastille, precision) = etiquetteOuverture(o, maintenant)
            LigneLieu(p.lieu, null, itineraire) {
                Pastille(pastille, when (o) { is Ouverture.Ouvert -> Ton.OUVERT; is Ouverture.Ferme -> Ton.FERME; else -> Ton.NEUTRE })
                precision?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!compact) p.telephone?.let { tel ->
                    TextButton(onClick = { ouvrir("tel:${tel.replace(" ", "")}") }) {
                        Icon(Icons.Rounded.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(tel)
                    }
                }
            }
        }
    }
}
