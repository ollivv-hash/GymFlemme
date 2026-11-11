package fr.olivier.forme.feature.nutrition

import fr.olivier.forme.feature.nutrition.domain.DishItem
import fr.olivier.forme.feature.nutrition.domain.DishList
import fr.olivier.forme.feature.nutrition.domain.DishSort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** V2.26: Mes plats, pinned first, then most used / recent / A → Z, searched without accents. */
class DishListTest {
    private fun at(day: Int) = Instant.parse("2026-11-%02dT12:00:00Z".format(day))
    private val dishes = listOf(
        DishItem(1, "Poulet riz", pinned = false, useCount = 12, lastUsedAt = at(1)),
        DishItem(2, "Pâtes bolo", pinned = false, useCount = 3, lastUsedAt = at(10)),
        DishItem(3, "Chili", pinned = true, useCount = 1, lastUsedAt = at(2)),
        DishItem(4, "Bol avoine", pinned = false, useCount = 12, lastUsedAt = null),
    )

    private fun names(sort: DishSort, query: String = "") = DishList.arrange(dishes, sort, query) { it }.map { it.name }

    @Test
    fun pinnedFirstThenMostUsedTiesByName() {
        assertEquals(listOf("Chili", "Bol avoine", "Poulet riz", "Pâtes bolo"), names(DishSort.MOST_USED))
    }

    @Test
    fun recentNeverUsedLast() {
        assertEquals(listOf("Chili", "Pâtes bolo", "Poulet riz", "Bol avoine"), names(DishSort.RECENT))
    }

    @Test
    fun alphabeticalIgnoresAccentsAndCase() {
        assertEquals(listOf("Chili", "Bol avoine", "Pâtes bolo", "Poulet riz"), names(DishSort.ALPHABETICAL))
    }

    @Test
    fun theSearchIsLimitedToTheDishesAndIgnoresAccents() {
        assertEquals(listOf("Pâtes bolo"), names(DishSort.MOST_USED, "pates"))
        assertEquals(listOf("Bol avoine", "Pâtes bolo"), names(DishSort.ALPHABETICAL, "BOL"))
        assertTrue(names(DishSort.MOST_USED, "pizza").isEmpty())
    }
}
