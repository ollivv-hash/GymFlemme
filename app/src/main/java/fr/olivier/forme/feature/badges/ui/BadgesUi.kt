package fr.olivier.forme.feature.badges.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.olivier.forme.R
import fr.olivier.forme.core.humor.Quip
import fr.olivier.forme.core.humor.QuipStyle
import fr.olivier.forme.core.humor.QuipText
import fr.olivier.forme.core.humor.rememberQuipStyle
import fr.olivier.forme.feature.badges.data.BadgeEntity
import fr.olivier.forme.feature.badges.data.BadgeRepository
import fr.olivier.forme.feature.badges.data.ChallengeEntity
import fr.olivier.forme.feature.badges.data.award
import fr.olivier.forme.feature.badges.domain.Award
import fr.olivier.forme.feature.badges.domain.Badge
import fr.olivier.forme.feature.badges.domain.BadgeConfig
import fr.olivier.forme.feature.badges.domain.BadgeKind
import fr.olivier.forme.feature.badges.domain.Badges
import fr.olivier.forme.feature.badges.domain.Challenge
import fr.olivier.forme.feature.badges.domain.ChallengeProgress
import fr.olivier.forme.feature.badges.domain.Challenges
import fr.olivier.forme.feature.badges.domain.Level
import fr.olivier.forme.feature.badges.domain.Tier
import fr.olivier.forme.feature.sport.domain.Fmt
import fr.olivier.forme.ui.components.FormeTopBar
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlin.math.floor

/** Awards as stored, keyed by award (unknown ids of a newer backup left out). */
private fun BadgeRepository.observeEarned(): Flow<Map<Award, BadgeEntity>> =
    observeAll().map { list -> list.mapNotNull { entity -> entity.award()?.let { it to entity } }.toMap() }

/** A stored monthly challenge, readable (unknown ids of a newer backup left out). */
data class MonthChallenge(val month: YearMonth, val challenge: Challenge, val entity: ChallengeEntity)

private fun BadgeRepository.observeMonthChallenges(): Flow<List<MonthChallenge>> = observeChallenges().map { list ->
    list.mapNotNull { e -> Challenge.byId(e.challenge)?.let { MonthChallenge(YearMonth.parse(e.month), it, e) } }
}

/** The challenge of the current month and its progress. */
data class CurrentChallenge(val month: YearMonth, val challenge: Challenge, val progress: ChallengeProgress)

data class BadgeBoard(
    val earned: Map<Award, BadgeEntity> = emptyMap(),
    val progress: Map<Badge, Double> = emptyMap(),
    val challenges: List<MonthChallenge> = emptyList(),
    val current: CurrentChallenge? = null,
) {
    val level: Level get() = Badges.level(Badges.points(earned.keys, challenges.count { it.entity.completedAt != null }))
}

/** Badges, challenges and the progress of both, recomputed whenever the history or the awards change. */
@OptIn(ExperimentalCoroutinesApi::class)
private fun BadgeRepository.observeBoard(): Flow<BadgeBoard> =
    combine(observeEarned(), observeMonthChallenges(), observeHistoryChanges()) { earned, challenges, _ -> earned to challenges }
        .mapLatest { (earned, challenges) ->
            val history = runCatching { history() }.getOrNull()
            val month = currentMonth()
            val current = challenges.firstOrNull { it.month == month }?.let { c ->
                history?.let { CurrentChallenge(month, c.challenge, Challenges.progress(c.challenge, month, it)) }
            }
            BadgeBoard(earned, history?.let(Badges::progress).orEmpty(), challenges, current)
        }

@HiltViewModel
class BadgesViewModel @Inject constructor(repository: BadgeRepository) : ViewModel() {
    val board = repository.observeBoard().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BadgeBoard())
}

/** Home: the level and the challenge of the month. */
@HiltViewModel
class LevelViewModel @Inject constructor(repository: BadgeRepository) : ViewModel() {
    val board = repository.observeBoard().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

private val dayFormat = DateTimeFormatter.ofPattern("d MMM yyyy", Fmt.locale)

private fun points(value: Int) = "%,d".format(Fmt.locale, value).replace(' ', ' ').replace(' ', ' ')

/**
 * Home (V2.18 – V2.19): level, title and progress towards the next level, then the challenge of the month; each opens
 * the Badges screen ([onClick] with its tab).
 */
@Composable
fun LevelCard(onClick: ((tab: Int) -> Unit)? = null, modifier: Modifier = Modifier, viewModel: LevelViewModel = hiltViewModel()) {
    val board by viewModel.board.collectAsStateWithLifecycle()
    val current = board ?: return
    Column(modifier) {
        LevelSummary(current.level, onClick?.let { { it(0) } })
        current.current?.let { ChallengeSummary(it, onClick?.let { { it(2) } }, Modifier.padding(top = 12.dp)) }
    }
}

private fun challengeMeasure(challenge: Challenge, value: Double): String =
    if (challenge == Challenge.ROWER_OF_THE_MONTH) Fmt.number(floor(value / 100) / 10, 1) else Fmt.number(floor(value), 0)

@Composable
private fun ChallengeSummary(current: CurrentChallenge, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    val resources = LocalContext.current.resources
    val style = rememberQuipStyle()
    val (month, challenge, progress) = current
    Card(
        modifier = modifier.fillMaxWidth().let { m -> onClick?.let { m.clickable(onClick = it) } ?: m },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "🎯 ${ChallengeWording.ofMonth(resources, month)} · ${ChallengeWording.name(resources, challenge)}",
                style = MaterialTheme.typography.titleMedium,
            )
            val done = progress.completedOn
            if (done != null) {
                Text("✓ " + ChallengeWording.success(resources, challenge, style), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                Text(
                    "Réussi le ${done.format(dayFormat)} · +${BadgeConfig.CHALLENGE_POINTS} points",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 2.dp),
                )
            } else {
                Text(ChallengeWording.announce(resources, challenge, style), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                LinearProgressIndicator(
                    progress = { if (progress.goal > 0) (progress.value / progress.goal).toFloat().coerceIn(0f, 1f) else 0f },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                )
                Text(
                    "${challengeMeasure(challenge, progress.value)} / ${challengeMeasure(challenge, progress.goal)} ${stringResource(ChallengeTexts.of(challenge).unit)}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun LevelSummary(level: Level, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    val resources = LocalContext.current.resources
    val style = rememberQuipStyle()
    Card(
        modifier = modifier.fillMaxWidth().let { m -> onClick?.let { m.clickable(onClick = it) } ?: m },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            val title = BadgeWording.levelDisplay(resources, level.number, style)
            Text(
                if (style.serious) "🏅 $title" else "🏅 Niveau ${level.number} · $title",
                style = MaterialTheme.typography.titleMedium,
            )
            val next = level.next
            if (next != null) {
                LinearProgressIndicator(
                    progress = { ((level.points - level.floor).toFloat() / (next - level.floor)).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                )
                Text("${points(level.points)} / ${points(next)} points", style = MaterialTheme.typography.bodySmall)
            } else {
                Text("${points(level.points)} points · niveau maximal", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

/** Écran Badges (V2.18): the level, then the tiered badges and the one-off ones (secret ones hidden until earned). */
@Composable
fun BadgesScreen(onBack: () -> Unit, initialTab: Int = 0, viewModel: BadgesViewModel = hiltViewModel()) {
    val board by viewModel.board.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(initialTab) }
    val style = rememberQuipStyle()
    Scaffold(topBar = { FormeTopBar("Badges", onBack = onBack) }) { padding ->
        Column(Modifier.padding(padding)) {
            LevelSummary(board.level, onClick = null, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Paliers") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Ponctuels") })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Défis") })
            }
            LazyColumn {
                if (tab == 0) {
                    items(Badge.entries.filter { it.kind == BadgeKind.TIERED }, key = { it.name }) { badge ->
                        TieredRow(badge, board, style)
                    }
                } else if (tab == 1) {
                    val (oneOff, secret) = Badge.entries.filter { it.kind != BadgeKind.TIERED }.partition { it.kind == BadgeKind.ONE_OFF }
                    items(oneOff + secret, key = { it.name }) { badge -> OneOffRow(badge, board.earned[Award(badge)], style) }
                } else {
                    item { board.current?.let { ChallengeSummary(it, onClick = null, modifier = Modifier.padding(16.dp)) } }
                    val met = board.challenges.filter { it.entity.completedAt != null && it.month != board.current?.month }.sortedByDescending { it.month }
                    items(met, key = { it.month.toString() }) { ChallengeRow(it, style) }
                }
            }
        }
    }
}

private fun tierEmoji(tier: Tier?) = when (tier) {
    Tier.BRONZE -> "🥉"
    Tier.SILVER -> "🥈"
    Tier.GOLD -> "🥇"
    Tier.PLATINUM -> "💎"
    null -> "🔒"
}

private fun emoji(award: Award) = if (award.tier != null) tierEmoji(award.tier) else "🏅"

/** A measure as shown: tonnes and kilometres with one decimal, never rounded up to the next threshold. */
private fun measure(badge: Badge, value: Double): String {
    val shown = when (badge) {
        Badge.MOVER, Badge.GALLEY_SLAVE -> floor(value / 100) / 10
        else -> floor(value)
    }
    return Fmt.number(shown, 1)
}

@Composable
private fun TieredRow(badge: Badge, board: BadgeBoard, style: QuipStyle) {
    val resources = LocalContext.current.resources
    val texts = BadgeTexts.of(badge)
    val reached = Tier.entries.filter { Award(badge, it) in board.earned }
    val best = reached.maxByOrNull { it.number }
    val next = Tier.entries.firstOrNull { it !in reached }
    val name = BadgeWording.name(resources, badge, style.feminine)
    ListItem(
        leadingContent = { Text(tierEmoji(best), style = MaterialTheme.typography.titleLarge) },
        headlineContent = {
            Text(
                name + (best?.let { " — " + BadgeWording.tier(resources, it) } ?: ""),
                color = if (best == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
        },
        supportingContent = {
            Column {
                best?.let { tier ->
                    val award = Award(badge, tier)
                    Text(BadgeWording.message(resources, award, style))
                    EarnedOn(board.earned.getValue(award))
                }
                if (next != null) {
                    val threshold = BadgeConfig.TIERS.getValue(badge)[next.number - 1]
                    val value = board.progress[badge] ?: 0.0
                    Text(
                        "Prochain palier : ${BadgeWording.tier(resources, next)}",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    LinearProgressIndicator(
                        progress = { (value / threshold).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    )
                    Text("${measure(badge, value)} / ${measure(badge, threshold)} ${stringResource(texts.unit!!)}", style = MaterialTheme.typography.bodySmall)
                } else {
                    Text("Palier maximal atteint", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
                }
            }
        },
    )
}

@Composable
private fun OneOffRow(badge: Badge, earned: BadgeEntity?, style: QuipStyle) {
    val resources = LocalContext.current.resources
    val texts = BadgeTexts.of(badge)
    if (earned == null) {
        val secret = badge.kind == BadgeKind.SECRET
        ListItem(
            leadingContent = { Text(if (secret) "❔" else "🔒", style = MaterialTheme.typography.titleLarge) },
            headlineContent = {
                Text(
                    if (secret) stringResource(R.string.badge_secret) else BadgeWording.name(resources, badge, style.feminine),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            supportingContent = if (secret) null else ({ Text(stringResource(texts.condition!!)) }),
        )
        return
    }
    ListItem(
        leadingContent = { Text("🏅", style = MaterialTheme.typography.titleLarge) },
        headlineContent = { Text(BadgeWording.name(resources, badge, style.feminine)) },
        supportingContent = {
            Column {
                Text(BadgeWording.message(resources, Award(badge), style))
                EarnedOn(earned)
            }
        },
    )
}

@Composable
private fun ChallengeRow(met: MonthChallenge, style: QuipStyle) {
    val resources = LocalContext.current.resources
    ListItem(
        leadingContent = { Text("🎯", style = MaterialTheme.typography.titleLarge) },
        headlineContent = { Text("${ChallengeWording.badge(resources, met.month)} — ${ChallengeWording.name(resources, met.challenge)}") },
        supportingContent = {
            Column {
                Text(ChallengeWording.success(resources, met.challenge, style))
                Text(
                    "Réussi le ${met.entity.completedAt!!.format(dayFormat)} · +${BadgeConfig.CHALLENGE_POINTS} points",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@Composable
private fun EarnedOn(entity: BadgeEntity) {
    Text(
        "Obtenu le ${entity.earnedAt.format(dayFormat)}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// --- Announcement ---

/** What the single announcement shows: the new awards, the level reached if it went up, the retroactive title. */
data class BadgeNews(
    val awards: List<Award> = emptyList(),
    val retro: Boolean = false,
    val level: Level? = null,
    val levelUp: Boolean = false,
    val challenges: List<MonthChallenge> = emptyList(),
) {
    val visible get() = awards.isNotEmpty() || levelUp || challenges.isNotEmpty()
}

@HiltViewModel
class BadgeAnnouncerViewModel @Inject constructor(private val repository: BadgeRepository) : ViewModel() {
    val news = combine(
        repository.observeEarned(),
        repository.observeMonthChallenges(),
        repository.retroPending,
        repository.announcedLevel,
    ) { earned, challenges, retro, announced ->
        val met = challenges.filter { it.entity.completedAt != null }
        val level = Badges.level(Badges.points(earned.keys, met.size))
        val unseen = earned.filterValues { !it.seen }.entries
            .sortedWith(compareBy({ it.value.earnedAt }, { it.key.badge.ordinal }, { it.key.tier?.number ?: 0 }))
            .map { it.key }
        BadgeNews(unseen, retro, level, levelUp = level.number > announced, challenges = met.filterNot { it.entity.seen })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BadgeNews())

    fun acknowledge() = viewModelScope.launch {
        repository.acknowledge(news.value.level?.number ?: 1)
    }
}

const val BADGES_ACK_TAG = "badges_ack"

/**
 * The single announcement of new badges (V2.18), over any screen but the session in progress (no interruption
 * during the sets): several badges earned together are listed in one sheet, with the level-up if any. After the
 * update, the badges of the history are shown once, under "Tes badges déjà gagnés".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BadgeAnnouncer(enabled: Boolean, viewModel: BadgeAnnouncerViewModel = hiltViewModel()) {
    val news by viewModel.news.collectAsStateWithLifecycle()
    if (!enabled || !news.visible) return
    val resources = LocalContext.current.resources
    val style = rememberQuipStyle()
    ModalBottomSheet(onDismissRequest = viewModel::acknowledge, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                when {
                    news.retro -> stringResource(R.string.badge_recap_retro)
                    news.awards.size + news.challenges.size > 1 -> "Nouveaux badges"
                    news.awards.isNotEmpty() -> "Nouveau badge"
                    news.challenges.isNotEmpty() -> "Défi réussi"
                    else -> "Nouveau niveau"
                },
                style = MaterialTheme.typography.titleLarge,
            )
            val level = news.level
            if (news.levelUp && level != null) {
                QuipText(
                    Quip.LEVEL_UP,
                    level.number,
                    BadgeWording.levelTitle(resources, level.number, style.feminine),
                    key = level.number,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            news.awards.forEach { award ->
                Row(Modifier.padding(top = 12.dp)) {
                    Text(emoji(award), style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(BadgeWording.title(resources, award, style.feminine), style = MaterialTheme.typography.titleSmall)
                        Text(BadgeWording.message(resources, award, style), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            news.challenges.forEach { met ->
                Row(Modifier.padding(top = 12.dp)) {
                    Text("🎯", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "${ChallengeWording.badge(resources, met.month)} — ${ChallengeWording.name(resources, met.challenge)}",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(ChallengeWording.success(resources, met.challenge, style), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Button(onClick = viewModel::acknowledge, modifier = Modifier.align(Alignment.End).padding(top = 16.dp).testTag(BADGES_ACK_TAG)) {
                Text("Compris")
            }
        }
    }
}
