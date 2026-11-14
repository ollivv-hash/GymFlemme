package fr.olivier.forme.feature.sport.data

import java.time.Instant

/**
 * Told when a session starts or is abandoned (V2.29: the session bonus of the hydration goal). The sport module
 * knows nothing of who listens; none by default (tests building the repository by hand).
 */
interface SessionListener {
    /** A session started at [startedAt] (also called when the running one is resumed: must be idempotent). */
    suspend fun onSessionStarted(startedAt: Instant) {}

    /** The session started at [startedAt] was abandoned and deleted; [hadSets]: at least one set or cardio entry done. */
    suspend fun onSessionAbandoned(startedAt: Instant, hadSets: Boolean) {}

    companion object {
        val NONE = object : SessionListener {}
    }
}
