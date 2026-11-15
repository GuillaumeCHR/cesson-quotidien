package com.yogistudio.cesson.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.yogistudio.cesson.CessonApp
import com.yogistudio.cesson.MainActivity
import com.yogistudio.cesson.core.donnees.EtatModule
import com.yogistudio.cesson.core.ui.CouleursClaires
import com.yogistudio.cesson.core.ui.CouleursSombres
import com.yogistudio.cesson.eau.vigicrues
import com.yogistudio.cesson.eau.vilaine
import com.yogistudio.cesson.environnement.meteo
import com.yogistudio.cesson.pratique.collecte
import com.yogistudio.cesson.transport.POLE_VIASILVA
import com.yogistudio.cesson.transport.trains
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant

private val Couleurs = ColorProviders(light = CouleursClaires, dark = CouleursSombres)

/**
 * Widget « Aujourd'hui à Cesson » : météo, vigilance crues, collecte, prochains bus et trains, Vilaine.
 * Android le rafraîchit toutes les 30 min (et l'app à chaque actualisation) ; un appui ouvre l'app.
 * Comme l'app, il ne lit que des données publiques et n'envoie rien.
 */
class WidgetCesson : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val resume = charger(context)
        provideContent { GlanceTheme(colors = Couleurs) { Contenu(resume) } }
    }

    private suspend fun charger(context: Context): ResumeWidget {
        val app = context.applicationContext as CessonApp
        app.depot.chargerCache()
        withTimeoutOrNull(10_000) { runCatching { app.depot.rafraichir() } } // hors connexion : le cache suffit
        suspend fun <T> donnees(f: Flow<EtatModule<T>>): T? = (f.first() as? EtatModule.Disponible)?.fichier?.donnees
        val arret = app.preferences.arretSuivi.first() ?: POLE_VIASILVA
        val departs = withTimeoutOrNull(8_000) { runCatching { app.star.departs(arret) }.getOrNull() }
        return resumer(
            Instant.now(), donnees(app.depot.meteo()), donnees(app.depot.vigicrues()), donnees(app.depot.vilaine()),
            donnees(app.depot.collecte()), arret, departs, donnees(app.depot.trains()),
        )
    }
}

@Composable
private fun Contenu(r: ResumeWidget) {
    // Petit widget : l'essentiel seulement (alertes d'abord, l'ordre de resumer() s'en charge).
    val place = ((LocalSize.current.height.value - 48f) / 22f).toInt().coerceIn(2, 8)
    Column(
        modifier = GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(20.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp).clickable(actionStartActivity<MainActivity>()),
    ) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(r.titre, style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp, color = GlanceTheme.colors.primary),
                modifier = GlanceModifier.defaultWeight())
            Text(r.misAJour, style = TextStyle(fontSize = 11.sp, color = GlanceTheme.colors.onSurfaceVariant))
        }
        Spacer(GlanceModifier.height(6.dp))
        if (r.lignes.isEmpty()) {
            Text("Ouvrez Cesson'App pour charger les données.", style = TextStyle(fontSize = 13.sp, color = GlanceTheme.colors.onSurface))
        }
        r.lignes.take(place).forEach { l ->
            Row(modifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(l.etiquette, style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    color = if (l.alerte) GlanceTheme.colors.error else GlanceTheme.colors.onSurfaceVariant),
                    modifier = GlanceModifier.width(64.dp), maxLines = 1)
                Text(l.texte, style = TextStyle(fontSize = 13.sp,
                    fontWeight = if (l.alerte) FontWeight.Bold else FontWeight.Normal,
                    color = if (l.alerte) GlanceTheme.colors.error else GlanceTheme.colors.onSurface), maxLines = 1)
            }
        }
    }
}

class RecepteurWidget : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WidgetCesson()
}
