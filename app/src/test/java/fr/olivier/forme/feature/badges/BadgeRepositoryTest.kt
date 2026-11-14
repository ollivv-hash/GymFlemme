package fr.olivier.forme.feature.badges

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.olivier.forme.core.db.SeedData
import fr.olivier.forme.core.db.TestClock
import fr.olivier.forme.core.db.testDatabase
import fr.olivier.forme.feature.badges.data.BadgeRepository
import fr.olivier.forme.feature.badges.data.BadgeSetRow
import fr.olivier.forme.feature.badges.data.ChallengeEntity
import fr.olivier.forme.feature.badges.data.award
import fr.olivier.forme.feature.badges.domain.Award
import fr.olivier.forme.feature.badges.domain.Badge
import fr.olivier.forme.feature.badges.domain.Badges
import fr.olivier.forme.feature.badges.domain.Challenge
import fr.olivier.forme.feature.badges.domain.ChallengeEligibility
import fr.olivier.forme.feature.badges.domain.Challenges
import fr.olivier.forme.feature.badges.domain.Tier
import fr.olivier.forme.feature.bodyweight.data.BodyWeightEntity
import fr.olivier.forme.feature.hydration.data.HydrationRepository
import fr.olivier.forme.feature.hydration.data.HydrationSettingsRepository
import fr.olivier.forme.feature.hydration.data.IntakeSource
import fr.olivier.forme.feature.nutrition.data.MealEntryEntity
import fr.olivier.forme.feature.nutrition.data.ProfileEntity
import fr.olivier.forme.feature.nutrition.domain.MealType
import fr.olivier.forme.feature.sport.data.CardioEntryEntity
import fr.olivier.forme.feature.sport.data.SessionRepository
import fr.olivier.forme.feature.sport.data.SetEntryEntity
import fr.olivier.forme.feature.sport.domain.ExerciseType
import fr.olivier.forme.feature.sport.domain.MuscleGroup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

/**
 * V2.18: the history already there is shown once in "Tes badges déjà gagnés", new badges are announced once, the
 * computation is idempotent; the rules of §6 read from the database (finished session, warm-ups, rower, proteins).
 */
@RunWith(AndroidJUnit4::class)
class BadgeRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val clock = TestClock()
    private val db = testDatabase(clock)
    private val sessions = SessionRepository(db, db.sessionDao(), db.templateDao(), db.exerciseDao(), clock)

    @After
    fun close() = db.close()

    private fun badges(scope: CoroutineScope) = BadgeRepository(
        db.badgeDao(),
        PreferenceDataStoreFactory.create(scope = scope) { File(tmp.root, "badges.preferences_pb") },
        clock,
    )

    private suspend fun chestPressSession(loadKg: Double, warmupKg: Double? = null) {
        val id = sessions.start(SeedData.templateC.id)
        clock.advance(Duration.ofMinutes(2))
        warmupKg?.let {
            sessions.logSet(SetEntryEntity(sessionId = id, exerciseId = SeedData.chestPress.id, setIndex = 0, loadKg = it, reps = 10, isWarmup = true, completedAt = clock.instant()))
        }
        sessions.logSet(SetEntryEntity(sessionId = id, exerciseId = SeedData.chestPress.id, setIndex = 1, loadKg = loadKg, reps = 10, completedAt = clock.instant()))
        clock.advance(Duration.ofMinutes(30))
        sessions.finish(id, 3, null)
    }

    private suspend fun stored() = db.badgeDao().getAll().associate { it.award()!! to it }

    @Test
    fun historyIsRecappedOnceThenNewBadgesAnnouncedOnce() = runTest {
        // First session at the seed's load: "Pas mort", but no record (the seed is the baseline).
        chestPressSession(15.0)
        val firstDay = LocalDate.now(clock)
        clock.advance(Duration.ofDays(9)) // a Saturday: no secret badge for a Sunday
        val repository = badges(backgroundScope)

        // First run of V2.18: the past badge is stored unseen, for the single recap.
        assertEquals(listOf(Award(Badge.NOT_DEAD)), repository.evaluate())
        assertTrue(repository.retroPending.first())
        assertEquals(firstDay, stored().getValue(Award(Badge.NOT_DEAD)).earnedAt)
        assertFalse(stored().getValue(Award(Badge.NOT_DEAD)).seen)

        // Idempotent: nothing new, no duplicate.
        assertEquals(emptyList<Award>(), repository.evaluate())
        assertEquals(1, db.badgeDao().getAll().size)

        repository.acknowledge(level = 1)
        assertFalse(repository.retroPending.first())
        assertTrue(db.badgeDao().getAll().all { it.seen })

        // Today: heavier than ever → first record, announced, once.
        chestPressSession(17.5)
        assertEquals(listOf(Award(Badge.FIRST_RECORD)), repository.evaluate())
        assertEquals(emptyList<Award>(), repository.evaluate())
        assertFalse(stored().getValue(Award(Badge.FIRST_RECORD)).seen)
        assertFalse(repository.retroPending.first())

        repository.acknowledge(level = 2)
        assertEquals(2, repository.announcedLevel.first())
    }

    @Test
    fun afterTheRecapOldBadgesOfARestoreStaySilent() = runTest {
        val repository = badges(backgroundScope)
        repository.evaluate() // first run, empty history
        chestPressSession(15.0)
        clock.advance(Duration.ofDays(10))
        assertEquals(emptyList<Award>(), repository.evaluate())
        assertTrue(stored().getValue(Award(Badge.NOT_DEAD)).seen)
        assertFalse(repository.retroPending.first())
    }

    @Test
    fun aSessionWithoutAnyEntryDoesNotCount() = runTest {
        val id = sessions.start(SeedData.templateC.id)
        clock.advance(Duration.ofMinutes(30))
        sessions.finish(id, null, null)
        val history = badges(backgroundScope).history()
        assertTrue(history.sessions.isEmpty())
    }

    @Test
    fun tonnageLeavesWarmupsOutAndRowerCountsItsDistance() = runTest {
        chestPressSession(40.0, warmupKg = 20.0)
        val id = sessions.start(null)
        sessions.saveCardio(CardioEntryEntity(sessionId = id, exerciseId = SeedData.rower.id, durationSec = 600, distanceM = 2_000))
        clock.advance(Duration.ofMinutes(15))
        sessions.finish(id, null, null)

        val history = badges(backgroundScope).history()
        assertEquals(2, history.sessions.size)
        assertEquals(400.0, history.liftedKg.sumOf { it.second }, 0.0)
        assertEquals(listOf(LocalDate.now(clock) to 2_000), history.rowedMetres)
        assertEquals(setOf(SeedData.chestPress.id, SeedData.rower.id), history.exercisesDone.map { it.first }.toSet())
    }

    @Test
    fun proteinDaysUseTheProfileAndTheWeightOfTheDay() = runTest {
        val today = LocalDate.now(clock)
        db.profileDao().upsert(ProfileEntity(proteinTargetGPerKg = 2.0))
        db.bodyWeightDao().upsert(BodyWeightEntity(date = today.minusDays(1), weightKg = 75.0))
        fun meal(day: LocalDate, protein: Double) = MealEntryEntity(
            date = day, mealType = MealType.LUNCH, foodId = null, name = "Poulet", quantityG = 100.0, proteinG = protein,
            kcal = null, carbsG = null, fatG = null, eatenAt = clock.instant(),
        )
        db.mealDao().insert(meal(today.minusDays(2), 100.0))
        db.mealDao().insert(meal(today.minusDays(2), 50.0)) // 150 g ≥ 2 × 75
        db.mealDao().insert(meal(today, 149.0))
        assertEquals(listOf(today.minusDays(2)), badges(backgroundScope).history().proteinDays)
    }

    @Test
    fun tiersAreStoredOnePerRow() = runTest {
        val repository = badges(backgroundScope)
        repeat(10) {
            chestPressSession(15.0)
            clock.advance(Duration.ofDays(1))
        }
        repository.evaluate()
        assertTrue(Award(Badge.REPEAT_OFFENDER, Tier.BRONZE) in stored())
        assertEquals(1, db.badgeDao().getAll().count { it.id == Badge.REPEAT_OFFENDER.name })
    }

    @Test
    fun theChallengeOfTheMonthIsDrawnOnceFromTheData() = runTest {
        val repository = badges(backgroundScope)
        val october = YearMonth.of(2026, 10)
        // The seed has the rower and the A → B → C rotation; no meal in September.
        val eligibility = repository.eligibility(october)
        assertEquals(ChallengeEligibility(hasRower = true, mealsLastMonth = false, rotationSize = 3), eligibility)

        repository.evaluate()
        val eligible = Challenges.eligible(eligibility)
        val expected = Challenges.draw(october, eligible, Challenges.draw(october.minusMonths(1), eligible, null))
        assertEquals(listOf(ChallengeEntity("2026-10", expected!!.name)), db.badgeDao().getChallenges())
        // Kept as drawn, even when the data changes.
        db.exerciseDao().getAll().filter { it.name == "Rameur" }.forEach { db.exerciseDao().update(it.copy(archived = true)) }
        repository.evaluate()
        assertEquals(expected, repository.drawChallenge(october))
        assertEquals(1, db.badgeDao().getChallenges().size)
    }

    @Test
    fun aChallengeMetIsStoredAnnouncedAndWorthPoints() = runTest {
        val repository = badges(backgroundScope)
        db.badgeDao().insertChallenges(listOf(ChallengeEntity("2026-10", Challenge.TWELVE_STRIKES.name)))
        repeat(11) {
            chestPressSession(15.0)
            clock.advance(Duration.ofDays(1))
        }
        repository.evaluate()
        assertEquals(null, db.badgeDao().getChallenge("2026-10")!!.completedAt)

        chestPressSession(15.0)
        repository.evaluate()
        val met = db.badgeDao().getChallenge("2026-10")!!
        assertEquals(LocalDate.of(2026, 10, 12), met.completedAt)
        assertFalse(met.seen)
        repository.evaluate() // idempotent
        assertEquals(met, db.badgeDao().getChallenge("2026-10"))

        repository.acknowledge(level = 1)
        assertTrue(db.badgeDao().getChallenge("2026-10")!!.seen)
        assertEquals(20, Badges.points(emptyList(), challengesMet = 1))
    }

    /**
     * 2.19.4 (one pass per exercise): a record beats every earlier session, seed included, by load or by volume; an
     * equal load is not a record, and each exercise has its own bests.
     */
    @Test
    fun recordsAreCountedAgainstEveryEarlierSession() = runTest {
        fun row(session: Long, exercise: Long, load: Double, reps: Int, seed: Boolean = false, target: Int? = 8) = BadgeSetRow(
            session, Instant.ofEpochSecond(session * 86_400), seed, exercise, ExerciseType.WEIGHTED, MuscleGroup.entries.first(),
            load, reps, target,
        )
        val sets = listOf(
            row(1, 1, 40.0, 10, seed = true), // baseline only
            row(2, 1, 40.0, 10), // equal: no record
            row(3, 1, 42.5, 8), // load record
            row(4, 1, 30.0, 6), // below the 8-rep target: its load does not count, its volume is lower
            row(5, 1, 40.0, 10), row(5, 1, 40.0, 10), // volume record (800 > 400)
            row(6, 1, 42.5, 8), // nothing beaten
            row(2, 2, 20.0, 10), // first time on exercise 2: no record
            row(3, 2, 22.5, 10), // record of exercise 2, same day as exercise 1's: counted for each
        )
        assertEquals(
            listOf(3L, 5L, 3L).map { Instant.ofEpochSecond(it * 86_400) },
            badges(backgroundScope).recordDays(sets),
        )
    }

    /** V2.29: the water of each day against the goal frozen that day, read from the database. */
    @Test
    fun hydrationBadgesReadTheWaterAndTheFrozenGoals() = runTest {
        val water = HydrationRepository(
            db, db.hydrationDao(),
            HydrationSettingsRepository(PreferenceDataStoreFactory.create(scope = backgroundScope) { File(tmp.root, "water.preferences_pb") }),
            clock,
        )
        // Seven days in a row with 2 L (no weight: the goal is 2 L), then today 1 L.
        val start = clock.now
        repeat(7) { day ->
            clock.now = start.minus(Duration.ofDays(7L - day))
            water.add(1, 2_000, IntakeSource.WATER_SCREEN)
        }
        clock.now = start
        water.add(1, 1_000, IntakeSource.WATER_SCREEN)
        val repository = badges(backgroundScope)
        repository.evaluate()
        val earned = stored().keys
        assertTrue(Award(Badge.CAMEL_REPENTANT, Tier.BRONZE) in earned)
        assertTrue(Award(Badge.ENDLESS_SOURCE, Tier.SILVER) in earned)
        assertTrue(Award(Badge.OASIS) !in earned) // 2 L only
        // 7 days with water last month or this one: Hydraté is eligible next month only if 5 of them were last month.
        val history = repository.history()
        assertEquals(8, history.water.size)
        assertEquals(listOf(2_000), history.water.map { it.goalMl }.distinct())
    }
}
