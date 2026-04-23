package net.interstellarai.unreminder.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Add new columns with safe defaults
        db.execSQL("ALTER TABLE `habits` ADD COLUMN `dedication_level` INTEGER NOT NULL DEFAULT 2")
        db.execSQL("ALTER TABLE `habits` ADD COLUMN `description_ladder` TEXT NOT NULL DEFAULT '[]'")
        db.execSQL("ALTER TABLE `habits` ADD COLUMN `auto_adjust_level` INTEGER NOT NULL DEFAULT 1")

        // Backfill: slot 0 = low_floor_description, slot 3 = full_description, rest empty.
        // Uses json_array() (available SQLite ≥ 3.38, Android API 30+; min SDK is 31) to properly
        // escape any special characters (quotes, backslashes, control chars) in existing descriptions.
        db.execSQL("""
            UPDATE `habits`
            SET `description_ladder` = json_array(
                `low_floor_description`,
                '',
                '',
                `full_description`,
                '',
                ''
            )
        """.trimIndent())
    }
}
