package fr.olivier.forme.feature.bodyweight.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/** One weigh-in per day: saving again on the same date replaces the value. */
@Entity(tableName = "body_weight_entry", indices = [Index(value = ["date"], unique = true)])
data class BodyWeightEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val weightKg: Double,
)
