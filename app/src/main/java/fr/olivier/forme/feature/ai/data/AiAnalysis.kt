package fr.olivier.forme.feature.ai.data

import android.util.Log
import fr.olivier.forme.core.di.ApplicationScope
import fr.olivier.forme.core.settings.SettingsRepository
import fr.olivier.forme.feature.ai.domain.AnalysisMarkdown
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** "Analyse IA": builds the day's Markdown file and hands it to the chosen AI app. */
@Singleton
class AiAnalysis @Inject constructor(
    private val days: AiDayRepository,
    private val share: AiShare,
    private val settings: SettingsRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    suspend fun markdown(date: LocalDate): String =
        AnalysisMarkdown.build(days.day(date), settings.aiPrompt.first() ?: AnalysisMarkdown.DEFAULT_PROMPT)

    /**
     * Starts the analysis of [date] in the application scope: gathering the data takes a moment and must not be
     * cancelled when the user leaves the screen meanwhile.
     */
    fun launch(date: LocalDate) {
        scope.launch {
            runCatching { share(date) }.onFailure { Log.w(TAG, "Analyse IA impossible", it) }
        }
    }

    suspend fun share(date: LocalDate) {
        val mode = if (settings.aiShareAsText.first()) ShareMode.TEXT else ShareMode.FILE
        share.share(settings.aiApp.first(), mode, AnalysisMarkdown.fileName(date), markdown(date), LEAD)
    }

    companion object {
        private const val TAG = "AiAnalysis"
        const val LEAD = "Analyse le fichier joint en suivant les consignes qu'il contient."
    }
}
