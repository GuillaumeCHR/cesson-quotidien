package com.yogistudio.cesson.transport

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.yogistudio.cesson.core.donnees.EtatModule
import com.yogistudio.cesson.core.ui.CarteDonnee
import com.yogistudio.cesson.core.ui.Pastille
import com.yogistudio.cesson.core.ui.TexteBientot
import com.yogistudio.cesson.core.ui.Ton
import com.yogistudio.cesson.core.ui.rememberMaintenant

private fun ton(e: EtatTrafic) = when (e) {
    EtatTrafic.FLUIDE -> Ton.OUVERT
    EtatTrafic.DENSE -> Ton.ALERTE
    EtatTrafic.SATURE, EtatTrafic.BLOQUE -> Ton.FERME
    EtatTrafic.INCONNU -> Ton.NEUTRE
}

/**
 * Circulation : rocade est de Rennes (le long de Cesson), dans les deux sens, avec les ralentissements situés entre
 * deux portes ; puis les axes de Cesson qui ne sont pas fluides. Données de Rennes Métropole, relues toutes les 3 min.
 */
@Composable
fun CarteCirculation(etat: EtatModule<DonneesCirculation>, modifier: Modifier = Modifier) {
    val maintenant = rememberMaintenant()
    when (etat) {
        EtatModule.Absent -> CarteDonnee("Circulation", Icons.Rounded.DirectionsCar, modifier, maintenant = maintenant) {
            TexteBientot("Conditions de circulation pas encore reçues. Tirez vers le bas pour actualiser.")
        }
        is EtatModule.Disponible -> {
            val d = etat.fichier.donnees
            CarteDonnee("Circulation", Icons.Rounded.DirectionsCar, modifier, etat.fraicheur, maintenant) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Rocade est de Rennes", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                    d.rocadeEst.forEach { s -> LigneSens(s) }
                    val axes = axesASignaler(d)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text("Dans Cesson", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                    if (axes.isEmpty()) {
                        Text("Pas de ralentissement mesuré sur les grands axes.", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        axes.take(5).forEach { a ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
                                    contentDescription = "${a.nom} : ${a.etat.libelle()}" + (a.vitesse?.let { ", $it kilomètres heure" } ?: "")
                                },
                            ) {
                                Text(a.nom, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                a.vitesse?.let {
                                    Text("$it km/h", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.width(8.dp))
                                }
                                Pastille(a.etat.libelle(), ton(a.etat))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LigneSens(s: SensRocade) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
                contentDescription = "${s.libelle} : ${s.etat.libelle()}" + (s.vitesse?.let { ", $it kilomètres heure en moyenne" } ?: "")
            },
        ) {
            Text(s.libelle, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            s.vitesse?.let {
                Text("$it km/h", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
            }
            Pastille(s.etat.libelle(), ton(s.etat))
        }
        s.ralentissements.forEach { r ->
            Text(
                "${r.etat.libelle()} entre ${r.entre}" + (r.vitesse?.let { " · $it km/h" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = if (r.etat == EtatTrafic.DENSE) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}
