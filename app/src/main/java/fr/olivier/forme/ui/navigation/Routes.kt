package fr.olivier.forme.ui.navigation

import kotlinx.serialization.Serializable

@Serializable data object HomeRoute
@Serializable data object HistoryRoute
@Serializable data object WeightRoute
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
