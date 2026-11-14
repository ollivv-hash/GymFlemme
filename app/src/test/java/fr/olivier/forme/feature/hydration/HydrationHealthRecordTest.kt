package fr.olivier.forme.feature.hydration

import fr.olivier.forme.feature.hydration.data.IntakeSource
import fr.olivier.forme.feature.hydration.data.WaterIntakeEntity
import fr.olivier.forme.feature.hydration.health.HydrationHealthMirror
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** V2.29: what is written to Health Connect for an intake. */
class HydrationHealthRecordTest {
    private val at = Instant.parse("2026-11-14T08:05:00Z")
    private fun intake(ml: Int, coefficient: Double) =
        WaterIntakeEntity(id = 42, timestamp = at, date = LocalDate.of(2026, 11, 14), drinkId = 1, volumeMl = ml, coefficientApplied = coefficient, source = IntakeSource.WATER_SCREEN)

    @Test
    fun theCountedVolumeIsWrittenWithTheIntakeIdAndItsTime() {
        val record = HydrationHealthMirror.record(intake(500, 0.8), ZoneId.of("Europe/Paris"), version = 7)!!
        assertEquals(400.0, record.volume.inMilliliters, 1e-9)
        assertEquals(at, record.startTime)
        assertEquals(Duration.ofMinutes(1), Duration.between(record.startTime, record.endTime))
        assertEquals(ZoneOffset.ofHours(1), record.startZoneOffset)
        assertEquals("water-42", record.metadata.clientRecordId)
        assertEquals(7L, record.metadata.clientRecordVersion)
    }

    @Test
    fun nothingIsWrittenWhenNothingCounts() = assertNull(HydrationHealthMirror.record(intake(330, 0.0)))
}
