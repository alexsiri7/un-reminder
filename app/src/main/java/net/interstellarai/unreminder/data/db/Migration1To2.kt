package net.interstellarai.unreminder.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1. Create junction table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `habit_location` (
                `habit_id` INTEGER NOT NULL,
                `location_id` INTEGER NOT NULL,
                PRIMARY KEY (`habit_id`, `location_id`),
                FOREIGN KEY (`habit_id`) REFERENCES `habits`(`id`) ON DELETE CASCADE,
                FOREIGN KEY (`location_id`) REFERENCES `locations`(`id`) ON DELETE CASCADE
            )
        """.trimIndent())

        // 1b. Create index on location_id for FK performance
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_habit_location_location_id` ON `habit_location` (`location_id`)")

        // 2. Seed cross-refs from existing HOME/WORK/COMMUTE location_tag values.
        //    COMMUTE has no matching location row (label JOIN returns nothing), so those
        //    habits naturally become "Anywhere" habits — this is intentional.
        db.execSQL("""
            INSERT OR IGNORE INTO `habit_location` (`habit_id`, `location_id`)
            SELECT h.id, l.id FROM habits h
            JOIN locations l ON UPPER(l.label) = h.location_tag
            WHERE h.location_tag IN ('HOME', 'WORK', 'COMMUTE')
        """.trimIndent())

        // 3. Recreate habits table without location_tag column
        db.execSQL("""
            CREATE TABLE `habits_new` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `full_description` TEXT NOT NULL,
                `low_floor_description` TEXT NOT NULL,
                `active` INTEGER NOT NULL DEFAULT 1,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("""
            INSERT INTO `habits_new`
            SELECT id, name, full_description, low_floor_description, active, created_at, updated_at
            FROM habits
        """.trimIndent())
        db.execSQL("DROP TABLE `habits`")
        db.execSQL("ALTER TABLE `habits_new` RENAME TO `habits`")

        // 4. Rename locations.label → locations.name
        db.execSQL("ALTER TABLE `locations` RENAME COLUMN `label` TO `name`")
    }
}
