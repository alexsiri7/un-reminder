package net.interstellarai.unreminder.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1. Create new habits table without full_description/low_floor_description
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `habits_new` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `active` INTEGER NOT NULL DEFAULT 1,
                `dedication_level` INTEGER NOT NULL DEFAULT 0,
                `auto_adjust_level` INTEGER NOT NULL DEFAULT 1,
                `created_at` INTEGER NOT NULL DEFAULT 0,
                `updated_at` INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())

        // 2. Copy data (dedication_level=0, auto_adjust_level=1 for all existing habits)
        db.execSQL("""
            INSERT INTO `habits_new` (`id`, `name`, `active`, `dedication_level`, `auto_adjust_level`, `created_at`, `updated_at`)
            SELECT `id`, `name`, `active`, 0, 1, `created_at`, `updated_at` FROM `habits`
        """.trimIndent())

        // 3. Create habit_level_descriptions table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `habit_level_descriptions` (
                `habit_id` INTEGER NOT NULL,
                `level` INTEGER NOT NULL,
                `description` TEXT NOT NULL,
                PRIMARY KEY(`habit_id`, `level`),
                FOREIGN KEY(`habit_id`) REFERENCES `habits`(`id`) ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_habit_level_descriptions_habit_id` ON `habit_level_descriptions` (`habit_id`)"
        )

        // 4. Backfill: level 0 from low_floor_description, level 5 from full_description
        //    Skip blank entries to match the invariant in HabitLevelDescriptionRepository.setDescriptions
        db.execSQL("""
            INSERT INTO `habit_level_descriptions` (`habit_id`, `level`, `description`)
            SELECT `id`, 0, `low_floor_description` FROM `habits`
            WHERE `low_floor_description` <> ''
        """.trimIndent())
        db.execSQL("""
            INSERT INTO `habit_level_descriptions` (`habit_id`, `level`, `description`)
            SELECT `id`, 5, `full_description` FROM `habits`
            WHERE `full_description` <> ''
        """.trimIndent())

        // 5. Map legacy completion statuses to unified COMPLETED
        db.execSQL("""
            UPDATE `triggers` SET `status` = 'COMPLETED'
            WHERE `status` IN ('COMPLETED_FULL', 'COMPLETED_LOW_FLOOR')
        """.trimIndent())

        // 6. Drop old habits table and rename new one
        db.execSQL("DROP TABLE `habits`")
        db.execSQL("ALTER TABLE `habits_new` RENAME TO `habits`")
    }
}
