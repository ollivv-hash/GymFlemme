package fr.olivier.forme.feature.settings.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.olivier.forme.R
import fr.olivier.forme.core.settings.AccentColor
import fr.olivier.forme.core.settings.Appearance
import fr.olivier.forme.core.settings.SettingsRepository
import fr.olivier.forme.core.settings.ThemeMode
import fr.olivier.forme.ui.components.FormeTopBar
import fr.olivier.forme.ui.components.SectionTitle
import fr.olivier.forme.ui.theme.dynamicColorAvailable
import fr.olivier.forme.ui.theme.labelRes
import fr.olivier.forme.ui.theme.pair
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class AppearanceViewModel @Inject constructor(private val settings: SettingsRepository) : ViewModel() {
    val appearance = settings.appearance.stateIn(viewModelScope, SharingStarted.Eagerly, Appearance())

    fun setMode(mode: ThemeMode) = viewModelScope.launch { settings.setThemeMode(mode) }
    fun setAccent(accent: AccentColor) = viewModelScope.launch { settings.setAccent(accent) }
    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch { settings.setDynamicColor(enabled) }
}

fun accentSwatchTag(accent: AccentColor) = "accent_${accent.name}"

/**
 * Réglages → Apparence (V2.24): light / dark / follow the system, the accent of the bento design among 6, and the
 * dynamic colours of Android 12+ (the swatches greyed out then). Applied at once.
 */
@Composable
fun AppearanceScreen(onBack: () -> Unit, viewModel: AppearanceViewModel = hiltViewModel()) {
    val appearance by viewModel.appearance.collectAsStateWithLifecycle()
    Scaffold(topBar = { FormeTopBar(stringResource(R.string.appearance_title), onBack) }) { padding ->
        Column(Modifier.padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
            SectionTitle(stringResource(R.string.appearance_mode))
            ModeChips(appearance.mode, viewModel::setMode)

            SectionTitle(stringResource(R.string.appearance_accent))
            Text(
                stringResource(if (appearance.dynamicColor && dynamicColorAvailable) R.string.dynamic_color_active else R.string.appearance_accent_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AccentSwatches(appearance.accent, enabled = !(appearance.dynamicColor && dynamicColorAvailable), viewModel::setAccent)

            if (dynamicColorAvailable) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.dynamic_color)) },
                    supportingContent = { Text(stringResource(R.string.dynamic_color_hint)) },
                    trailingContent = { Switch(checked = appearance.dynamicColor, onCheckedChange = viewModel::setDynamicColor) },
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModeChips(mode: ThemeMode, onMode: (ThemeMode) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(ThemeMode.LIGHT to R.string.theme_light, ThemeMode.DARK to R.string.theme_dark, ThemeMode.SYSTEM to R.string.theme_system)
            .forEach { (m, label) -> FilterChip(selected = mode == m, onClick = { onMode(m) }, label = { Text(stringResource(label)) }) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccentSwatches(selected: AccentColor, enabled: Boolean, onAccent: (AccentColor) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(vertical = 12.dp).alpha(if (enabled) 1f else 0.38f),
    ) {
        AccentColor.entries.forEach { accent ->
            val pair = accent.pair()
            val name = stringResource(accent.labelRes)
            val isSelected = accent == selected
            val description = if (isSelected) stringResource(R.string.appearance_accent_selected, name) else name
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(48.dp)
                        .testTag(accentSwatchTag(accent))
                        .border(if (isSelected) BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface) else BorderStroke(0.dp, pair.accent), CircleShape)
                        .padding(if (isSelected) 5.dp else 0.dp)
                        .background(pair.accent, CircleShape)
                        .selectable(selected = isSelected, enabled = enabled, role = Role.RadioButton) { onAccent(accent) }
                        .semantics { contentDescription = description },
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) Icon(Icons.Filled.Check, contentDescription = null, tint = pair.onAccent)
                }
                Text(name, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}
