package fr.olivier.forme.core.db

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import fr.olivier.forme.feature.badges.domain.BadgeMigration
import fr.olivier.forme.feature.library.data.ExerciseLibraryLinks
import fr.olivier.forme.feature.hydration.data.HydrationDefaults
import fr.olivier.forme.feature.nutrition.data.SupplementDefaults
import fr.olivier.forme.feature.nutrition.data.SupplementEntity

/**
 * Explicit migrations, registered on the database builder. Destructive migration is never enabled.
 * SQL is copied verbatim from the exported schema (app/schemas/.../2.json) and checked by MigrationTest.
 */
object Migrations {

    /** V1.1: Nutrition (profile, Ciqual reference + FTS index, foods, meals, creatine). */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `profile` (`id` INTEGER NOT NULL, `proteinTargetGPerKg` REAL NOT NULL, " +
                    "`wheyProteinPer100g` REAL, `wheyKcalPer100g` REAL, `wheyCarbsPer100g` REAL, `wheyFatPer100g` REAL, " +
                    "`wheyScoopG` REAL NOT NULL, `creatineDoseG` REAL NOT NULL, `creatineReminderEnabled` INTEGER NOT NULL, " +
                    "`creatineReminderMinutes` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `ciqual_food` (`code` INTEGER NOT NULL, `name` TEXT NOT NULL, `groupName` TEXT NOT NULL, " +
                    "`kcalPer100g` REAL, `proteinPer100g` REAL, `carbsPer100g` REAL, `fatPer100g` REAL, PRIMARY KEY(`code`))",
            )
            db.execSQL(
                "CREATE VIRTUAL TABLE IF NOT EXISTS `ciqual_fts` USING FTS4(`name` TEXT NOT NULL, " +
                    "tokenize=unicode61 `remove_diacritics=2`, content=`ciqual_food`)",
            )
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_ciqual_fts_BEFORE_UPDATE BEFORE UPDATE ON `ciqual_food` " +
                    "BEGIN DELETE FROM `ciqual_fts` WHERE `docid`=OLD.`rowid`; END",
            )
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_ciqual_fts_BEFORE_DELETE BEFORE DELETE ON `ciqual_food` " +
                    "BEGIN DELETE FROM `ciqual_fts` WHERE `docid`=OLD.`rowid`; END",
            )
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_ciqual_fts_AFTER_UPDATE AFTER UPDATE ON `ciqual_food` " +
                    "BEGIN INSERT INTO `ciqual_fts`(`docid`, `name`) VALUES (NEW.`rowid`, NEW.`name`); END",
            )
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_ciqual_fts_AFTER_INSERT AFTER INSERT ON `ciqual_food` " +
                    "BEGIN INSERT INTO `ciqual_fts`(`docid`, `name`) VALUES (NEW.`rowid`, NEW.`name`); END",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `food` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `source` TEXT NOT NULL, " +
                    "`sourceCode` TEXT, `name` TEXT NOT NULL, `brand` TEXT, `kcalPer100g` REAL, `proteinPer100g` REAL NOT NULL, " +
                    "`carbsPer100g` REAL, `fatPer100g` REAL, `defaultPortionG` REAL, `favorite` INTEGER NOT NULL, `lastUsedAt` INTEGER)",
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_food_source_sourceCode` ON `food` (`source`, `sourceCode`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_food_lastUsedAt` ON `food` (`lastUsedAt`)")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `meal_entry` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `date` INTEGER NOT NULL, " +
                    "`mealType` TEXT NOT NULL, `foodId` INTEGER, `name` TEXT NOT NULL, `quantityG` REAL NOT NULL, " +
                    "`proteinG` REAL NOT NULL, `kcal` REAL, `carbsG` REAL, `fatG` REAL, `isWhey` INTEGER NOT NULL, " +
                    "`eatenAt` INTEGER NOT NULL, FOREIGN KEY(`foodId`) REFERENCES `food`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_meal_entry_date` ON `meal_entry` (`date`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_meal_entry_foodId` ON `meal_entry` (`foodId`)")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `creatine_entry` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`date` INTEGER NOT NULL, `doseG` REAL NOT NULL, `takenAt` INTEGER NOT NULL)",
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_creatine_entry_date` ON `creatine_entry` (`date`)")
        }
    }

    /** V1.2a: supersets, busy-machine replacement, "for next time" note. Nullable columns only. */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `exercise` ADD COLUMN `nextNote` TEXT")
            db.execSQL("ALTER TABLE `template_exercise` ADD COLUMN `supersetGroup` INTEGER")
            db.execSQL("ALTER TABLE `session_exercise` ADD COLUMN `supersetGroup` INTEGER")
            db.execSQL("ALTER TABLE `session_exercise` ADD COLUMN `replacedExerciseId` INTEGER")
        }
    }

    /** V2.1: heart rate of sessions, and age / sex / maximum heart rate in the profile. */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `profile` ADD COLUMN `birthYear` INTEGER")
            db.execSQL("ALTER TABLE `profile` ADD COLUMN `sex` TEXT")
            db.execSQL("ALTER TABLE `profile` ADD COLUMN `hrMaxOverride` INTEGER")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `session_heart` (`sessionId` INTEGER NOT NULL, `source` TEXT NOT NULL, " +
                    "`seriesStart` INTEGER NOT NULL, `intervalSec` INTEGER NOT NULL, `bpmSeries` TEXT NOT NULL, " +
                    "`avgBpm` INTEGER NOT NULL, `minBpm` INTEGER NOT NULL, `maxBpm` INTEGER NOT NULL, `hrMax` INTEGER, " +
                    "`zone1Sec` INTEGER NOT NULL, `zone2Sec` INTEGER NOT NULL, `zone3Sec` INTEGER NOT NULL, " +
                    "`zone4Sec` INTEGER NOT NULL, `zone5Sec` INTEGER NOT NULL, `kcal` REAL, `kcalSource` TEXT, " +
                    "PRIMARY KEY(`sessionId`), FOREIGN KEY(`sessionId`) REFERENCES `session`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
        }
    }

    /** V2.2: calorie target (height, goal, personalised rest-day target). Nullable columns only. */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `profile` ADD COLUMN `heightCm` INTEGER")
            db.execSQL("ALTER TABLE `profile` ADD COLUMN `nutritionGoal` TEXT")
            db.execSQL("ALTER TABLE `profile` ADD COLUMN `kcalTargetOverride` INTEGER")
        }
    }

    /**
     * V2.8: exercise library (Open Exercise Illustrations, filled from the APK at startup) and the link of the
     * user's exercises to it, set for the 23 known exercises by exact name.
     */
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `library_exercise` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                    "`aliases` TEXT NOT NULL, `imageOf` TEXT, `difference` TEXT, `equipment` TEXT NOT NULL, " +
                    "`equipmentLabel` TEXT NOT NULL, `machineType` TEXT, `primaryMuscles` TEXT NOT NULL, " +
                    "`secondaryMuscles` TEXT NOT NULL, `alsoTargets` TEXT NOT NULL, `movementPattern` TEXT NOT NULL, " +
                    "`category` TEXT NOT NULL, `level` TEXT NOT NULL, `impact` TEXT NOT NULL, `unilateral` INTEGER NOT NULL, " +
                    "`isWarmUp` INTEGER NOT NULL, `suitableFor` TEXT NOT NULL, `view` TEXT NOT NULL, `frames` TEXT NOT NULL, " +
                    "`imageId` TEXT NOT NULL, `width` INTEGER NOT NULL, `height` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            )
            db.execSQL("ALTER TABLE `exercise` ADD COLUMN `libraryId` TEXT")
            ExerciseLibraryLinks.BY_NAME.forEach { (name, libraryId) ->
                db.execSQL("UPDATE `exercise` SET `libraryId` = ? WHERE `name` = ?", arrayOf(libraryId, name))
            }
        }
    }

    /** V2.17: badges earned (filled at the next start from the history, silently for the past ones). */
    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `badge` (`id` TEXT NOT NULL, `earnedAt` INTEGER NOT NULL, " +
                    "`seen` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            )
        }
    }

    /**
     * V2.18: badges with tiers. One row per badge and tier (0 = none); the V2.17 badges are converted, with their day
     * and their "seen" state (Pilier de la salle → Récidiviste Argent, Créatine fidèle → Argent, …).
     */
    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `badge_new` (`id` TEXT NOT NULL, `tier` INTEGER NOT NULL, " +
                    "`earnedAt` INTEGER NOT NULL, `seen` INTEGER NOT NULL, PRIMARY KEY(`id`, `tier`))",
            )
            db.execSQL(
                "INSERT OR IGNORE INTO `badge_new` (`id`, `tier`, `earnedAt`, `seen`) " +
                    "SELECT ${BadgeMigration.sqlId}, ${BadgeMigration.sqlTier}, `earnedAt`, `seen` FROM `badge`",
            )
            db.execSQL("DROP TABLE `badge`")
            db.execSQL("ALTER TABLE `badge_new` RENAME TO `badge`")
        }
    }

    /** V2.19: the challenge of each month, drawn once. */
    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `monthly_challenge` (`month` TEXT NOT NULL, `challenge` TEXT NOT NULL, " +
                    "`completedAt` INTEGER, `seen` INTEGER NOT NULL, PRIMARY KEY(`month`))",
            )
        }
    }

    /**
     * V2.20: food supplements. Creatine becomes the first supplement (its dose, reminder and every day taken are
     * kept), the other built-in ones are added switched off; `creatine_entry` and the creatine columns of the profile
     * go away.
     */
    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `supplement` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                    "`code` TEXT, `amount` REAL NOT NULL, `unit` TEXT NOT NULL, `active` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, " +
                    "`reminderEnabled` INTEGER NOT NULL, `reminderMinutes` INTEGER NOT NULL)",
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_supplement_code` ON `supplement` (`code`)")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `supplement_intake` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`supplementId` INTEGER NOT NULL, `date` INTEGER NOT NULL, `amount` REAL NOT NULL, `takenAt` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`supplementId`) REFERENCES `supplement`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_supplement_intake_supplementId_date` ON `supplement_intake` (`supplementId`, `date`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_supplement_intake_date` ON `supplement_intake` (`date`)")

            SupplementDefaults.legacy.forEach { db.insert("supplement", SQLiteDatabase.CONFLICT_ABORT, it.toValues()) }
            // Creatine as set in the profile (none = the defaults above).
            db.execSQL(
                """UPDATE supplement SET
                     amount = (SELECT creatineDoseG FROM profile WHERE id = 1),
                     reminderEnabled = (SELECT creatineReminderEnabled FROM profile WHERE id = 1),
                     reminderMinutes = (SELECT creatineReminderMinutes FROM profile WHERE id = 1)
                   WHERE code = 'creatine' AND EXISTS (SELECT 1 FROM profile WHERE id = 1)""",
            )
            db.execSQL(
                """INSERT INTO supplement_intake (supplementId, date, amount, takenAt)
                   SELECT (SELECT id FROM supplement WHERE code = 'creatine'), date, doseG, takenAt FROM creatine_entry""",
            )
            db.execSQL("DROP TABLE `creatine_entry`")

            // The profile without its creatine columns (no DROP COLUMN before SQLite 3.35).
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `profile_new` (`id` INTEGER NOT NULL, `proteinTargetGPerKg` REAL NOT NULL, " +
                    "`wheyProteinPer100g` REAL, `wheyKcalPer100g` REAL, `wheyCarbsPer100g` REAL, `wheyFatPer100g` REAL, " +
                    "`wheyScoopG` REAL NOT NULL, `birthYear` INTEGER, `sex` TEXT, `hrMaxOverride` INTEGER, `heightCm` INTEGER, " +
                    "`nutritionGoal` TEXT, `kcalTargetOverride` INTEGER, PRIMARY KEY(`id`))",
            )
            val columns = "id, proteinTargetGPerKg, wheyProteinPer100g, wheyKcalPer100g, wheyCarbsPer100g, wheyFatPer100g, " +
                "wheyScoopG, birthYear, sex, hrMaxOverride, heightCm, nutritionGoal, kcalTargetOverride"
            db.execSQL("INSERT INTO profile_new ($columns) SELECT $columns FROM profile")
            db.execSQL("DROP TABLE profile")
            db.execSQL("ALTER TABLE profile_new RENAME TO profile")
        }
    }

    /** V2.21: dishes of one's own (a food of source DISH, its cooked weight, servings and ingredients). */
    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `dish` (`foodId` INTEGER NOT NULL, `cookedWeightG` REAL, `servings` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`foodId`), FOREIGN KEY(`foodId`) REFERENCES `food`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `dish_ingredient` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `dishId` INTEGER NOT NULL, " +
                    "`position` INTEGER NOT NULL, `name` TEXT NOT NULL, `grams` REAL NOT NULL, `kcalPer100g` REAL, `proteinPer100g` REAL NOT NULL, " +
                    "`carbsPer100g` REAL, `fatPer100g` REAL, " +
                    "FOREIGN KEY(`dishId`) REFERENCES `dish`(`foodId`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_dish_ingredient_dishId` ON `dish_ingredient` (`dishId`)")
        }
    }

    /** V2.22: first name in the profile (home screen). */
    val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `profile` ADD COLUMN `firstName` TEXT")
        }
    }

    /** V2.25: activity level and a protein target in grams in the profile (null: as before). */
    val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `profile` ADD COLUMN `activityLevel` TEXT")
            db.execSQL("ALTER TABLE `profile` ADD COLUMN `proteinTargetOverrideG` INTEGER")
        }
    }

    /**
     * V2.26: how often each dish was added, and its last use, counted from the meals already entered (a meal keeps
     * its dish's id until the dish is deleted).
     */
    val MIGRATION_13_14 = object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `dish` ADD COLUMN `useCount` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("UPDATE dish SET useCount = (SELECT COUNT(*) FROM meal_entry WHERE meal_entry.foodId = dish.foodId)")
            db.execSQL(
                """UPDATE food SET lastUsedAt = (SELECT MAX(eatenAt) FROM meal_entry WHERE meal_entry.foodId = food.id)
                   WHERE source = 'DISH' AND EXISTS (SELECT 1 FROM meal_entry WHERE meal_entry.foodId = food.id)
                     AND (lastUsedAt IS NULL OR lastUsedAt < (SELECT MAX(eatenAt) FROM meal_entry WHERE meal_entry.foodId = food.id))""",
            )
        }
    }

    /**
     * V2.29: hydration (drinks, containers, intakes, frozen goal of each day). The drinks and containers offered
     * from the start are written here; nothing else changes.
     */
    val MIGRATION_14_15 = object : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `drink` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `code` TEXT, `name` TEXT NOT NULL, " +
                    "`icon` TEXT NOT NULL, `hydrationCoefficient` REAL NOT NULL, `linkedFoodId` INTEGER, `isCaloric` INTEGER NOT NULL, " +
                    "`archived` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`linkedFoodId`) REFERENCES `food`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_drink_code` ON `drink` (`code`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_drink_linkedFoodId` ON `drink` (`linkedFoodId`)")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `container` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                    "`volumeMl` INTEGER NOT NULL, `defaultDrinkId` INTEGER NOT NULL, `isDefault` INTEGER NOT NULL, `archived` INTEGER NOT NULL, " +
                    "`sortOrder` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`defaultDrinkId`) REFERENCES `drink`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_container_defaultDrinkId` ON `container` (`defaultDrinkId`)")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `water_intake` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, " +
                    "`date` INTEGER NOT NULL, `drinkId` INTEGER NOT NULL, `volumeMl` INTEGER NOT NULL, `coefficientApplied` REAL NOT NULL, " +
                    "`linkedMealEntryId` INTEGER, `source` TEXT NOT NULL, " +
                    "FOREIGN KEY(`drinkId`) REFERENCES `drink`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , " +
                    "FOREIGN KEY(`linkedMealEntryId`) REFERENCES `meal_entry`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_water_intake_date` ON `water_intake` (`date`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_water_intake_drinkId` ON `water_intake` (`drinkId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_water_intake_linkedMealEntryId` ON `water_intake` (`linkedMealEntryId`)")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `water_day` (`date` INTEGER NOT NULL, `baseGoalMl` INTEGER NOT NULL, `customGoalMl` INTEGER, " +
                    "`bonusMl` INTEGER NOT NULL, `sessionDay` INTEGER NOT NULL, PRIMARY KEY(`date`))",
            )
            HydrationDefaults.drinks.forEach { db.insert("drink", SQLiteDatabase.CONFLICT_ABORT, with(HydrationDefaults) { it.toValues() }) }
            HydrationDefaults.containers.forEach { db.insert("container", SQLiteDatabase.CONFLICT_ABORT, with(HydrationDefaults) { it.toValues() }) }
        }
    }

    internal fun SupplementEntity.toValues() = ContentValues().apply {
        put("name", name)
        put("code", code)
        put("amount", amount)
        put("unit", unit.name)
        put("active", active)
        put("sortOrder", sortOrder)
        put("reminderEnabled", reminderEnabled)
        put("reminderMinutes", reminderMinutes)
    }

    val ALL: Array<Migration> = arrayOf(
        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
        MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15,
    )
}
