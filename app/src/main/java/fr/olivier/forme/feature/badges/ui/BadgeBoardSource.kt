package fr.olivier.forme.feature.badges.ui

import fr.olivier.forme.core.di.ApplicationScope
import fr.olivier.forme.feature.badges.data.BadgeRepository
import fr.olivier.forme.feature.badges.domain.Badges
import fr.olivier.forme.feature.badges.domain.Challenges
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.withIndex
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 2.19.4: the badges, challenges and their progress, computed once for the home screen and the Badges screen
 * (the history is read and computed off the main thread, see [BadgeRepository.history]). Changes coming in a burst
 * (sets of a session, a restored backup) are computed once, [DEBOUNCE_MS] after the last one; the first board at once.
 */
@Singleton
class BadgeBoardSource @Inject constructor(
    repository: BadgeRepository,
    @ApplicationScope scope: CoroutineScope,
) {
    /** Null until the first computation. */
    val board: StateFlow<BadgeBoard?> = repository.observeBoard()
        .stateIn(scope, SharingStarted.WhileSubscribed(STOP_AFTER_MS), null)

    private companion object {
        const val DEBOUNCE_MS = 300L
        const val STOP_AFTER_MS = 5_000L
    }

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    private fun BadgeRepository.observeBoard(): Flow<BadgeBoard> =
        combine(observeEarned(), observeMonthChallenges(), observeHistoryChanges()) { earned, challenges, _ -> earned to challenges }
            .withIndex()
            .debounce { if (it.index == 0) 0L else DEBOUNCE_MS } // the first board at once
            .mapLatest { (_, value) ->
                val (earned, challenges) = value
                val history = runCatching { history() }.getOrNull()
                val month = currentMonth()
                val current = challenges.firstOrNull { it.month == month }?.let { c ->
                    history?.let { CurrentChallenge(month, c.challenge, Challenges.progress(c.challenge, month, it)) }
                }
                BadgeBoard(earned, history?.let(Badges::progress).orEmpty(), challenges, current)
            }
}
