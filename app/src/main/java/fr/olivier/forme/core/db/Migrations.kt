package fr.olivier.forme.core.db

import androidx.room.migration.Migration

/**
 * Explicit migrations, registered on the database builder.
 * V1.1 (Nutrition) will add MIGRATION_1_2 here; destructive migration is never enabled.
 */
object Migrations {
    val ALL: Array<Migration> = emptyArray()
}
