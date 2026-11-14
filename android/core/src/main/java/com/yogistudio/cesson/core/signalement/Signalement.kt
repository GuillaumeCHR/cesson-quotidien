package com.yogistudio.cesson.core.signalement

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Rubrique « Vos idées / signaler une erreur ». Pas de serveur ni de compte : le signalement
 * ouvre l'application mail de l'utilisateur avec un message pré-rempli. Le contexte joint
 * (module, heure de la donnée affichée, version de l'app) aide à corriger vite un horaire faux ;
 * l'utilisateur le voit et peut le modifier avant d'envoyer.
 */
data class ContexteSignalement(
    val sujet: String,
    val module: String? = null,
    val details: List<String> = emptyList(),
)

fun interface Signaleur {
    fun signaler(contexte: ContexteSignalement)
}

val LocalSignaleur = staticCompositionLocalOf<Signaleur> { Signaleur { } }
