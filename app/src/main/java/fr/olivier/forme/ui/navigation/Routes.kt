package fr.olivier.forme.ui.navigation

import kotlinx.serialization.Serializable

@Serializable data object HomeRoute
@Serializable data object HistoryRoute
@Serializable data object ConfigRoute

@Serializable data object ActiveSessionRoute
@Serializable data class SessionDetailRoute(val sessionId: Long, val justFinished: Boolean = false)
@Serializable data class ExerciseHistoryRoute(val exerciseId: Long)
/** Écran Badges (V2.18), from the level card of the home screen. */
@Serializable data object BadgesRoute

@Serializable data object CatalogRoute
@Serializable data class ExerciseEditRoute(val exerciseId: Long = 0)
@Serializable data object TemplatesRoute
/** templateId = 0 → new template; sourceTemplateId / sourceSessionId → duplicate. */
@Serializable data class TemplateEditRoute(
    val templateId: Long = 0,
    val sourceTemplateId: Long = 0,
    val sourceSessionId: Long = 0,
)
@Serializable data object RotationRoute
@Serializable data object SettingsRoute

// --- Nutrition (V1.1) ---
/** [tab]: 0 = journal, 1 = body weight. */
@Serializable data class NutritionRoute(val tab: Int = 0)
@Serializable data class AddFoodRoute(val epochDay: Long, val meal: String)
@Serializable data class ScanRoute(val epochDay: Long, val meal: String)
@Serializable data object FoodsRoute
/** foodId = 0 → new food, optionally linked to a scanned [barcode] and pre-filled with [name]. */
@Serializable data class FoodEditRoute(val foodId: Long = 0, val barcode: String = "", val name: String = "")
@Serializable data object ProfileRoute

// --- Heart rate (V2.1) ---
@Serializable data object WatchTestRoute

// --- AI analysis (V2.4) ---
@Serializable data object AiSettingsRoute

// --- Exercise library (V2.8 – V2.9) ---
/** Sheet of a catalogue exercise ([exerciseId]) or of a library entry alone ([libraryId]). */
@Serializable data class ExerciseSheetRoute(val exerciseId: Long = 0, val libraryId: String = "")
/** Choice of an illustration; the chosen id is returned to the previous screen as [LIBRARY_PICK_KEY]. */
@Serializable data object LibraryPickerRoute
/** Config → Bibliothèque: browse the library and add entries to the catalogue (V2.10). */
@Serializable data object LibraryRoute
/** Config → Sources et licences (V2.10). */
@Serializable data object SourcesRoute
const val LIBRARY_PICK_KEY = "library_pick"
