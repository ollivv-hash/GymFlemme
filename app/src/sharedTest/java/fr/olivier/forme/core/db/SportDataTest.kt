package fr.olivier.forme.core.db

import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.olivier.forme.feature.bodyweight.data.BodyWeightRepository
import fr.olivier.forme.feature.sport.data.SessionRepository
import fr.olivier.forme.feature.sport.data.SetEntryEntity
import fr.olivier.forme.feature.sport.data.TemplateRepository
import fr.olivier.forme.feature.sport.domain.SessionExerciseStatus
import fr.olivier.forme.feature.sport.domain.Suggestion
import fr.olivier.forme.feature.sport.domain.ProgressionRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class SportDataTest {
    private lateinit var clock: TestClock
    private lateinit var db: FormeDatabase
    private lateinit var sessions: SessionRepository
    private lateinit var templates: TemplateRepository

    @Before
    fun setUp() {
        clock = TestClock()
        db = testDatabase(clock)
        templates = TemplateRepository(db, db.templateDao(), db.sessionDao())
        sessions = SessionRepository(db, db.sessionDao(), db.templateDao(), db.exerciseDao(), clock)
    }

    @After
    fun tearDown() = db.close()

    /** Runs a whole session of [templateId], logging every planned set with [load] and [reps]. */
    private suspend fun doSession(templateId: Long?, load: Double? = null, reps: Int = 10, rir: Int? = null): Long {
        clock.advance(Duration.ofDays(2))
        val id = sessions.start(templateId)
        db.sessionDao().getSessionLines(id).forEach { (line, exercise) ->
            repeat(line.targetSets) { index ->
                sessions.logSet(
                    SetEntryEntity(
                        sessionId = id, exerciseId = exercise.id, setIndex = index,
                        loadKg = load, reps = reps, rir = rir, completedAt = Instant.EPOCH,
                    ),
                )
            }
        }
        clock.advance(Duration.ofHours(1))
        sessions.finish(id, 4, null)
        return id
    }

    @Test
    fun seedIsWrittenAtCreationAndProposesC() = runTest {
        assertEquals(16, db.exerciseDao().count())
        assertEquals(4, db.templateDao().getAll().size)
        assertEquals("C", templates.observeProposed().first()?.code)
        assertTrue(sessions.observeFinished().first().isEmpty()) // seed hidden from history
    }

    @Test
    fun lastTimeIncludesSeedButProgressionIgnoresIt() = runTest {
        val id = sessions.start(SeedData.templateC.id)
        val last = sessions.lastWorkingSets(SeedData.chestPress.id, id)
        assertEquals(listOf(15.0, 15.0, 15.0), last.map { it.loadKg })
        assertNull(sessions.previousPerformance(SeedData.chestPress.id, id))
    }

    @Test
    fun rotationAdvancesAndLowerBodyDoesNotShiftIt() = runTest {
        doSession(SeedData.templateC.id, load = 15.0)
        assertEquals("A", templates.observeProposed().first()?.code)
        doSession(SeedData.templateLower.id, load = 15.0)
        assertEquals("A", templates.observeProposed().first()?.code)
    }

    @Test
    fun rotationIsRecomputedWhenTemplatesChange() = runTest {
        templates.setArchived(SeedData.templateC.id, true)
        assertEquals("A", templates.observeProposed().first()?.code)
        templates.saveRotation(listOf(SeedData.templateA.id, SeedData.templateB.id, SeedData.templateLower.id))
        assertEquals("BAS", templates.observeProposed().first()?.code)
    }

    @Test
    fun startingCopiesTemplateAndLaterTemplateEditsDoNotTouchIt() = runTest {
        val id = sessions.start(SeedData.templateB.id)
        assertEquals(id, sessions.start(SeedData.templateA.id)) // resumes the running session
        val before = db.sessionDao().getSessionLines(id).map { it.line.exerciseId to it.line.targetSets }
        assertEquals(6, before.size)
        assertTrue(db.sessionDao().getSessionLines(id).last().line.optional)

        val lines = templates.getLines(SeedData.templateB.id).map { it.line }
        templates.save(SeedData.templateB.copy(name = "Renamed"), lines.drop(1).map { it.copy(targetSets = 5) })
        templates.setArchived(SeedData.templateB.id, true)

        val after = db.sessionDao().getSessionLines(id).map { it.line.exerciseId to it.line.targetSets }
        assertEquals(before, after)
        assertEquals("Tirage + tronc", db.sessionDao().getSession(id)?.templateNameSnapshot)
    }

    @Test
    fun progressionAndRecordsAfterRealSessions() = runTest {
        doSession(SeedData.templateC.id, load = 15.0, reps = 10, rir = 3)
        val running = sessions.start(SeedData.templateA.id)
        val previous = sessions.previousPerformance(SeedData.chestPress.id, running)
        assertNotNull(previous)
        assertEquals(
            Suggestion.Load(17.5),
            ProgressionRule.suggest(SeedData.chestPress.type, SeedData.chestPress.loadIncrementKg, previous),
        )
        sessions.delete(running)

        val heavier = doSession(SeedData.templateC.id, load = 17.5, reps = 10)
        val report = sessions.buildReport(heavier)!!
        val chest = report.exercises.first { it.exerciseId == SeedData.chestPress.id }
        assertEquals(17.5, chest.newRecords.loadKg!!, 0.0)
        assertNotNull(report.previous)
        assertTrue(report.volume.totalKg > report.previous!!.volume.totalKg)
    }

    @Test
    fun skippedAndReorderedLinesArePersisted() = runTest {
        val id = sessions.start(SeedData.templateB.id)
        val lines = db.sessionDao().getSessionLines(id).map { it.line }
        sessions.setStatus(lines.last().id, SessionExerciseStatus.SKIPPED)
        sessions.moveLine(id, lines.last().id, -1)
        sessions.addExercise(id, SeedData.pecMachine.id)
        val after = db.sessionDao().getSessionLines(id).map { it.line }
        assertEquals(lines.last().id, after[4].id)
        assertEquals(SessionExerciseStatus.SKIPPED, after[4].status)
        assertEquals(SeedData.pecMachine.id, after.last().exerciseId)
        assertEquals(SeedData.pecMachine.defaultTargetSets, after.last().targetSets)
    }

    @Test
    fun replacingABusyMachineKeepsTheSlotAndTheTemplate() = runTest {
        val id = sessions.start(SeedData.templateC.id)
        val chestLine = db.sessionDao().getSessionLines(id).first { it.exercise.id == SeedData.chestPress.id }.line
        sessions.replaceExercise(id, chestLine, SeedData.pecMachine.id)
        val replaced = db.sessionDao().getSessionLines(id).first { it.line.id == chestLine.id }.line
        assertEquals(SeedData.pecMachine.id, replaced.exerciseId)
        assertEquals(SeedData.chestPress.id, replaced.replacedExerciseId)
        assertEquals(chestLine.targetSets, replaced.targetSets)
        assertTrue(templates.getLines(SeedData.templateC.id).any { it.exercise.id == SeedData.chestPress.id })

        // Pec machine prefill comes from its own history (seed: 10 kg).
        assertEquals(10.0, sessions.lastWorkingSets(SeedData.pecMachine.id, id).first().loadKg!!, 0.0)
        val report = run {
            sessions.logSet(SetEntryEntity(sessionId = id, exerciseId = SeedData.pecMachine.id, setIndex = 0, loadKg = 10.0, reps = 10, completedAt = Instant.EPOCH))
            sessions.finish(id, null, null)
            sessions.buildReport(id)!!
        }
        assertTrue(report.exercises.any { it.name == "Pec machine (remplace Chest press)" })
    }

    @Test
    fun replacingAfterSetsInsertsTheReplacementAfterTheOriginal() = runTest {
        val id = sessions.start(SeedData.templateC.id)
        val chestLine = db.sessionDao().getSessionLines(id).first { it.exercise.id == SeedData.chestPress.id }.line
        sessions.logSet(SetEntryEntity(sessionId = id, exerciseId = SeedData.chestPress.id, setIndex = 0, loadKg = 15.0, reps = 10, completedAt = Instant.EPOCH))
        sessions.replaceExercise(id, chestLine, SeedData.pecMachine.id)
        val lines = db.sessionDao().getSessionLines(id).map { it.line }
        val i = lines.indexOfFirst { it.id == chestLine.id }
        assertEquals(SessionExerciseStatus.DONE, lines[i].status)
        assertEquals(SeedData.pecMachine.id, lines[i + 1].exerciseId)
        assertEquals(lines.indices.toList(), lines.map { it.position })
    }

    @Test
    fun supersetIsCopiedFromTemplateAndDissolvedWhenSplit() = runTest {
        val lines = templates.getLines(SeedData.templateB.id).map { it.line }
        // Link "Chaise romaine" (index 3) with "Planche latérale" (index 4).
        templates.save(SeedData.templateB, lines.mapIndexed { i, l -> if (i == 3 || i == 4) l.copy(supersetGroup = 1) else l })
        val id = sessions.start(SeedData.templateB.id)
        val sessionLines = db.sessionDao().getSessionLines(id).map { it.line }
        assertEquals(listOf(null, null, null, 1, 1, null), sessionLines.map { it.supersetGroup })

        // Moving one member away breaks the pair.
        sessions.moveLine(id, sessionLines[4].id, 1)
        assertTrue(db.sessionDao().getSessionLines(id).all { it.line.supersetGroup == null })

        // Linking in the session itself.
        val first = db.sessionDao().getSessionLines(id).first().line
        sessions.toggleSuperset(id, first.id)
        assertEquals(listOf(1, 1, null, null, null, null), db.sessionDao().getSessionLines(id).map { it.line.supersetGroup })
    }

    @Test
    fun nextNoteIsStoredOnTheExerciseUntilCleared() = runTest {
        val repo = fr.olivier.forme.feature.sport.data.ExerciseRepository(db.exerciseDao(), context())
        repo.setNextNote(SeedData.chestPress.id, "  Monter la charge ")
        assertEquals("Monter la charge", repo.get(SeedData.chestPress.id)!!.nextNote)
        repo.setNextNote(SeedData.chestPress.id, "")
        assertNull(repo.get(SeedData.chestPress.id)!!.nextNote)
    }

    @Test
    fun bodyWeightKeepsOneEntryPerDay() = runTest {
        val repo = BodyWeightRepository(db.bodyWeightDao())
        val day = LocalDate.of(2026, 10, 1)
        repo.save(day, 82.0)
        repo.save(day, 81.4)
        repo.save(day.plusDays(1), 81.2)
        assertEquals(listOf(81.4, 81.2), repo.observeAll().first().map { it.weightKg })
    }
}
