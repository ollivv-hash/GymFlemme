package fr.olivier.forme.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode(val label: String) { DARK("Sombre"), LIGHT("Clair"), SYSTEM("Système") }

/** Accent colour of the bento design (V2.24): session tile, active tab, goal reached, progress bars. */
enum class AccentColor { GREEN, BLUE, ORANGE, LIME, LAVENDER, CORAL }

/**
 * Réglages → Apparence (V2.24). [dynamicColor]: the accent comes from the wallpaper (Material You, Android 12+),
 * the chosen [accent] is kept for when it is turned off.
 */
data class Appearance(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val accent: AccentColor = AccentColor.GREEN,
    val dynamicColor: Boolean = false,
)

data class TrainingGoals(
    val sessionsPerWeek: Int = SettingsRepository.DEFAULT_SESSIONS_PER_WEEK,
    val minSets: Int = SettingsRepository.DEFAULT_MIN_SETS,
    val maxSets: Int = SettingsRepository.DEFAULT_MAX_SETS,
    /** Weekly band per large muscle of the exercise library (V2.12), set apart from the groups' band. */
    val muscleMinSets: Int = SettingsRepository.DEFAULT_MIN_SETS,
    val muscleMaxSets: Int = SettingsRepository.DEFAULT_MAX_SETS,
) {
    /** Within the allowed ranges, each max at least its min. */
    fun coerced(): TrainingGoals {
        val min = minSets.coerceIn(1, 40)
        val muscleMin = muscleMinSets.coerceIn(1, 40)
        return TrainingGoals(sessionsPerWeek.coerceIn(1, 7), min, maxSets.coerceIn(min, 60), muscleMin, muscleMaxSets.coerceIn(muscleMin, 60))
    }
}

/**
 * Automatic backup: the JSON file chosen once with the system picker (typically in Google Drive), rewritten after
 * each finished session and once a day. Times are epoch millis.
 */
data class AutoBackupState(
    val targetUri: String? = null,
    val lastSuccessAt: Long? = null,
    val lastAttemptAt: Long? = null,
    val lastError: String? = null,
)

/** Heart rate device (watch broadcasting its heart rate, or a chest strap) chosen once in Config. */
data class HrDevice(val address: String, val name: String?)

/** Rest timer state, persisted so the countdown survives a process kill. */
data class RestTimerState(val endsAtMillis: Long, val totalSec: Int)

@Singleton
class SettingsRepository @Inject constructor(private val dataStore: DataStore<Preferences>) {

    val defaultRestSec: Flow<Int> = dataStore.data.map { it[DEFAULT_REST] ?: DEFAULT_REST_SEC }

    /** Follows the system by default since V2.24 (dark before: kept for those who updated, see [keepDarkThemeOnUpdate]). */
    val themeMode: Flow<ThemeMode> = dataStore.data.map { prefs -> prefs.themeMode() }

    val appearance: Flow<Appearance> = dataStore.data.map { prefs ->
        Appearance(
            mode = prefs.themeMode(),
            accent = prefs[ACCENT]?.let { name -> AccentColor.entries.firstOrNull { it.name == name } } ?: AccentColor.GREEN,
            dynamicColor = prefs[DYNAMIC_COLOR] == true,
        )
    }

    private fun Preferences.themeMode(): ThemeMode =
        this[THEME]?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } } ?: ThemeMode.SYSTEM

    suspend fun setAccent(accent: AccentColor) {
        dataStore.edit { it[ACCENT] = accent.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { it[DYNAMIC_COLOR] = enabled }
    }

    /** From a backup (V2.24, backup v13). */
    suspend fun setAppearance(appearance: Appearance) {
        dataStore.edit {
            it[THEME] = appearance.mode.name
            it[ACCENT] = appearance.accent.name
            it[DYNAMIC_COLOR] = appearance.dynamicColor
        }
    }

    /**
     * Once, at the first launch of V2.24: the default theme went from dark to "follow the system". Someone who
     * [updated] the app and never chose a theme keeps the dark one they had; a new install follows the system.
     */
    suspend fun keepDarkThemeOnUpdate(updated: Boolean) {
        dataStore.edit {
            if (it[THEME_DEFAULT_CHECKED] == true) return@edit
            if (updated && it[THEME] == null) it[THEME] = ThemeMode.DARK.name
            it[THEME_DEFAULT_CHECKED] = true
        }
    }

    val restTimer: Flow<RestTimerState?> = dataStore.data.map { prefs ->
        val endsAt = prefs[REST_ENDS_AT] ?: return@map null
        RestTimerState(endsAt, prefs[REST_TOTAL] ?: 0)
    }

    /** Weekly goals: sessions per week and the recommended band of working sets per muscle group. */
    val trainingGoals: Flow<TrainingGoals> = dataStore.data.map { prefs ->
        TrainingGoals(
            sessionsPerWeek = prefs[SESSIONS_PER_WEEK] ?: DEFAULT_SESSIONS_PER_WEEK,
            minSets = prefs[MIN_SETS] ?: DEFAULT_MIN_SETS,
            maxSets = prefs[MAX_SETS] ?: DEFAULT_MAX_SETS,
            muscleMinSets = prefs[MUSCLE_MIN_SETS] ?: DEFAULT_MIN_SETS,
            muscleMaxSets = prefs[MUSCLE_MAX_SETS] ?: DEFAULT_MAX_SETS,
        )
    }

    val autoBackup: Flow<AutoBackupState> = dataStore.data.map { prefs ->
        AutoBackupState(
            targetUri = prefs[AUTO_BACKUP_URI],
            lastSuccessAt = prefs[AUTO_BACKUP_SUCCESS_AT],
            lastAttemptAt = prefs[AUTO_BACKUP_ATTEMPT_AT],
            lastError = prefs[AUTO_BACKUP_ERROR],
        )
    }

    /** Sets (or clears with null) the backup target; the previous status is reset. */
    suspend fun setAutoBackupTarget(uri: String?) {
        dataStore.edit { prefs ->
            if (uri == null) prefs.remove(AUTO_BACKUP_URI) else prefs[AUTO_BACKUP_URI] = uri
            prefs.remove(AUTO_BACKUP_SUCCESS_AT)
            prefs.remove(AUTO_BACKUP_ATTEMPT_AT)
            prefs.remove(AUTO_BACKUP_ERROR)
        }
    }

    /** Records the outcome of an automatic backup to the target: [error] null means success. */
    suspend fun recordAutoBackup(atMillis: Long, error: String?) {
        dataStore.edit { prefs ->
            prefs[AUTO_BACKUP_ATTEMPT_AT] = atMillis
            if (error == null) {
                prefs[AUTO_BACKUP_SUCCESS_AT] = atMillis
                prefs.remove(AUTO_BACKUP_ERROR)
            } else {
                prefs[AUTO_BACKUP_ERROR] = error
            }
        }
    }

    /** Package of the AI app receiving the analysis (V2.4); null = ask each time (system chooser). */
    val aiApp: Flow<String?> = dataStore.data.map { prefs -> prefs[AI_APP]?.takeIf { it.isNotEmpty() } }

    suspend fun setAiApp(packageName: String?) {
        dataStore.edit { if (packageName == null) it.remove(AI_APP) else it[AI_APP] = packageName }
    }

    /** How the analysis is shared (V2.4): the file alone suits Claude, the text suits apps refusing files. */
    val aiShareAsText: Flow<Boolean> = dataStore.data.map { prefs -> prefs[AI_AS_TEXT] ?: false }

    suspend fun setAiShareAsText(asText: Boolean) {
        dataStore.edit { it[AI_AS_TEXT] = asText }
    }

    /** Analysis instructions written by the user; null = the app's default prompt. */
    val aiPrompt: Flow<String?> = dataStore.data.map { prefs -> prefs[AI_PROMPT]?.takeIf { it.isNotBlank() } }

    suspend fun setAiPrompt(prompt: String?) {
        dataStore.edit { if (prompt.isNullOrBlank()) it.remove(AI_PROMPT) else it[AI_PROMPT] = prompt }
    }

    val hrDevice: Flow<HrDevice?> = dataStore.data.map { prefs ->
        prefs[HR_DEVICE_ADDRESS]?.let { HrDevice(it, prefs[HR_DEVICE_NAME]) }
    }

    suspend fun setHrDevice(device: HrDevice?) {
        dataStore.edit { prefs ->
            if (device == null) {
                prefs.remove(HR_DEVICE_ADDRESS)
                prefs.remove(HR_DEVICE_NAME)
            } else {
                prefs[HR_DEVICE_ADDRESS] = device.address
                if (device.name == null) prefs.remove(HR_DEVICE_NAME) else prefs[HR_DEVICE_NAME] = device.name
            }
        }
    }

    /** Week (Monday) whose deload advice was dismissed; the advice comes back the following week if still due. */
    /** Semaine tab: volume shown per library muscle rather than per group (V2.12), remembered between visits. */
    val volumeByMuscle: Flow<Boolean> = dataStore.data.map { prefs -> prefs[VOLUME_BY_MUSCLE] ?: false }

    /** Developer mode (V2.23): 7 taps on the version in À propos; shows the Bluetooth log. Not backed up. */
    val developerMode: Flow<Boolean> = dataStore.data.map { it[DEVELOPER_MODE] == true }

    suspend fun setDeveloperMode(enabled: Boolean) {
        dataStore.edit { it[DEVELOPER_MODE] = enabled }
    }

    /** V2.25: the first-launch wizard was shown (finished or skipped), or was not needed (data already there). */
    val onboardingDone: Flow<Boolean> = dataStore.data.map { it[ONBOARDING_DONE] == true }

    suspend fun setOnboardingDone() {
        dataStore.edit { it[ONBOARDING_DONE] = true }
    }

    /** V2.26: order of Mes plats, kept from one meal to the next. */
    val dishSort: Flow<fr.olivier.forme.feature.nutrition.domain.DishSort> = dataStore.data.map { prefs ->
        prefs[DISH_SORT]?.let { n -> fr.olivier.forme.feature.nutrition.domain.DishSort.entries.firstOrNull { it.name == n } }
            ?: fr.olivier.forme.feature.nutrition.domain.DishSort.MOST_USED
    }

    suspend fun setDishSort(sort: fr.olivier.forme.feature.nutrition.domain.DishSort) {
        dataStore.edit { it[DISH_SORT] = sort.name }
    }

    suspend fun setVolumeByMuscle(byMuscle: Boolean) {
        dataStore.edit { it[VOLUME_BY_MUSCLE] = byMuscle }
    }

    val deloadDismissedWeek: Flow<LocalDate?> = dataStore.data.map { prefs -> prefs[DELOAD_DISMISSED_WEEK]?.let(LocalDate::ofEpochDay) }

    suspend fun dismissDeload(week: LocalDate) {
        dataStore.edit { it[DELOAD_DISMISSED_WEEK] = week.toEpochDay() }
    }

    suspend fun setTrainingGoals(goals: TrainingGoals) {
        dataStore.edit {
            val g = goals.coerced()
            it[SESSIONS_PER_WEEK] = g.sessionsPerWeek
            it[MIN_SETS] = g.minSets
            it[MAX_SETS] = g.maxSets
            it[MUSCLE_MIN_SETS] = g.muscleMinSets
            it[MUSCLE_MAX_SETS] = g.muscleMaxSets
        }
    }

    suspend fun currentDefaultRestSec(): Int = defaultRestSec.first()

    suspend fun setDefaultRestSec(seconds: Int) {
        dataStore.edit { it[DEFAULT_REST] = seconds.coerceIn(MIN_REST_SEC, MAX_REST_SEC) }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[THEME] = mode.name }
    }

    suspend fun setRestTimer(state: RestTimerState?) {
        dataStore.edit { prefs ->
            if (state == null) {
                prefs.remove(REST_ENDS_AT)
                prefs.remove(REST_TOTAL)
            } else {
                prefs[REST_ENDS_AT] = state.endsAtMillis
                prefs[REST_TOTAL] = state.totalSec
            }
        }
    }

    companion object {
        const val DEFAULT_REST_SEC = 90
        const val MIN_REST_SEC = 15
        const val MAX_REST_SEC = 600
        private val DEFAULT_REST = intPreferencesKey("default_rest_sec")
        private val THEME = stringPreferencesKey("theme_mode")
        private val REST_ENDS_AT = longPreferencesKey("rest_ends_at")
        private val REST_TOTAL = intPreferencesKey("rest_total_sec")
        const val DEFAULT_SESSIONS_PER_WEEK = 3
        const val DEFAULT_MIN_SETS = 10
        const val DEFAULT_MAX_SETS = 20
        private val SESSIONS_PER_WEEK = intPreferencesKey("goal_sessions_per_week")
        private val MIN_SETS = intPreferencesKey("goal_min_sets_per_group")
        private val MAX_SETS = intPreferencesKey("goal_max_sets_per_group")
        private val MUSCLE_MIN_SETS = intPreferencesKey("goal_min_sets_per_muscle")
        private val MUSCLE_MAX_SETS = intPreferencesKey("goal_max_sets_per_muscle")
        private val VOLUME_BY_MUSCLE = booleanPreferencesKey("week_volume_by_muscle")
        private val HR_DEVICE_ADDRESS = stringPreferencesKey("hr_device_address")
        private val HR_DEVICE_NAME = stringPreferencesKey("hr_device_name")
        private val AUTO_BACKUP_URI = stringPreferencesKey("auto_backup_uri")
        private val AUTO_BACKUP_SUCCESS_AT = longPreferencesKey("auto_backup_success_at")
        private val AUTO_BACKUP_ATTEMPT_AT = longPreferencesKey("auto_backup_attempt_at")
        private val AUTO_BACKUP_ERROR = stringPreferencesKey("auto_backup_error")
        private val DELOAD_DISMISSED_WEEK = longPreferencesKey("deload_dismissed_week")
        private val AI_APP = stringPreferencesKey("ai_app_package")
        private val AI_AS_TEXT = booleanPreferencesKey("ai_share_as_text")
        private val AI_PROMPT = stringPreferencesKey("ai_prompt")
        private val DISH_SORT = stringPreferencesKey("dish_sort")
        private val DEVELOPER_MODE = booleanPreferencesKey("developer_mode")
        private val ACCENT = stringPreferencesKey("accent_color")
        private val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        private val THEME_DEFAULT_CHECKED = booleanPreferencesKey("theme_default_checked")
        private val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
    }
}
