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
class Migration9To10Test {

    private fun createV9Database(): SupportSQLiteDatabase {
        val config = SupportSQLiteOpenHelper.Configuration.builder(RuntimeEnvironment.getApplication())
            .name(null) // in-memory
            .callback(object : SupportSQLiteOpenHelper.Callback(9) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `windows` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`start_time` INTEGER NOT NULL, " +
                        "`end_time` INTEGER NOT NULL, " +
                        "`days_of_week_bitmask` INTEGER NOT NULL, " +
                        "`frequency_per_day` INTEGER NOT NULL, " +
                        "`active` INTEGER NOT NULL)"
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `variations` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`habit_id` INTEGER NOT NULL, " +
                        "`text` TEXT NOT NULL, " +
                        "`prompt_fingerprint` TEXT NOT NULL, " +
                        "`generated_at` INTEGER NOT NULL, " +
                        "`consumed_at` INTEGER)"
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        return FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase
    }

    @Test
    fun `MIGRATION_9_10 adds name column with default empty string`() {
        val db = createV9Database()

        db.execSQL(
            "INSERT INTO windows (start_time, end_time, days_of_week_bitmask, frequency_per_day, active) " +
            "VALUES (32400, 61200, 127, 1, 1)"
        )

        MIGRATION_9_10.migrate(db)

        val cursor = db.query("SELECT name FROM windows LIMIT 1")
        cursor.moveToFirst()
        assertEquals("", cursor.getString(0))
        cursor.close()

        db.close()
    }

    @Test
    fun `MIGRATION_9_10 adds action_url column defaulting to null`() {
        val db = createV9Database()

        db.execSQL(
            "INSERT INTO variations (habit_id, text, prompt_fingerprint, generated_at) " +
            "VALUES (1, 'Sing the scale', 'fp', 0)"
        )

        MIGRATION_9_10.migrate(db)

        val cursor = db.query("SELECT action_url FROM variations WHERE text = 'Sing the scale'")
        cursor.moveToFirst()
        assertNull(cursor.getString(0))
        cursor.close()

        db.close()
    }

    @Test
    fun `MIGRATION_9_10 allows inserting non-null action_url after migration`() {
        val db = createV9Database()
        MIGRATION_9_10.migrate(db)

        val url = "https://www.youtube.com/results?search_query=C+major+scale"
        db.execSQL(
            "INSERT INTO variations (habit_id, text, prompt_fingerprint, generated_at, action_url) " +
            "VALUES (1, 'Sing the scale', 'fp', 0, '$url')"
        )

        val cursor = db.query("SELECT action_url FROM variations WHERE text = 'Sing the scale'")
        cursor.moveToFirst()
        assert(cursor.getString(0) == url)
        cursor.close()

        db.close()
    }
}
