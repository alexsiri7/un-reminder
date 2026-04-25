package net.interstellarai.unreminder.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Add new columns with safe defaults
        db.execSQL("ALTER TABLE `habits` ADD COLUMN `dedication_level` INTEGER NOT NULL DEFAULT 2")
        db.execSQL("ALTER TABLE `habits` ADD COLUMN `description_ladder` TEXT NOT NULL DEFAULT '[]'")
        db.execSQL("ALTER TABLE `habits` ADD COLUMN `auto_adjust_level` INTEGER NOT NULL DEFAULT 1")

        // description_ladder starts as empty array; users re-enter descriptions via the edit screen.
    }
}
