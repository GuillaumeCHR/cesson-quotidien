package com.yogistudio.cesson.rappels

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
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
import com.yogistudio.cesson.pratique.DonneesCollecte
import com.yogistudio.cesson.pratique.bacsARappeler
import com.yogistudio.cesson.pratique.collecte
import com.yogistudio.cesson.pratique.prochainRappel
import com.yogistudio.cesson.pratique.texteRappel
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Rappel de collecte la veille à 17h30, calculé sur le téléphone à partir du calendrier en cache :
 * aucun serveur ne sait qui reçoit quoi. WorkManager réveille l'app à l'heure prévue (à quelques
 * minutes près, selon la mise en veille d'Android) ; chaque passage planifie le suivant.
 */
object RappelsCollecte {
    private const val TRAVAIL = "rappel_collecte"
    const val CANAL = "collecte"

    /** Sans calendrier en cache, on revérifie dans 12 h. */
    private val SANS_DONNEES: Duration = Duration.ofHours(12)

    suspend fun planifier(context: Context) {
        val app = context.applicationContext as CessonApp
        val wm = WorkManager.getInstance(context)
        if (!app.preferences.rappelCollecteActif.first()) {
            wm.cancelUniqueWork(TRAVAIL)
            return
        }
        val maintenant = Instant.now()
        val d = (app.depot.collecte().first() as? EtatModule.Disponible)?.fichier?.donnees
        val delai = when {
            d == null -> SANS_DONNEES
            // Le rappel de ce soir n'est pas encore parti : tout de suite.
            bacsARappeler(d, maintenant).isNotEmpty() && !dejaRappeleCeSoir(app, maintenant) -> Duration.ZERO
            else -> prochainRappel(d, maintenant)?.let { Duration.between(maintenant, it) } ?: SANS_DONNEES
        }
        wm.enqueueUniqueWork(
            TRAVAIL, ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<TravailRappel>().setInitialDelay(delai.toMillis(), TimeUnit.MILLISECONDS).build(),
        )
    }

    private suspend fun dejaRappeleCeSoir(app: CessonApp, maintenant: Instant): Boolean =
        app.preferences.dernierRappelCollecte.first() == maintenant.atZone(FuseauCesson).toLocalDate().toString()

    fun creerCanal(context: Context) {
        val canal = NotificationChannel(CANAL, "Rappels de collecte", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "La veille au soir, le bac à sortir pour la collecte du lendemain."
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(canal)
    }

    fun notificationsAutorisees(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    internal suspend fun envoyerSiBesoin(context: Context, d: DonneesCollecte, maintenant: Instant) {
        val app = context.applicationContext as CessonApp
        val bacs = bacsARappeler(d, maintenant)
        if (bacs.isEmpty() || dejaRappeleCeSoir(app, maintenant) || !notificationsAutorisees(context)) return
        val (titre, texte) = texteRappel(bacs)
        val ouvrir = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CANAL)
            .setSmallIcon(R.drawable.ic_notif_collecte)
            .setContentTitle(titre)
            .setContentText(texte)
            .setContentIntent(ouvrir)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(1, notification)
            app.preferences.noterRappelCollecte(maintenant.atZone(FuseauCesson).toLocalDate().toString())
        } catch (_: SecurityException) {
            // Permission retirée entre la vérification et l'envoi : rien à faire.
        }
    }
}

class TravailRappel(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as CessonApp
        app.depot.chargerCache()
        runCatching { app.depot.rafraichir() } // hors connexion : le calendrier en cache suffit
        val d = (app.depot.collecte().first() as? EtatModule.Disponible)?.fichier?.donnees
        if (d != null && app.preferences.rappelCollecteActif.first()) {
            RappelsCollecte.envoyerSiBesoin(applicationContext, d, Instant.now())
        }
        RappelsCollecte.planifier(applicationContext)
        return Result.success()
    }
}
