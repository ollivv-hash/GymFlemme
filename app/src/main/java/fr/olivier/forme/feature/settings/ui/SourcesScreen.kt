package fr.olivier.forme.feature.settings.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import fr.olivier.forme.BuildConfig
import fr.olivier.forme.feature.settings.domain.Credit
import fr.olivier.forme.feature.settings.domain.Credits
import fr.olivier.forme.ui.components.FormeTopBar
import fr.olivier.forme.ui.components.SectionTitle

/** Config → Sources et licences (V2.10): the data and software shipped in the app, with their licences. */
@Composable
fun SourcesScreen(onBack: () -> Unit) {
    Scaffold(topBar = { FormeTopBar("Sources et licences", onBack) }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionTitle("Données")
            Credits.data.forEach { CreditCard(it) }
            SectionTitle("Logiciels")
            Credits.software.forEach { CreditCard(it) }
            Text(
                "GymFlemme ${BuildConfig.VERSION_NAME} · application personnelle, données gardées sur le téléphone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        }
    }
}

@Composable
private fun CreditCard(credit: Credit) {
    val uri = LocalUriHandler.current
    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
            Text(credit.title, style = MaterialTheme.typography.titleMedium)
            Text(credit.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(credit.usage, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
            Text("Licence : ${credit.license}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
            credit.note?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
        }
        Row(Modifier.padding(horizontal = 4.dp)) {
            TextButton(onClick = { uri.openUri(credit.url) }) { Text("Site") }
            TextButton(onClick = { uri.openUri(credit.licenseUrl) }) { Text("Licence") }
        }
    }
}
