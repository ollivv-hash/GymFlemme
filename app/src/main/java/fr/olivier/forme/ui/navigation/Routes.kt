package fr.olivier.forme.ui.navigation

import kotlinx.serialization.Serializable

@Serializable data object HomeRoute
@Serializable data object HistoryRoute
@Serializable data object ConfigRoute

@Serializable data object ActiveSessionRoute
@Serializable data class SessionDetailRoute(val sessionId: Long, val justFinished: Boolean = false)
@Serializable data class ExerciseHistoryRoute(val exerciseId: Long)

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
/** [fromHome] = opened from a home card: shown above home with a back arrow. */
@Serializable data class NutritionRoute(val tab: Int = 0, val fromHome: Boolean = false)
@Serializable data class AddFoodRoute(val epochDay: Long, val meal: String)
@Serializable data class ScanRoute(val epochDay: Long, val meal: String)
@Serializable data object FoodsRoute
/** foodId = 0 → new food, optionally linked to a scanned [barcode] and pre-filled with [name]. */
@Serializable data class FoodEditRoute(val foodId: Long = 0, val barcode: String = "", val name: String = "")
@Serializable data object ProfileRoute
