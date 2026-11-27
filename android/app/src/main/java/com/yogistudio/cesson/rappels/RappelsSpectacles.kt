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
import com.yogistudio.cesson.actus.PREFIXE_FAVORI_MATCH
import com.yogistudio.cesson.actus.RappelSpectacle
import com.yogistudio.cesson.actus.agenda
import com.yogistudio.cesson.actus.cleFavori
import com.yogistudio.cesson.actus.handball
import com.yogistudio.cesson.actus.rappelsMatchs
import com.yogistudio.cesson.actus.rappelsSpectacles
import com.yogistudio.cesson.core.donnees.EtatModule
import com.yogistudio.cesson.core.ui.FuseauCesson
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * Spectacles favoris : rappel la veille à 18h, et alerte si la Ville l'annonce annulé, reporté ou déplacé. Matchs de
 * handball favoris (même liste, préfixe « handball: ») : rappel la veille à 18h. Contrôle à 9h, 13h et 18h (à quelques
 * minutes près), sur le téléphone ; les favoris ne quittent jamais le téléphone.
 */
object RappelsSpectacles {
    private const val TRAVAIL = "rappels_spectacles"
    const val CANAL = "spectacles"
    private val CONTROLES = listOf(LocalTime.of(9, 0), LocalTime.of(13, 0), LocalTime.of(18, 0))

    suspend fun basculer(context: Context, id: String) {
        app(context).preferences.basculerSpectacleFavori(id)
        planifier(context)
        verifier(context)
    }

    fun creerCanal(context: Context) {
        val canal = NotificationChannel(CANAL, "Spectacles et matchs favoris", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "La veille d'un spectacle ou d'un match mis en favori, et si un spectacle est annulé ou reporté."
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(canal)
    }

    suspend fun planifier(context: Context, maintenant: Instant = Instant.now()) {
        val wm = WorkManager.getInstance(context)
        val p = app(context).preferences
        val favoris = p.spectaclesFavoris.first()
        // On ne se réveille que pour les favoris dont les rappels sont allumés.
        val (matchs, spectacles) = favoris.partition { it.startsWith(PREFIXE_FAVORI_MATCH) }
        val utile = (spectacles.isNotEmpty() && p.rappelsSpectaclesActifs.first()) || (matchs.isNotEmpty() && p.rappelsMatchsActifs.first())
        if (!utile) {
            wm.cancelUniqueWork(TRAVAIL)
            return
        }
        val ici = maintenant.atZone(FuseauCesson)
        val prochain = (0L..1L).flatMap { j -> CONTROLES.map { ici.toLocalDate().plusDays(j).atTime(it).atZone(FuseauCesson) } }
            .first { it.isAfter(ici) }
        wm.enqueueUniqueWork(
            TRAVAIL, ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<TravailRappelsSpectacles>()
                .setInitialDelay(Duration.between(ici, prochain).toMillis(), TimeUnit.MILLISECONDS).build(),
        )
    }

    suspend fun verifier(context: Context, maintenant: Instant = Instant.now()) {
        val app = app(context)
        val p = app.preferences
        val favoris = p.spectaclesFavoris.first()
        if (favoris.isEmpty()) return
        val ici = maintenant.atZone(FuseauCesson)
        val deja = p.rappelsSpectaclesEnvoyes.first()
        val rappels = mutableListOf<RappelSpectacle>()
        val (matchs, spectacles) = favoris.partition { it.startsWith(PREFIXE_FAVORI_MATCH) }
        var gardes = favoris
        // Favoris passés ou retirés de leur source : on les oublie (seulement avec une source à jour).
        (app.depot.agenda().first() as? EtatModule.Disponible)?.let { etat ->
            val d = etat.fichier.donnees
            if (!etat.fraicheur.estPerimee(maintenant)) {
                val encore = d.evenements.filter { !it.au.isBefore(ici.toLocalDate()) }.map { it.id }.toSet()
                gardes = gardes - spectacles.filter { it !in encore }.toSet()
            }
            if (p.rappelsSpectaclesActifs.first()) rappels += rappelsSpectacles(d, spectacles.toSet(), ici, deja)
        }
        (app.depot.handball().first() as? EtatModule.Disponible)?.let { etat ->
            val d = etat.fichier.donnees
            if (!etat.fraicheur.estPerimee(maintenant)) {
                val encore = d.aVenir.map { it.cleFavori() }.toSet()
                gardes = gardes - matchs.filter { it !in encore }.toSet()
            }
            if (p.rappelsMatchsActifs.first()) rappels += rappelsMatchs(d, matchs.toSet(), ici, deja)
        }
        if (gardes != favoris) p.enregistrerSpectaclesFavoris(gardes)
        if (rappels.isEmpty() || !RappelsCollecte.notificationsAutorisees(context)) return
        val envoyes = rappels.filter { envoyer(context, it) }.map { it.cle }
        val ids = p.spectaclesFavoris.first()
        p.enregistrerRappelsSpectaclesEnvoyes((deja + envoyes).filter { it.substringBefore("|") in ids }.toSet())
    }

    private fun envoyer(context: Context, r: RappelSpectacle): Boolean {
        val ouvrir = PendingIntent.getActivity(
            context, 4, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CANAL)
            .setSmallIcon(R.drawable.ic_notif_spectacle)
            .setContentTitle(r.titre)
            .setContentText(r.texte)
            .setStyle(NotificationCompat.BigTextStyle().bigText(r.texte))
            .setContentIntent(ouvrir)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .build()
        return try {
            NotificationManagerCompat.from(context).notify(r.cle.hashCode(), notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    private fun app(context: Context) = context.applicationContext as CessonApp
}

class TravailRappelsSpectacles(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as CessonApp
        app.depot.chargerCache()
        runCatching { app.depot.rafraichir() }
        RappelsSpectacles.verifier(applicationContext)
        RappelsSpectacles.planifier(applicationContext)
        return Result.success()
    }
}
