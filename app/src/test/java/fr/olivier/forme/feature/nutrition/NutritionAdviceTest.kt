package fr.olivier.forme.feature.nutrition

import fr.olivier.forme.feature.heart.domain.Sex
import fr.olivier.forme.feature.nutrition.domain.DayIntake
import fr.olivier.forme.feature.nutrition.domain.EnergyMath
import fr.olivier.forme.feature.nutrition.domain.MacroTargets
import fr.olivier.forme.feature.nutrition.domain.MealProtein
import fr.olivier.forme.feature.nutrition.domain.MealType
import fr.olivier.forme.feature.nutrition.domain.NutritionGoal
import fr.olivier.forme.feature.nutrition.domain.NutritionWeek
import fr.olivier.forme.feature.nutrition.domain.ProteinAdvice
import fr.olivier.forme.feature.nutrition.domain.UpcomingMeal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class NutritionAdviceTest {
    private val today = LocalDate.of(2026, 10, 7) // a Wednesday

    @Test
    fun mealOfTheHour() {
        assertEquals(MealType.BREAKFAST, MealType.at(LocalTime.of(7, 30)))
        assertEquals(MealType.LUNCH, MealType.at(LocalTime.of(11, 0)))
        assertEquals(MealType.SNACK, MealType.at(LocalTime.of(16, 0)))
        assertEquals(MealType.DINNER, MealType.at(LocalTime.of(18, 30)))
        assertEquals(MealType.DINNER, MealType.at(LocalTime.of(23, 50)))
    }

    @Test
    fun fatAndCarbTargetsFromCaloriesAndProtein() {
        // 2300 kcal, 160 g of protein (640 kcal): fat 30 % = 690 kcal = 77 g, carbs (2300 - 640 - 690) / 4 = 243 g.
        assertEquals(MacroTargets(fatG = 77, carbsG = 243), EnergyMath.macroTargets(2300, 160.0, 80.0, NutritionGoal.MAINTAIN))
        assertEquals(MacroTargets(fatG = 77, carbsG = 243), EnergyMath.macroTargets(2300, 160.0, 80.0, NutritionGoal.BULK))
        // Cut: fat 25 % = 64 g, more carbs left for training.
        assertEquals(MacroTargets(fatG = 64, carbsG = 271), EnergyMath.macroTargets(2300, 160.0, 80.0, NutritionGoal.CUT))
        // Very low calories: fat floor of 0.6 g/kg (60 g at 100 kg), carbs never negative.
        assertEquals(MacroTargets(fatG = 60, carbsG = 0), EnergyMath.macroTargets(1200, 200.0, 100.0, NutritionGoal.CUT))
        assertNull(EnergyMath.macroTargets(null, 160.0, 80.0, NutritionGoal.CUT))
        assertNull(EnergyMath.macroTargets(2300, null, 80.0, NutritionGoal.CUT))
    }

    @Test
    fun calorieTargetFromMifflinAndGoal() {
        // Male, 80 kg, 178 cm, 46 y: BMR = 800 + 1112.5 - 230 + 5 = 1687.5; × 1.4 = 2362.5.
        assertEquals(1687.5, EnergyMath.bmr(80.0, 178, 46, Sex.MALE), 0.01)
        assertEquals(1521.5, EnergyMath.bmr(80.0, 178, 46, Sex.FEMALE), 0.01)
        assertEquals(2360, EnergyMath.advisedBase(80.0, 178, 46, Sex.MALE, NutritionGoal.MAINTAIN))
        assertEquals(2600, EnergyMath.advisedBase(80.0, 178, 46, Sex.MALE, NutritionGoal.BULK))
        assertEquals(1890, EnergyMath.advisedBase(80.0, 178, 46, Sex.MALE, NutritionGoal.CUT))
        assertNull(EnergyMath.advisedBase(80.0, null, 46, Sex.MALE, NutritionGoal.CUT))
        assertNull(EnergyMath.advisedBase(80.0, 178, 46, Sex.MALE, null))

        val training = EnergyMath.target(2360, 312.4)!!
        assertEquals(2672, training.totalKcal)
        assertEquals(0, EnergyMath.target(2360, null)!!.sessionKcal)
        assertNull(EnergyMath.target(null, 300.0))
        assertTrue(EnergyMath.onTarget(2500.0, 2360))
        assertTrue(!EnergyMath.onTarget(2700.0, 2360))
    }

    @Test
    fun sessionCaloriesWithoutBasalMetabolism() {
        // 311 kcal estimated for 30 measured minutes; BMR 1687.5 kcal/day → 35.2 kcal already in the rest-day target.
        assertEquals(275.8, EnergyMath.netSessionKcal(311.0, fromWatch = false, measuredSeconds = 1800, bmrPerDay = 1687.5), 0.1)
        // The watch's active calories are already net.
        assertEquals(254.0, EnergyMath.netSessionKcal(254.0, fromWatch = true, measuredSeconds = 1800, bmrPerDay = 1687.5), 0.0)
        // Unknown metabolism: kept as is; never negative.
        assertEquals(311.0, EnergyMath.netSessionKcal(311.0, fromWatch = false, measuredSeconds = 1800, bmrPerDay = null), 0.0)
        assertEquals(0.0, EnergyMath.netSessionKcal(10.0, fromWatch = false, measuredSeconds = 3600, bmrPerDay = 2000.0), 0.0)
    }

    @Test
    fun usualMealsIgnoreWheyExtrasAndRareMeals() {
        val history = (1..5).flatMap { d ->
            val date = today.minusDays(d.toLong())
            listOf(
                MealProtein(date, MealType.DINNER, 20.0, false),
                MealProtein(date, MealType.DINNER, 15.0, false), // two dishes: 35 g that day
                MealProtein(date, MealType.SNACK, 24.0, true), // whey: not a habit of the snack
                MealProtein(date, MealType.EXTRA, 10.0, false),
            )
        } + listOf(
            MealProtein(today.minusDays(1), MealType.LUNCH, 40.0, false),
            MealProtein(today.minusDays(2), MealType.LUNCH, 40.0, false), // only 2 days: not a habit yet
        )
        assertEquals(mapOf(MealType.DINNER to 35.0), ProteinAdvice.usualProtein(history))
    }

    @Test
    fun upcomingMealsAreTheUsualOnesNotLoggedAndNotPast() {
        val usual = mapOf(MealType.LUNCH to 40.0, MealType.SNACK to 15.0, MealType.DINNER to 35.0)
        val logged = listOf(
            MealProtein(today, MealType.BREAKFAST, 20.0, false),
            MealProtein(today, MealType.SNACK, 24.0, true), // whey alone does not count as the snack
        )
        assertEquals(
            listOf(UpcomingMeal(MealType.SNACK, 15.0), UpcomingMeal(MealType.DINNER, 35.0)),
            ProteinAdvice.upcoming(logged, usual, LocalTime.of(16, 0)),
        )
        assertEquals(listOf(UpcomingMeal(MealType.DINNER, 35.0)), ProteinAdvice.upcoming(logged, usual, LocalTime.of(19, 0)))
        assertTrue(ProteinAdvice.upcoming(logged, usual, LocalTime.of(23, 0)).isEmpty())
    }

    @Test
    fun wheyDosesCoverTheGapUpToTwo() {
        val dinner = listOf(UpcomingMeal(MealType.DINNER, 35.0))

        val two = ProteinAdvice.plan(consumedG = 60.0, targetG = 157.0, upcoming = dinner, proteinPerDoseG = 24.0, dosesTaken = 0)
        assertEquals(2, two.doses)
        assertEquals(14.0, two.shortAfterG, 0.01)
        assertEquals(
            "Il te manque 97 g : ton dîner habituel apporte ~35 g → 2 doses de whey (48 g). " +
                "Encore ~14 g à trouver : skyr, fromage blanc, œufs, viande ou poisson.",
            ProteinAdvice.message(two),
        )

        val one = ProteinAdvice.plan(95.0, 157.0, dinner, 24.0, 0)
        assertEquals(1, one.doses)
        assertEquals(0.0, one.shortAfterG, 0.0)
        assertEquals("Il te manque 62 g : ton dîner habituel apporte ~35 g → 1 dose de whey (24 g).", ProteinAdvice.message(one))

        val mealsEnough = ProteinAdvice.plan(120.0, 157.0, dinner, 24.0, 0)
        assertEquals(0, mealsEnough.doses)
        assertEquals("Il te manque 37 g : ton dîner habituel apporte ~35 g, ça devrait suffire.", ProteinAdvice.message(mealsEnough))

        // Second dose already taken: no more whey, the rest comes from food.
        val capped = ProteinAdvice.plan(100.0, 157.0, emptyList(), 24.0, dosesTaken = 2)
        assertEquals(0, capped.doses)
        assertEquals(
            "Il te manque 57 g. Encore ~57 g à trouver : skyr, fromage blanc, œufs, viande ou poisson.",
            ProteinAdvice.message(capped),
        )

        val twoMeals = ProteinAdvice.plan(40.0, 157.0, listOf(UpcomingMeal(MealType.SNACK, 15.0)) + dinner, 24.0, 1)
        assertEquals(1, twoMeals.doses) // one already taken today
        assertTrue(ProteinAdvice.message(twoMeals).startsWith("Il te manque 117 g : tes repas à venir (collation, dîner) apportent ~50 g → 1 dose"))

        val reached = ProteinAdvice.plan(160.0, 157.0, dinner, 24.0, 0)
        assertTrue(reached.reached)
        assertEquals("Objectif protéines atteint.", ProteinAdvice.message(reached))

        // V2.15: under the headline quip, only what follows "Il te manque N g".
        assertEquals(
            "Ton dîner habituel apporte ~35 g → 2 doses de whey (48 g). Encore ~14 g à trouver : skyr, fromage blanc, œufs, viande ou poisson.",
            ProteinAdvice.detail(two),
        )
        assertEquals("Ton dîner habituel apporte ~35 g, ça devrait suffire.", ProteinAdvice.detail(mealsEnough))
        assertEquals("Encore ~57 g à trouver : skyr, fromage blanc, œufs, viande ou poisson.", ProteinAdvice.detail(capped))
        assertEquals("À prévoir : 2 doses de whey (48 g).", ProteinAdvice.detail(ProteinAdvice.plan(100.0, 150.0, emptyList(), 24.0, 0)))
        assertEquals(null, ProteinAdvice.detail(reached))

        // Whey not configured: no dose, only food.
        assertEquals(0, ProteinAdvice.plan(60.0, 157.0, dinner, null, 0).doses)
    }

    @Test
    fun weekSummaryCountsLoggedDaysOnly() {
        val monday = LocalDate.of(2026, 10, 5)
        val intakes = listOf(
            DayIntake(monday, 160.0, 2500.0, whey = true),
            DayIntake(monday.plusDays(1), 120.0, 1900.0, whey = false),
            DayIntake(monday.minusDays(1), 200.0, 4000.0, whey = true), // previous week
        )
        val week = NutritionWeek.of(
            weekStart = monday,
            today = today,
            intakes = intakes,
            creatineDates = listOf(monday, monday.plusDays(2), monday.minusDays(3)),
            proteinTargetG = 150.0,
            kcalTargetOf = { d -> if (d == monday) 2650 else 2360 },
        )
        assertEquals(3, week.elapsedDays)
        assertEquals(2, week.loggedDays)
        assertEquals(140.0, week.avgProteinG!!, 0.01)
        assertEquals(1, week.proteinTargetDays)
        assertEquals(2200.0, week.avgKcal!!, 0.01)
        assertEquals(1, week.kcalTargetDays) // 2500 vs 2650 ok; 1900 vs 2360 is 19 % short
        assertEquals(1, week.wheyDays)
        assertEquals(2, week.creatineDays)

        val past = NutritionWeek.of(monday.minusWeeks(1), today, emptyList(), emptyList(), null, null)
        assertEquals(7, past.elapsedDays)
        assertNull(past.avgProteinG)
        assertNull(past.proteinTargetDays)
        assertNull(past.kcalTargetDays)
    }
}
