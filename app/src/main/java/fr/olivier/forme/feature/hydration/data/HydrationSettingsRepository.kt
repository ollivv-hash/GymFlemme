package fr.olivier.forme.feature.hydration.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import fr.olivier.forme.feature.hydration.domain.GoalSettings
import fr.olivier.forme.feature.hydration.domain.HydrationMath
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reminders (Réglages › Hydratation): on, between [startMinutes] and [endMinutes] (minutes after midnight), every
 * [intervalMinutes]; skipped right after a drink ([skipAfterDrink]) or during a session ([skipDuringSession]). The two
 * notification actions add [action1Ml] and [action2Ml] of water.
 */
data class ReminderSettings(
    val enabled: Boolean = true,
    val startMinutes: Int = 8 * 60,
    val endMinutes: Int = 21 * 60,
    val intervalMinutes: Int = 90,
    val skipAfterDrink: Boolean = true,
    val skipDuringSession: Boolean = true,
    val action1Ml: Int = 250,
    val action2Ml: Int = 500,
) {
    companion object {
        val INTERVALS = listOf(60, 90, 120)
    }
}

/** Every hydration setting (V2.29). [introSeen]: the first-use card was answered; no reminder before. */
data class HydrationSettings(
    val goal: GoalSettings = GoalSettings(),
    val reminders: ReminderSettings = ReminderSettings(),
    val healthConnectWrite: Boolean = false,
    val introSeen: Boolean = false,
)

/** Hydration settings in the app's DataStore (V2.29). */
@Singleton
class HydrationSettingsRepository @Inject constructor(private val dataStore: DataStore<Preferences>) {

    val settings: Flow<HydrationSettings> = dataStore.data.map { it.toSettings() }.distinctUntilChanged()

    val goal: Flow<GoalSettings> = settings.map { it.goal }.distinctUntilChanged()

    suspend fun current(): HydrationSettings = settings.first()

    private fun Preferences.toSettings() = HydrationSettings(
        goal = GoalSettings(
            customEnabled = this[CUSTOM_ENABLED] ?: false,
            customMl = this[CUSTOM_ML] ?: HydrationMath.DEFAULT_CUSTOM_ML,
            bonusEnabled = this[BONUS_ENABLED] ?: true,
            bonusMl = this[BONUS_ML] ?: HydrationMath.DEFAULT_BONUS_ML,
        ),
        reminders = ReminderSettings(
            enabled = this[REMINDERS_ENABLED] ?: true,
            startMinutes = this[REMINDER_START] ?: ReminderSettings().startMinutes,
            endMinutes = this[REMINDER_END] ?: ReminderSettings().endMinutes,
            intervalMinutes = this[REMINDER_INTERVAL] ?: ReminderSettings().intervalMinutes,
            skipAfterDrink = this[SKIP_AFTER_DRINK] ?: true,
            skipDuringSession = this[SKIP_DURING_SESSION] ?: true,
            action1Ml = this[ACTION1_ML] ?: ReminderSettings().action1Ml,
            action2Ml = this[ACTION2_ML] ?: ReminderSettings().action2Ml,
        ),
        healthConnectWrite = this[HEALTH_CONNECT] ?: false,
        introSeen = this[INTRO_SEEN] ?: false,
    )

    suspend fun setGoal(goal: GoalSettings) {
        dataStore.edit {
            it[CUSTOM_ENABLED] = goal.customEnabled
            it[CUSTOM_ML] = HydrationMath.coerceCustom(goal.customMl)
            it[BONUS_ENABLED] = goal.bonusEnabled
            it[BONUS_ML] = HydrationMath.coerceBonus(goal.bonusMl)
        }
    }

    suspend fun setReminders(reminders: ReminderSettings) {
        dataStore.edit {
            it[REMINDERS_ENABLED] = reminders.enabled
            it[REMINDER_START] = reminders.startMinutes.coerceIn(0, 24 * 60 - 1)
            it[REMINDER_END] = reminders.endMinutes.coerceIn(0, 24 * 60 - 1)
            it[REMINDER_INTERVAL] = reminders.intervalMinutes.takeIf { m -> m in ReminderSettings.INTERVALS } ?: 90
            it[SKIP_AFTER_DRINK] = reminders.skipAfterDrink
            it[SKIP_DURING_SESSION] = reminders.skipDuringSession
            it[ACTION1_ML] = reminders.action1Ml.coerceIn(HydrationMath.FREE_MIN_ML, HydrationMath.FREE_MAX_ML)
            it[ACTION2_ML] = reminders.action2Ml.coerceIn(HydrationMath.FREE_MIN_ML, HydrationMath.FREE_MAX_ML)
        }
    }

    suspend fun setHealthConnectWrite(enabled: Boolean) {
        dataStore.edit { it[HEALTH_CONNECT] = enabled }
    }

    /** The first-use card answered: "C'est parti" ([remindersOn] as ticked) or "Plus tard" (null: unchanged). */
    suspend fun setIntroSeen(remindersOn: Boolean?) {
        dataStore.edit {
            it[INTRO_SEEN] = true
            if (remindersOn != null) it[REMINDERS_ENABLED] = remindersOn
        }
    }

    /** Day the "goal reached" message was shown (once a day). */
    suspend fun markGoalAnnounced(day: LocalDate): Boolean {
        var first = false
        dataStore.edit {
            if (it[GOAL_ANNOUNCED_DAY] != day.toEpochDay()) {
                it[GOAL_ANNOUNCED_DAY] = day.toEpochDay()
                first = true
            }
        }
        return first
    }

    private companion object {
        val CUSTOM_ENABLED = booleanPreferencesKey("water_custom_enabled")
        val CUSTOM_ML = intPreferencesKey("water_custom_ml")
        val BONUS_ENABLED = booleanPreferencesKey("water_bonus_enabled")
        val BONUS_ML = intPreferencesKey("water_bonus_ml")
        val REMINDERS_ENABLED = booleanPreferencesKey("water_reminders_enabled")
        val REMINDER_START = intPreferencesKey("water_reminder_start")
        val REMINDER_END = intPreferencesKey("water_reminder_end")
        val REMINDER_INTERVAL = intPreferencesKey("water_reminder_interval")
        val SKIP_AFTER_DRINK = booleanPreferencesKey("water_skip_after_drink")
        val SKIP_DURING_SESSION = booleanPreferencesKey("water_skip_during_session")
        val ACTION1_ML = intPreferencesKey("water_action1_ml")
        val ACTION2_ML = intPreferencesKey("water_action2_ml")
        val HEALTH_CONNECT = booleanPreferencesKey("water_health_connect")
        val INTRO_SEEN = booleanPreferencesKey("water_intro_seen")
        val GOAL_ANNOUNCED_DAY = longPreferencesKey("water_goal_announced_day")
    }
}
