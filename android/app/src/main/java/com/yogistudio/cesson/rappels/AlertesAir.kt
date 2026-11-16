package com.yogistudio.cesson.rappels

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.yogistudio.cesson.CessonApp
import com.yogistudio.cesson.MainActivity
import com.yogistudio.cesson.R
import com.yogistudio.cesson.core.donnees.EtatModule
import com.yogistudio.cesson.core.ui.FuseauCesson
import com.yogistudio.cesson.environnement.CONTROLES_AIR
import com.yogistudio.cesson.environnement.CONTROLES_VIGILANCE
import com.yogistudio.cesson.environnement.CouleurVigilance
import com.yogistudio.cesson.environnement.NotificationAir
import com.yogistudio.cesson.environnement.notificationsVigilance
import com.yogistudio.cesson.environnement.vigilanceMeteo
import com.yogistudio.cesson.environnement.clesAirAGarder
import com.yogistudio.cesson.environnement.episodesPollution
import com.yogistudio.cesson.environnement.notificationsEpisodes
import com.yogistudio.cesson.environnement.notificationsPollens
import com.yogistudio.cesson.environnement.pollens
import com.yogistudio.cesson.environnement.prochainControleAir
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Alertes pollens, épisodes de pollution et vigilance météo, décidées sur le téléphone (voir environnement/NotificationsAir.kt) :
 * aucun serveur de notification, aucun identifiant d'appareil. WorkManager réveille l'app aux heures de contrôle
 * (7h30, 11h30, 16h30, 18h30, et chaque heure de 7h35 à 22h35 si l'alerte de vigilance est active, à quelques
 * minutes près) ; chaque passage planifie le suivant.
 */
object AlertesAir {
    private const val TRAVAIL = "alertes_air"
    const val CANAL = "air"
    const val CANAL_VIGILANCE = "vigilance_meteo"

    suspend fun planifier(context: Context) {
        val app = context.applicationContext as CessonApp
        val wm = WorkManager.getInstance(context)
        val p = app.preferences
        if (!p.alertePollensActive.first() && !p.alerteEpisodesActive.first() && !p.alerteVigilanceActive.first()) {
            wm.cancelUniqueWork(TRAVAIL)
            return
        }
        val maintenant = Instant.now()
        val creneaux = if (p.alerteVigilanceActive.first()) CONTROLES_AIR + CONTROLES_VIGILANCE else CONTROLES_AIR
        val delai = Duration.between(maintenant, prochainControleAir(maintenant, creneaux))
        wm.enqueueUniqueWork(
            TRAVAIL, ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<TravailAlertesAir>().setInitialDelay(delai.toMillis(), TimeUnit.MILLISECONDS).build(),
        )
    }

    fun creerCanal(context: Context) {
        val canal = NotificationChannel(CANAL, "Pollens et pollution", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Indice pollen élevé et épisodes de pollution à Cesson, aujourd'hui ou demain."
        }
        val vigilance = NotificationChannel(CANAL_VIGILANCE, "Vigilance météo", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Vigilance jaune, orange ou rouge de Météo-France en Ille-et-Vilaine, aujourd'hui ou demain."
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannels(listOf(canal, vigilance))
    }

    /** Vérifie tout de suite, avec les données en cache (à l'activation d'une alerte, par exemple). */
    suspend fun verifier(context: Context, maintenant: Instant = Instant.now()) {
        val app = context.applicationContext as CessonApp
        val p = app.preferences
        val aujourdhui = maintenant.atZone(FuseauCesson).toLocalDate()
        val deja = p.alertesAirEnvoyees.first()
        val aEnvoyer = buildList {
            if (p.alertePollensActive.first()) {
                (app.depot.pollens().first() as? EtatModule.Disponible)?.takeIf { !it.fraicheur.estPerimee(maintenant) }?.let {
                    addAll(notificationsPollens(it.fichier.donnees, aujourdhui, p.seuilPollens.first(), deja))
                }
            }
            if (p.alerteEpisodesActive.first()) {
                (app.depot.episodesPollution().first() as? EtatModule.Disponible)?.takeIf { !it.fraicheur.estPerimee(maintenant) }?.let {
                    addAll(notificationsEpisodes(it.fichier.donnees, aujourdhui, deja))
                }
            }
            if (p.alerteVigilanceActive.first()) {
                val seuil = runCatching { CouleurVigilance.valueOf(p.seuilVigilance.first()) }.getOrDefault(CouleurVigilance.JAUNE)
                (app.depot.vigilanceMeteo().first() as? EtatModule.Disponible)?.takeIf { !it.fraicheur.estPerimee(maintenant) }?.let {
                    addAll(notificationsVigilance(it.fichier.donnees, maintenant, seuil, deja))
                }
            }
        }
        if (aEnvoyer.isEmpty() || !RappelsCollecte.notificationsAutorisees(context)) return
        val envoyees = aEnvoyer.filter { envoyer(context, it) }.map { it.cle }
        p.enregistrerAlertesAirEnvoyees(clesAirAGarder(deja + envoyees, aujourdhui))
    }

    private fun envoyer(context: Context, n: NotificationAir): Boolean {
        val ouvrir = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val vigilance = n.cle.startsWith("vigilance:")
        val notification = NotificationCompat.Builder(context, if (vigilance) CANAL_VIGILANCE else CANAL)
            .setSmallIcon(R.drawable.ic_notif_air)
            .setContentTitle(n.titre)
            .setContentText(n.texte)
            .setStyle(NotificationCompat.BigTextStyle().bigText(n.texte))
            .setContentIntent(ouvrir)
            .setAutoCancel(true)
            .setCategory(if (vigilance) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_RECOMMENDATION)
            .build()
        return try {
            NotificationManagerCompat.from(context).notify(n.cle.hashCode(), notification)
            true
        } catch (_: SecurityException) {
            false // permission retirée entre la vérification et l'envoi
        }
    }
}

class TravailAlertesAir(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as CessonApp
        app.depot.chargerCache()
        runCatching { app.depot.rafraichir() } // hors connexion : rien de neuf, rien d'envoyé (données périmées ignorées)
        AlertesAir.verifier(applicationContext)
        AlertesAir.planifier(applicationContext)
        return Result.success()
    }
}
