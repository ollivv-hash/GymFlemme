package fr.olivier.forme

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.compose.runtime.CompositionLocalProvider
import fr.olivier.forme.core.humor.LocalQuips
import fr.olivier.forme.core.humor.QuipRepository
import fr.olivier.forme.core.launch.QuickAction
import fr.olivier.forme.core.launch.QuickStart
import fr.olivier.forme.core.settings.SettingsRepository
import fr.olivier.forme.core.settings.Appearance
import fr.olivier.forme.core.settings.ThemeMode
import android.content.Context
import fr.olivier.forme.feature.onboarding.Onboarding
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.olivier.forme.feature.sport.data.SessionRepository
import fr.olivier.forme.ui.navigation.FormeNavHost
import fr.olivier.forme.ui.theme.FormeTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext context: Context,
    settings: SettingsRepository,
    private val sessions: SessionRepository,
    private val quickStart: QuickStart,
    private val onboarding: Onboarding,
) : ViewModel() {
    /** V2.25: the first-launch wizard, once, on a new install. */
    suspend fun shouldShowOnboarding(): Boolean = onboarding.shouldShow()

    val appearance: StateFlow<Appearance> =
        settings.appearance.stateIn(viewModelScope, SharingStarted.Eagerly, Appearance(mode = ThemeMode.DARK))

    init {
        // V2.24: the default theme now follows the system; an update keeps the dark one (installed, then updated).
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        viewModelScope.launch { settings.keepDarkThemeOnUpdate(updated = info.lastUpdateTime > info.firstInstallTime) }
    }

    /** A session still running at launch (process killed mid-workout) is resumed. */
    suspend fun hasActiveSession(): Boolean = sessions.observeActive().first() != null

    /** Launcher shortcut or widget action waiting for the navigation (V2.6). */
    private val _pendingAction = MutableStateFlow<QuickAction?>(null)
    val pendingAction: StateFlow<QuickAction?> = _pendingAction.asStateFlow()

    fun onIntent(intent: Intent?) {
        QuickAction.from(intent)?.let { _pendingAction.value = it }
        templateUri(intent)?.let { _templateToImport.value = it.toString() }
    }

    /** V2.27: a shared template opened from another app (file manager, messaging, mail) or picked in the app. */
    private val _templateToImport = MutableStateFlow<String?>(null)
    val templateToImport: StateFlow<String?> = _templateToImport.asStateFlow()

    fun importTemplate(uri: android.net.Uri) {
        _templateToImport.value = uri.toString()
    }

    fun templateHandled() {
        _templateToImport.value = null
    }

    private fun templateUri(intent: Intent?): android.net.Uri? = when (intent?.action) {
        Intent.ACTION_VIEW -> intent.data
        Intent.ACTION_SEND -> androidx.core.content.IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, android.net.Uri::class.java)
        else -> null
    }

    fun actionHandled() {
        _pendingAction.value = null
    }

    /** Starts the proposed session (or keeps the running one), then [then] on the main thread. */
    fun startProposedSession(then: () -> Unit) = viewModelScope.launch {
        quickStart.startProposedSession()
        then()
    }
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    @Inject lateinit var quips: QuipRepository

    /** V2.28: the GitHub update in the "github" variant, nothing in "play". */
    @Inject lateinit var updates: fr.olivier.forme.core.update.UpdateFeature

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // After a recreation (rotation), the launching action was already performed.
        if (savedInstanceState == null) handle(intent)
        if (savedInstanceState == null) updates.onAppStart()
        setContent {
            val appearance = viewModel.appearance.collectAsStateWithLifecycle()
            val pendingAction = viewModel.pendingAction.collectAsStateWithLifecycle()
            val templateToImport = viewModel.templateToImport.collectAsStateWithLifecycle()
            // "Importer un modèle": the system file picker (V2.27), any type: the content is checked on reading.
            val pickTemplate = androidx.activity.compose.rememberLauncherForActivityResult(
                androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
            ) { uri -> uri?.let(viewModel::importTemplate) }
            FormeTheme(appearance.value) {
                CompositionLocalProvider(LocalQuips provides quips, fr.olivier.forme.core.update.LocalUpdateFeature provides updates) {
                    FormeNavHost(
                        resumeOnStart = savedInstanceState == null,
                        hasActiveSession = viewModel::hasActiveSession,
                        shouldShowOnboarding = viewModel::shouldShowOnboarding,
                        templateToImport = templateToImport.value,
                        onTemplateHandled = viewModel::templateHandled,
                        onPickTemplateFile = { pickTemplate.launch(arrayOf("*/*")) },
                        pendingAction = pendingAction.value,
                        startProposedSession = { then -> viewModel.startProposedSession(then) },
                        onActionHandled = viewModel::actionHandled,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        // Lets the launcher rank the shortcuts by use.
        QuickAction.from(intent)?.let { ShortcutManagerCompat.reportShortcutUsed(this, it.name) }
        viewModel.onIntent(intent)
    }
}
