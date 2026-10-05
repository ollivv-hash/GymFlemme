package fr.olivier.forme.feature.nutrition.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.Index
import androidx.room.PrimaryKey
import fr.olivier.forme.feature.heart.domain.Sex
import fr.olivier.forme.feature.nutrition.domain.FoodSource
import fr.olivier.forme.feature.nutrition.domain.MealType
import java.time.Instant
import java.time.LocalDate

/** Single-row profile (id = 1). Absent row = defaults. */
@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val id: Long = 1,
    val proteinTargetGPerKg: Double = DEFAULT_PROTEIN_G_PER_KG,
    /** Whey label values, entered manually; null = whey not configured. */
    val wheyProteinPer100g: Double? = null,
    val wheyKcalPer100g: Double? = null,
    val wheyCarbsPer100g: Double? = null,
    val wheyFatPer100g: Double? = null,
    val wheyScoopG: Double = 30.0,
    val creatineDoseG: Double = 5.0,
    val creatineReminderEnabled: Boolean = true,
    /** Minutes after midnight. */
    val creatineReminderMinutes: Int = 20 * 60,
    /** Heart rate (V2.1): age and sex for the maximum heart rate and calorie estimates. */
    val birthYear: Int? = null,
    val sex: Sex? = null,
    /** Maximum heart rate entered or accepted by the user; null = computed from the age. */
    val hrMaxOverride: Int? = null,
) {
    companion object {
        const val DEFAULT_PROTEIN_G_PER_KG = 1.8
    }
}

/** ANSES Ciqual reference table, loaded from assets (read-only, never exported). */
@Entity(tableName = "ciqual_food")
data class CiqualFoodEntity(
    @PrimaryKey val code: Long,
    val name: String,
    val groupName: String,
    val kcalPer100g: Double?,
    val proteinPer100g: Double?,
    val carbsPer100g: Double?,
    val fatPer100g: Double?,
)

/** Accent-insensitive full-text index over Ciqual names. */
@Fts4(
    contentEntity = CiqualFoodEntity::class,
    tokenizer = FtsOptions.TOKENIZER_UNICODE61,
    tokenizerArgs = ["remove_diacritics=2"],
)
@Entity(tableName = "ciqual_fts")
data class CiqualFtsEntity(val name: String)

/** "My" foods: custom ones, Open Food Facts cache, and Ciqual foods once used (recents / favourites). */
@Entity(
    tableName = "food",
    indices = [Index(value = ["source", "sourceCode"], unique = true), Index("lastUsedAt")],
)
data class FoodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val source: FoodSource,
    /** Ciqual code or barcode; null for custom foods without barcode. */
    val sourceCode: String? = null,
    val name: String,
    val brand: String? = null,
    val kcalPer100g: Double? = null,
    val proteinPer100g: Double,
    val carbsPer100g: Double? = null,
    val fatPer100g: Double? = null,
    val defaultPortionG: Double? = null,
    val favorite: Boolean = false,
    val lastUsedAt: Instant? = null,
)

/** A food eaten; values are a snapshot, so editing or deleting the food never changes history. */
@Entity(
    tableName = "meal_entry",
    foreignKeys = [
        ForeignKey(entity = FoodEntity::class, parentColumns = ["id"], childColumns = ["foodId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("date"), Index("foodId")],
)
data class MealEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val mealType: MealType,
    val foodId: Long?,
    val name: String,
    val quantityG: Double,
    val proteinG: Double,
    val kcal: Double?,
    val carbsG: Double?,
    val fatG: Double?,
    val isWhey: Boolean = false,
    val eatenAt: Instant,
)

@Entity(tableName = "creatine_entry", indices = [Index(value = ["date"], unique = true)])
data class CreatineEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val doseG: Double,
    val takenAt: Instant,
)
