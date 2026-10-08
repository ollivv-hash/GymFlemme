package fr.olivier.forme.feature.badges.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.olivier.forme.core.humor.QuipText
import fr.olivier.forme.core.humor.rememberFeminine
import fr.olivier.forme.feature.badges.data.BadgeEntity
import fr.olivier.forme.feature.badges.data.BadgeRepository
import fr.olivier.forme.feature.badges.domain.Badge
import fr.olivier.forme.feature.sport.domain.Fmt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/** Badges stored but not announced yet (earned today or yesterday). */
fun BadgeRepository.observeNew(): Flow<List<Badge>> = observeAll().map { list ->
    list.filterNot { it.seen }.mapNotNull { entity -> Badge.entries.firstOrNull { it.name == entity.id } }
}

@HiltViewModel
class BadgesViewModel @Inject constructor(repository: BadgeRepository) : ViewModel() {
    val earned = repository.observeAll()
        .map { list -> list.associateBy { it.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
}

private val dayFormat = DateTimeFormatter.ofPattern("d MMM yyyy", Fmt.locale)

/** Historique → Badges (V2.17): earned ones with their day and message, then those still to earn and how. */
@Composable
fun BadgesContent(viewModel: BadgesViewModel = hiltViewModel()) {
    val earned by viewModel.earned.collectAsStateWithLifecycle()
    val feminine = rememberFeminine()
    val (got, toGet) = Badge.entries.partition { it.name in earned }
    LazyColumn {
        item {
            Text(
                "${got.size} / ${Badge.entries.size} badges",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp),
            )
        }
        items(got.sortedBy { earned.getValue(it.name).earnedAt }, key = { it.name }) { badge ->
            EarnedRow(badge, earned.getValue(badge.name), feminine)
        }
        items(toGet, key = { it.name }) { badge ->
            val texts = BadgeTexts.of(badge)
            ListItem(
                leadingContent = { Text("🔒", style = MaterialTheme.typography.titleLarge) },
                headlineContent = { Text(stringResource(texts.name(feminine)), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                supportingContent = { Text(stringResource(texts.condition)) },
            )
        }
    }
}

@Composable
private fun EarnedRow(badge: Badge, entity: BadgeEntity, feminine: Boolean) {
    val texts = BadgeTexts.of(badge)
    val name = stringResource(texts.name(feminine))
    ListItem(
        leadingContent = { Text("🏅", style = MaterialTheme.typography.titleLarge) },
        headlineContent = { Text(name) },
        supportingContent = {
            Column {
                QuipText(texts.quip, name)
                Text(
                    "Obtenu le ${entity.earnedAt.format(dayFormat)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

/** New badges (recap and home screen) until "Compris". */
@Composable
fun NewBadgesCard(badges: List<Badge>, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    if (badges.isEmpty()) return
    val feminine = rememberFeminine()
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiary,
            contentColor = MaterialTheme.colorScheme.onTertiary,
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            badges.forEach { badge ->
                val texts = BadgeTexts.of(badge)
                val name = stringResource(texts.name(feminine))
                Text("🏅 $name", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
                QuipText(texts.quip, name, style = MaterialTheme.typography.bodyMedium)
            }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text("Compris", color = MaterialTheme.colorScheme.onTertiary)
            }
        }
    }
}
