package net.interstellarai.unreminder.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `habit_window` (
                `habit_id` INTEGER NOT NULL,
                `window_id` INTEGER NOT NULL,
                PRIMARY KEY(`habit_id`, `window_id`),
                FOREIGN KEY(`habit_id`) REFERENCES `habits`(`id`) ON DELETE CASCADE,
                FOREIGN KEY(`window_id`) REFERENCES `windows`(`id`) ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_habit_window_window_id` ON `habit_window` (`window_id`)"
        )
    }
}
