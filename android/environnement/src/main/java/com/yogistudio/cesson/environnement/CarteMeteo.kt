package com.yogistudio.cesson.environnement

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Dehaze
import androidx.compose.material.icons.rounded.Grain
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Thunderstorm
import androidx.compose.material.icons.rounded.Umbrella
import androidx.compose.material.icons.rounded.WbCloudy
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yogistudio.cesson.core.donnees.EtatModule
import com.yogistudio.cesson.core.ui.CarteDonnee
import com.yogistudio.cesson.core.ui.FuseauCesson
import com.yogistudio.cesson.core.ui.StyleChiffres
import com.yogistudio.cesson.core.ui.TexteBientot
import com.yogistudio.cesson.core.ui.rememberMaintenant
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val FormatHeure = DateTimeFormatter.ofPattern("HH'h'", Locale.FRANCE)
private val FormatHeureMinute = DateTimeFormatter.ofPattern("H'h'mm", Locale.FRANCE)

@Composable
fun CarteMeteo(etat: EtatModule<DonneesMeteo>, modifier: Modifier = Modifier) {
    val maintenant = rememberMaintenant()
    when (etat) {
        EtatModule.Absent -> CarteDonnee("Météo à Cesson", Icons.Rounded.WbCloudy, modifier, maintenant = maintenant) {
            TexteBientot("Prévisions pas encore reçues. Tirez vers le bas pour actualiser.")
        }
        is EtatModule.Disponible -> {
            val d = etat.fichier.donnees
            val a = d.actuelle
            val aujourdhui = d.jours.firstOrNull { it.date == maintenant.atZone(FuseauCesson).toLocalDate() }
            val demain = d.jours.firstOrNull { it.date == maintenant.atZone(FuseauCesson).toLocalDate().plusDays(1) }
            CarteDonnee(
                titre = "Météo à Cesson",
                icone = icone(a.code, a.jour),
                modifier = modifier,
                fraicheur = etat.fraicheur,
                maintenant = maintenant,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.semantics(mergeDescendants = true) {
                            contentDescription = "Actuellement ${a.temperature.roundToInt()} degrés, ${a.libelle}, " +
                                "ressenti ${a.ressentie.roundToInt()} degrés"
                        },
                    ) {
                        Icon(icone(a.code, a.jour), contentDescription = null, modifier = Modifier.size(44.dp),
                            tint = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(12.dp))
                        Text(degres(a.temperature), style = StyleChiffres.copy(fontSize = 40.sp, fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(a.libelle, style = MaterialTheme.typography.bodyLarge)
                            Text("Ressenti ${degres(a.ressentie)}", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    aujourdhui?.let { LigneJour("Aujourd'hui", it) }
                    if (aujourdhui != null && aujourdhui.rafalesMaxKmh >= SEUIL_RAFALES_KMH) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Air, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Rafales jusqu'à ${aujourdhui.rafalesMaxKmh.roundToInt()} km/h",
                                style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    val creneaux = prochainsCreneaux(d.heures, maintenant)
                    if (creneaux.isNotEmpty()) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            creneaux.forEach { Creneau(it) }
                        }
                    }
                    demain?.let { LigneJour("Demain", it) }
                    aujourdhui?.let {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.WbTwilight, contentDescription = null, modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Soleil : ${FormatHeureMinute.format(it.lever.atZone(FuseauCesson))} – " +
                                    FormatHeureMinute.format(it.coucher.atZone(FuseauCesson)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LigneJour(nom: String, j: MeteoJour) {
    val pluie = if (j.precipitationMm >= 0.1) " · ${millimetres(j.precipitationMm)}" else ""
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = "$nom : minimum ${j.min.roundToInt()} degrés, maximum ${j.max.roundToInt()} degrés, " +
                j.libelle + if (j.precipitationMm >= 0.1) ", ${millimetres(j.precipitationMm)} de pluie" else ""
        },
    ) {
        Icon(icone(j.code, true), contentDescription = null, modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Text("$nom ", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
        Text("${degres(j.min)} / ${degres(j.max)}", style = StyleChiffres.copy(fontSize = 15.sp))
        Text(" · ${j.libelle}$pluie", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Creneau(h: MeteoHeure) {
    val heure = FormatHeure.format(h.horodatage.atZone(FuseauCesson))
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = "$heure : ${h.temperature.roundToInt()} degrés, ${h.libelle}" +
                if (h.precipitationMm >= 0.1) ", ${millimetres(h.precipitationMm)}" else ""
        },
    ) {
        Text(heure, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Icon(icone(h.code, h.jour), contentDescription = null, modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.secondary)
        Text(degres(h.temperature), style = StyleChiffres.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium))
        Text(
            if (h.precipitationMm >= 0.1) String.format(Locale.FRANCE, "%.1f", h.precipitationMm) else " ",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
}

/** Icône Material arrondie selon le code météo WMO et le jour ou la nuit. */
fun icone(code: Int, jour: Boolean): ImageVector = when (code) {
    0, 1 -> if (jour) Icons.Rounded.WbSunny else Icons.Rounded.NightsStay
    2 -> if (jour) Icons.Rounded.WbCloudy else Icons.Rounded.NightsStay
    3 -> Icons.Rounded.Cloud
    45, 48 -> Icons.Rounded.Dehaze
    51, 53, 55, 56, 57 -> Icons.Rounded.Grain
    61, 63, 65, 66, 67, 80, 81, 82 -> Icons.Rounded.Umbrella
    71, 73, 75, 77, 85, 86 -> Icons.Rounded.AcUnit
    95, 96, 99 -> Icons.Rounded.Thunderstorm
    else -> Icons.Rounded.WbCloudy
}
