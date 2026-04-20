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
                        "CREATE TABLE IF NOT EXISTS `variations` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`habit_id` INTEGER NOT NULL, `text` TEXT NOT NULL, " +
                        "`prompt_fingerprint` TEXT NOT NULL, " +
                        "`generated_at` INTEGER NOT NULL DEFAULT 0, " +
                        "`consumed_at` INTEGER, " +
                        "FOREIGN KEY(`habit_id`) REFERENCES `habits`(`id`) ON DELETE CASCADE)"
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_variations_habit_id` ON `variations` (`habit_id`)")
                    db.execSQL(
                        "CREATE UNIQUE INDEX IF NOT EXISTS `index_variations_habit_id_prompt_fingerprint_text` " +
                        "ON `variations` (`habit_id`, `prompt_fingerprint`, `text`)"
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        return FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase
    }

    @Test
    fun `MIGRATION_5_6 adds dedication_level and auto_adjust_level columns`() {
        val db = createV5Database()
        db.execSQL(
            "INSERT INTO habits (name, full_description, low_floor_description, active, created_at, updated_at) " +
            "VALUES ('h', 'full', 'low', 1, 0, 0)"
        )

        MIGRATION_5_6.migrate(db)

        val cursor = db.query("PRAGMA table_info(habits)")
        val columns = mutableSetOf<String>()
        while (cursor.moveToNext()) {
            columns.add(cursor.getString(cursor.getColumnIndex("name")))
        }
        cursor.close()
        assertTrue(columns.contains("dedication_level"))
        assertTrue(columns.contains("auto_adjust_level"))

        // Verify defaults
        val valueCursor = db.query("SELECT dedication_level, auto_adjust_level FROM habits WHERE id = 1")
        valueCursor.moveToFirst()
        assertEquals(0, valueCursor.getInt(0))
        assertEquals(1, valueCursor.getInt(1))
        valueCursor.close()

        db.close()
    }

    @Test
    fun `MIGRATION_5_6 creates habit_level_descriptions table`() {
        val db = createV5Database()

        MIGRATION_5_6.migrate(db)

        val tableCursor = db.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='habit_level_descriptions'"
        )
        assertEquals(1, tableCursor.count)
        tableCursor.close()

        val colCursor = db.query("PRAGMA table_info(habit_level_descriptions)")
        val columns = mutableSetOf<String>()
        while (colCursor.moveToNext()) {
            columns.add(colCursor.getString(colCursor.getColumnIndex("name")))
        }
        colCursor.close()
        assertTrue(columns.containsAll(listOf("id", "habit_id", "level", "description")))

        // Verify unique index exists
        val idxCursor = db.query(
            "SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='habit_level_descriptions'"
        )
        val indices = mutableSetOf<String>()
        while (idxCursor.moveToNext()) {
            indices.add(idxCursor.getString(0))
        }
        idxCursor.close()
        assertTrue(indices.contains("index_habit_level_descriptions_habit_id_level"))

        db.close()
    }

    @Test
    fun `MIGRATION_5_6 backfills level 0 from low_floor_description`() {
        val db = createV5Database()
        db.execSQL(
            "INSERT INTO habits (name, full_description, low_floor_description, active, created_at, updated_at) " +
            "VALUES ('h', 'full text', 'low text', 1, 0, 0)"
        )

        MIGRATION_5_6.migrate(db)

        val cursor = db.query(
            "SELECT description FROM habit_level_descriptions WHERE habit_id = 1 AND level = 0"
        )
        cursor.moveToFirst()
        assertEquals("low text", cursor.getString(0))
        cursor.close()

        db.close()
    }

    @Test
    fun `MIGRATION_5_6 backfills level 5 from full_description`() {
        val db = createV5Database()
        db.execSQL(
            "INSERT INTO habits (name, full_description, low_floor_description, active, created_at, updated_at) " +
            "VALUES ('h', 'full text', 'low text', 1, 0, 0)"
        )

        MIGRATION_5_6.migrate(db)

        val cursor = db.query(
            "SELECT description FROM habit_level_descriptions WHERE habit_id = 1 AND level = 5"
        )
        cursor.moveToFirst()
        assertEquals("full text", cursor.getString(0))
        cursor.close()

        db.close()
    }

}
