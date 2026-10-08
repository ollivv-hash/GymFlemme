package fr.olivier.forme.feature.sport.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** V2.13: session suggested from the muscles lagging behind their weekly band. */
class CatchUpAdvisorTest {
    private val legCurl = Muscles(primary = listOf("hamstrings"), secondary = listOf("calves"))
    private val calfRaise = Muscles(primary = listOf("calves"), secondary = emptyList())
    private val benchPress = Muscles(primary = listOf("chest"), secondary = listOf("triceps", "shoulders"))

    /** Every large muscle at the minimum (10), except those given. */
    private fun week(vararg done: Pair<String, Double>) =
        MuscleWeek(MuscleStats.LARGE.associateWith { MuscleVolume(direct = 10.0) } + done.associate { (m, v) -> m to MuscleVolume(direct = v) }, 0)

    private fun line(sets: Int, muscles: Muscles?, type: ExerciseType = ExerciseType.WEIGHTED, optional: Boolean = false) =
        PlannedLine(sets, type, muscles, optional)

    @Test
    fun laggingMusclesAreTheLargeOnesUnderTheMinimumBiggestGapFirst() {
        val lagging = CatchUpAdvisor.lagging(week("hamstrings" to 4.0, "calves" to 0.0, "chest" to 9.5), 10)
        assertEquals(listOf("calves", "hamstrings", "chest"), lagging.map { it.muscle })
        assertEquals(6.0, lagging[1].missing, 0.0)
        // A muscle never trained this week lags with 0.
        val untrained = CatchUpAdvisor.lagging(MuscleWeek(mapOf("chest" to MuscleVolume(direct = 12.0)), 0), 10)
        assertEquals(MuscleStats.LARGE - "chest", untrained.map { it.muscle })
        assertEquals(0.0, untrained.first().done, 0.0)
    }

    @Test
    fun gainsCountLikeTheWeeklyViewCappedAtWhatEachMuscleMisses() {
        val lagging = CatchUpAdvisor.lagging(week("hamstrings" to 4.0, "calves" to 0.0), 10)
        val catchUp = CatchUpAdvisor.catchUp(
            2,
            listOf(
                line(4, legCurl), // hamstrings +4, calves +2
                line(4, legCurl), // hamstrings +4 → capped at 6 missing
                line(3, calfRaise), // calves +3
                line(3, calfRaise, optional = true), // optional: not counted
                line(2, calfRaise, ExerciseType.CARDIO), // cardio: not counted
                line(3, null), // not linked
                line(4, benchPress), // chest is not lagging
            ),
            lagging,
        )
        assertEquals(mapOf("hamstrings" to 6.0, "calves" to 7.0), catchUp.gains)
        assertEquals(13.0, catchUp.total, 0.0)
    }

    @Test
    fun suggestsTheTemplateCatchingUpAtLeastThreeSetsMoreThanTheRotation() {
        val w = week("hamstrings" to 4.0, "calves" to 0.0)
        val templates = listOf(
            1L to listOf(line(4, benchPress)), // rotation: 0
            2L to listOf(line(3, legCurl)), // hamstrings 3 + calves 1.5 = 4.5
            3L to listOf(line(1, legCurl)), // 1.5
        )
        val advice = CatchUpAdvisor.advise(w, sessionsThisWeek = 1, min = 10, templates = templates, proposedId = 1)!!
        assertEquals(2L, advice.suggested.templateId)
        assertEquals(listOf("calves", "hamstrings"), advice.lagging.map { it.muscle })

        // The rotation's template already catches up nearly as much: no advice.
        val close = listOf(1L to listOf(line(2, legCurl)), 2L to listOf(line(3, legCurl)))
        assertNull(CatchUpAdvisor.advise(w, 1, 10, close, proposedId = 1))
    }

    @Test
    fun tiesKeepTheTemplatesOrder() {
        val w = week("calves" to 0.0)
        val templates = listOf(1L to emptyList(), 2L to listOf(line(4, calfRaise)), 3L to listOf(line(4, calfRaise)))
        assertEquals(2L, CatchUpAdvisor.advise(w, 2, 10, templates, proposedId = 1)!!.suggested.templateId)
        // Without a rotation, compared with nothing.
        assertEquals(2L, CatchUpAdvisor.advise(w, 2, 10, templates.drop(1), proposedId = null)!!.suggested.templateId)
    }

    @Test
    fun noAdviceBeforeTheSecondSessionWithoutLinkedSetsOrWithoutLaggingMuscle() {
        val templates = listOf(1L to emptyList(), 2L to listOf(line(4, calfRaise)))
        assertNull(CatchUpAdvisor.advise(week("calves" to 0.0), sessionsThisWeek = 0, min = 10, templates = templates, proposedId = 1))
        assertNull(CatchUpAdvisor.advise(MuscleWeek(emptyMap(), 12), 2, 10, templates, proposedId = 1))
        assertNull(CatchUpAdvisor.advise(week(), 2, 10, templates, proposedId = 1))
    }
}
