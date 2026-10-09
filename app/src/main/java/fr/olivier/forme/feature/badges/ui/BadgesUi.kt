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
import fr.olivier.forme.feature.badges.data.award
import fr.olivier.forme.feature.badges.domain.Award
import fr.olivier.forme.feature.badges.domain.Badge
import fr.olivier.forme.feature.badges.domain.BadgeConfig
import fr.olivier.forme.feature.badges.domain.BadgeKind
import fr.olivier.forme.feature.badges.domain.Badges
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
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlin.math.floor

/** Awards as stored, keyed by award (unknown ids of a newer backup left out). */
private fun BadgeRepository.observeEarned(): Flow<Map<Award, BadgeEntity>> =
    observeAll().map { list -> list.mapNotNull { entity -> entity.award()?.let { it to entity } }.toMap() }

data class BadgeBoard(val earned: Map<Award, BadgeEntity> = emptyMap(), val progress: Map<Badge, Double> = emptyMap()) {
    val level: Level get() = Badges.level(Badges.points(earned.keys))
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BadgesViewModel @Inject constructor(repository: BadgeRepository) : ViewModel() {
    val board = repository.observeEarned()
        .mapLatest { earned -> BadgeBoard(earned, runCatching { Badges.progress(repository.history()) }.getOrDefault(emptyMap())) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BadgeBoard())
}

@HiltViewModel
class LevelViewModel @Inject constructor(repository: BadgeRepository) : ViewModel() {
    val level = repository.observeEarned().map { Badges.level(Badges.points(it.keys)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

private val dayFormat = DateTimeFormatter.ofPattern("d MMM yyyy", Fmt.locale)

private fun points(value: Int) = "%,d".format(Fmt.locale, value).replace(' ', ' ').replace(' ', ' ')

/** Level, title and progress towards the next level (home: opens the Badges screen). */
@Composable
fun LevelCard(onClick: (() -> Unit)? = null, modifier: Modifier = Modifier, viewModel: LevelViewModel = hiltViewModel()) {
    val level by viewModel.level.collectAsStateWithLifecycle()
    LevelSummary(level ?: return, onClick, modifier)
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
fun BadgesScreen(onBack: () -> Unit, viewModel: BadgesViewModel = hiltViewModel()) {
    val board by viewModel.board.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val style = rememberQuipStyle()
    Scaffold(topBar = { FormeTopBar("Badges", onBack = onBack) }) { padding ->
        Column(Modifier.padding(padding)) {
            LevelSummary(board.level, onClick = null, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Paliers") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Ponctuels") })
            }
            LazyColumn {
                if (tab == 0) {
                    items(Badge.entries.filter { it.kind == BadgeKind.TIERED }, key = { it.name }) { badge ->
                        TieredRow(badge, board, style)
                    }
                } else {
                    val (oneOff, secret) = Badge.entries.filter { it.kind != BadgeKind.TIERED }.partition { it.kind == BadgeKind.ONE_OFF }
                    items(oneOff + secret, key = { it.name }) { badge -> OneOffRow(badge, board.earned[Award(badge)], style) }
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
private fun EarnedOn(entity: BadgeEntity) {
    Text(
        "Obtenu le ${entity.earnedAt.format(dayFormat)}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// --- Announcement ---

/** What the single announcement shows: the new awards, the level reached if it went up, the retroactive title. */
data class BadgeNews(val awards: List<Award> = emptyList(), val retro: Boolean = false, val level: Level? = null, val levelUp: Boolean = false) {
    val visible get() = awards.isNotEmpty() || levelUp
}

@HiltViewModel
class BadgeAnnouncerViewModel @Inject constructor(private val repository: BadgeRepository) : ViewModel() {
    val news = combine(repository.observeEarned(), repository.retroPending, repository.announcedLevel) { earned, retro, announced ->
        val level = Badges.level(Badges.points(earned.keys))
        val unseen = earned.filterValues { !it.seen }.entries
            .sortedWith(compareBy({ it.value.earnedAt }, { it.key.badge.ordinal }, { it.key.tier?.number ?: 0 }))
            .map { it.key }
        BadgeNews(unseen, retro, level, levelUp = level.number > announced)
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
                    news.awards.size > 1 -> "Nouveaux badges"
                    news.awards.size == 1 -> "Nouveau badge"
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
            Button(onClick = viewModel::acknowledge, modifier = Modifier.align(Alignment.End).padding(top = 16.dp).testTag(BADGES_ACK_TAG)) {
                Text("Compris")
            }
        }
    }
}
