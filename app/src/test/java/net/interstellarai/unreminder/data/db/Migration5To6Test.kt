package net.interstellarai.unreminder.data.db

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class Migration5To6Test {

    private fun createV5Database(): SupportSQLiteDatabase {
        val config = SupportSQLiteOpenHelper.Configuration.builder(RuntimeEnvironment.getApplication())
            .name(null) // in-memory
            .callback(object : SupportSQLiteOpenHelper.Callback(5) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `habits` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `full_description` TEXT NOT NULL, " +
                        "`low_floor_description` TEXT NOT NULL, `active` INTEGER NOT NULL DEFAULT 1, " +
                        "`created_at` INTEGER NOT NULL DEFAULT 0, `updated_at` INTEGER NOT NULL DEFAULT 0)"
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `windows` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`label` TEXT NOT NULL, `start_time` INTEGER NOT NULL, " +
                        "`end_time` INTEGER NOT NULL, `days_of_week_bitmask` INTEGER NOT NULL, " +
                        "`active` INTEGER NOT NULL DEFAULT 1)"
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        return FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase
    }

    @Test
    fun `MIGRATION_5_6 creates habit_window table with composite PK and index`() {
        val db = createV5Database()

        // Seed data for foreign key references
        db.execSQL(
            "INSERT INTO habits (name, full_description, low_floor_description, active, created_at, updated_at) " +
            "VALUES ('h', 'f', 'l', 1, 0, 0)"
        )
        db.execSQL(
            "INSERT INTO windows (label, start_time, end_time, days_of_week_bitmask, active) " +
            "VALUES ('Morning', 28800, 43200, 127, 1)"
        )

        // Apply the migration
        MIGRATION_5_6.migrate(db)

        // Verify the habit_window table was created
        val tableCursor = db.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='habit_window'"
        )
        assertEquals(1, tableCursor.count)
        tableCursor.close()

        // Verify all required columns exist
        val colCursor = db.query("PRAGMA table_info(habit_window)")
        val columns = mutableSetOf<String>()
        while (colCursor.moveToNext()) {
            columns.add(colCursor.getString(colCursor.getColumnIndex("name")))
        }
        colCursor.close()
        assertTrue(columns.containsAll(listOf("habit_id", "window_id")))

        // Verify index was created
        val idxCursor = db.query(
            "SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='habit_window'"
        )
        val indices = mutableSetOf<String>()
        while (idxCursor.moveToNext()) {
            indices.add(idxCursor.getString(0))
        }
        idxCursor.close()
        assertTrue(indices.contains("index_habit_window_window_id"))

        // Verify data can be inserted
        db.execSQL("INSERT INTO habit_window (habit_id, window_id) VALUES (1, 1)")
        val countCursor = db.query("SELECT COUNT(*) FROM habit_window WHERE habit_id = 1")
        countCursor.moveToFirst()
        assertEquals(1, countCursor.getInt(0))
        countCursor.close()

        db.close()
    }

    @Test
    fun `MIGRATION_5_6 is idempotent with IF NOT EXISTS clauses`() {
        val db = createV5Database()

        // Run migration twice — should not throw
        MIGRATION_5_6.migrate(db)
        MIGRATION_5_6.migrate(db)

        val tableCursor = db.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='habit_window'"
        )
        assertEquals(1, tableCursor.count)
        tableCursor.close()

        db.close()
    }
}
