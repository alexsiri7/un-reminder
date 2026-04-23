package net.interstellarai.unreminder.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration 6 → 7: Replace binary full/low-floor descriptions with a 6-level
 * dedication ladder.
 *
 * Data mapping:
 * - low_floor_description → levelDescriptions[0] (level 0, "just starting")
 * - full_description      → levelDescriptions[3] (level 3, "committed")
 * - COMPLETED_FULL        → COMPLETED with completionLevel = 3
 * - COMPLETED_LOW_FLOOR   → COMPLETED with completionLevel = 0
 *
 * Uses table recreation to remove the old NOT NULL columns (full_description,
 * low_floor_description) — SQLite < 3.35 (minSdk 31) doesn't support DROP COLUMN.
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1. Add completion_level to triggers and remap old statuses
        db.execSQL("ALTER TABLE triggers ADD COLUMN completion_level INTEGER")
        db.execSQL("UPDATE triggers SET status = 'COMPLETED', completion_level = 3 WHERE status = 'COMPLETED_FULL'")
        db.execSQL("UPDATE triggers SET status = 'COMPLETED', completion_level = 0 WHERE status = 'COMPLETED_LOW_FLOOR'")

        // 2. Recreate habits table: drop old columns, add new ones
        db.execSQL("""
            CREATE TABLE `habits_new` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `dedication_level` INTEGER NOT NULL DEFAULT 0,
                `level_descriptions` TEXT NOT NULL DEFAULT '["","","","","",""]',
                `active` INTEGER NOT NULL DEFAULT 1,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL
            )
        """.trimIndent())

        // Build JSON array via string concatenation (avoids json_array() which
        // requires the JSON1 extension that may not be available in all SQLite builds).
        // Escape double-quote inside values so the result is valid JSON.
        val escQuote = """REPLACE(%s, '"', '\"')"""
        val lowExpr = escQuote.format("`low_floor_description`")
        val fullExpr = escQuote.format("`full_description`")
        db.execSQL(
            "INSERT INTO `habits_new` (`id`, `name`, `dedication_level`, `level_descriptions`, `active`, `created_at`, `updated_at`) " +
            "SELECT `id`, `name`, 0, " +
            "'[\"' || $lowExpr || '\",\"\",\"\",\"' || $fullExpr || '\",\"\",\"\"]', " +
            "`active`, `created_at`, `updated_at` " +
            "FROM `habits`"
        )

        db.execSQL("DROP TABLE `habits`")
        db.execSQL("ALTER TABLE `habits_new` RENAME TO `habits`")
    }
}
