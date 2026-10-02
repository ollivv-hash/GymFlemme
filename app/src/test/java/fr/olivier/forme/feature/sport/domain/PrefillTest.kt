package fr.olivier.forme.feature.sport.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PrefillTest {
    private val target = SetTarget(10, 12, null)

    @Test
    fun `first set uses the first set of last time`() {
        val last = listOf(WorkSet(20.0, 10, rir = 2), WorkSet(20.0, 9))
        assertEquals(WorkSet(20.0, 10), Prefill.nextSet(emptyList(), last, target))
    }

    @Test
    fun `next set copies the set just done in this session`() {
        val done = listOf(WorkSet(22.5, 8, rir = 1))
        assertEquals(WorkSet(22.5, 8), Prefill.nextSet(done, listOf(WorkSet(20.0, 10)), target))
    }

    @Test
    fun `falls back to the target without load when nothing is known`() {
        val draft = Prefill.nextSet(emptyList(), emptyList(), target)
        assertNull(draft.loadKg)
        assertEquals(12, draft.reps)
    }

    @Test
    fun `suggestion is applied only on demand`() {
        val draft = WorkSet(20.0, 10)
        assertEquals(WorkSet(22.5, 10), Prefill.apply(draft, Suggestion.Load(22.5)))
        assertEquals(WorkSet(20.0, 13), Prefill.apply(draft, Suggestion.Reps(13)))
    }

    @Test
    fun `pace per 500 m`() {
        assertEquals(120, Prefill.pacePer500m(300, 1250))
        assertNull(Prefill.pacePer500m(300, null))
        assertNull(Prefill.pacePer500m(300, 0))
    }
}
