package net.interstellarai.unreminder.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE habits ADD COLUMN dedication_level INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE habits ADD COLUMN level_descriptions TEXT NOT NULL DEFAULT '[\"\",\"\",\"\",\"\",\"\",\"\"]'")
        // Backfill: slot 0 = low-floor (entry level), slot 3 = full (committed level)
        // Matches LevelDescriptionsBlock labels in HabitEditScreen
        db.execSQL("""
            UPDATE habits SET level_descriptions =
                json_array(low_floor_description, '', '', full_description, '', '')
        """.trimIndent())
        db.execSQL("ALTER TABLE triggers ADD COLUMN completion_level INTEGER")
        db.execSQL("UPDATE triggers SET status = 'COMPLETED', completion_level = 3 WHERE status = 'COMPLETED_FULL'")
        db.execSQL("UPDATE triggers SET status = 'COMPLETED', completion_level = 0 WHERE status = 'COMPLETED_LOW_FLOOR'")
    }
}
