package fr.olivier.forme.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.olivier.forme.MainActivity
import fr.olivier.forme.core.db.FormeDatabase
import fr.olivier.forme.core.db.Migrations
import fr.olivier.forme.core.db.SeedCallback
import fr.olivier.forme.core.db.SeedData
import fr.olivier.forme.core.db.TestClock
import fr.olivier.forme.core.db.context
import fr.olivier.forme.feature.bodyweight.data.BodyWeightRepository
import fr.olivier.forme.feature.heart.data.HeartRepository
import fr.olivier.forme.feature.heart.domain.HeartSource
import fr.olivier.forme.feature.heart.domain.Sex
import fr.olivier.forme.feature.nutrition.data.SupplementIntakeEntity
import fr.olivier.forme.feature.nutrition.data.MealEntryEntity
import fr.olivier.forme.feature.nutrition.data.ProfileEntity
import fr.olivier.forme.feature.nutrition.domain.MealType
import fr.olivier.forme.feature.nutrition.domain.NutritionGoal
import fr.olivier.forme.feature.sport.data.SessionRepository
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** V2.2: calorie target (rest day + session), protein / whey advice, calorie goal in the profile, week nutrition. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class NutritionAdviceSmokeTest {
    @get:Rule
    val rule = createEmptyComposeRule()

    private fun shot(name: String) {
        val dir = File("build/smoke-screens").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use {
            rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun waitFor(text: String) = rule.waitUntilAtLeastOneExists(hasText(text, substring = true), 10_000)

    @Test
    fun calorieTargetWheyAdviceAndWeek() {
        val start = Instant.now().minus(Duration.ofMinutes(50))
        val clock = TestClock(start)
        val today = LocalDate.now()
        val db = Room.databaseBuilder(context(), FormeDatabase::class.java, FormeDatabase.NAME)
            .addMigrations(*Migrations.ALL)
            .addCallback(SeedCallback(clock))
            .build()
        runBlocking {
            db.profileDao().upsert(
                ProfileEntity(
                    birthYear = today.year - 46,
                    sex = Sex.MALE,
                    heightCm = 178,
                    nutritionGoal = NutritionGoal.BULK,
                    wheyProteinPer100g = 80.0,
                    wheyKcalPer100g = 380.0,
                ),
            )
            BodyWeightRepository(db.bodyWeightDao()).save(today, 80.0)
            fun meal(date: LocalDate, type: MealType, name: String, protein: Double, kcal: Double) = MealEntryEntity(
                date = date, mealType = type, foodId = null, name = name, quantityG = 200.0, proteinG = protein,
                kcal = kcal, carbsG = null, fatG = null, eatenAt = Instant.now(),
            )
            // A usual dinner of ~35 g of protein over the last days, and today's breakfast and lunch.
            (1..5).forEach { d -> db.mealDao().insert(meal(today.minusDays(d.toLong()), MealType.DINNER, "Poulet riz", 35.0, 700.0)) }
            db.mealDao().insert(meal(today, MealType.BREAKFAST, "Flocons d'avoine", 20.0, 550.0))
            db.mealDao().insert(meal(today, MealType.LUNCH, "Steak haché pâtes", 40.0, 900.0))
            val creatine = db.supplementDao().getByCode("creatine")!!
            db.supplementDao().upsertIntake(SupplementIntakeEntity(supplementId = creatine.id, date = today, amount = 5.0, takenAt = Instant.now()))

            // A 40-minute session today, with its heart rate (~300 kcal estimated).
            val sessions = SessionRepository(db, db.sessionDao(), db.templateDao(), db.exerciseDao(), clock)
            val id = sessions.start(SeedData.templateC.id)
            clock.now = start.plus(Duration.ofMinutes(40))
            sessions.finish(id, 4, null)
            val samples = (0 until 40 * 60 step 5).map { sec -> start.plusSeconds(sec.toLong()) to 125 + (sec / 60) % 20 }
            HeartRepository(db.sessionHeartDao(), db.sessionDao(), db.profileDao(), db.bodyWeightDao(), clock)
                .saveSession(id, samples, HeartSource.BLE)
        }
        db.close()

        ActivityScenario.launch(MainActivity::class.java).use {
            rule.waitForHome()
            rule.tab("Nutrition")
            waitFor("repos ")
            waitFor("protéines")
            shot("n5-calorie-target-advice")

            rule.onNode(hasText("Ajouter") and hasClickAction()).performScrollTo().performClick()
            waitFor("prot.)")
            shot("n6-after-whey")

            rule.onNode(hasText("Profil") and hasClickAction()).performClick()
            // The profile opens once loaded: wait for its form before scrolling (raced the navigation on CI).
            waitFor("Jour de repos (kcal)")
            rule.onNodeWithText("Objectif calories").performScrollTo()
            rule.onNodeWithText("Jour de repos (kcal)").performScrollTo()
            waitFor("Valeur conseillée")
            shot("n7-profile-calories")
            it.onActivity { a -> a.onBackPressedDispatcher.onBackPressed() }

            rule.tab("Séances")
            rule.onNode(hasText("Semaine") and hasClickAction()).performClick()
            waitFor("saisi")
            rule.onNodeWithText("Whey", substring = true).performScrollTo()
            shot("n8-week-nutrition")
        }
    }
}
