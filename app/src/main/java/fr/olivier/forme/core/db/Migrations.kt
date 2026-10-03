package fr.olivier.forme.core.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
}
