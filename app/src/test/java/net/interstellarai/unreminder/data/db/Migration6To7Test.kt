package net.interstellarai.unreminder.data.db

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class Migration6To7Test {

    /**
     * Create an in-memory SQLite database at "version 6" with the V6 schema (habits + triggers),
     * then apply MIGRATION_6_7 and verify the backfill results.
     * Follows the pattern in Migration4To5Test (exportSchema = false in this project).
     */
    private fun createV6Database(): SupportSQLiteDatabase {
        val config = SupportSQLiteOpenHelper.Configuration.builder(RuntimeEnvironment.getApplication())
            .name(null) // in-memory
            .callback(object : SupportSQLiteOpenHelper.Callback(6) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `habits` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`full_description` TEXT NOT NULL, " +
                        "`low_floor_description` TEXT NOT NULL, " +
                        "`active` INTEGER NOT NULL DEFAULT 1, " +
                        "`created_at` INTEGER NOT NULL DEFAULT 0, " +
                        "`updated_at` INTEGER NOT NULL DEFAULT 0)"
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `triggers` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`habit_id` INTEGER, " +
                        "`window_id` INTEGER, " +
                        "`scheduled_at` INTEGER NOT NULL, " +
                        "`fired_at` INTEGER, " +
                        "`status` TEXT NOT NULL, " +
                        "`generated_prompt` TEXT, " +
                        "`source` TEXT)"
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        return FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase
    }

    @Test
    fun `MIGRATION_6_7 backfills level_descriptions with correct slot ordering`() {
        val db = createV6Database()

        db.execSQL(
            "INSERT INTO habits (id, name, full_description, low_floor_description, active, created_at, updated_at) " +
            "VALUES (1, 'med', 'Full version', 'Low version', 1, 0, 0)"
        )

        MIGRATION_6_7.migrate(db)

        val cursor = db.query("SELECT level_descriptions, dedication_level FROM habits WHERE id = 1")
        cursor.moveToFirst()
        val json = cursor.getString(0)
        val arr = org.json.JSONArray(json)
        assertEquals("Low version", arr.getString(0))   // slot 0 = low-floor
        assertEquals("", arr.getString(1))
        assertEquals("", arr.getString(2))
        assertEquals("Full version", arr.getString(3))  // slot 3 = full
        assertEquals("", arr.getString(4))
        assertEquals("", arr.getString(5))
        assertEquals(0, cursor.getInt(1))               // dedication_level defaults to 0
        cursor.close()

        db.close()
    }

    @Test
    fun `MIGRATION_6_7 backfills COMPLETED_FULL to completion_level 3`() {
        val db = createV6Database()

        db.execSQL(
            "INSERT INTO habits (id, name, full_description, low_floor_description, active, created_at, updated_at) " +
            "VALUES (1, 'med', 'f', 'l', 1, 0, 0)"
        )
        db.execSQL("INSERT INTO triggers (id, scheduled_at, status, habit_id) VALUES (1, 0, 'COMPLETED_FULL', 1)")

        MIGRATION_6_7.migrate(db)

        val cursor = db.query("SELECT status, completion_level FROM triggers WHERE id = 1")
        cursor.moveToFirst()
        assertEquals("COMPLETED", cursor.getString(0))
        assertEquals(3, cursor.getInt(1))
        cursor.close()

        db.close()
    }

    @Test
    fun `MIGRATION_6_7 backfills COMPLETED_LOW_FLOOR to completion_level 0`() {
        val db = createV6Database()

        db.execSQL(
            "INSERT INTO habits (id, name, full_description, low_floor_description, active, created_at, updated_at) " +
            "VALUES (1, 'med', 'f', 'l', 1, 0, 0)"
        )
        db.execSQL("INSERT INTO triggers (id, scheduled_at, status, habit_id) VALUES (2, 0, 'COMPLETED_LOW_FLOOR', 1)")

        MIGRATION_6_7.migrate(db)

        val cursor = db.query("SELECT status, completion_level FROM triggers WHERE id = 2")
        cursor.moveToFirst()
        assertEquals("COMPLETED", cursor.getString(0))
        assertEquals(0, cursor.getInt(1))
        cursor.close()

        db.close()
    }

    @Test
    fun `MIGRATION_6_7 removes old columns so new inserts succeed`() {
        val db = createV6Database()

        db.execSQL(
            "INSERT INTO habits (id, name, full_description, low_floor_description, active, created_at, updated_at) " +
            "VALUES (1, 'med', 'f', 'l', 1, 0, 0)"
        )

        MIGRATION_6_7.migrate(db)

        // Insert a new habit using only v7 columns — would crash before fix
        // because full_description/low_floor_description were NOT NULL with no default
        db.execSQL(
            "INSERT INTO habits (name, dedication_level, level_descriptions, created_at, updated_at) " +
            "VALUES ('New Habit', 0, '[\"a\",\"\",\"\",\"b\",\"\",\"\"]', 0, 0)"
        )
        val cursor = db.query("SELECT name FROM habits WHERE name = 'New Habit'")
        assertEquals(1, cursor.count)
        cursor.close()

        // Verify old columns no longer exist
        val colCursor = db.query("PRAGMA table_info(habits)")
        val columns = mutableSetOf<String>()
        while (colCursor.moveToNext()) {
            columns.add(colCursor.getString(colCursor.getColumnIndex("name")))
        }
        colCursor.close()
        assertFalse("full_description should have been removed", "full_description" in columns)
        assertFalse("low_floor_description should have been removed", "low_floor_description" in columns)

        db.close()
    }

    @Test
    fun `MIGRATION_6_7 leaves non-completion triggers unchanged`() {
        val db = createV6Database()

        db.execSQL(
            "INSERT INTO habits (id, name, full_description, low_floor_description, active, created_at, updated_at) " +
            "VALUES (1, 'med', 'f', 'l', 1, 0, 0)"
        )
        db.execSQL("INSERT INTO triggers (id, scheduled_at, status, habit_id) VALUES (3, 0, 'DISMISSED', 1)")

        MIGRATION_6_7.migrate(db)

        val cursor = db.query("SELECT status, completion_level FROM triggers WHERE id = 3")
        cursor.moveToFirst()
        assertEquals("DISMISSED", cursor.getString(0))
        // completion_level should be null for DISMISSED triggers
        assertEquals(true, cursor.isNull(1))
        cursor.close()

        db.close()
    }
}
