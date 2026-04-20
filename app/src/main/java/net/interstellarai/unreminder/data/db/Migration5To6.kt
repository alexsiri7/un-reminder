package net.interstellarai.unreminder.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `habits` ADD COLUMN `dedication_level` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `habits` ADD COLUMN `auto_adjust_level` INTEGER NOT NULL DEFAULT 1")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `habit_level_descriptions` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `habit_id` INTEGER NOT NULL,
                `level` INTEGER NOT NULL,
                `description` TEXT NOT NULL DEFAULT '',
                FOREIGN KEY(`habit_id`) REFERENCES `habits`(`id`) ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_hld_habit_id_level` " +
            "ON `habit_level_descriptions` (`habit_id`, `level`)"
        )
        // Backfill level 0 from existing low_floor_description
        db.execSQL("""
            INSERT OR IGNORE INTO `habit_level_descriptions` (`habit_id`, `level`, `description`)
            SELECT `id`, 0, `low_floor_description` FROM `habits`
        """.trimIndent())
        // Backfill level 5 from existing full_description
        db.execSQL("""
            INSERT OR IGNORE INTO `habit_level_descriptions` (`habit_id`, `level`, `description`)
            SELECT `id`, 5, `full_description` FROM `habits`
        """.trimIndent())
    }
}
