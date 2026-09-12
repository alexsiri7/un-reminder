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
class Migration11To12Test {

    private fun createV11Database(): SupportSQLiteDatabase {
        val config = SupportSQLiteOpenHelper.Configuration.builder(RuntimeEnvironment.getApplication())
            .name(null) // in-memory
            .callback(object : SupportSQLiteOpenHelper.Callback(11) {
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
                        "`sprite_tag` TEXT)"
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        return FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase
    }

    @Test
    fun `MIGRATION_11_12 preserves existing rows and leaves their shape null`() {
        val db = createV11Database()

        db.execSQL(
            "INSERT INTO variations (habit_id, text, prompt_fingerprint, generated_at, sprite_tag) " +
            "VALUES (1, 'Sing the scale', 'fp', 0, 'astronaut_zero_g')"
        )

        MIGRATION_11_12.migrate(db)

        val cursor = db.query("SELECT text, sprite_tag, shape FROM variations")
        cursor.moveToFirst()
        assertEquals(1, cursor.count)
        assertEquals("Sing the scale", cursor.getString(0))
        assertEquals("astronaut_zero_g", cursor.getString(1))
        assertNull(cursor.getString(2))
        cursor.close()

        db.close()
    }

    @Test
    fun `MIGRATION_11_12 allows inserting a shape after migration`() {
        val db = createV11Database()
        MIGRATION_11_12.migrate(db)

        db.execSQL(
            "INSERT INTO variations (habit_id, text, prompt_fingerprint, generated_at, shape) " +
            "VALUES (1, 'Drink water', 'fp', 0, 'TERSE')"
        )

        val cursor = db.query("SELECT shape FROM variations WHERE text = 'Drink water'")
        cursor.moveToFirst()
        assertEquals("TERSE", cursor.getString(0))
        cursor.close()

        db.close()
    }
}
