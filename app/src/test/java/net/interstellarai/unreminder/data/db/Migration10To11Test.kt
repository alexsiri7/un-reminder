package net.interstellarai.unreminder.data.db

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class Migration10To11Test {

    private fun createV10Database(): SupportSQLiteDatabase {
        val config = SupportSQLiteOpenHelper.Configuration.builder(RuntimeEnvironment.getApplication())
            .name(null) // in-memory
            .callback(object : SupportSQLiteOpenHelper.Callback(10) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `variations` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`habit_id` INTEGER NOT NULL, " +
                        "`text` TEXT NOT NULL, " +
                        "`prompt_fingerprint` TEXT NOT NULL, " +
                        "`generated_at` INTEGER NOT NULL, " +
                        "`consumed_at` INTEGER, " +
                        "`action_url` TEXT)"
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        return FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase
    }

    @Test
    fun `MIGRATION_10_11 preserves existing rows and defaults sprite_tag to null`() {
        val db = createV10Database()

        db.execSQL(
            "INSERT INTO variations (habit_id, text, prompt_fingerprint, generated_at, action_url) " +
            "VALUES (1, 'Sing the scale', 'fp', 0, 'https://www.youtube.com/results?search_query=scale')"
        )

        MIGRATION_10_11.migrate(db)

        val cursor = db.query("SELECT text, action_url, sprite_tag FROM variations")
        cursor.moveToFirst()
        assertEquals(1, cursor.count)
        assertEquals("Sing the scale", cursor.getString(0))
        assertEquals("https://www.youtube.com/results?search_query=scale", cursor.getString(1))
        assertNull(cursor.getString(2))
        cursor.close()

        db.close()
    }

    @Test
    fun `MIGRATION_10_11 allows inserting non-null sprite_tag after migration`() {
        val db = createV10Database()
        MIGRATION_10_11.migrate(db)

        db.execSQL(
            "INSERT INTO variations (habit_id, text, prompt_fingerprint, generated_at, sprite_tag) " +
            "VALUES (1, 'Drink water', 'fp', 0, 'astronaut_zero_g')"
        )

        val cursor = db.query("SELECT sprite_tag FROM variations WHERE text = 'Drink water'")
        cursor.moveToFirst()
        assertEquals("astronaut_zero_g", cursor.getString(0))
        cursor.close()

        db.close()
    }
}
