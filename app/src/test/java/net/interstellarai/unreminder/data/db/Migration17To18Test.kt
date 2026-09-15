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
class Migration17To18Test {

    private fun createV17Database(): SupportSQLiteDatabase {
        val config = SupportSQLiteOpenHelper.Configuration.builder(RuntimeEnvironment.getApplication())
            .name(null) // in-memory
            .callback(object : SupportSQLiteOpenHelper.Callback(17) {
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
                        "`style` TEXT, " +
                        "`source` TEXT)"
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        return FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase
    }

    @Test
    fun `MIGRATION_17_18 keeps existing triggers and leaves their sprite tag null`() {
        val db = createV17Database()

        db.execSQL(
            "INSERT INTO triggers (habit_id, scheduled_at, fired_at, status, generated_prompt, action_url, style) " +
            "VALUES (1, 0, 10, 'FIRED', 'sing', 'https://www.youtube.com/results?search_query=scale', 'SPRITE')"
        )

        MIGRATION_17_18.migrate(db)

        val cursor = db.query("SELECT generated_prompt, action_url, style, sprite_tag FROM triggers")
        cursor.moveToFirst()
        assertEquals(1, cursor.count)
        assertEquals("sing", cursor.getString(0))
        assertEquals("https://www.youtube.com/results?search_query=scale", cursor.getString(1))
        assertEquals("SPRITE", cursor.getString(2))
        assertTrue(cursor.isNull(3))
        cursor.close()

        db.close()
    }

    @Test
    fun `MIGRATION_17_18 allows storing a sprite tag after migration`() {
        val db = createV17Database()
        MIGRATION_17_18.migrate(db)

        db.execSQL(
            "INSERT INTO triggers (habit_id, scheduled_at, fired_at, status, generated_prompt, sprite_tag) " +
            "VALUES (1, 0, 10, 'FIRED', 'breathe', 'wizard_starry_robe')"
        )

        val cursor = db.query("SELECT sprite_tag FROM triggers WHERE generated_prompt = 'breathe'")
        cursor.moveToFirst()
        assertEquals("wizard_starry_robe", cursor.getString(0))
        cursor.close()

        db.close()
    }
}
