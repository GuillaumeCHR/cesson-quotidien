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
import com.yogistudio.cesson.core.modele.JsonCesson
import com.yogistudio.cesson.core.ui.formaterHeure
import com.yogistudio.cesson.transport.AlerteBus
import com.yogistudio.cesson.transport.Decision
import com.yogistudio.cesson.transport.ErreurStar
import com.yogistudio.cesson.transport.decider
import com.yogistudio.cesson.transport.texteAlerte
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Alerte de passage d'un bus, suivie sur le téléphone : WorkManager réveille l'app, qui relit le temps réel
 * de STAR pour cet arrêt, suit le bus s'il prend du retard, et prévient quand il est à quelques minutes.
 * Aucun serveur ne sait qu'une alerte existe.
 */
object AlertesBus {
    private const val TRAVAIL = "alerte_bus"
    const val CANAL = "passages"

    suspend fun armer(context: Context, alerte: AlerteBus) {
        app(context).preferences.enregistrerAlerteBus(JsonCesson.encodeToString(AlerteBus.serializer(), alerte))
        planifier(context, Instant.now())
    }

    suspend fun annuler(context: Context) {
        app(context).preferences.enregistrerAlerteBus(null)
        WorkManager.getInstance(context).cancelUniqueWork(TRAVAIL)
    }

    suspend fun alerte(context: Context): AlerteBus? =
        app(context).preferences.alerteBus.first()?.let { runCatching { JsonCesson.decodeFromString(AlerteBus.serializer(), it) }.getOrNull() }

    internal fun planifier(context: Context, quand: Instant) {
        val delai = Duration.between(Instant.now(), quand).toMillis().coerceAtLeast(0)
        WorkManager.getInstance(context).enqueueUniqueWork(
            TRAVAIL, ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<TravailAlerteBus>().setInitialDelay(delai, TimeUnit.MILLISECONDS).build(),
        )
    }

    fun creerCanal(context: Context) {
        val canal = NotificationChannel(CANAL, "Alertes de passage", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Quelques minutes avant le passage du bus choisi à votre arrêt."
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(canal)
    }

    internal fun prevenir(context: Context, alerte: AlerteBus, minutes: Long, heure: Instant) {
        if (!RappelsCollecte.notificationsAutorisees(context)) return
        val ouvrir = PendingIntent.getActivity(
            context, 1, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CANAL)
            .setSmallIcon(R.drawable.ic_notif_bus)
            .setContentTitle(texteAlerte(alerte.ligne, alerte.destination, minutes))
            .setContentText("À ${alerte.arret}, passage à ${formaterHeure(heure, Instant.now())} (temps réel STAR).")
            .setContentIntent(ouvrir)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(2, notification)
        } catch (_: SecurityException) {
        }
    }

    private fun app(context: Context) = context.applicationContext as CessonApp
}

class TravailAlerteBus(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as CessonApp
        val alerte = AlertesBus.alerte(applicationContext) ?: return Result.success()
        val maintenant = Instant.now()
        val departs = try {
            app.star.departs(alerte.arret)
        } catch (_: ErreurStar) {
            // STAR injoignable : on réessaie dans une minute, tant que l'heure du bus n'est pas passée.
            if (maintenant.isAfter(Instant.ofEpochSecond(alerte.heureVisee).plusSeconds(120))) AlertesBus.annuler(applicationContext)
            else AlertesBus.planifier(applicationContext, maintenant.plusSeconds(60))
            return Result.success()
        }
        when (val d = decider(alerte, departs, maintenant)) {
            is Decision.Prevenir -> {
                AlertesBus.prevenir(applicationContext, alerte, d.minutes, d.depart.heure)
                app.preferences.enregistrerAlerteBus(null)
            }
            is Decision.Revoir -> {
                app.preferences.enregistrerAlerteBus(
                    JsonCesson.encodeToString(AlerteBus.serializer(), alerte.copy(heureVisee = d.heure.epochSecond)))
                AlertesBus.planifier(applicationContext, d.quand)
            }
            Decision.Abandonner -> app.preferences.enregistrerAlerteBus(null)
        }
        return Result.success()
    }
}
