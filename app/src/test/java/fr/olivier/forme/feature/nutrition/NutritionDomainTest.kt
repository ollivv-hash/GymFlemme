package fr.olivier.forme.feature.nutrition

import fr.olivier.forme.feature.nutrition.data.NutritionRepository
import fr.olivier.forme.feature.nutrition.data.OffResult
import fr.olivier.forme.feature.nutrition.data.OpenFoodFactsClient
import fr.olivier.forme.feature.nutrition.domain.CreatineStreak
import fr.olivier.forme.feature.nutrition.domain.Macros
import fr.olivier.forme.feature.nutrition.domain.NutritionMath
import fr.olivier.forme.feature.nutrition.domain.Per100
import fr.olivier.forme.feature.nutrition.domain.WheyAdvice
import fr.olivier.forme.feature.nutrition.domain.WheyMoment
import fr.olivier.forme.feature.nutrition.reminder.SupplementReminder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class NutritionDomainTest {
    @Test
    fun `values scale with quantity and unknown values stay unknown`() {
        val m = NutritionMath.forQuantity(Per100(23.4, 110.0, null, 1.5), 150.0)
        assertEquals(35.1, m.proteinG, 1e-9)
        assertEquals(165.0, m.kcal!!, 1e-9)
        assertNull(m.carbsG)
        assertEquals(2.25, m.fatG!!, 1e-9)
    }

    @Test
    fun `day totals and energy split`() {
        val totals = NutritionMath.dayTotals(listOf(Macros(30.0, 200.0, 10.0, 5.0), Macros(20.0, null, 40.0, 10.0)))
        assertEquals(50.0, totals.proteinG, 0.0)
        assertEquals(200.0, totals.kcal, 0.0)
        val split = NutritionMath.energySplit(totals)!!
        // 200 kcal protein, 200 kcal carbs, 135 kcal fat
        assertEquals(37, split.proteinPct)
        assertEquals(37, split.carbsPct)
        assertEquals(26, split.fatPct)
        assertNull(NutritionMath.energySplit(NutritionMath.dayTotals(emptyList())))
    }

    @Test
    fun `protein target uses the last weight and the profile factor`() {
        assertEquals(147.6, NutritionMath.proteinTarget(82.0, 1.8)!!, 1e-9)
        assertNull(NutritionMath.proteinTarget(null, 1.8))
    }

    @Test
    fun `whey moment`() {
        assertEquals(WheyMoment.DONE, WheyAdvice.moment(takenToday = true, trainingToday = true))
        assertEquals(WheyMoment.AFTER_SESSION, WheyAdvice.moment(takenToday = false, trainingToday = true))
        assertEquals(WheyMoment.REST_DAY, WheyAdvice.moment(takenToday = false, trainingToday = false))
    }

    @Test
    fun `creatine streak counts consecutive days and is not broken before today's dose`() {
        val today = LocalDate.of(2026, 10, 2)
        val days = listOf(today.minusDays(1), today.minusDays(2), today.minusDays(3), today.minusDays(5))
        assertEquals(3, CreatineStreak.days(days, today))
        assertEquals(4, CreatineStreak.days(days + today, today))
        assertEquals(0, CreatineStreak.days(listOf(today.minusDays(2)), today))
    }

    @Test
    fun `reminder fires today if the time is still ahead, otherwise tomorrow`() {
        val zone = ZoneId.of("Europe/Paris")
        val morning = ZonedDateTime.of(2026, 10, 2, 9, 0, 0, 0, zone)
        assertEquals(ZonedDateTime.of(2026, 10, 2, 20, 0, 0, 0, zone), SupplementReminder.nextTrigger(morning, 20 * 60))
        val evening = ZonedDateTime.of(2026, 10, 2, 20, 0, 0, 0, zone)
        assertEquals(ZonedDateTime.of(2026, 10, 3, 20, 0, 0, 0, zone), SupplementReminder.nextTrigger(evening, 20 * 60))
    }

    @Test
    fun `search text becomes an accent-free prefix query`() {
        assertEquals("epinard* cuit*", NutritionRepository.ftsQuery("Épinard, cuit"))
        assertEquals("oeuf* dur*", NutritionRepository.ftsQuery("œuf dur"))
        assertNull(NutritionRepository.ftsQuery(" -- "))
    }

    @Test
    fun `open food facts product is parsed, missing product is not found`() {
        val body = """{"code":"3017624010701","status":1,"product":{"product_name_fr":"Skyr nature","brands":"Siggi's, Arla",
            "serving_quantity":"150","nutriments":{"proteins_100g":10.5,"energy-kcal_100g":63,"carbohydrates_100g":"4,1","fat_100g":0.2}}}"""
        val found = OpenFoodFactsClient.parse("3017624010701", body) as OffResult.Found
        assertEquals("Skyr nature", found.product.name)
        assertEquals("Siggi's", found.product.brand)
        assertEquals(10.5, found.product.proteinPer100g!!, 0.0)
        assertEquals(4.1, found.product.carbsPer100g!!, 1e-9)
        assertEquals(150.0, found.product.servingG!!, 0.0)
        assertTrue(OpenFoodFactsClient.parse("1", """{"status":0,"status_verbose":"product not found"}""") is OffResult.NotFound)
    }
}
