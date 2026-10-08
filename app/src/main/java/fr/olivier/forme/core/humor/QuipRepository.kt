package fr.olivier.forme.core.humor

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.olivier.forme.feature.heart.domain.Sex
import fr.olivier.forme.feature.nutrition.data.ProfileDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Draws the variants of the humour catalogue (V2.15): never the same variant twice in a row for an entry (the last
 * index is kept in DataStore), neutral texts in serious mode, feminine agreement from the profile's sex.
 */
@Singleton
class QuipRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>,
    profileDao: ProfileDao,
) {
    val seriousMode: Flow<Boolean> = dataStore.data.map { it[SERIOUS_MODE] ?: false }

    val style: Flow<QuipStyle> = combine(seriousMode, profileDao.observe()) { serious, profile ->
        QuipStyle(serious = serious, feminine = profile?.sex == Sex.FEMALE)
    }

    suspend fun setSeriousMode(serious: Boolean) {
        dataStore.edit { it[SERIOUS_MODE] = serious }
    }

    /** Draws the variant to show for [quip] now, and remembers it so that the next draw differs. */
    suspend fun draw(quip: Quip): Int {
        val size = context.resources.getStringArray(quip.humor).size
        var index = 0
        dataStore.edit { prefs ->
            index = Quips.nextIndex(size, prefs[lastKey(quip)])
            prefs[lastKey(quip)] = index
        }
        return index
    }

    /** Draws a variant and words it at once (notifications and other texts built outside the UI). */
    suspend fun text(quip: Quip, vararg args: Any): String =
        Quips.text(context.resources, quip, draw(quip), style.first(), *args)

    private fun lastKey(quip: Quip) = intPreferencesKey("quip_last_${quip.name.lowercase()}")

    private companion object {
        val SERIOUS_MODE = booleanPreferencesKey("serious_mode")
    }
}
