package fr.olivier.forme.feature.heart.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.olivier.forme.core.settings.ThemeMode
import fr.olivier.forme.ui.components.FormeTopBar
import fr.olivier.forme.ui.theme.FormeTheme

/** Shown by Health Connect ("why does this app need access?"); declared in the manifest, required on Android 14+. */
class HealthPermissionsRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FormeTheme(ThemeMode.SYSTEM) {
                Scaffold(topBar = { FormeTopBar("Données de santé", onBack = ::finish) }) { padding ->
                    Column(Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) {
                        Text(RATIONALE, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }

    private companion object {
        const val RATIONALE =
            "GymBro lit dans Health Connect la fréquence cardiaque et les calories enregistrées par ta montre, " +
                "uniquement pour les rattacher à tes séances de musculation (courbe, zones, calories dépensées).\n\n" +
                "Ces données restent sur ce téléphone : elles ne sont envoyées à aucun service. Elles sont incluses " +
                "dans tes sauvegardes (export JSON et sauvegarde automatique vers l'emplacement que tu as choisi).\n\n" +
                "GymBro n'écrit rien dans Health Connect. Tu peux retirer l'accès à tout moment dans " +
                "Paramètres → Health Connect → Autorisations des applications."
    }
}
