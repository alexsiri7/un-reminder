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
class Migration4To5Test {

    /**
     * Create an in-memory SQLite database at "version 4" with the habits table,
     * then apply MIGRATION_4_5 and verify the result.
     * This avoids MigrationTestHelper which requires exported Room schemas
     * (exportSchema = false in this project).
     */
    private fun createV4Database(): SupportSQLiteDatabase {
        val config = SupportSQLiteOpenHelper.Configuration.builder(RuntimeEnvironment.getApplication())
            .name(null) // in-memory
            .callback(object : SupportSQLiteOpenHelper.Callback(4) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `habits` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `full_description` TEXT NOT NULL, " +
                        "`low_floor_description` TEXT NOT NULL, `active` INTEGER NOT NULL DEFAULT 1, " +
                        "`created_at` INTEGER NOT NULL DEFAULT 0, `updated_at` INTEGER NOT NULL DEFAULT 0)"
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        return FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase
    }

    @Test
    fun `MIGRATION_4_5 creates variations table with all required columns`() {
        val db = createV4Database()

        // Seed a habit for foreign key references
        db.execSQL(
            "INSERT INTO habits (name, full_description, low_floor_description, active, created_at, updated_at) " +
            "VALUES ('h', 'f', 'l', 1, 0, 0)"
        )

        // Apply the migration
        MIGRATION_4_5.migrate(db)

        // Verify the variations table was created
        val tableCursor = db.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='variations'"
        )
        assertEquals(1, tableCursor.count)
        tableCursor.close()

        // Verify all required columns exist
        val colCursor = db.query("PRAGMA table_info(variations)")
        val columns = mutableSetOf<String>()
        while (colCursor.moveToNext()) {
            columns.add(colCursor.getString(colCursor.getColumnIndex("name")))
        }
        colCursor.close()
        assertTrue(
            columns.containsAll(
                listOf("id", "habit_id", "text", "prompt_fingerprint", "generated_at", "consumed_at")
            )
        )

        // Verify both indices were created
        val idxCursor = db.query(
            "SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='variations'"
        )
        val indices = mutableSetOf<String>()
        while (idxCursor.moveToNext()) {
            indices.add(idxCursor.getString(0))
        }
        idxCursor.close()
        assertTrue(indices.contains("index_variations_habit_id"))
        assertTrue(indices.contains("index_variations_habit_id_prompt_fingerprint_text"))

        // Verify data can be inserted
        db.execSQL(
            "INSERT INTO variations (habit_id, text, prompt_fingerprint, generated_at) VALUES (1, 'v', 'fp', 0)"
        )
        val countCursor = db.query("SELECT COUNT(*) FROM variations WHERE habit_id = 1")
        countCursor.moveToFirst()
        assertEquals(1, countCursor.getInt(0))
        countCursor.close()

        db.close()
    }

    @Test
    fun `MIGRATION_4_5 is idempotent with IF NOT EXISTS clauses`() {
        val db = createV4Database()

        // Run migration twice — should not throw
        MIGRATION_4_5.migrate(db)
        MIGRATION_4_5.migrate(db)

        val tableCursor = db.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='variations'"
        )
        assertEquals(1, tableCursor.count)
        tableCursor.close()

        db.close()
    }
}
