package fr.olivier.forme.feature.settings

import fr.olivier.forme.feature.library.data.ExerciseLibraryLoader
import fr.olivier.forme.feature.settings.domain.Credits
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** V2.11: the credits name the library commit actually shipped, and every link is https. */
class CreditsTest {
    @Test
    fun libraryCreditMatchesTheShippedDataset() {
        val shortCommit = ExerciseLibraryLoader.DATASET_VERSION.substringAfter('+')
        assertTrue(Credits.LIBRARY_COMMIT.startsWith(shortCommit))
        // Same commit as the proof of licence kept next to the assets.
        val source = listOf("third_party", "../third_party").map { File(it, "open-exercise-illustrations/SOURCE.md") }.first { it.exists() }
        assertTrue(source.readText().contains(Credits.LIBRARY_COMMIT))
        assertTrue(Credits.data.first().license.startsWith("CC0"))
    }

    @Test
    fun everyCreditHasHttpsLinks() {
        (Credits.data + Credits.software).forEach {
            assertTrue(it.title, it.url.startsWith("https://") && it.licenseUrl.startsWith("https://"))
        }
    }
}
