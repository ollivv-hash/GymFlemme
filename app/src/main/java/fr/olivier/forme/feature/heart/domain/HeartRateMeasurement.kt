package fr.olivier.forme.feature.heart.domain

/**
 * One notification of the standard Bluetooth "Heart Rate Measurement" characteristic (0x2A37).
 *
 * [sensorContact] is null when the device does not report contact; [rrIntervalsMs] are the beat-to-beat intervals
 * when the device sends them (rarely the case for watches).
 */
data class HeartRateMeasurement(
    val bpm: Int,
    val sensorContact: Boolean?,
    val energyExpendedKj: Int?,
    val rrIntervalsMs: List<Int>,
) {
    companion object {
        private const val FLAG_UINT16 = 0x01
        private const val FLAG_CONTACT_SUPPORTED = 0x04
        private const val FLAG_CONTACT_DETECTED = 0x02
        private const val FLAG_ENERGY = 0x08
        private const val FLAG_RR = 0x10

        /** Parses the raw characteristic value; null when it is too short to hold a heart rate. */
        fun parse(value: ByteArray): HeartRateMeasurement? {
            if (value.size < 2) return null
            val flags = value[0].toInt() and 0xFF
            var offset = 1
            val bpm = if (flags and FLAG_UINT16 != 0) {
                if (value.size < 3) return null
                u16(value, offset).also { offset += 2 }
            } else {
                (value[offset].toInt() and 0xFF).also { offset += 1 }
            }
            val contact = if (flags and FLAG_CONTACT_SUPPORTED != 0) flags and FLAG_CONTACT_DETECTED != 0 else null
            val energy = if (flags and FLAG_ENERGY != 0 && value.size >= offset + 2) {
                u16(value, offset).also { offset += 2 }
            } else {
                null
            }
            val rr = buildList {
                if (flags and FLAG_RR != 0) {
                    while (value.size >= offset + 2) {
                        // RR unit is 1/1024 s.
                        add(u16(value, offset) * 1000 / 1024)
                        offset += 2
                    }
                }
            }
            return HeartRateMeasurement(bpm, contact, energy, rr)
        }

        private fun u16(value: ByteArray, offset: Int) =
            (value[offset].toInt() and 0xFF) or ((value[offset + 1].toInt() and 0xFF) shl 8)
    }
}
