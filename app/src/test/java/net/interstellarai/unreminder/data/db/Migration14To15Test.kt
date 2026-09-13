package net.interstellarai.unreminder.data.db

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class Migration14To15Test {

    private fun createV14Database(): SupportSQLiteDatabase {
        val config = SupportSQLiteOpenHelper.Configuration.builder(RuntimeEnvironment.getApplication())
            .name(null) // in-memory
            .callback(object : SupportSQLiteOpenHelper.Callback(14) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `variations` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`habit_id` INTEGER NOT NULL, " +
                        "`text` TEXT NOT NULL, " +
                        "`prompt_fingerprint` TEXT NOT NULL, " +
                        "`generated_at` INTEGER NOT NULL, " +
                        "`consumed_at` INTEGER, " +
                        "`action_url` TEXT, " +
                        "`sprite_tag` TEXT, " +
                        "`shape` TEXT)"
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        return FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase
    }

    @Test
    fun `MIGRATION_14_15 keeps existing variations and reads them as mode-neutral`() {
        val db = createV14Database()

        db.execSQL(
            "INSERT INTO variations (habit_id, text, prompt_fingerprint, generated_at, shape) " +
            "VALUES (1, 'breathe slowly', 'fp', 0, 'STATEMENT')"
        )

        MIGRATION_14_15.migrate(db)

        val cursor = db.query("SELECT text, modes FROM variations")
        cursor.moveToFirst()
        assertEquals(1, cursor.count)
        assertEquals("breathe slowly", cursor.getString(0))
        assertEquals(0, cursor.getInt(1))
        cursor.close()

        db.close()
    }

    @Test
    fun `MIGRATION_14_15 allows storing a mode set after migration`() {
        val db = createV14Database()
        MIGRATION_14_15.migrate(db)

        db.execSQL(
            "INSERT INTO variations (habit_id, text, prompt_fingerprint, generated_at, shape, modes) " +
            "VALUES (1, 'count 20 steps', 'fp', 0, 'TERSE', 5)"
        )

        val cursor = db.query("SELECT modes FROM variations WHERE text = 'count 20 steps'")
        cursor.moveToFirst()
        assertEquals(5, cursor.getInt(0))
        cursor.close()

        db.close()
    }
}
