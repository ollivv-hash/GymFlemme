package fr.olivier.forme.feature.settings.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import fr.olivier.forme.R
import fr.olivier.forme.ui.components.FormeTopBar

@Composable
fun ConfigScreen(
    onCatalog: () -> Unit,
    onLibrary: () -> Unit,
    onTemplates: () -> Unit,
    onRotation: () -> Unit,
    onSettings: () -> Unit,
    onAppearance: () -> Unit,
    onProfile: () -> Unit,
    onFoods: () -> Unit,
    onSupplements: () -> Unit,
    onWatch: () -> Unit,
    onAi: () -> Unit,
    onSources: () -> Unit,
) {
    // The "Réglages" tab (V2.23): no back arrow, as the other tabs.
    Scaffold(topBar = { FormeTopBar(stringResource(R.string.settings_title)) }) { padding ->
        // Scrolls: the entries no longer fit a phone screen since the library entry (2.10.1).
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            Entry("Catalogue des exercices", "Machines, réglages, photos, archivage", onCatalog)
            Entry("Bibliothèque d'exercices", "Exercices illustrés : rechercher, filtrer, ajouter au catalogue", onLibrary)
            Entry("Modèles de séance", "Créer, dupliquer, modifier, archiver", onTemplates)
            Entry("Rotation", "Modèles de la rotation et leur ordre", onRotation)
            Entry("Profil", "Âge, sexe, FC max, objectifs (protéines, calories, entraînement), whey", onProfile)
            Entry("Mes aliments", "Aliments perso, produits scannés, favoris", onFoods)
            Entry("Compléments", "Créatine, oméga-3, vitamines… : actifs, quantité, rappels", onSupplements)
            Entry("Montre cardio", "Choisir la montre, tester la FC en direct et Health Connect", onWatch)
            Entry("Analyse IA", "App IA par défaut (Claude, ChatGPT…), test du partage", onAi)
            Entry(stringResource(R.string.appearance_title), stringResource(R.string.appearance_subtitle), onAppearance)
            Entry(stringResource(R.string.preferences_title), "Repos, ton, sauvegarde auto (Drive), export / import", onSettings)
            Entry(stringResource(R.string.about_title), stringResource(R.string.about_subtitle), onSources)
        }
    }
}

@Composable
private fun Entry(title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        modifier = Modifier.clickable(onClick = onClick),
    )
    HorizontalDivider()
}
