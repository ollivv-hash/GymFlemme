package fr.olivier.forme.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
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

data class TrainingGoals(
    val sessionsPerWeek: Int = SettingsRepository.DEFAULT_SESSIONS_PER_WEEK,
    val minSets: Int = SettingsRepository.DEFAULT_MIN_SETS,
    val maxSets: Int = SettingsRepository.DEFAULT_MAX_SETS,
)

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

    val themeMode: Flow<ThemeMode> = dataStore.data.map { prefs ->
        prefs[THEME]?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } } ?: ThemeMode.DARK
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
    val deloadDismissedWeek: Flow<LocalDate?> = dataStore.data.map { prefs -> prefs[DELOAD_DISMISSED_WEEK]?.let(LocalDate::ofEpochDay) }

    suspend fun dismissDeload(week: LocalDate) {
        dataStore.edit { it[DELOAD_DISMISSED_WEEK] = week.toEpochDay() }
    }

    suspend fun setTrainingGoals(goals: TrainingGoals) {
        dataStore.edit {
            it[SESSIONS_PER_WEEK] = goals.sessionsPerWeek.coerceIn(1, 7)
            it[MIN_SETS] = goals.minSets.coerceIn(1, 40)
            it[MAX_SETS] = goals.maxSets.coerceIn(goals.minSets.coerceIn(1, 40), 60)
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
        private val HR_DEVICE_ADDRESS = stringPreferencesKey("hr_device_address")
        private val HR_DEVICE_NAME = stringPreferencesKey("hr_device_name")
        private val AUTO_BACKUP_URI = stringPreferencesKey("auto_backup_uri")
        private val AUTO_BACKUP_SUCCESS_AT = longPreferencesKey("auto_backup_success_at")
        private val AUTO_BACKUP_ATTEMPT_AT = longPreferencesKey("auto_backup_attempt_at")
        private val AUTO_BACKUP_ERROR = stringPreferencesKey("auto_backup_error")
        private val DELOAD_DISMISSED_WEEK = longPreferencesKey("deload_dismissed_week")
        private val AI_APP = stringPreferencesKey("ai_app_package")
    }
}
