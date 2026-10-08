package fr.olivier.forme.core.humor

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.olivier.forme.R
import fr.olivier.forme.core.db.TestClock
import fr.olivier.forme.core.db.context
import fr.olivier.forme.core.db.testDatabase
import fr.olivier.forme.feature.heart.domain.Sex
import fr.olivier.forme.feature.nutrition.data.ProfileEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalTime
import kotlin.random.Random

/** V2.15: the humour catalogue is complete, and its variants are drawn without immediate repetition. */
@RunWith(AndroidJUnit4::class)
class QuipCatalogueTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val resources = context().resources
    private val db = testDatabase(TestClock())

    @After
    fun close() = db.close()

    /** Sample arguments of the parameterised entries. */
    private fun args(quip: Quip): Array<Any> = when (quip) {
        Quip.PROGRESSION, Quip.CREATINE -> arrayOf("2,5")
        Quip.PROTEIN_MISSING, Quip.PROTEIN_ALMOST, Quip.CREATINE_STREAK -> arrayOf(25)
        else -> emptyArray()
    }

    @Test
    fun everyHumourArrayHasVariantsANeutralVersionAndAnEntry() {
        val arrays = R.array::class.java.fields.map { it.name }.filter { it.startsWith("humor_") && !it.endsWith("_f") }
        val strings = R.string::class.java.fields.map { it.name }.toSet()
        arrays.forEach { name ->
            assertTrue("$name has no neutral version", "neutral_" + name.removePrefix("humor_") in strings)
        }
        assertEquals("every array is a Quip", arrays.toSet(), Quip.entries.map { resources.getResourceEntryName(it.humor) }.toSet())
        Quip.entries.forEach { quip ->
            val variants = resources.getStringArray(quip.humor)
            assertTrue("${quip.name} is empty", variants.isNotEmpty() && variants.none { it.isBlank() })
            assertTrue("${quip.name} neutral is blank", resources.getString(quip.neutral).isNotBlank())
            quip.humorFeminine?.let { assertEquals("${quip.name}: feminine variants not aligned", variants.size, resources.getStringArray(it).size) }
        }
    }

    @Test
    fun everyVariantFormatsWithItsArguments() {
        Quip.entries.forEach { quip ->
            val size = resources.getStringArray(quip.humor).size
            listOf(QuipStyle(), QuipStyle(feminine = true), QuipStyle(serious = true), QuipStyle(serious = true, feminine = true)).forEach { style ->
                (0 until size).forEach { i ->
                    val text = Quips.text(resources, quip, i, style, *args(quip))
                    assertTrue("${quip.name}[$i] $style: $text", text.isNotBlank() && "%" !in text)
                }
            }
        }
        assertEquals("Prête à monter d'un cran ? +2,5 kg.", Quips.text(resources, Quip.PROGRESSION, 3, QuipStyle(feminine = true), "2,5"))
        assertEquals("Augmentation de charge suggérée : +2,5 kg", Quips.text(resources, Quip.PROGRESSION, 3, QuipStyle(serious = true), "2,5"))
        assertEquals("Prête pour ta séance ?", Quips.text(resources, Quip.START, 0, QuipStyle(serious = true, feminine = true)))
    }

    @Test
    fun neverTheSameVariantTwiceInARow() {
        val random = Random(42)
        var last: Int? = null
        repeat(200) {
            val next = Quips.nextIndex(5, last, random)
            assertTrue(next in 0 until 5)
            assertNotEquals(last, next)
            last = next
        }
        assertEquals(0, Quips.nextIndex(1, 0, random))
    }

    @Test
    fun almostOnlyWhenLittleProteinIsMissing() {
        assertEquals(Quip.PROTEIN_MISSING, Quip.proteinMissing(148))
        assertEquals(Quip.PROTEIN_ALMOST, Quip.proteinMissing(30))
        assertTrue(resources.getStringArray(R.array.humor_protein_missing).none { "Presque" in it })
        assertTrue(resources.getStringArray(R.array.humor_protein_almost).any { "Presque" in it })
    }

    @Test
    fun greetingSlotsFollowTheLocalTime() {
        assertEquals(Quip.GREETING_NIGHT, Quip.greeting(LocalTime.of(4, 59)))
        assertEquals(Quip.GREETING_MORNING, Quip.greeting(LocalTime.of(5, 0)))
        assertEquals(Quip.GREETING_NOON, Quip.greeting(LocalTime.of(11, 0)))
        assertEquals(Quip.GREETING_AFTERNOON, Quip.greeting(LocalTime.of(14, 0)))
        assertEquals(Quip.GREETING_EVENING, Quip.greeting(LocalTime.of(18, 0)))
        assertEquals(Quip.GREETING_NIGHT, Quip.greeting(LocalTime.of(23, 0)))
    }

    @Test
    fun repositoryDrawsWithoutRepeatAndFollowsSeriousModeAndProfile() = runTest {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope) { File(tmp.root, "q.preferences_pb") }
        val quips = QuipRepository(context(), store, db.profileDao())
        val draws = List(20) { quips.draw(Quip.SET_DONE) }
        assertTrue(draws.zipWithNext().none { (a, b) -> a == b })

        db.profileDao().upsert(ProfileEntity(sex = Sex.FEMALE))
        quips.setSeriousMode(true)
        assertEquals("Prête pour ta séance ?", quips.text(Quip.START))
        quips.setSeriousMode(false)
        assertTrue(quips.text(Quip.SESSION_DONE) in resources.getStringArray(R.array.humor_session_done_f))
    }
}
