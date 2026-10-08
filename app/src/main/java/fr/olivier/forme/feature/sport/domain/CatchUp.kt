package fr.olivier.forme.feature.sport.domain

/** A large muscle still under the minimum of its weekly band. */
data class LaggingMuscle(val muscle: String, val done: Double, val min: Int) {
    val missing get() = min - done
}

/** A planned line of a template: its working sets and the muscles of its exercise (null when not linked). */
data class PlannedLine(val sets: Int, val type: ExerciseType, val muscles: Muscles?, val optional: Boolean = false)

/** What a template would bring to the lagging muscles, each capped at what it still misses. */
data class CatchUp(val templateId: Long, val gains: Map<String, Double>) {
    val total get() = gains.values.sum()
}

data class CatchUpAdvice(val lagging: List<LaggingMuscle>, val suggested: CatchUp)

/**
 * Session suggested from the lagging muscles (V2.13): the template that best catches up the large muscles still
 * under their band minimum this week, counted like the weekly view (main muscle 1 per set, assisting one ½).
 * Only an advice next to the rotation, never replacing it.
 */
object CatchUpAdvisor {
    /** A template is suggested only if it catches up at least this many sets more than the rotation's. */
    const val MIN_EXTRA_SETS = 3.0

    /** Before the second session of the week every muscle lags: no advice. */
    const val MIN_SESSIONS_DONE = 1

    /** Large muscles under [min], the biggest gap first. */
    fun lagging(week: MuscleWeek, min: Int): List<LaggingMuscle> =
        MuscleStats.LARGE
            .map { LaggingMuscle(it, week.byMuscle[it]?.total ?: 0.0, min) }
            .filter { it.missing > 0 }
            .sortedByDescending { it.missing }

    /** Optional lines and cardio are left out: only the sets the template surely plans. */
    fun catchUp(templateId: Long, lines: List<PlannedLine>, lagging: List<LaggingMuscle>): CatchUp {
        val missing = lagging.associate { it.muscle to it.missing }
        val brought = mutableMapOf<String, Double>()
        lines.filter { it.type != ExerciseType.CARDIO && !it.optional }.forEach { line ->
            val muscles = line.muscles ?: return@forEach
            muscles.primary.distinct().forEach { brought.merge(it, line.sets.toDouble(), Double::plus) }
            (muscles.secondary.distinct() - muscles.primary.toSet()).forEach {
                brought.merge(it, line.sets * MuscleStats.SECONDARY_SHARE, Double::plus)
            }
        }
        val gains = lagging.mapNotNull { l -> brought[l.muscle]?.let { l.muscle to minOf(it, missing.getValue(l.muscle)) } }.toMap()
        return CatchUp(templateId, gains)
    }

    /**
     * [templates] are the active templates (rotation order first, ties keep it) with their lines;
     * [proposedId] the rotation's. Null when nothing is clearly better than the rotation.
     */
    fun advise(
        week: MuscleWeek,
        sessionsThisWeek: Int,
        min: Int,
        templates: List<Pair<Long, List<PlannedLine>>>,
        proposedId: Long?,
    ): CatchUpAdvice? {
        // Without any linked set the muscles are unknown, not lagging.
        if (sessionsThisWeek < MIN_SESSIONS_DONE || week.byMuscle.isEmpty()) return null
        val lagging = lagging(week, min).ifEmpty { return null }
        val all = templates.map { (id, lines) -> catchUp(id, lines, lagging) }
        val rotation = all.firstOrNull { it.templateId == proposedId }?.total ?: 0.0
        val best = all.filter { it.templateId != proposedId }.maxByOrNull { it.total } ?: return null
        return if (best.total >= rotation + MIN_EXTRA_SETS) CatchUpAdvice(lagging, best) else null
    }
}
