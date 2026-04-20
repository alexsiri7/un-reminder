package net.interstellarai.unreminder.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `variations` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `habit_id` INTEGER NOT NULL,
                `text` TEXT NOT NULL,
                `prompt_fingerprint` TEXT NOT NULL,
                `generated_at` INTEGER NOT NULL,
                `consumed_at` INTEGER,
                FOREIGN KEY(`habit_id`) REFERENCES `habits`(`id`) ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_variations_habit_id` ON `variations` (`habit_id`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_variations_habit_id_prompt_fingerprint_text` ON `variations` (`habit_id`, `prompt_fingerprint`, `text`)")
    }
}
