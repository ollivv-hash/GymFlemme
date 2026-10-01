package fr.olivier.forme

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.olivier.forme.core.settings.SettingsRepository
import fr.olivier.forme.core.settings.ThemeMode
import fr.olivier.forme.feature.sport.data.SessionRepository
import fr.olivier.forme.ui.navigation.FormeNavHost
import fr.olivier.forme.ui.theme.FormeTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    settings: SettingsRepository,
    private val sessions: SessionRepository,
) : ViewModel() {
    val themeMode: StateFlow<ThemeMode> =
        settings.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.DARK)

    /** A session still running at launch (process killed mid-workout) is resumed. */
    suspend fun hasActiveSession(): Boolean = sessions.observeActive().first() != null
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val theme = viewModel.themeMode.collectAsStateWithLifecycle()
            FormeTheme(theme.value) {
                FormeNavHost(
                    resumeOnStart = savedInstanceState == null,
                    hasActiveSession = viewModel::hasActiveSession,
                )
            }
        }
    }
}
