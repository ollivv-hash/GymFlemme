package fr.olivier.forme.feature.sport.domain

/** Typical length of a template's session (V2.22, home screen "~40 min"). */
object SessionDurations {
    const val LAST_SESSIONS = 5

    /**
     * Median of the [LAST_SESSIONS] latest durations (minutes, latest first), rounded to 5 minutes; null without any
     * session yet. A session left open for hours (forgotten) is not a typical one: over 4 h is ignored.
     */
    fun typicalMinutes(latestFirst: List<Long>): Int? {
        val recent = latestFirst.filter { it in 1..MAX_MINUTES }.take(LAST_SESSIONS).sorted()
        if (recent.isEmpty()) return null
        val median = if (recent.size % 2 == 1) recent[recent.size / 2].toDouble() else (recent[recent.size / 2 - 1] + recent[recent.size / 2]) / 2.0
        return (Math.round(median / 5) * 5).toInt().coerceAtLeast(5)
    }

    private const val MAX_MINUTES = 240L
}
