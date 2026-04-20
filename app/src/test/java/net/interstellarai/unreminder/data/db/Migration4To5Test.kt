package net.interstellarai.unreminder.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class Migration4To5Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun `MIGRATION_4_5 creates variation table with all required columns`() {
        val db = helper.createDatabase(TEST_DB, 4)
        // Create the v4 habits table so the foreign key reference is valid
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `habits` (" +
            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`name` TEXT NOT NULL, `full_description` TEXT NOT NULL, " +
            "`low_floor_description` TEXT NOT NULL, `active` INTEGER NOT NULL DEFAULT 1, " +
            "`created_at` INTEGER NOT NULL DEFAULT 0, `updated_at` INTEGER NOT NULL DEFAULT 0)"
        )
        db.execSQL(
            "INSERT INTO habits (name, full_description, low_floor_description, active, created_at, updated_at) " +
            "VALUES ('h', 'f', 'l', 1, 0, 0)"
        )

        // Apply the migration directly
        MIGRATION_4_5.migrate(db)

        // Verify the variation table was created
        val tableCursor = db.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='variation'"
        )
        assertEquals(1, tableCursor.count)
        tableCursor.close()

        // Verify all required columns exist
        val colCursor = db.query("PRAGMA table_info(variation)")
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
            "SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='variation'"
        )
        val indices = mutableSetOf<String>()
        while (idxCursor.moveToNext()) {
            indices.add(idxCursor.getString(0))
        }
        idxCursor.close()
        assertTrue(indices.contains("index_variation_habit_id"))
        assertTrue(indices.contains("index_variation_habit_id_prompt_fingerprint_text"))

        // Verify data can be inserted
        db.execSQL(
            "INSERT INTO variation (habit_id, text, prompt_fingerprint, generated_at) VALUES (1, 'v', 'fp', 0)"
        )
        // Verify data can be inserted (duplicate handling is tested in VariationDaoTest)
        val countCursor = db.query("SELECT COUNT(*) FROM variation WHERE habit_id = 1")
        countCursor.moveToFirst()
        assertEquals(1, countCursor.getInt(0))
        countCursor.close()

        db.close()
    }

    companion object {
        private const val TEST_DB = "migration-4-5-test"
    }
}
