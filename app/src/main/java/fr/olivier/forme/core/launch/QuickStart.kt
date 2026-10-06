package fr.olivier.forme.core.launch

import fr.olivier.forme.feature.sport.data.SessionRepository
import fr.olivier.forme.feature.sport.data.TemplateRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** "Démarrer la séance" from a shortcut or the widget: the running session, otherwise the one the rotation proposes. */
@Singleton
class QuickStart @Inject constructor(
    private val sessions: SessionRepository,
    private val templates: TemplateRepository,
) {
    suspend fun startProposedSession() {
        if (sessions.observeActive().first() != null) return
        sessions.start(templates.observeProposed().first()?.id)
    }
}
