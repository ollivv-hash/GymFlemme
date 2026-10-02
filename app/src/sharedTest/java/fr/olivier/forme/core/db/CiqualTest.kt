package fr.olivier.forme.core.db

import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.olivier.forme.feature.nutrition.data.CiqualLoader
import fr.olivier.forme.feature.nutrition.data.NutritionRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CiqualTest {
    @Test
    fun embeddedTableLoadsAndSearchIgnoresAccents() = runTest {
        val db = testDatabase(TestClock())
        val foods = context().assets.open(CiqualLoader.ASSET).use(CiqualLoader::parse)
        assertTrue("expected the full Ciqual table, got ${foods.size}", foods.size > 3000)
        db.ciqualDao().insertAll(foods)
        assertEquals(foods.size, db.ciqualDao().count())

        val chicken = db.ciqualDao().search(NutritionRepository.ftsQuery("poulet filet cru")!!, 20)
        assertTrue(chicken.any { it.name == "Poulet, filet sans peau cru" })
        assertEquals(23.4, chicken.first { it.name == "Poulet, filet sans peau cru" }.proteinPer100g!!, 0.0)

        // "epinard" (no accent) must find "Épinard".
        val spinach = db.ciqualDao().search(NutritionRepository.ftsQuery("epinard")!!, 20)
        assertTrue(spinach.isNotEmpty() && spinach.all { it.name.contains("pinard", ignoreCase = true) })
        db.close()
    }
}
