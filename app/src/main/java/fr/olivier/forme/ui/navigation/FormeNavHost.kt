package fr.olivier.forme.ui.navigation

import fr.olivier.forme.core.update.LocalUpdateFeature
import fr.olivier.forme.feature.hydration.ui.DrinkFoodScreen
import fr.olivier.forme.feature.hydration.ui.WaterScreen
import fr.olivier.forme.feature.badges.ui.BadgeAnnouncer
import fr.olivier.forme.feature.badges.ui.BadgesScreen
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import fr.olivier.forme.ui.theme.LocalBento
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import fr.olivier.forme.R
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import fr.olivier.forme.core.launch.QuickAction
import fr.olivier.forme.feature.nutrition.domain.MealType
import fr.olivier.forme.feature.nutrition.ui.AddFoodScreen
import fr.olivier.forme.feature.nutrition.ui.FoodEditScreen
import fr.olivier.forme.feature.nutrition.ui.FoodsScreen
import fr.olivier.forme.feature.nutrition.ui.DishEditScreen
import fr.olivier.forme.feature.nutrition.domain.FoodSource
import fr.olivier.forme.feature.nutrition.ui.SupplementsScreen
import fr.olivier.forme.feature.nutrition.ui.NutritionScreen
import fr.olivier.forme.feature.nutrition.ui.ProfileScreen
import fr.olivier.forme.feature.nutrition.ui.ScanScreen
import fr.olivier.forme.feature.settings.ui.ConfigScreen
import fr.olivier.forme.feature.settings.ui.SettingsScreen
import fr.olivier.forme.feature.settings.ui.AboutScreen
import fr.olivier.forme.feature.onboarding.OnboardingScreen
import fr.olivier.forme.feature.sharing.TemplateImportScreen
import fr.olivier.forme.feature.sharing.TemplateScanScreen
import fr.olivier.forme.feature.settings.ui.AppearanceScreen
import fr.olivier.forme.feature.heart.ui.WatchTestScreen
import fr.olivier.forme.feature.library.ui.ExerciseSheetScreen
import fr.olivier.forme.feature.library.ui.LibraryPickerScreen
import fr.olivier.forme.feature.library.ui.LibraryScreen
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime

/** Tabs of the floating bar (V2.22; Réglages, the former Config, a tab since V2.23). Icons: Material Symbols. */
private data class TopLevel(val route: Any, val label: Int, val icon: Int)

private val topLevels = listOf(
    TopLevel(HomeRoute, R.string.nav_home, R.drawable.ic_nav_home),
    TopLevel(HistoryRoute, R.string.nav_sessions, R.drawable.ic_nav_sessions),
    TopLevel(NutritionRoute(), R.string.nav_nutrition, R.drawable.ic_nav_nutrition),
    TopLevel(BadgesRoute(), R.string.nav_badges, R.drawable.ic_nav_badges),
    TopLevel(ConfigRoute, R.string.nav_settings, R.drawable.ic_nav_settings),
)

@Composable
fun FormeNavHost(
    resumeOnStart: Boolean,
    hasActiveSession: suspend () -> Boolean,
    shouldShowOnboarding: suspend () -> Boolean = { false },
    /** V2.27: a template file opened from another app, or picked by "Importer un modèle". */
    templateToImport: String? = null,
    onTemplateHandled: () -> Unit = {},
    onPickTemplateFile: () -> Unit = {},
    pendingAction: QuickAction? = null,
    startProposedSession: (then: () -> Unit) -> Unit = { it() },
    onActionHandled: () -> Unit = {},
) {
    val nav = rememberNavController()
    val updates = LocalUpdateFeature.current
    val backStack by nav.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val showBar = topLevels.any { top -> destination?.hasRoute(top.route::class) == true }

    LaunchedEffect(Unit) {
        // V2.25: the first-launch wizard, over the home screen, on a new install only.
        if (resumeOnStart && shouldShowOnboarding()) {
            nav.currentBackStackEntryFlow.first()
            // Back on the main thread whatever the data reads resumed on (navigation must run there).
            withContext(Dispatchers.Main.immediate) { nav.navigate(OnboardingRoute) }
            return@LaunchedEffect
        }
        // A launch action (shortcut, widget) decides where to go instead.
        if (resumeOnStart && pendingAction == null && hasActiveSession()) {
            // The graph is set by NavHost on its first composition: wait for it before navigating.
            nav.currentBackStackEntryFlow.first()
            nav.navigate(ActiveSessionRoute)
        }
    }

    // Launcher shortcuts and widget (V2.6): opened above the home screen, so "back" returns to it.
    LaunchedEffect(templateToImport) {
        val uri = templateToImport ?: return@LaunchedEffect
        nav.currentBackStackEntryFlow.first()
        withContext(Dispatchers.Main.immediate) { nav.navigate(TemplateImportRoute(uri = uri)) }
        onTemplateHandled()
    }

    LaunchedEffect(pendingAction) {
        val action = pendingAction ?: return@LaunchedEffect
        nav.currentBackStackEntryFlow.first()
        fun open(route: Any) = nav.navigate(route) {
            popUpTo<HomeRoute>()
            launchSingleTop = true
        }
        val meal = MealType.at(LocalTime.now()).name
        when (action) {
            QuickAction.START_SESSION -> startProposedSession { open(ActiveSessionRoute) }
            QuickAction.ADD_MEAL -> open(AddFoodRoute(LocalDate.now().toEpochDay(), meal))
            QuickAction.SCAN -> open(ScanRoute(LocalDate.now().toEpochDay(), meal))
            QuickAction.OPEN_NUTRITION -> nav.switchTab(NutritionRoute(tab = 0))
            QuickAction.OPEN_HOME -> nav.switchTab(HomeRoute)
        }
        onActionHandled()
    }

    val onHome = destination?.hasRoute(HomeRoute::class) == true
    Scaffold(
        // Around the floating bar: the bento background on the home screen, the theme's elsewhere.
        containerColor = if (onHome) LocalBento.current.background else MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBar) {
                FloatingNavBar(
                    topLevels.map { top ->
                        NavEntry(stringResource(top.label), top.icon, destination?.hasRoute(top.route::class) == true) {
                            nav.navigateTopLevel(top.route)
                        }
                    },
                )
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = HomeRoute, modifier = Modifier.padding(bottom = padding.calculateBottomPadding())) {
            updates?.register(this, nav)
            composable<HomeRoute> {
                HomeScreen(
                    onOpenSession = { nav.navigate(ActiveSessionRoute) },
                    onOpenNutrition = { nav.switchTab(NutritionRoute(tab = 0)) },
                    onOpenBadges = { tab -> nav.switchTab(BadgesRoute(tab)) },
                    onOpenUpdate = { updates?.open(nav) },
                    onOpenWater = { nav.navigate(WaterRoute) },
                    onNeedsFood = { nav.navigate(DrinkFoodRoute(it)) },
                )
            }
            composable<WaterRoute> { WaterScreen(onBack = { nav.popBackStack() }, onNeedsFood = { nav.navigate(DrinkFoodRoute(it)) }) }
            composable<DrinkFoodRoute> {
                DrinkFoodScreen(
                    onDone = { nav.popBackStack() },
                    onScan = { drinkId -> nav.navigate(ScanRoute(LocalDate.now().toEpochDay(), MealType.SNACK.name, drinkId)) },
                )
            }
            composable<BadgesRoute> { BadgesScreen(onBack = { nav.popBackStack() }, initialTab = it.toRoute<BadgesRoute>().tab) }
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
                    onSaveAsDish = { date, meal -> nav.navigate(DishEditRoute(epochDay = date.toEpochDay(), meal = meal.name)) },
                    onScan = { date, meal -> nav.navigate(ScanRoute(date.toEpochDay(), meal.name)) },
                    onProfile = { nav.navigate(ProfileRoute) },
                    onOpenWater = { nav.navigate(WaterRoute) },
                )
            }
            composable<AddFoodRoute> {
                AddFoodScreen(
                    onBack = { nav.popBackStack() },
                    onScan = { date, meal -> nav.navigate(ScanRoute(date.toEpochDay(), meal.name)) },
                    onCreateFood = { nav.navigate(FoodEditRoute()) },
                    onCreateDish = { nav.navigate(DishEditRoute()) },
                    onEditFood = { food -> nav.navigate(if (food.source == FoodSource.DISH) DishEditRoute(food.id) else FoodEditRoute(foodId = food.id)) },
                )
            }
            composable<ScanRoute> { entry ->
                val linking = entry.toRoute<ScanRoute>().drinkId != 0L
                ScanScreen(
                    onBack = { nav.popBackStack() },
                    // V2.29: the product of a drink chosen, back to the screen that asked for it.
                    onAdded = { if (linking) nav.popBackStack<DrinkFoodRoute>(inclusive = true) else nav.popBackStack() },
                    onCreateFood = { barcode, name -> nav.navigate(FoodEditRoute(barcode = barcode, name = name)) },
                    onSearch = { date, meal ->
                        if (linking) nav.popBackStack() else nav.navigate(AddFoodRoute(date.toEpochDay(), meal.name)) { popUpTo<ScanRoute> { inclusive = true } }
                    },
                )
            }
            composable<SupplementsRoute> { SupplementsScreen(onBack = { nav.popBackStack() }) }
            composable<FoodsRoute> {
                FoodsScreen(
                    onBack = { nav.popBackStack() },
                    onEdit = { nav.navigate(FoodEditRoute(foodId = it)) },
                    onEditDish = { nav.navigate(DishEditRoute(it)) },
                )
            }
            composable<DishEditRoute> { DishEditScreen(onDone = { nav.popBackStack() }) }
            composable<FoodEditRoute> { FoodEditScreen(onDone = { nav.popBackStack() }) }
            composable<ProfileRoute> { ProfileScreen(onDone = { nav.popBackStack() }) }
            composable<ConfigRoute> {
                ConfigScreen(
                    onCatalog = { nav.navigate(CatalogRoute) },
                    onLibrary = { nav.navigate(LibraryRoute) },
                    onSources = { nav.navigate(SourcesRoute) },
                    onTemplates = { nav.navigate(TemplatesRoute) },
                    onRotation = { nav.navigate(RotationRoute) },
                    onSettings = { nav.navigate(SettingsRoute) },
                    onAppearance = { nav.navigate(AppearanceRoute) },
                    onProfile = { nav.navigate(ProfileRoute) },
                    onFoods = { nav.navigate(FoodsRoute) },
                    onSupplements = { nav.navigate(SupplementsRoute) },
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
                    onSheet = { nav.navigate(ExerciseSheetRoute(exerciseId = it)) },
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
            composable<ExerciseHistoryRoute> {
                ExerciseHistoryScreen(onBack = { nav.popBackStack() }, onSheet = { nav.navigate(ExerciseSheetRoute(exerciseId = it)) })
            }
            composable<CatalogRoute> {
                CatalogScreen(
                    onBack = { nav.popBackStack() },
                    onEdit = { nav.navigate(ExerciseEditRoute(it)) },
                    onHistory = { nav.navigate(ExerciseHistoryRoute(it)) },
                    onSheet = { nav.navigate(ExerciseSheetRoute(exerciseId = it)) },
                )
            }
            composable<ExerciseEditRoute> { entry ->
                val picked by entry.savedStateHandle.getStateFlow<String?>(LIBRARY_PICK_KEY, null).collectAsState()
                ExerciseEditScreen(
                    onDone = { nav.popBackStack() },
                    onPickIllustration = { nav.navigate(LibraryPickerRoute) },
                    onOpenSheet = { nav.navigate(ExerciseSheetRoute(libraryId = it)) },
                    pickedLibraryId = picked,
                    onPickConsumed = { entry.savedStateHandle[LIBRARY_PICK_KEY] = null },
                )
            }
            composable<ExerciseSheetRoute> {
                ExerciseSheetScreen(onBack = { nav.popBackStack() }, onEdit = { nav.navigate(ExerciseEditRoute(it)) })
            }
            composable<LibraryPickerRoute> {
                LibraryPickerScreen(
                    onBack = { nav.popBackStack() },
                    onPick = { id ->
                        nav.previousBackStackEntry?.savedStateHandle?.set(LIBRARY_PICK_KEY, id)
                        nav.popBackStack()
                    },
                    onPreview = { nav.navigate(ExerciseSheetRoute(libraryId = it)) },
                )
            }
            composable<LibraryRoute> {
                LibraryScreen(
                    onBack = { nav.popBackStack() },
                    onSheet = { nav.navigate(ExerciseSheetRoute(libraryId = it)) },
                    onEdit = { nav.navigate(ExerciseEditRoute(it)) },
                )
            }
            composable<SourcesRoute> { AboutScreen(onBack = { nav.popBackStack() }, onOpenUpdate = { updates?.open(nav) }) }
            composable<TemplatesRoute> {
                TemplatesScreen(
                    onBack = { nav.popBackStack() },
                    onEdit = { nav.navigate(TemplateEditRoute(templateId = it)) },
                    onDuplicate = { nav.navigate(TemplateEditRoute(sourceTemplateId = it)) },
                    onCreate = { nav.navigate(TemplateEditRoute()) },
                    onRotation = { nav.navigate(RotationRoute) },
                    onImport = onPickTemplateFile,
                    onScan = { nav.navigate(TemplateScanRoute) },
                )
            }
            composable<TemplateImportRoute> { TemplateImportScreen(onDone = { nav.popBackStack() }) }
            composable<TemplateScanRoute> {
                TemplateScanScreen(
                    onBack = { nav.popBackStack() },
                    onPayload = { payload -> nav.navigate(TemplateImportRoute(payload = payload)) { popUpTo<TemplateScanRoute> { inclusive = true } } },
                )
            }
            composable<TemplateEditRoute> { TemplateEditScreen(onDone = { nav.popBackStack() }) }
            composable<RotationRoute> { RotationScreen(onBack = { nav.popBackStack() }) }
            composable<SettingsRoute> { SettingsScreen(onBack = { nav.popBackStack() }) }
            composable<OnboardingRoute> { OnboardingScreen(onDone = { nav.popBackStack() }) }
            composable<AppearanceRoute> { AppearanceScreen(onBack = { nav.popBackStack() }) }
            composable<WatchTestRoute> { WatchTestScreen(onBack = { nav.popBackStack() }) }
            composable<AiSettingsRoute> { AiSettingsScreen(onBack = { nav.popBackStack() }) }
        }
        // V2.18: new badges, announced once over any screen but the session in progress.
        BadgeAnnouncer(enabled = destination != null && destination.hasRoute(ActiveSessionRoute::class).not())
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
