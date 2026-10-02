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
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode(val label: String) { DARK("Sombre"), LIGHT("Clair"), SYSTEM("Système") }

data class TrainingGoals(
    val sessionsPerWeek: Int = SettingsRepository.DEFAULT_SESSIONS_PER_WEEK,
    val minSets: Int = SettingsRepository.DEFAULT_MIN_SETS,
    val maxSets: Int = SettingsRepository.DEFAULT_MAX_SETS,
)

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
    }
}
