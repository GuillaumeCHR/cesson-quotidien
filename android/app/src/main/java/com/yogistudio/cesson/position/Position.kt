package com.yogistudio.cesson.position

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.yogistudio.cesson.core.modele.Coord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Position de l'utilisateur, demandée seulement quand il touche « Les plus proches de moi ». Gardée en mémoire le
 * temps de l'ouverture de l'app, jamais enregistrée ni envoyée : elle ne sert qu'à trier des listes sur le téléphone.
 */
object Position {
    val actuelle = MutableStateFlow<Coord?>(null)

    fun autorisee(context: Context): Boolean = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    @SuppressLint("MissingPermission") // vérifiée par autorisee() juste avant
    suspend fun localiser(context: Context): Coord? {
        if (!autorisee(context)) return null
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val fournisseurs = lm.getProviders(true)
        // Une position récente (moins de 10 min) suffit pour trier des lieux d'une commune.
        val recente = fournisseurs.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .filter { System.currentTimeMillis() - it.time < 10 * 60_000 }
            .maxByOrNull { it.time }
        val loc = recente ?: withTimeoutOrNull(15_000) { courante(lm, fournisseurs) }
            ?: fournisseurs.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
        return loc?.let { Coord(it.latitude, it.longitude) }?.also { actuelle.value = it }
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private suspend fun courante(lm: LocationManager, fournisseurs: List<String>): Location? {
        val f = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER).firstOrNull { it in fournisseurs } ?: return null
        return suspendCancellableCoroutine { suite ->
            if (Build.VERSION.SDK_INT >= 30) {
                val annulation = CancellationSignal()
                suite.invokeOnCancellation { annulation.cancel() }
                lm.getCurrentLocation(f, annulation, { it.run() }) { suite.resume(it) }
            } else {
                lm.requestSingleUpdate(f, { suite.resume(it) }, android.os.Looper.getMainLooper())
            }
        }
    }
}

/** État et action « Les plus proches de moi » : permission demandée au premier appui, puis localisation. */
class DemandePosition(val enCours: Boolean, val demander: () -> Unit)

@Composable
fun rememberDemandePosition(): DemandePosition {
    val context = LocalContext.current
    val portee = rememberCoroutineScope()
    var enCours by remember { mutableStateOf(false) }
    fun localiser() = portee.launch {
        enCours = true
        Position.localiser(context)
        enCours = false
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.any { it }) localiser()
    }
    return DemandePosition(enCours) {
        if (Position.autorisee(context)) localiser()
        else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
}
