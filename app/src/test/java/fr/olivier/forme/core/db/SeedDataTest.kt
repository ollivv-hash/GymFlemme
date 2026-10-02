package fr.olivier.forme.core.db

import fr.olivier.forme.feature.sport.domain.RotationResolver
import fr.olivier.forme.feature.sport.domain.RotationTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SeedDataTest {
    @Test
    fun `next proposed session after seed is C`() {
        val templates = SeedData.templates.map { RotationTemplate(it.id, it.orderInRotation, it.archived) }
        val seed = SeedData.seedSession(Instant.parse("2026-10-01T10:00:00Z"))
        assertEquals(SeedData.templateC.id, RotationResolver.next(templates, listOfNotNull(seed.templateId)))
    }

    @Test
    fun `catalogue and templates match the programme`() {
        assertEquals(16, SeedData.exercises.size)
        assertEquals(16, SeedData.exercises.map { it.id }.toSet().size)
        assertEquals(listOf(6, 6, 5, 5), SeedData.templates.map { t -> SeedData.templateLines.count { it.templateId == t.id } })
        assertTrue(SeedData.templateLines.filter { it.exerciseId == SeedData.rower.id }.all { it.targetDurationSec == 300 })
        assertEquals(1.0, SeedData.lateralRaises.loadIncrementKg, 0.0)
        assertTrue(SeedData.sidePlank.perSide)
    }

    @Test
    fun `seed sets carry current loads`() {
        val sets = SeedData.seedSets(Instant.parse("2026-10-01T10:00:00Z"))
        assertEquals(3, sets.count { it.exerciseId == SeedData.chestPress.id })
        assertEquals(22.5, sets.first { it.exerciseId == SeedData.pullDownNeutral.id }.loadKg!!, 0.0)
        assertEquals(10.0, sets.first { it.exerciseId == SeedData.hackSquat.id }.loadKg!!, 0.0)
        assertTrue(sets.none { it.exerciseId == SeedData.bicepsCurl.id })
    }
}
