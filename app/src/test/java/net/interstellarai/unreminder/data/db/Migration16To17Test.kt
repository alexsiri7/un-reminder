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
class Migration16To17Test {

    private fun createV16Database(): SupportSQLiteDatabase {
        val config = SupportSQLiteOpenHelper.Configuration.builder(RuntimeEnvironment.getApplication())
            .name(null) // in-memory
            .callback(object : SupportSQLiteOpenHelper.Callback(16) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `triggers` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`window_id` INTEGER, " +
                        "`habit_id` INTEGER, " +
                        "`scheduled_at` INTEGER NOT NULL, " +
                        "`fired_at` INTEGER, " +
                        "`status` TEXT NOT NULL, " +
                        "`generated_prompt` TEXT, " +
                        "`action_url` TEXT, " +
                        "`source` TEXT)"
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        return FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase
    }

    @Test
    fun `MIGRATION_16_17 keeps existing triggers and leaves their style null`() {
        val db = createV16Database()

        db.execSQL(
            "INSERT INTO triggers (habit_id, scheduled_at, fired_at, status, generated_prompt, action_url) " +
            "VALUES (1, 0, 10, 'FIRED', 'sing', 'https://www.youtube.com/results?search_query=scale')"
        )

        MIGRATION_16_17.migrate(db)

        val cursor = db.query("SELECT generated_prompt, action_url, style FROM triggers")
        cursor.moveToFirst()
        assertEquals(1, cursor.count)
        assertEquals("sing", cursor.getString(0))
        assertEquals("https://www.youtube.com/results?search_query=scale", cursor.getString(1))
        assertTrue(cursor.isNull(2))
        cursor.close()

        db.close()
    }

    @Test
    fun `MIGRATION_16_17 allows storing a style after migration`() {
        val db = createV16Database()
        MIGRATION_16_17.migrate(db)

        db.execSQL(
            "INSERT INTO triggers (habit_id, scheduled_at, fired_at, status, generated_prompt, style) " +
            "VALUES (1, 0, 10, 'FIRED', 'breathe', 'BIG_PICTURE')"
        )

        val cursor = db.query("SELECT style FROM triggers WHERE generated_prompt = 'breathe'")
        cursor.moveToFirst()
        assertEquals("BIG_PICTURE", cursor.getString(0))
        cursor.close()

        db.close()
    }
}
