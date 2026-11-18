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
import com.yogistudio.cesson.pratique.estFerie
import com.yogistudio.cesson.transport.AlerteTrain
import com.yogistudio.cesson.transport.DEBUT_SURVEILLANCE
import com.yogistudio.cesson.transport.PAS_SURVEILLANCE
import com.yogistudio.cesson.transport.TrainFavori
import com.yogistudio.cesson.transport.alerteTrain
import com.yogistudio.cesson.transport.ecrireTrainsFavoris
import com.yogistudio.cesson.transport.estLe
import com.yogistudio.cesson.transport.lireTrainsFavoris
import com.yogistudio.cesson.transport.prochainDepart
import com.yogistudio.cesson.transport.trains
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Trains favoris (« le 7h08 vers Rennes ») : WorkManager réveille l'app une heure avant le départ prévu, puis toutes
 * les 5 min jusqu'au départ ; l'app relit les trains publiés par le serveur (temps réel SNCF) et prévient seulement
 * en cas de suppression ou de retard de 5 min ou plus. Aucun serveur ne sait quel train vous prenez.
 */
object AlertesTrains {
    private const val TRAVAIL = "alertes_trains"
    const val CANAL = "trains"

    suspend fun favoris(context: Context): List<TrainFavori> = lireTrainsFavoris(app(context).preferences.trainsFavoris.first())

    suspend fun enregistrer(context: Context, favoris: List<TrainFavori>) {
        app(context).preferences.enregistrerTrainsFavoris(ecrireTrainsFavoris(favoris.distinctBy { it.heure to it.direction }))
        planifier(context)
        verifier(context)
    }

    fun creerCanal(context: Context) {
        val canal = NotificationChannel(CANAL, "Trains favoris", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Suppression ou retard de 5 min et plus de vos trains habituels à la halte de Cesson."
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(canal)
    }

    /** Prochain réveil : une heure avant le prochain départ surveillé, ou dans 5 min si on est déjà dans l'heure. */
    suspend fun planifier(context: Context, maintenant: Instant = Instant.now()) {
        val wm = WorkManager.getInstance(context)
        val favoris = favoris(context)
        val prochain = favoris.mapNotNull { it.prochainDepart(maintenant, horaires(context), ::estFerie) }.minOrNull()
        if (prochain == null) {
            wm.cancelUniqueWork(TRAVAIL)
            return
        }
        val debut = prochain.toInstant().minus(DEBUT_SURVEILLANCE)
        val quand = if (maintenant.isBefore(debut)) debut else minOf(maintenant.plus(PAS_SURVEILLANCE), prochain.toInstant())
        wm.enqueueUniqueWork(
            TRAVAIL, ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<TravailAlertesTrains>()
                .setInitialDelay(Duration.between(maintenant, quand).toMillis().coerceAtLeast(0), TimeUnit.MILLISECONDS).build(),
        )
    }

    /** Compare les trains favoris du jour (dans l'heure qui précède leur départ) aux trains publiés. */
    suspend fun verifier(context: Context, maintenant: Instant = Instant.now()) {
        val app = app(context)
        val favoris = favoris(context)
        if (favoris.isEmpty()) return
        val donnees = (app.depot.trains().first() as? EtatModule.Disponible)
            ?.takeIf { !it.fraicheur.estPerimee(maintenant) }?.fichier?.donnees ?: return // info ancienne : on se tait
        val aujourdhui = maintenant.atZone(FuseauCesson).toLocalDate()
        val deja = app.preferences.alertesTrainsEnvoyees.first()
        val alertes = favoris.mapNotNull { f ->
            val depart = f.prochainDepart(maintenant.minusSeconds(120), donnees.horaires, ::estFerie)
            if (depart == null || depart.toLocalDate() != aujourdhui || maintenant.isBefore(depart.toInstant().minus(DEBUT_SURVEILLANCE))) {
                return@mapNotNull null
            }
            donnees.departs.firstOrNull { f.estLe(it) && it.heureTheorique.atZone(FuseauCesson).toLocalDate() == aujourdhui }
                ?.let { alerteTrain(f, aujourdhui, it, deja) }
        }
        if (alertes.isEmpty() || !RappelsCollecte.notificationsAutorisees(context)) return
        val envoyees = alertes.filter { envoyer(context, it) }.map { it.cle }
        // On garde les clés des trois derniers jours.
        val limite = aujourdhui.minusDays(3)
        app.preferences.enregistrerAlertesTrainsEnvoyees((deja + envoyees).filter {
            runCatching { !LocalDate.parse(it.substringBefore("|")).isBefore(limite) }.getOrDefault(false)
        }.toSet())
    }

    private suspend fun horaires(context: Context) =
        (app(context).depot.trains().first() as? EtatModule.Disponible)?.fichier?.donnees?.horaires.orEmpty()

    private fun envoyer(context: Context, a: AlerteTrain): Boolean {
        val ouvrir = PendingIntent.getActivity(
            context, 3, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CANAL)
            .setSmallIcon(R.drawable.ic_notif_train)
            .setContentTitle(a.titre)
            .setContentText(a.texte)
            .setStyle(NotificationCompat.BigTextStyle().bigText(a.texte))
            .setContentIntent(ouvrir)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
        return try {
            // Une seule notification par train et par jour : la nouvelle (retard aggravé, suppression) remplace l'ancienne.
            NotificationManagerCompat.from(context).notify(a.cle.substringBeforeLast("|").hashCode(), notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    private fun app(context: Context) = context.applicationContext as CessonApp
}

class TravailAlertesTrains(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as CessonApp
        app.depot.chargerCache()
        runCatching { app.depot.rafraichir() } // hors connexion : données anciennes, donc aucune alerte
        AlertesTrains.verifier(applicationContext)
        AlertesTrains.planifier(applicationContext)
        return Result.success()
    }
}
