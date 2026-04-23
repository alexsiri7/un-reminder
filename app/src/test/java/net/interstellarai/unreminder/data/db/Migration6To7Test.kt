package net.interstellarai.unreminder.data.db

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class Migration6To7Test {

    private fun createV6Database(): SupportSQLiteDatabase {
        val config = SupportSQLiteOpenHelper.Configuration.builder(RuntimeEnvironment.getApplication())
            .name(null) // in-memory
            .callback(object : SupportSQLiteOpenHelper.Callback(6) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `habits` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `full_description` TEXT NOT NULL, " +
                        "`low_floor_description` TEXT NOT NULL, `active` INTEGER NOT NULL DEFAULT 1, " +
                        "`created_at` INTEGER NOT NULL DEFAULT 0, `updated_at` INTEGER NOT NULL DEFAULT 0)"
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `habit_window` (" +
                        "`habit_id` INTEGER NOT NULL, `window_id` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`habit_id`, `window_id`)" +
                        ")"
                    )
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            }).build()
        return FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase
    }

    @Test
    fun `migration adds dedication_level and auto_adjust_level columns to habits`() {
        val db = createV6Database()
        db.execSQL(
            "INSERT INTO habits (name, full_description, low_floor_description) VALUES ('h', 'full', 'low')"
        )

        MIGRATION_6_7.migrate(db)

        val cursor = db.query("PRAGMA table_info(habits)")
        val columns = mutableSetOf<String>()
        while (cursor.moveToNext()) {
            columns.add(cursor.getString(cursor.getColumnIndex("name")))
        }
        cursor.close()

        assertTrue(columns.contains("dedication_level"))
        assertTrue(columns.contains("auto_adjust_level"))
        assertFalse(columns.contains("full_description"))
        assertFalse(columns.contains("low_floor_description"))

        db.close()
    }

    @Test
    fun `migration creates habit_level_descriptions table`() {
        val db = createV6Database()
        MIGRATION_6_7.migrate(db)

        val tableCursor = db.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='habit_level_descriptions'"
        )
        assertEquals(1, tableCursor.count)
        tableCursor.close()

        db.close()
    }

    @Test
    fun `migration backfills level descriptions from old columns`() {
        val db = createV6Database()
        db.execSQL(
            "INSERT INTO habits (name, full_description, low_floor_description) VALUES ('h', 'full desc', 'low desc')"
        )

        MIGRATION_6_7.migrate(db)

        val cursor = db.query(
            "SELECT * FROM habit_level_descriptions WHERE habit_id = 1 ORDER BY level ASC"
        )
        assertEquals(2, cursor.count)

        cursor.moveToFirst()
        assertEquals(0, cursor.getInt(cursor.getColumnIndex("level")))
        assertEquals("low desc", cursor.getString(cursor.getColumnIndex("description")))

        cursor.moveToNext()
        assertEquals(5, cursor.getInt(cursor.getColumnIndex("level")))
        assertEquals("full desc", cursor.getString(cursor.getColumnIndex("description")))

        cursor.close()
        db.close()
    }

    @Test
    fun `migration preserves habit data`() {
        val db = createV6Database()
        db.execSQL(
            "INSERT INTO habits (name, full_description, low_floor_description, active, created_at, updated_at) " +
            "VALUES ('meditation', 'full', 'low', 1, 100, 200)"
        )

        MIGRATION_6_7.migrate(db)

        val cursor = db.query("SELECT * FROM habits WHERE id = 1")
        cursor.moveToFirst()
        assertEquals("meditation", cursor.getString(cursor.getColumnIndex("name")))
        assertEquals(1, cursor.getInt(cursor.getColumnIndex("active")))
        assertEquals(0, cursor.getInt(cursor.getColumnIndex("dedication_level")))
        assertEquals(1, cursor.getInt(cursor.getColumnIndex("auto_adjust_level")))
        assertEquals(100, cursor.getLong(cursor.getColumnIndex("created_at")))
        assertEquals(200, cursor.getLong(cursor.getColumnIndex("updated_at")))
        cursor.close()

        db.close()
    }

    @Test
    fun `migration creates index on habit_level_descriptions`() {
        val db = createV6Database()
        MIGRATION_6_7.migrate(db)

        val idxCursor = db.query(
            "SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='habit_level_descriptions'"
        )
        val indices = mutableSetOf<String>()
        while (idxCursor.moveToNext()) {
            indices.add(idxCursor.getString(0))
        }
        idxCursor.close()

        assertTrue(indices.contains("index_habit_level_descriptions_habit_id"))

        db.close()
    }
}
