package fr.olivier.forme.feature.nutrition

import fr.olivier.forme.feature.nutrition.domain.DishIngredient
import fr.olivier.forme.feature.nutrition.domain.DishMath
import fr.olivier.forme.feature.nutrition.domain.Per100
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DishMathTest {
    private val pasta = DishIngredient("Pâtes", 100.0, Per100(12.0, 360.0, 72.0, 1.5))
    private val tuna = DishIngredient("Thon", 100.0, Per100(25.0, 110.0, null, 1.0))

    @Test
    fun `values per 100 g of the raw ingredients, or of the cooked dish`() {
        val raw = DishMath.per100(listOf(pasta, tuna), cookedWeightG = null)!!
        assertEquals(18.5, raw.proteinG, 1e-9)
        assertEquals(235.0, raw.kcal!!, 1e-9)
        val cooked = DishMath.per100(listOf(pasta, tuna), cookedWeightG = 370.0)!!
        assertEquals(37.0 / 3.7, cooked.proteinG, 1e-9)
        assertEquals(DishMath.per100(listOf(pasta, tuna), cookedWeightG = 0.0), raw) // 0 = not weighed
    }

    @Test
    fun `an unknown value counts as zero unless unknown for every ingredient`() {
        assertEquals(72.0, DishMath.totals(listOf(pasta, tuna)).carbsG!!, 1e-9)
        assertNull(DishMath.totals(listOf(tuna)).carbsG)
    }

    @Test
    fun `a portion is the dish shared by its servings`() {
        assertEquals(100.0, DishMath.portion(listOf(pasta, tuna), null, 2), 1e-9)
        assertEquals(400.0, DishMath.portion(listOf(pasta, tuna), 400.0, 0), 1e-9) // at least one serving
        assertNull(DishMath.per100(emptyList(), null))
    }
}
