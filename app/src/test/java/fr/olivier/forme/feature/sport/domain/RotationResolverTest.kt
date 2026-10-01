package fr.olivier.forme.feature.sport.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RotationResolverTest {
    private val a = RotationTemplate(1, 1, false)
    private val b = RotationTemplate(2, 2, false)
    private val c = RotationTemplate(3, 3, false)
    private val lower = RotationTemplate(4, null, false)
    private val base = listOf(a, b, c, lower)

    @Test
    fun `after B comes C`() = assertEquals(3L, RotationResolver.next(base, listOf(2)))

    @Test
    fun `wraps around after the last template`() = assertEquals(1L, RotationResolver.next(base, listOf(3, 2, 1)))

    @Test
    fun `lower body session does not shift the rotation`() =
        assertEquals(3L, RotationResolver.next(base, listOf(4, 4, 2, 1)))

    @Test
    fun `no history proposes the first template`() = assertEquals(1L, RotationResolver.next(base, emptyList()))

    @Test
    fun `empty rotation proposes nothing`() = assertNull(RotationResolver.next(listOf(lower), listOf(4)))

    @Test
    fun `adding a template to the rotation is taken into account`() {
        val d = RotationTemplate(5, 4, false)
        assertEquals(5L, RotationResolver.next(base + d, listOf(3)))
    }

    @Test
    fun `archived template is skipped`() {
        val templates = listOf(a, b, c.copy(archived = true), lower)
        assertEquals(1L, RotationResolver.next(templates, listOf(2)))
    }

    @Test
    fun `last session on a template removed from rotation falls back to the previous rotation session`() {
        val templates = listOf(a, b.copy(orderInRotation = null), c, lower)
        // Last sessions: B (no longer in rotation), then A → next after A is C.
        assertEquals(3L, RotationResolver.next(templates, listOf(2, 1)))
    }

    @Test
    fun `reordering the rotation changes the proposal`() {
        val templates = listOf(a.copy(orderInRotation = 2), b.copy(orderInRotation = 1), c, lower) // B, A, C
        assertEquals(1L, RotationResolver.next(templates, listOf(2)))
    }
}
