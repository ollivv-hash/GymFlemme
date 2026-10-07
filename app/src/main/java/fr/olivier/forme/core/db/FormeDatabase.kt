package fr.olivier.forme.core.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import fr.olivier.forme.feature.bodyweight.data.BodyWeightDao
import fr.olivier.forme.feature.bodyweight.data.BodyWeightEntity
import fr.olivier.forme.feature.heart.data.SessionHeartDao
import fr.olivier.forme.feature.heart.data.SessionHeartEntity
import fr.olivier.forme.feature.library.data.LibraryDao
import fr.olivier.forme.feature.library.data.LibraryExerciseEntity
import fr.olivier.forme.feature.nutrition.data.CiqualDao
import fr.olivier.forme.feature.nutrition.data.CiqualFoodEntity
import fr.olivier.forme.feature.nutrition.data.CiqualFtsEntity
import fr.olivier.forme.feature.nutrition.data.CreatineDao
import fr.olivier.forme.feature.nutrition.data.CreatineEntryEntity
import fr.olivier.forme.feature.nutrition.data.FoodDao
import fr.olivier.forme.feature.nutrition.data.FoodEntity
import fr.olivier.forme.feature.nutrition.data.MealDao
import fr.olivier.forme.feature.nutrition.data.MealEntryEntity
import fr.olivier.forme.feature.nutrition.data.ProfileDao
import fr.olivier.forme.feature.nutrition.data.ProfileEntity
import fr.olivier.forme.feature.sport.data.CardioEntryEntity
import fr.olivier.forme.feature.sport.data.ExerciseDao
import fr.olivier.forme.feature.sport.data.ExerciseEntity
import fr.olivier.forme.feature.sport.data.SessionDao
import fr.olivier.forme.feature.sport.data.SessionEntity
import fr.olivier.forme.feature.sport.data.SessionExerciseEntity
import fr.olivier.forme.feature.sport.data.SetEntryEntity
import fr.olivier.forme.feature.sport.data.TemplateDao
import fr.olivier.forme.feature.sport.data.TemplateExerciseEntity
import fr.olivier.forme.feature.sport.data.WorkoutTemplateEntity

@Database(
    entities = [
        ExerciseEntity::class,
        WorkoutTemplateEntity::class,
        TemplateExerciseEntity::class,
        SessionEntity::class,
        SessionExerciseEntity::class,
        SetEntryEntity::class,
        CardioEntryEntity::class,
        BodyWeightEntity::class,
        ProfileEntity::class,
        CiqualFoodEntity::class,
        CiqualFtsEntity::class,
        FoodEntity::class,
        MealEntryEntity::class,
        CreatineEntryEntity::class,
        SessionHeartEntity::class,
        LibraryExerciseEntity::class,
    ],
    version = FormeDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class FormeDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun templateDao(): TemplateDao
    abstract fun sessionDao(): SessionDao
    abstract fun bodyWeightDao(): BodyWeightDao
    abstract fun profileDao(): ProfileDao
    abstract fun ciqualDao(): CiqualDao
    abstract fun foodDao(): FoodDao
    abstract fun mealDao(): MealDao
    abstract fun creatineDao(): CreatineDao
    abstract fun sessionHeartDao(): SessionHeartDao
    abstract fun libraryDao(): LibraryDao

    companion object {
        const val VERSION = 6
        const val NAME = "forme.db"
    }
}
