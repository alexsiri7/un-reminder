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
class Migration13To14Test {

    private fun createV13Database(): SupportSQLiteDatabase {
        val config = SupportSQLiteOpenHelper.Configuration.builder(RuntimeEnvironment.getApplication())
            .name(null) // in-memory
            .callback(object : SupportSQLiteOpenHelper.Callback(13) {
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
                        "`source` TEXT)"
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        return FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase
    }

    @Test
    fun `MIGRATION_13_14 keeps existing triggers and leaves their action_url null`() {
        val db = createV13Database()

        db.execSQL(
            "INSERT INTO triggers (habit_id, scheduled_at, fired_at, status, generated_prompt) " +
            "VALUES (1, 0, 10, 'FIRED', 'breathe slowly')"
        )

        MIGRATION_13_14.migrate(db)

        val cursor = db.query("SELECT generated_prompt, action_url FROM triggers")
        cursor.moveToFirst()
        assertEquals(1, cursor.count)
        assertEquals("breathe slowly", cursor.getString(0))
        assertTrue(cursor.isNull(1))
        cursor.close()

        db.close()
    }

    @Test
    fun `MIGRATION_13_14 allows storing an action_url after migration`() {
        val db = createV13Database()
        MIGRATION_13_14.migrate(db)

        db.execSQL(
            "INSERT INTO triggers (habit_id, scheduled_at, fired_at, status, generated_prompt, action_url) " +
            "VALUES (1, 0, 10, 'FIRED', 'sing', 'https://www.youtube.com/results?search_query=scale')"
        )

        val cursor = db.query("SELECT action_url FROM triggers WHERE generated_prompt = 'sing'")
        cursor.moveToFirst()
        assertEquals("https://www.youtube.com/results?search_query=scale", cursor.getString(0))
        cursor.close()

        db.close()
    }
}
