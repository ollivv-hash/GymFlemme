package fr.olivier.forme.feature.sport.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionDurationsTest {
    @Test
    fun `median of the five latest sessions, rounded to 5 minutes`() {
        assertEquals(40, SessionDurations.typicalMinutes(listOf(38, 44, 41, 90, 36, 10)))
        assertEquals(45, SessionDurations.typicalMinutes(listOf(40, 48)))
        assertEquals(5, SessionDurations.typicalMinutes(listOf(2)))
    }

    @Test
    fun `no session, or only forgotten ones, gives nothing`() {
        assertNull(SessionDurations.typicalMinutes(emptyList()))
        assertNull(SessionDurations.typicalMinutes(listOf(600, 0)))
    }
}
