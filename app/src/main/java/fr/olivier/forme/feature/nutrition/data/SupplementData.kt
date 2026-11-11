package fr.olivier.forme.feature.nutrition.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import fr.olivier.forme.feature.sport.domain.Fmt
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

/** Unit of a supplement's daily amount (V2.20). */
enum class SupplementUnit(val label: String) {
    G("g"),
    MG("mg"),
    UG("µg"),
    IU("UI"),
    CAPSULE("gélule"),
    TABLET("comprimé"),
    ML("ml"),
}

/**
 * A food supplement taken once a day (V2.20; creatine until 2.19). Built-in ones have a [code] (creatine keeps its
 * badges and its streak); the user's own have none.
 */
@Entity(tableName = "supplement", indices = [Index(value = ["code"], unique = true)])
data class SupplementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val code: String? = null,
    /** Amount per day, in [unit]. */
    val amount: Double,
    val unit: SupplementUnit,
    val active: Boolean,
    val sortOrder: Int,
    val reminderEnabled: Boolean = false,
    /** Minutes after midnight. */
    val reminderMinutes: Int = DEFAULT_REMINDER_MINUTES,
) {
    val isCreatine get() = code == CREATINE

    companion object {
        const val CREATINE = "creatine"
        const val DEFAULT_REMINDER_MINUTES = 20 * 60
    }
}

/** The supplement was taken on [date] (one row per supplement and day). */
@Entity(
    tableName = "supplement_intake",
    foreignKeys = [
        ForeignKey(entity = SupplementEntity::class, parentColumns = ["id"], childColumns = ["supplementId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["supplementId", "date"], unique = true), Index("date")],
)
data class SupplementIntakeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val supplementId: Long,
    val date: LocalDate,
    /** Amount taken, in the supplement's unit at that time. */
    val amount: Double,
    val takenAt: Instant,
)

/** "5 g", "300 mg", "2 gélules". */
fun SupplementEntity.dose(): String {
    val plural = amount > 1 && (unit == SupplementUnit.CAPSULE || unit == SupplementUnit.TABLET)
    return "${Fmt.number(amount)} ${unit.label}${if (plural) "s" else ""}"
}

/** The supplements offered from the start: creatine on (as before 2.20), the others off. */
object SupplementDefaults {
    /** V2.25: 3 g a day for a new install (5 g before, kept by those who had it). */
    const val CREATINE_DEFAULT_G = 3.0

    /** The default creatine dose before V2.25: what the migration 9 → 10 and the backups before v10 assume. */
    const val LEGACY_CREATINE_G = 5.0

    /** [all] as they were before V2.25, for the older data converted (migration 9 → 10, backups before v10). */
    val legacy get() = all.map { if (it.isCreatine) it.copy(amount = LEGACY_CREATINE_G) else it }

    val all: List<SupplementEntity> = listOf(
        SupplementEntity(name = "Créatine", code = SupplementEntity.CREATINE, amount = CREATINE_DEFAULT_G, unit = SupplementUnit.G, active = true, sortOrder = 0, reminderEnabled = true),
        SupplementEntity(name = "Oméga-3", code = "omega3", amount = 1.0, unit = SupplementUnit.G, active = false, sortOrder = 1),
        SupplementEntity(name = "Vitamine D", code = "vitamin_d", amount = 1000.0, unit = SupplementUnit.IU, active = false, sortOrder = 2),
        SupplementEntity(name = "Magnésium", code = "magnesium", amount = 300.0, unit = SupplementUnit.MG, active = false, sortOrder = 3),
        SupplementEntity(name = "Zinc", code = "zinc", amount = 15.0, unit = SupplementUnit.MG, active = false, sortOrder = 4),
        SupplementEntity(name = "Multivitamines", code = "multivitamin", amount = 1.0, unit = SupplementUnit.TABLET, active = false, sortOrder = 5),
        SupplementEntity(name = "Vitamine C", code = "vitamin_c", amount = 500.0, unit = SupplementUnit.MG, active = false, sortOrder = 6),
    )
}

@Dao
interface SupplementDao {
    @Query("SELECT * FROM supplement ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<SupplementEntity>>

    @Query("SELECT * FROM supplement ORDER BY sortOrder, id")
    suspend fun getAll(): List<SupplementEntity>

    @Query("SELECT * FROM supplement WHERE id = :id")
    suspend fun get(id: Long): SupplementEntity?

    @Query("SELECT * FROM supplement WHERE code = :code")
    suspend fun getByCode(code: String): SupplementEntity?

    @Insert
    suspend fun insert(supplement: SupplementEntity): Long

    @Insert
    suspend fun insertAll(supplements: List<SupplementEntity>)

    @Update
    suspend fun update(supplement: SupplementEntity)

    @Query("DELETE FROM supplement WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM supplement")
    suspend fun deleteAll()

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM supplement")
    suspend fun nextSortOrder(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertIntake(intake: SupplementIntakeEntity)

    @Insert
    suspend fun insertIntakes(intakes: List<SupplementIntakeEntity>)

    @Query("DELETE FROM supplement_intake WHERE supplementId = :supplementId AND date = :date")
    suspend fun deleteIntake(supplementId: Long, date: LocalDate)

    @Query("SELECT * FROM supplement_intake WHERE supplementId = :supplementId AND date = :date")
    suspend fun getIntake(supplementId: Long, date: LocalDate): SupplementIntakeEntity?

    @Query("SELECT * FROM supplement_intake WHERE date = :date")
    fun observeDay(date: LocalDate): Flow<List<SupplementIntakeEntity>>

    @Query("SELECT * FROM supplement_intake WHERE date = :date")
    suspend fun getDay(date: LocalDate): List<SupplementIntakeEntity>

    /** Days creatine was taken, latest first (streak, badges). */
    @Query(
        """SELECT i.date FROM supplement_intake i JOIN supplement s ON s.id = i.supplementId
           WHERE s.code = 'creatine' ORDER BY i.date DESC""",
    )
    fun observeCreatineDates(): Flow<List<LocalDate>>

    @Query(
        """SELECT i.date FROM supplement_intake i JOIN supplement s ON s.id = i.supplementId
           WHERE s.code = 'creatine' ORDER BY i.date DESC""",
    )
    suspend fun creatineDates(): List<LocalDate>

    @Query("SELECT * FROM supplement_intake")
    suspend fun getAllIntakes(): List<SupplementIntakeEntity>

    @Query("SELECT * FROM supplement_intake WHERE date BETWEEN :from AND :to")
    suspend fun getIntakes(from: LocalDate, to: LocalDate): List<SupplementIntakeEntity>
}
