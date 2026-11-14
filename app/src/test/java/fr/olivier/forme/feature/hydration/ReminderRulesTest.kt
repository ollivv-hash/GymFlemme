package fr.olivier.forme.feature.hydration

import fr.olivier.forme.feature.hydration.domain.ReminderRules
import fr.olivier.forme.feature.hydration.domain.ReminderSkip
import fr.olivier.forme.feature.hydration.domain.ReminderState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class ReminderRulesTest {
    private val now = LocalDateTime.of(2026, 11, 14, 10, 0)

    /** Every condition met: the reminder goes. */
    private fun state(
        now: LocalDateTime = this.now,
        enabled: Boolean = true,
        introSeen: Boolean = true,
        allowed: Boolean = true,
        lastIntake: LocalDateTime? = null,
        reached: Boolean = false,
        session: Boolean = false,
        skipAfterDrink: Boolean = true,
        skipDuringSession: Boolean = true,
    ) = ReminderState(now, enabled, introSeen, allowed, 8 * 60, 21 * 60, skipAfterDrink, skipDuringSession, lastIntake, reached, session)

    @Test
    fun `sent when every condition is met`() = assertNull(ReminderRules.skip(state()))

    @Test
    fun `each rule alone stops it`() {
        assertEquals(ReminderSkip.DISABLED, ReminderRules.skip(state(enabled = false)))
        assertEquals(ReminderSkip.INTRO_NOT_SEEN, ReminderRules.skip(state(introSeen = false)))
        assertEquals(ReminderSkip.NO_PERMISSION, ReminderRules.skip(state(allowed = false)))
        assertEquals(ReminderSkip.OUT_OF_HOURS, ReminderRules.skip(state(now = now.withHour(7).withMinute(59))))
        assertEquals(ReminderSkip.OUT_OF_HOURS, ReminderRules.skip(state(now = now.withHour(21).withMinute(1))))
        assertEquals(ReminderSkip.GOAL_REACHED, ReminderRules.skip(state(reached = true)))
        assertEquals(ReminderSkip.SESSION_RUNNING, ReminderRules.skip(state(session = true)))
        assertEquals(ReminderSkip.JUST_DRANK, ReminderRules.skip(state(lastIntake = now.minusMinutes(29))))
    }

    @Test
    fun `the hours include both ends and a drink 30 minutes ago no longer counts`() {
        assertNull(ReminderRules.skip(state(now = now.withHour(8).withMinute(0))))
        assertNull(ReminderRules.skip(state(now = now.withHour(21).withMinute(0))))
        assertNull(ReminderRules.skip(state(lastIntake = now.minusMinutes(30))))
    }

    @Test
    fun `the smart options can be switched off`() {
        assertNull(ReminderRules.skip(state(lastIntake = now.minusMinutes(5), skipAfterDrink = false)))
        assertNull(ReminderRules.skip(state(session = true, skipDuringSession = false)))
        // The goal reached and the hours always count.
        assertEquals(ReminderSkip.GOAL_REACHED, ReminderRules.skip(state(reached = true, skipAfterDrink = false, skipDuringSession = false)))
    }

    @Test
    fun `combined, the first reason is given`() {
        assertEquals(ReminderSkip.OUT_OF_HOURS, ReminderRules.skip(state(now = now.withHour(6), reached = true, session = true)))
        assertEquals(ReminderSkip.GOAL_REACHED, ReminderRules.skip(state(reached = true, session = true, lastIntake = now.minusMinutes(1))))
        assertEquals(ReminderSkip.SESSION_RUNNING, ReminderRules.skip(state(session = true, lastIntake = now.minusMinutes(1))))
        assertEquals(ReminderSkip.INTRO_NOT_SEEN, ReminderRules.skip(state(introSeen = false, allowed = false)))
    }

    @Test
    fun `day change - a drink late yesterday does not stop the first reminder`() {
        val morning = LocalDateTime.of(2026, 11, 15, 8, 0)
        assertNull(ReminderRules.skip(state(now = morning, lastIntake = LocalDateTime.of(2026, 11, 14, 23, 50))))
        // The goal is today's: reached yesterday, not today.
        assertNull(ReminderRules.skip(state(now = morning, reached = false)))
    }

    @Test
    fun `hours over midnight`() {
        assertTrue(ReminderRules.inHours(LocalTime.of(23, 30), 22 * 60, 2 * 60))
        assertTrue(ReminderRules.inHours(LocalTime.of(1, 0), 22 * 60, 2 * 60))
        assertFalse(ReminderRules.inHours(LocalTime.of(12, 0), 22 * 60, 2 * 60))
    }

    @Test
    fun `next reminder - interval within the hours, else the next start`() {
        val zone = ZoneId.of("Europe/Paris")
        fun at(day: Int, h: Int, m: Int) = ZonedDateTime.of(2026, 11, day, h, m, 0, 0, zone)
        assertEquals(at(14, 11, 30), ReminderRules.next(at(14, 10, 0), 8 * 60, 21 * 60, 90))
        assertEquals(at(14, 21, 0), ReminderRules.next(at(14, 20, 0), 8 * 60, 21 * 60, 60))
        // Past the end: tomorrow's start.
        assertEquals(at(15, 8, 0), ReminderRules.next(at(14, 20, 0), 8 * 60, 21 * 60, 90))
        assertEquals(at(15, 8, 0), ReminderRules.next(at(14, 22, 15), 8 * 60, 21 * 60, 60))
        // Before the start: today's start.
        assertEquals(at(14, 8, 0), ReminderRules.next(at(14, 6, 30), 8 * 60, 21 * 60, 120))
        assertEquals(at(14, 8, 0), ReminderRules.next(at(14, 7, 0), 8 * 60, 21 * 60, 90))
    }
}
