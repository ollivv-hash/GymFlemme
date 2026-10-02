package fr.olivier.forme.feature.settings.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import fr.olivier.forme.ui.components.FormeTopBar

@Composable
fun ConfigScreen(
    onCatalog: () -> Unit,
    onTemplates: () -> Unit,
    onRotation: () -> Unit,
    onSettings: () -> Unit,
    onProfile: () -> Unit,
    onFoods: () -> Unit,
) {
    Scaffold(topBar = { FormeTopBar("Configuration") }) { padding ->
        Column(Modifier.padding(padding)) {
            Entry("Catalogue des exercices", "Machines, réglages, photos, archivage", onCatalog)
            Entry("Modèles de séance", "Créer, dupliquer, modifier, archiver", onTemplates)
            Entry("Rotation", "Modèles de la rotation et leur ordre", onRotation)
            Entry("Profil nutrition", "Objectif protéines, whey, créatine et rappel", onProfile)
            Entry("Mes aliments", "Aliments perso, produits scannés, favoris", onFoods)
            Entry("Réglages et sauvegarde", "Repos par défaut, thème, export / import JSON", onSettings)
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
