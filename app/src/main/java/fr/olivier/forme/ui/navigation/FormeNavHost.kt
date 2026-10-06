package fr.olivier.forme.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import fr.olivier.forme.feature.nutrition.domain.MealType
import fr.olivier.forme.feature.nutrition.ui.AddFoodScreen
import fr.olivier.forme.feature.nutrition.ui.FoodEditScreen
import fr.olivier.forme.feature.nutrition.ui.FoodsScreen
import fr.olivier.forme.feature.nutrition.ui.NutritionScreen
import fr.olivier.forme.feature.nutrition.ui.ProfileScreen
import fr.olivier.forme.feature.nutrition.ui.ScanScreen
import fr.olivier.forme.feature.settings.ui.ConfigScreen
import fr.olivier.forme.feature.settings.ui.SettingsScreen
import fr.olivier.forme.feature.heart.ui.WatchTestScreen
import fr.olivier.forme.feature.sport.ui.catalog.CatalogScreen
import fr.olivier.forme.feature.sport.ui.catalog.ExerciseEditScreen
import fr.olivier.forme.feature.sport.ui.history.ExerciseHistoryScreen
import fr.olivier.forme.feature.sport.ui.history.HistoryScreen
import fr.olivier.forme.feature.sport.ui.history.SessionDetailScreen
import fr.olivier.forme.feature.sport.ui.home.HomeScreen
import fr.olivier.forme.feature.sport.ui.session.ActiveSessionScreen
import fr.olivier.forme.feature.sport.ui.templates.RotationScreen
import fr.olivier.forme.feature.sport.ui.templates.TemplateEditScreen
import fr.olivier.forme.feature.sport.ui.templates.TemplatesScreen
import fr.olivier.forme.feature.ai.ui.AiSettingsScreen
import kotlinx.coroutines.flow.first

private data class TopLevel(val route: Any, val label: String, val icon: ImageVector)

private val topLevels = listOf(
    TopLevel(HomeRoute, "Accueil", Icons.Filled.Home),
    TopLevel(HistoryRoute, "Historique", Icons.Filled.DateRange),
    TopLevel(NutritionRoute(), "Nutrition", Icons.Filled.Favorite),
    TopLevel(ConfigRoute, "Config", Icons.Filled.Settings),
)

@Composable
fun FormeNavHost(resumeOnStart: Boolean, hasActiveSession: suspend () -> Boolean) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val showBar = topLevels.any { top -> destination?.hasRoute(top.route::class) == true }

    LaunchedEffect(Unit) {
        if (resumeOnStart && hasActiveSession()) {
            // The graph is set by NavHost on its first composition: wait for it before navigating.
            nav.currentBackStackEntryFlow.first()
            nav.navigate(ActiveSessionRoute)
        }
    }

    Scaffold(
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    topLevels.forEach { top ->
                        NavigationBarItem(
                            selected = destination?.hasRoute(top.route::class) == true,
                            onClick = { nav.navigateTopLevel(top.route) },
                            icon = { Icon(top.icon, contentDescription = null) },
                            label = { Text(top.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = HomeRoute, modifier = Modifier.padding(bottom = padding.calculateBottomPadding())) {
            composable<HomeRoute> {
                HomeScreen(
                    onOpenSession = { nav.navigate(ActiveSessionRoute) },
                    onOpenSessionDetail = { nav.navigate(SessionDetailRoute(it)) },
                    onOpenWeight = { nav.switchTab(NutritionRoute(tab = 1)) },
                    onOpenNutrition = { nav.switchTab(NutritionRoute(tab = 0)) },
                )
            }
            composable<HistoryRoute> {
                HistoryScreen(
                    onOpenSession = { nav.navigate(SessionDetailRoute(it)) },
                    onOpenExercise = { nav.navigate(ExerciseHistoryRoute(it)) },
                )
            }
            composable<NutritionRoute> {
                NutritionScreen(
                    initialTab = it.toRoute<NutritionRoute>().tab,
                    onAddFood = { date, meal -> nav.navigate(AddFoodRoute(date.toEpochDay(), meal.name)) },
                    onScan = { date, meal -> nav.navigate(ScanRoute(date.toEpochDay(), meal.name)) },
                    onProfile = { nav.navigate(ProfileRoute) },
                )
            }
            composable<AddFoodRoute> {
                AddFoodScreen(
                    onBack = { nav.popBackStack() },
                    onScan = { date, meal -> nav.navigate(ScanRoute(date.toEpochDay(), meal.name)) },
                    onCreateFood = { nav.navigate(FoodEditRoute()) },
                )
            }
            composable<ScanRoute> {
                ScanScreen(
                    onBack = { nav.popBackStack() },
                    onAdded = { nav.popBackStack() },
                    onCreateFood = { barcode, name -> nav.navigate(FoodEditRoute(barcode = barcode, name = name)) },
                    onSearch = { date, meal ->
                        nav.navigate(AddFoodRoute(date.toEpochDay(), meal.name)) { popUpTo<ScanRoute> { inclusive = true } }
                    },
                )
            }
            composable<FoodsRoute> { FoodsScreen(onBack = { nav.popBackStack() }, onEdit = { nav.navigate(FoodEditRoute(foodId = it)) }) }
            composable<FoodEditRoute> { FoodEditScreen(onDone = { nav.popBackStack() }) }
            composable<ProfileRoute> { ProfileScreen(onDone = { nav.popBackStack() }) }
            composable<ConfigRoute> {
                ConfigScreen(
                    onCatalog = { nav.navigate(CatalogRoute) },
                    onTemplates = { nav.navigate(TemplatesRoute) },
                    onRotation = { nav.navigate(RotationRoute) },
                    onSettings = { nav.navigate(SettingsRoute) },
                    onProfile = { nav.navigate(ProfileRoute) },
                    onFoods = { nav.navigate(FoodsRoute) },
                    onWatch = { nav.navigate(WatchTestRoute) },
                    onAi = { nav.navigate(AiSettingsRoute) },
                )
            }
            composable<ActiveSessionRoute> {
                ActiveSessionScreen(
                    onBack = { nav.popBackStack() },
                    onFinished = { id ->
                        nav.navigate(SessionDetailRoute(id, justFinished = true)) {
                            popUpTo(ActiveSessionRoute) { inclusive = true }
                        }
                    },
                    onAbandoned = { nav.popBackStack() },
                )
            }
            composable<SessionDetailRoute> {
                val route = it.toRoute<SessionDetailRoute>()
                SessionDetailScreen(
                    onBack = { nav.popBackStack() },
                    onOpenExercise = { id -> nav.navigate(ExerciseHistoryRoute(id)) },
                    onCreateTemplate = { nav.navigate(TemplateEditRoute(sourceSessionId = route.sessionId)) },
                )
            }
            composable<ExerciseHistoryRoute> { ExerciseHistoryScreen(onBack = { nav.popBackStack() }) }
            composable<CatalogRoute> {
                CatalogScreen(
                    onBack = { nav.popBackStack() },
                    onEdit = { nav.navigate(ExerciseEditRoute(it)) },
                    onHistory = { nav.navigate(ExerciseHistoryRoute(it)) },
                )
            }
            composable<ExerciseEditRoute> { ExerciseEditScreen(onDone = { nav.popBackStack() }) }
            composable<TemplatesRoute> {
                TemplatesScreen(
                    onBack = { nav.popBackStack() },
                    onEdit = { nav.navigate(TemplateEditRoute(templateId = it)) },
                    onDuplicate = { nav.navigate(TemplateEditRoute(sourceTemplateId = it)) },
                    onCreate = { nav.navigate(TemplateEditRoute()) },
                    onRotation = { nav.navigate(RotationRoute) },
                )
            }
            composable<TemplateEditRoute> { TemplateEditScreen(onDone = { nav.popBackStack() }) }
            composable<RotationRoute> { RotationScreen(onBack = { nav.popBackStack() }) }
            composable<SettingsRoute> { SettingsScreen(onBack = { nav.popBackStack() }) }
            composable<WatchTestRoute> { WatchTestScreen(onBack = { nav.popBackStack() }) }
            composable<AiSettingsRoute> { AiSettingsScreen(onBack = { nav.popBackStack() }) }
        }
    }
}

/**
 * Switches to a bottom-bar tab with explicit arguments (e.g. the weight sub-tab from a home card).
 * Same stack handling as the bottom bar, so the "Accueil" button brings home back; the arguments
 * are applied instead of restoring the tab's saved state.
 */
private fun NavHostController.switchTab(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
    }
}

private fun NavHostController.navigateTopLevel(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
