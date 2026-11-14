package fr.olivier.forme.feature.hydration.domain

import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZonedDateTime

/** Why a hydration reminder is not sent (V2.29); null: it is sent. */
enum class ReminderSkip { DISABLED, INTRO_NOT_SEEN, NO_PERMISSION, OUT_OF_HOURS, JUST_DRANK, GOAL_REACHED, SESSION_RUNNING }

/** What a reminder depends on, at the time it fires. */
data class ReminderState(
    val now: LocalDateTime,
    val enabled: Boolean,
    val introSeen: Boolean,
    val notificationsAllowed: Boolean,
    val startMinutes: Int,
    val endMinutes: Int,
    val skipAfterDrink: Boolean,
    val skipDuringSession: Boolean,
    val lastIntakeAt: LocalDateTime?,
    val goalReached: Boolean,
    val sessionRunning: Boolean,
)

/** When a hydration reminder is sent, and when the next one is due (WorkManager, no exact alarm). */
object ReminderRules {
    /** "Pas juste après avoir bu": no reminder this long after an intake. */
    val RECENT_DRINK: Duration = Duration.ofMinutes(30)

    fun skip(state: ReminderState): ReminderSkip? = when {
        !state.enabled -> ReminderSkip.DISABLED
        !state.introSeen -> ReminderSkip.INTRO_NOT_SEEN
        !state.notificationsAllowed -> ReminderSkip.NO_PERMISSION
        !inHours(state.now.toLocalTime(), state.startMinutes, state.endMinutes) -> ReminderSkip.OUT_OF_HOURS
        state.goalReached -> ReminderSkip.GOAL_REACHED
        state.skipDuringSession && state.sessionRunning -> ReminderSkip.SESSION_RUNNING
        state.skipAfterDrink && state.lastIntakeAt != null && !state.lastIntakeAt.isAfter(state.now) &&
            Duration.between(state.lastIntakeAt, state.now) < RECENT_DRINK -> ReminderSkip.JUST_DRANK
        else -> null
    }

    /** [time] within the hours, both ends included; an end before the start spans midnight. */
    fun inHours(time: LocalTime, startMinutes: Int, endMinutes: Int): Boolean {
        val m = time.hour * 60 + time.minute
        return if (startMinutes <= endMinutes) m in startMinutes..endMinutes else m >= startMinutes || m <= endMinutes
    }

    /**
     * The next reminder: [intervalMinutes] after [now] when that is within the hours, else the next start of the
     * hours (today's if it is still to come, else tomorrow's).
     */
    fun next(now: ZonedDateTime, startMinutes: Int, endMinutes: Int, intervalMinutes: Int): ZonedDateTime {
        val candidate = now.plusMinutes(intervalMinutes.toLong()).withSecond(0).withNano(0)
        if (inHours(candidate.toLocalTime(), startMinutes, endMinutes) && inHours(now.toLocalTime(), startMinutes, endMinutes)) return candidate
        val start = LocalTime.of(startMinutes / 60, startMinutes % 60)
        val today = now.toLocalDate().atTime(start).atZone(now.zone)
        return if (today.isAfter(now)) today else today.plusDays(1)
    }
}
