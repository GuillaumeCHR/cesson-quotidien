package com.yogistudio.cesson.widget

import com.yogistudio.cesson.core.ui.FuseauCesson
import com.yogistudio.cesson.core.ui.formaterHeure
import com.yogistudio.cesson.eau.DonneesVigicrues
import com.yogistudio.cesson.eau.DonneesVilaine
import com.yogistudio.cesson.eau.estUneAlerte
import com.yogistudio.cesson.eau.formaterHauteur
import com.yogistudio.cesson.eau.libelleCourt
import com.yogistudio.cesson.eau.libelleTendance
import com.yogistudio.cesson.environnement.DonneesMeteo
import com.yogistudio.cesson.pratique.DonneesCollecte
import com.yogistudio.cesson.pratique.formaterJourCollecte
import com.yogistudio.cesson.pratique.prochaines
import com.yogistudio.cesson.transport.Depart
import com.yogistudio.cesson.transport.DonneesTrains
import com.yogistudio.cesson.transport.StatutTrain
import com.yogistudio.cesson.transport.departsDeLaDirection
import com.yogistudio.cesson.transport.departsUtiles
import com.yogistudio.cesson.transport.regrouper
import java.time.Instant
import kotlin.math.roundToInt

/** Une ligne du widget : « Collecte » · « Bac gris demain ». [alerte] la met en évidence. */
data class LigneWidget(val etiquette: String, val texte: String, val alerte: Boolean = false)

/**
 * Contenu du widget « Aujourd'hui à Cesson », calculé à partir des données déjà en cache (et des départs STAR
 * de l'arrêt suivi). Les heures sont des heures d'horloge (« 8h12 ») : le widget n'est rafraîchi que toutes
 * les 30 min, un « dans 3 min » y serait vite faux.
 */
data class ResumeWidget(val titre: String, val lignes: List<LigneWidget>, val misAJour: String)

private fun heure(i: Instant, maintenant: Instant) = formaterHeure(i, maintenant)

fun resumer(
    maintenant: Instant,
    meteo: DonneesMeteo?,
    vigicrues: DonneesVigicrues?,
    vilaine: DonneesVilaine?,
    collecte: DonneesCollecte?,
    arret: String,
    departs: List<Depart>?,
    trains: DonneesTrains?,
): ResumeWidget {
    val lignes = mutableListOf<LigneWidget>()
    // Une vigilance crues passe avant tout le reste.
    vigicrues?.takeIf { it.vigilance.estUneAlerte }?.let {
        lignes += LigneWidget("Crues", "${it.vigilance.libelleCourt()} · ${it.troncon.nom}", alerte = true)
    }
    meteo?.actuelle?.let { lignes += LigneWidget("Météo", "${it.temperature.roundToInt()}° · ${it.libelle}") }
    collecte?.let { c ->
        prochaines(c, maintenant).mapNotNull { p -> p.passage?.let { p.flux to it } }.minByOrNull { it.second.date }?.let { (flux, passage) ->
            val demain = passage.date == maintenant.atZone(FuseauCesson).toLocalDate().plusDays(1)
            lignes += LigneWidget("Collecte", "Bac ${flux.bac} ${formaterJourCollecte(passage.date, maintenant)}" +
                if (demain) " · à sortir ce soir" else "", alerte = demain)
        }
    }
    departs?.let { d ->
        regrouper(departsUtiles(d, maintenant), parDirection = 2).take(2).forEach { dir ->
            lignes += LigneWidget(if (dir.mode.name == "METRO") "Métro" else arret,
                "${dir.ligne} → ${dir.destination} · ${dir.departs.joinToString(", ") { heure(it.heure, maintenant) }}")
        }
    }
    trains?.let { t ->
        t.directions.forEach { dir ->
            departsDeLaDirection(t, dir.id, maintenant).firstOrNull()?.let { tr ->
                val etat = when (tr.statut) {
                    StatutTrain.RETARD -> " (+${tr.retardMin} min)"
                    StatutTrain.SUPPRIME -> " (supprimé)"
                    else -> ""
                }
                lignes += LigneWidget("Train", "${dir.libelle.removePrefix("Vers ")} · ${heure(tr.heureTheorique, maintenant)}$etat",
                    alerte = tr.statut == StatutTrain.RETARD || tr.statut == StatutTrain.SUPPRIME)
            }
        }
    }
    vilaine?.let { lignes += LigneWidget("Vilaine", "${formaterHauteur(it.mesure)} · ${libelleTendance(it.mesure.tendance)}") }
    return ResumeWidget("Aujourd'hui à Cesson", lignes, "Mis à jour à ${heure(maintenant, maintenant)}")
}
