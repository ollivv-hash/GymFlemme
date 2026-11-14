package fr.olivier.forme.feature.hydration

import fr.olivier.forme.feature.hydration.domain.GoalSettings
import fr.olivier.forme.feature.hydration.domain.HydrationMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HydrationMathTest {
    @Test
    fun `computed goal is the weight times 35 ml rounded to the nearest 100 ml`() {
        assertEquals(2_800, HydrationMath.computedGoal(80.0)) // 2 800
        assertEquals(2_900, HydrationMath.computedGoal(82.0)) // 2 870
        assertEquals(2_700, HydrationMath.computedGoal(78.5)) // 2 747.5
        assertEquals(2_800, HydrationMath.computedGoal(78.6)) // 2 751
        assertEquals(2_700, HydrationMath.computedGoal(76.0)) // 2 660
        assertEquals(2_500, HydrationMath.computedGoal(70.0)) // 2 450: half up
    }

    @Test
    fun `without a weight the goal is 2 litres`() {
        val goal = HydrationMath.goal(null, GoalSettings(), sessionDay = false)
        assertEquals(2_000, goal.totalMl)
        assertFalse(goal.hasWeight)
        assertEquals(2_000, HydrationMath.computedGoal(0.0))
    }

    @Test
    fun `a goal of one's own replaces the computed one, which stays known`() {
        val goal = HydrationMath.goal(82.0, GoalSettings(customEnabled = true, customMl = 2_500), sessionDay = false)
        assertEquals(2_900, goal.computedMl)
        assertEquals(2_500, goal.customMl)
        assertEquals(2_500, goal.totalMl)
        assertNull(HydrationMath.goal(82.0, GoalSettings(customEnabled = false, customMl = 2_500), false).customMl)
    }

    @Test
    fun `the session bonus adds to the computed goal and to one's own, unless it is off`() {
        assertEquals(3_400, HydrationMath.goal(82.0, GoalSettings(), sessionDay = true).totalMl)
        assertEquals(3_000, HydrationMath.goal(82.0, GoalSettings(customEnabled = true, customMl = 2_500), sessionDay = true).totalMl)
        assertEquals(3_650, HydrationMath.goal(82.0, GoalSettings(bonusMl = 750), sessionDay = true).totalMl)
        assertEquals(2_900, HydrationMath.goal(82.0, GoalSettings(bonusEnabled = false), sessionDay = true).totalMl)
        assertEquals(2_900, HydrationMath.goal(82.0, GoalSettings(), sessionDay = false).totalMl)
        assertEquals(2_500, HydrationMath.goal(null, GoalSettings(), sessionDay = true).totalMl)
    }

    @Test
    fun `settings stay within their bounds and steps`() {
        assertEquals(1_500, HydrationMath.coerceBonus(2_000))
        assertEquals(500, HydrationMath.coerceBonus(600))
        assertEquals(0, HydrationMath.coerceBonus(-10))
        assertEquals(10, HydrationMath.coerceFree(3))
        assertEquals(2_000, HydrationMath.coerceFree(5_000))
        assertEquals(330, HydrationMath.coerceFree(333))
        assertEquals(0.3, HydrationMath.coerceCoefficient(0.27), 1e-9)
        assertEquals(1.0, HydrationMath.coerceCoefficient(1.4), 1e-9)
        assertEquals(2_800, HydrationMath.coerceCustom(2_777))
    }

    @Test
    fun `volumes read in litres`() {
        assertEquals("1,8 L", HydrationMath.liters(1_800))
        assertEquals("3,4 L", HydrationMath.liters(3_400))
        assertEquals("2 L", HydrationMath.liters(2_000))
        assertEquals("750 ml", HydrationMath.liters(750))
        assertEquals("1,8 litre", HydrationMath.litersSpoken(1_800))
        assertEquals("3,4 litres", HydrationMath.litersSpoken(3_400))
        assertTrue(HydrationMath.counted(330, 0.0) == 0.0)
    }
}
