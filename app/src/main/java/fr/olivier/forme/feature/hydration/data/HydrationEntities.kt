package fr.olivier.forme.feature.hydration.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import fr.olivier.forme.feature.nutrition.data.FoodEntity
import fr.olivier.forme.feature.nutrition.data.MealEntryEntity
import java.time.Instant
import java.time.LocalDate

/**
 * A kind of drink (V2.29). Built-in ones have a [code] (water is the drink of the notification actions); the user's
 * own have none. [icon] is the name of a Material Symbol (see [DrinkIcons]). [hydrationCoefficient]: share of the
 * volume counted as hydration (0.0 to 1.0). A caloric drink linked to a food ([linkedFoodId]) adds a meal entry
 * at each intake. Never deleted, only [archived] (it stays in the history).
 */
@Entity(
    tableName = "drink",
    foreignKeys = [
        ForeignKey(entity = FoodEntity::class, parentColumns = ["id"], childColumns = ["linkedFoodId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index(value = ["code"], unique = true), Index("linkedFoodId")],
)
data class DrinkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val code: String? = null,
    val name: String,
    val icon: String,
    val hydrationCoefficient: Double,
    val linkedFoodId: Long? = null,
    val isCaloric: Boolean,
    val archived: Boolean = false,
    val sortOrder: Int,
) {
    val isWater get() = code == WATER

    companion object {
        const val WATER = "water"
    }
}

/** A container ("Gourde", 750 ml) and the drink it usually holds. A single one is the default ([isDefault]). */
@Entity(
    tableName = "container",
    foreignKeys = [
        ForeignKey(entity = DrinkEntity::class, parentColumns = ["id"], childColumns = ["defaultDrinkId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("defaultDrinkId")],
)
data class ContainerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val volumeMl: Int,
    val defaultDrinkId: Long,
    val isDefault: Boolean = false,
    val archived: Boolean = false,
    val sortOrder: Int,
)

/** Where an intake was entered. [HEALTH_CONNECT_IMPORT] is reserved: reading from Health Connect is not done yet. */
enum class IntakeSource { HOME_TILE, WATER_SCREEN, NOTIFICATION, HEALTH_CONNECT_IMPORT }

/**
 * One drink. [date] is the local day of [timestamp] when it was entered (00:00 to 23:59). [coefficientApplied] is a
 * copy of the drink's coefficient at that time, never recomputed: hydration counted = volume × coefficient. A meal
 * entry linked ([linkedMealEntryId]) goes with it: deleting the meal entry deletes the intake (cascade), the
 * repository deletes the meal entry with the intake.
 */
@Entity(
    tableName = "water_intake",
    foreignKeys = [
        ForeignKey(entity = DrinkEntity::class, parentColumns = ["id"], childColumns = ["drinkId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = MealEntryEntity::class, parentColumns = ["id"], childColumns = ["linkedMealEntryId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("date"), Index("drinkId"), Index("linkedMealEntryId")],
)
data class WaterIntakeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Instant,
    val date: LocalDate,
    val drinkId: Long,
    val volumeMl: Int,
    val coefficientApplied: Double,
    val linkedMealEntryId: Long? = null,
    val source: IntakeSource,
)

/**
 * The goal of a day, frozen: the past days keep the goal they had (weight, goal of one's own, session bonus at the
 * time), so a new weighing or goal never changes a badge already earned. Today's row follows the settings.
 * [sessionDay]: a session was started that day (the bonus applies if it is on); [bonusMl]: the bonus applied.
 */
@Entity(tableName = "water_day")
data class WaterDayEntity(
    @PrimaryKey val date: LocalDate,
    val baseGoalMl: Int,
    val customGoalMl: Int? = null,
    val bonusMl: Int = 0,
    val sessionDay: Boolean = false,
) {
    /** The goal used that day. */
    val goalMl get() = (customGoalMl ?: baseGoalMl) + bonusMl
}

/** An intake with its drink (name, icon) for the lists. */
data class IntakeWithDrink(
    @Embedded val intake: WaterIntakeEntity,
    @Relation(parentColumn = "drinkId", entityColumn = "id") val drink: DrinkEntity,
)

/** Hydration counted on a day, in ml (sum of volume × coefficient, rounded). */
data class DailyWater(val date: LocalDate, val countedMl: Int)

/** Hydration of a day with its frozen goal (null: no goal row, 2 L assumed by the readers). */
data class WaterDayTotal(val date: LocalDate, val countedMl: Int, val goalMl: Int?)
