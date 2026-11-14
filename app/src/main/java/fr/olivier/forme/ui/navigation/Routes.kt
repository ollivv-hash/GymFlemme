package fr.olivier.forme.ui.navigation

import kotlinx.serialization.Serializable

@Serializable data object HomeRoute
@Serializable data object HistoryRoute
@Serializable data object ConfigRoute

@Serializable data object ActiveSessionRoute
@Serializable data class SessionDetailRoute(val sessionId: Long, val justFinished: Boolean = false)
@Serializable data class ExerciseHistoryRoute(val exerciseId: Long)
/** Écran Badges (V2.18), from the level and challenge cards of the home screen; [tab]: 0 Paliers, 1 Ponctuels, 2 Défis. */
@Serializable data class BadgesRoute(val tab: Int = 0)

@Serializable data object CatalogRoute
@Serializable data class ExerciseEditRoute(val exerciseId: Long = 0)
@Serializable data object TemplatesRoute
/** V2.27: a shared template, from a file ([uri]) or a QR code ([payload]). */
@Serializable data class TemplateImportRoute(val uri: String? = null, val payload: String? = null)
@Serializable data object TemplateScanRoute
/** templateId = 0 → new template; sourceTemplateId / sourceSessionId → duplicate. */
@Serializable data class TemplateEditRoute(
    val templateId: Long = 0,
    val sourceTemplateId: Long = 0,
    val sourceSessionId: Long = 0,
)
@Serializable data object RotationRoute
/** First launch (V2.25). */
@Serializable data object OnboardingRoute
@Serializable data object SettingsRoute
/** Réglages → Apparence (V2.24). */
@Serializable data object AppearanceRoute

// --- Nutrition (V1.1) ---
/** [tab]: 0 = journal, 1 = body weight. */
@Serializable data class NutritionRoute(val tab: Int = 0)
@Serializable data class AddFoodRoute(val epochDay: Long, val meal: String)
/** [drinkId] (V2.29): the product scanned becomes that drink's product instead of being added to a meal. */
@Serializable data class ScanRoute(val epochDay: Long, val meal: String, val drinkId: Long = 0)
@Serializable data object FoodsRoute
@Serializable data object SupplementsRoute

/** V2.29: hydration. */
@Serializable data object WaterRoute
@Serializable data class DrinkFoodRoute(val drinkId: Long)
@Serializable data object HydrationSettingsRoute

/** V2.21: a dish to create (0) or edit; with [epochDay] and [meal], filled in from that meal of the journal. */
@Serializable data class DishEditRoute(val foodId: Long = 0, val epochDay: Long = 0, val meal: String = "")
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
/** Réglages → À propos (V2.23; "Sources et licences" since V2.10). */
@Serializable data object SourcesRoute
const val LIBRARY_PICK_KEY = "library_pick"
