package fr.olivier.forme.feature.sport.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupersetRulesTest {
    @Test
    fun `link and unlink with next`() {
        val linked = SupersetRules.toggleWithNext(listOf(null, null, null, null), 1)
        assertEquals(listOf(null, 1, 1, null), linked)
        assertEquals(listOf(null, null, null, null), SupersetRules.toggleWithNext(linked, 1))
    }

    @Test
    fun `an exercise belongs to one superset only, size is 2`() {
        val first = SupersetRules.toggleWithNext(listOf(null, null, null, null), 0) // [1,1,-,-]
        val moved = SupersetRules.toggleWithNext(first, 1) // link 1 with 2: breaks 0-1
        assertEquals(listOf(null, 1, 1, null), moved)
    }

    @Test
    fun `groups broken by a reorder are dissolved and renumbered`() {
        assertEquals(listOf(null, null, null), SupersetRules.normalize(listOf(3, null, 3)))
        assertEquals(listOf(1, 1, null, 2, 2), SupersetRules.normalize(listOf(5, 5, null, 2, 2)))
        assertEquals(listOf(null, null, null), SupersetRules.normalize(listOf(1, 1, 1)))
    }

    @Test
    fun `rest starts after the second exercise or when the partner is finished`() {
        assertFalse(SupersetRules.restAfterSet(isLastOfPair = false, partnerFinished = false))
        assertTrue(SupersetRules.restAfterSet(isLastOfPair = true, partnerFinished = false))
        assertTrue(SupersetRules.restAfterSet(isLastOfPair = false, partnerFinished = true))
    }
}
