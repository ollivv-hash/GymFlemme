package fr.olivier.forme.core.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** V2.24: Réglages → Apparence, and the default theme moving from dark to "follow the system". */
class AppearanceSettingsTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun TestScope.settings() =
        SettingsRepository(PreferenceDataStoreFactory.create(scope = backgroundScope) { File(tmp.root, "s.preferences_pb") })

    @Test
    fun aNewInstallFollowsTheSystemInGreen() = runTest {
        val s = settings()
        s.keepDarkThemeOnUpdate(updated = false)
        assertEquals(Appearance(ThemeMode.SYSTEM, AccentColor.GREEN, false), s.appearance.first())
    }

    @Test
    fun anUpdateKeepsTheDarkThemeOnce() = runTest {
        val s = settings()
        s.keepDarkThemeOnUpdate(updated = true)
        assertEquals(ThemeMode.DARK, s.appearance.first().mode)
        // Chosen afterwards, the system theme stays: the check is done once.
        s.setThemeMode(ThemeMode.SYSTEM)
        s.keepDarkThemeOnUpdate(updated = true)
        assertEquals(ThemeMode.SYSTEM, s.appearance.first().mode)
    }

    @Test
    fun aThemeAlreadyChosenIsKept() = runTest {
        val s = settings()
        s.setThemeMode(ThemeMode.LIGHT)
        s.keepDarkThemeOnUpdate(updated = true)
        assertEquals(ThemeMode.LIGHT, s.appearance.first().mode)
    }

    @Test
    fun accentAndDynamicColours() = runTest {
        val s = settings()
        s.setAccent(AccentColor.LAVENDER)
        s.setDynamicColor(true)
        assertEquals(Appearance(ThemeMode.SYSTEM, AccentColor.LAVENDER, true), s.appearance.first())
        s.setAppearance(Appearance(ThemeMode.LIGHT, AccentColor.CORAL, false))
        assertEquals(Appearance(ThemeMode.LIGHT, AccentColor.CORAL, false), s.appearance.first())
    }
}
