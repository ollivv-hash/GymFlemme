package fr.olivier.forme.core.backup

import fr.olivier.forme.feature.hydration.data.ContainerEntity
import fr.olivier.forme.feature.hydration.data.DrinkEntity
import fr.olivier.forme.feature.hydration.data.HydrationSettings
import fr.olivier.forme.feature.hydration.data.IntakeSource
import fr.olivier.forme.feature.hydration.data.ReminderSettings
import fr.olivier.forme.feature.hydration.data.WaterDayEntity
import fr.olivier.forme.feature.hydration.data.WaterIntakeEntity
import fr.olivier.forme.feature.hydration.domain.GoalSettings
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate

/**
 * V2.29 (backup v16): drinks, containers, intakes, the frozen goal of each day and the hydration settings (not the
 * Health Connect switch, which depends on a permission of the phone, nor the first-use card). Absent in older
 * files: the drinks and containers offered from the start, no intake, the settings left as they are.
 */
@Serializable
data class HydrationBackup(
    val drinks: List<DrinkDto> = emptyList(),
    val containers: List<ContainerDto> = emptyList(),
    val intakes: List<WaterIntakeDto> = emptyList(),
    val days: List<WaterDayDto> = emptyList(),
    val settings: HydrationSettingsDto? = null,
)

@Serializable
data class DrinkDto(
    val id: Long,
    val code: String? = null,
    val name: String,
    val icon: String,
    val hydrationCoefficient: Double,
    val linkedFoodId: Long? = null,
    val isCaloric: Boolean,
    val archived: Boolean = false,
    val sortOrder: Int,
)

@Serializable
data class ContainerDto(
    val id: Long,
    val name: String,
    val volumeMl: Int,
    val defaultDrinkId: Long,
    val isDefault: Boolean = false,
    val archived: Boolean = false,
    val sortOrder: Int,
)

@Serializable
data class WaterIntakeDto(
    val id: Long,
    val timestamp: Long,
    val date: String,
    val drinkId: Long,
    val volumeMl: Int,
    val coefficientApplied: Double,
    val linkedMealEntryId: Long? = null,
    val source: IntakeSource,
)

@Serializable
data class WaterDayDto(val date: String, val baseGoalMl: Int, val customGoalMl: Int? = null, val bonusMl: Int = 0, val sessionDay: Boolean = false)

@Serializable
data class HydrationSettingsDto(
    val customGoalEnabled: Boolean = false,
    val customGoalMl: Int = GoalSettings().customMl,
    val bonusEnabled: Boolean = true,
    val bonusMl: Int = GoalSettings().bonusMl,
    val remindersEnabled: Boolean = true,
    val reminderStartMinutes: Int = ReminderSettings().startMinutes,
    val reminderEndMinutes: Int = ReminderSettings().endMinutes,
    val reminderIntervalMinutes: Int = ReminderSettings().intervalMinutes,
    val skipAfterDrink: Boolean = true,
    val skipDuringSession: Boolean = true,
    val action1Ml: Int = ReminderSettings().action1Ml,
    val action2Ml: Int = ReminderSettings().action2Ml,
) {
    fun goal() = GoalSettings(customGoalEnabled, customGoalMl, bonusEnabled, bonusMl)

    fun reminders() = ReminderSettings(
        remindersEnabled, reminderStartMinutes, reminderEndMinutes, reminderIntervalMinutes, skipAfterDrink, skipDuringSession, action1Ml, action2Ml,
    )

    companion object {
        fun of(s: HydrationSettings) = HydrationSettingsDto(
            s.goal.customEnabled, s.goal.customMl, s.goal.bonusEnabled, s.goal.bonusMl,
            s.reminders.enabled, s.reminders.startMinutes, s.reminders.endMinutes, s.reminders.intervalMinutes,
            s.reminders.skipAfterDrink, s.reminders.skipDuringSession, s.reminders.action1Ml, s.reminders.action2Ml,
        )
    }
}

fun DrinkEntity.toDto() = DrinkDto(id, code, name, icon, hydrationCoefficient, linkedFoodId, isCaloric, archived, sortOrder)
fun DrinkDto.toEntity() = DrinkEntity(id, code, name, icon, hydrationCoefficient, linkedFoodId, isCaloric, archived, sortOrder)
fun ContainerEntity.toDto() = ContainerDto(id, name, volumeMl, defaultDrinkId, isDefault, archived, sortOrder)
fun ContainerDto.toEntity() = ContainerEntity(id, name, volumeMl, defaultDrinkId, isDefault, archived, sortOrder)

fun WaterIntakeEntity.toDto() = WaterIntakeDto(
    id, timestamp.toEpochMilli(), date.toString(), drinkId, volumeMl, coefficientApplied, linkedMealEntryId, source,
)

fun WaterIntakeDto.toEntity() = WaterIntakeEntity(
    id, Instant.ofEpochMilli(timestamp), LocalDate.parse(date), drinkId, volumeMl, coefficientApplied, linkedMealEntryId, source,
)

fun WaterDayEntity.toDto() = WaterDayDto(date.toString(), baseGoalMl, customGoalMl, bonusMl, sessionDay)
fun WaterDayDto.toEntity() = WaterDayEntity(LocalDate.parse(date), baseGoalMl, customGoalMl, bonusMl, sessionDay)
