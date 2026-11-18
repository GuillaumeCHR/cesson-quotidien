package com.yogistudio.cesson.core.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Préférences qui restent sur le téléphone : aucune n'est envoyée nulle part.
 * Cartes de chaque onglet (ordre et cartes masquées), guide de démarrage, carburant, arrêt et équipements suivis,
 * rappel de collecte.
 */
private val Context.preferencesCesson by preferencesDataStore(name = "preferences")

data class ConfigAccueil(val ordre: List<String>, val masquees: Set<String>)

const val ACCUEIL = "accueil"
const val AFFICHAGE_TELEPHONE = "telephone"
const val AFFICHAGE_CLAIR = "clair"
const val AFFICHAGE_SOMBRE = "sombre"

class PreferencesLocales(private val context: Context) {
    private val guideVu = booleanPreferencesKey("guide_demarrage_vu")
    private val cleAffichage = stringPreferencesKey("affichage")
    private val cleAlerteBus = stringPreferencesKey("alerte_bus")
    private val cleDelaiAlerte = androidx.datastore.preferences.core.intPreferencesKey("alerte_bus_delai")
    private val cleEquipementsMasques = stringSetPreferencesKey("equipements_masques")
    private val carburant = stringPreferencesKey("carburant_suivi")
    private val arret = stringPreferencesKey("arret_suivi")
    private val cleLignePreferee = stringPreferencesKey("ligne_preferee")
    private val rappelCollecte = booleanPreferencesKey("rappel_collecte")
    private val cleAccueilDetaille = booleanPreferencesKey("accueil_detaille")
    private val dernierRappel = stringPreferencesKey("rappel_collecte_dernier")
    private val cleAlertePollens = booleanPreferencesKey("alerte_pollens")
    private val cleSeuilPollens = intPreferencesKey("alerte_pollens_seuil")
    private val cleAlerteEpisodes = booleanPreferencesKey("alerte_episodes")
    private val cleAlertesAirEnvoyees = stringSetPreferencesKey("alertes_air_envoyees")
    private val cleAlerteVigilance = booleanPreferencesKey("alerte_vigilance")
    private val cleSeuilVigilance = stringPreferencesKey("alerte_vigilance_seuil")
    private val cleTrainsFavoris = stringPreferencesKey("trains_favoris")
    private val cleAlertesTrainsEnvoyees = stringSetPreferencesKey("alertes_trains_envoyees")
    private val cleSpectaclesFavoris = stringSetPreferencesKey("spectacles_favoris")
    private val cleRappelsSpectaclesEnvoyes = stringSetPreferencesKey("rappels_spectacles_envoyes")

    // L'accueil garde ses clés d'origine ; chaque onglet a les siennes (« onglet_bouger_ordre »…).
    private fun cleOrdre(onglet: String) = stringPreferencesKey(if (onglet == ACCUEIL) "accueil_ordre" else "onglet_${onglet}_ordre")
    private fun cleMasquees(onglet: String) =
        stringSetPreferencesKey(if (onglet == ACCUEIL) "accueil_masquees" else "onglet_${onglet}_masquees")

    /** Ordre et cartes masquées d'un onglet (« accueil », « bouger », « sortir », « pratique »). */
    fun configOnglet(onglet: String): Flow<ConfigAccueil> = context.preferencesCesson.data.map { p ->
        ConfigAccueil(
            ordre = p[cleOrdre(onglet)]?.split(',')?.filter { it.isNotBlank() } ?: emptyList(),
            masquees = p[cleMasquees(onglet)] ?: emptySet(),
        )
    }

    suspend fun enregistrerOnglet(onglet: String, config: ConfigAccueil) {
        context.preferencesCesson.edit {
            it[cleOrdre(onglet)] = config.ordre.joinToString(",")
            it[cleMasquees(onglet)] = config.masquees
        }
    }

    val configAccueil: Flow<ConfigAccueil> get() = configOnglet(ACCUEIL)

    suspend fun enregistrerAccueil(config: ConfigAccueil) = enregistrerOnglet(ACCUEIL, config)

    /** Thème : « telephone » (par défaut, suit le réglage d'Android), « clair » ou « sombre ». */
    val affichage: Flow<String> = context.preferencesCesson.data.map { it[cleAffichage] ?: AFFICHAGE_TELEPHONE }

    suspend fun enregistrerAffichage(mode: String) {
        context.preferencesCesson.edit { it[cleAffichage] = mode }
    }

    /** Cartes détaillées sur tous les écrans ; par défaut compactes (l'essentiel). Clé d'origine conservée. */
    val cartesDetaillees: Flow<Boolean> = context.preferencesCesson.data.map { it[cleAccueilDetaille] ?: false }

    suspend fun enregistrerCartesDetaillees(detaille: Boolean) {
        context.preferencesCesson.edit { it[cleAccueilDetaille] = detaille }
    }

    /** Alerte de passage armée (JSON d'AlerteBus), null sans alerte. */
    val alerteBus: Flow<String?> = context.preferencesCesson.data.map { it[cleAlerteBus] }

    suspend fun enregistrerAlerteBus(json: String?) {
        context.preferencesCesson.edit { if (json == null) it.remove(cleAlerteBus) else it[cleAlerteBus] = json }
    }

    /** Délai de prévenance des alertes de passage, en minutes (5 par défaut). */
    val delaiAlerteBus: Flow<Int> = context.preferencesCesson.data.map { it[cleDelaiAlerte] ?: 5 }

    suspend fun enregistrerDelaiAlerteBus(minutes: Int) {
        context.preferencesCesson.edit { it[cleDelaiAlerte] = minutes }
    }

    /** Guide de démarrage : montré au premier lancement, puis accessible depuis le menu. */
    val guideDemarrageVu: Flow<Boolean> = context.preferencesCesson.data.map { it[guideVu] ?: false }

    suspend fun marquerGuideVu() {
        context.preferencesCesson.edit { it[guideVu] = true }
    }

    /** Équipements retirés de la carte « Mes équipements » (identifiants « equipement:… »). */
    val equipementsMasques: Flow<Set<String>> = context.preferencesCesson.data.map { it[cleEquipementsMasques] ?: emptySet() }

    suspend fun enregistrerEquipementsMasques(ids: Set<String>) {
        context.preferencesCesson.edit { it[cleEquipementsMasques] = ids }
    }

    /** Code du carburant affiché sur la carte Carburants (gazole, e10…), null tant qu'aucun n'est choisi. */
    val carburantSuivi: Flow<String?> = context.preferencesCesson.data.map { it[carburant] }

    suspend fun enregistrerCarburant(code: String) {
        context.preferencesCesson.edit { it[carburant] = code }
    }

    /** Nom STAR de l'arrêt de la carte « Mon arrêt », null tant qu'aucun n'est choisi. */
    val arretSuivi: Flow<String?> = context.preferencesCesson.data.map { it[arret] }

    suspend fun enregistrerArret(nom: String) {
        context.preferencesCesson.edit { it[arret] = nom }
    }

    /** Ligne affichée en premier sur la carte « Mon arrêt de bus » (nom court : « C2 », « 64 »), null sans préférence. */
    val lignePreferee: Flow<String?> = context.preferencesCesson.data.map { it[cleLignePreferee] }

    suspend fun enregistrerLignePreferee(ligne: String?) {
        context.preferencesCesson.edit { if (ligne == null) it.remove(cleLignePreferee) else it[cleLignePreferee] = ligne }
    }

    /** Rappel de collecte la veille au soir : désactivé tant que l'utilisateur ne l'a pas demandé. */
    val rappelCollecteActif: Flow<Boolean> = context.preferencesCesson.data.map { it[rappelCollecte] ?: false }

    suspend fun activerRappelCollecte(actif: Boolean) {
        context.preferencesCesson.edit { it[rappelCollecte] = actif }
    }

    /** Date (AAAA-MM-JJ) du dernier rappel envoyé : jamais deux rappels le même soir. */
    val dernierRappelCollecte: Flow<String?> = context.preferencesCesson.data.map { it[dernierRappel] }

    suspend fun noterRappelCollecte(jour: String) {
        context.preferencesCesson.edit { it[dernierRappel] = jour }
    }

    /** Alerte pollens : désactivée tant que l'utilisateur ne l'a pas demandée. */
    val alertePollensActive: Flow<Boolean> = context.preferencesCesson.data.map { it[cleAlertePollens] ?: false }

    suspend fun activerAlertePollens(actif: Boolean) {
        context.preferencesCesson.edit { it[cleAlertePollens] = actif }
    }

    /** Niveau de l'indice pollen à partir duquel prévenir (3 modéré, 4 élevé, 5 très élevé). */
    val seuilPollens: Flow<Int> = context.preferencesCesson.data.map { it[cleSeuilPollens] ?: SEUIL_POLLENS_DEFAUT }

    suspend fun enregistrerSeuilPollens(seuil: Int) {
        context.preferencesCesson.edit { it[cleSeuilPollens] = seuil }
    }

    /** Alerte épisodes de pollution : désactivée tant que l'utilisateur ne l'a pas demandée. */
    val alerteEpisodesActive: Flow<Boolean> = context.preferencesCesson.data.map { it[cleAlerteEpisodes] ?: false }

    suspend fun activerAlerteEpisodes(actif: Boolean) {
        context.preferencesCesson.edit { it[cleAlerteEpisodes] = actif }
    }

    /** Alertes air déjà envoyées (« pollens:2026-04-12 », « episode:2026-07-03:alerte ») : jamais deux fois la même. */
    val alertesAirEnvoyees: Flow<Set<String>> = context.preferencesCesson.data.map { it[cleAlertesAirEnvoyees] ?: emptySet() }

    suspend fun enregistrerAlertesAirEnvoyees(cles: Set<String>) {
        context.preferencesCesson.edit { it[cleAlertesAirEnvoyees] = cles }
    }

    /** Alerte vigilance météo : désactivée tant que l'utilisateur ne l'a pas demandée. */
    val alerteVigilanceActive: Flow<Boolean> = context.preferencesCesson.data.map { it[cleAlerteVigilance] ?: false }

    suspend fun activerAlerteVigilance(actif: Boolean) {
        context.preferencesCesson.edit { it[cleAlerteVigilance] = actif }
    }

    /** Couleur à partir de laquelle prévenir : « JAUNE » (par défaut), « ORANGE » ou « ROUGE ». */
    val seuilVigilance: Flow<String> = context.preferencesCesson.data.map { it[cleSeuilVigilance] ?: SEUIL_VIGILANCE_DEFAUT }

    suspend fun enregistrerSeuilVigilance(seuil: String) {
        context.preferencesCesson.edit { it[cleSeuilVigilance] = seuil }
    }

    /** Trains favoris (liste JSON de TrainFavori, module transport) : prévenir d'un retard ou d'une suppression. */
    val trainsFavoris: Flow<String?> = context.preferencesCesson.data.map { it[cleTrainsFavoris] }

    suspend fun enregistrerTrainsFavoris(json: String) {
        context.preferencesCesson.edit { it[cleTrainsFavoris] = json }
    }

    /** Alertes de train déjà envoyées (« 2026-11-19|07:08|rennes|retard:8 ») : jamais deux fois la même. */
    val alertesTrainsEnvoyees: Flow<Set<String>> = context.preferencesCesson.data.map { it[cleAlertesTrainsEnvoyees] ?: emptySet() }

    suspend fun enregistrerAlertesTrainsEnvoyees(cles: Set<String>) {
        context.preferencesCesson.edit { it[cleAlertesTrainsEnvoyees] = cles }
    }

    /** Spectacles favoris (identifiants de l'agenda, « openagenda:70693599 ») : rappel la veille à 18h. */
    val spectaclesFavoris: Flow<Set<String>> = context.preferencesCesson.data.map { it[cleSpectaclesFavoris] ?: emptySet() }

    suspend fun basculerSpectacleFavori(id: String) {
        context.preferencesCesson.edit {
            val actuels = it[cleSpectaclesFavoris] ?: emptySet()
            it[cleSpectaclesFavoris] = if (id in actuels) actuels - id else actuels + id
        }
    }

    suspend fun enregistrerSpectaclesFavoris(ids: Set<String>) {
        context.preferencesCesson.edit { it[cleSpectaclesFavoris] = ids }
    }

    /** Rappels de spectacle déjà envoyés (« openagenda:70693599|veille|2026-11-19 », « …|Annulé ») : jamais deux fois. */
    val rappelsSpectaclesEnvoyes: Flow<Set<String>> = context.preferencesCesson.data.map { it[cleRappelsSpectaclesEnvoyes] ?: emptySet() }

    suspend fun enregistrerRappelsSpectaclesEnvoyes(cles: Set<String>) {
        context.preferencesCesson.edit { it[cleRappelsSpectaclesEnvoyes] = cles }
    }

}

/** Par défaut, on prévient à partir d'un indice pollen « élevé ». */
const val SEUIL_POLLENS_DEFAUT = 4

/** Par défaut, on prévient dès la vigilance météo jaune. */
const val SEUIL_VIGILANCE_DEFAUT = "JAUNE"

/**
 * Ordre effectif des tuiles : l'ordre enregistré d'abord (sans les tuiles qui n'existent plus),
 * puis les tuiles apparues depuis, dans leur ordre par défaut. Une nouvelle tuile ajoutée par une
 * mise à jour apparaît donc sans effacer les choix de l'utilisateur.
 */
fun ordonnerTuiles(parDefaut: List<String>, enregistre: List<String>): List<String> {
    val connues = parDefaut.toSet()
    val retenues = enregistre.filter { it in connues }.distinct()
    return retenues + parDefaut.filter { it !in retenues }
}

/** Déplace une tuile d'un cran (-1 vers le haut, +1 vers le bas) ; sans effet aux extrémités. */
fun deplacer(ordre: List<String>, id: String, sens: Int): List<String> {
    val i = ordre.indexOf(id)
    val j = i + sens
    if (i < 0 || j !in ordre.indices) return ordre
    return ordre.toMutableList().apply { add(j, removeAt(i)) }
}
