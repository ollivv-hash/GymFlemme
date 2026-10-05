package fr.olivier.forme.feature.heart.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeartRateMeasurementTest {
    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun uint8HeartRateWithoutOptions() {
        val m = HeartRateMeasurement.parse(bytes(0x00, 72))!!
        assertEquals(72, m.bpm)
        assertNull(m.sensorContact)
        assertNull(m.energyExpendedKj)
        assertEquals(emptyList<Int>(), m.rrIntervalsMs)
    }

    @Test
    fun uint16HeartRateWithContactEnergyAndRr() {
        // flags: uint16 + contact supported & detected + energy + RR; bpm 182; energy 300 kJ; RR 1024 and 512 (1/1024 s).
        val m = HeartRateMeasurement.parse(bytes(0x1F, 182, 0, 0x2C, 0x01, 0x00, 0x04, 0x00, 0x02))!!
        assertEquals(182, m.bpm)
        assertEquals(true, m.sensorContact)
        assertEquals(300, m.energyExpendedKj)
        assertEquals(listOf(1000, 500), m.rrIntervalsMs)
    }

    @Test
    fun contactSupportedButNotDetected() {
        assertEquals(false, HeartRateMeasurement.parse(bytes(0x04, 90))!!.sensorContact)
    }

    @Test
    fun truncatedValuesAreRejected() {
        assertNull(HeartRateMeasurement.parse(bytes(0x00)))
        assertNull(HeartRateMeasurement.parse(bytes(0x01, 90)))
    }
}
