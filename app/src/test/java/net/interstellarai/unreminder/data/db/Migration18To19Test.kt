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
class Migration18To19Test {

    private fun createV18Database(): SupportSQLiteDatabase {
        val config = SupportSQLiteOpenHelper.Configuration.builder(RuntimeEnvironment.getApplication())
            .name(null) // in-memory
            .callback(object : SupportSQLiteOpenHelper.Callback(18) {
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
                        "`sprite_tag` TEXT, " +
                        "`source` TEXT)"
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        return FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase
    }

    @Test
    fun `MIGRATION_18_19 keeps existing triggers and leaves their variation id null`() {
        val db = createV18Database()

        db.execSQL(
            "INSERT INTO triggers (habit_id, scheduled_at, fired_at, status, generated_prompt, style, sprite_tag) " +
            "VALUES (1, 0, 10, 'FIRED', 'sing', 'SPRITE', 'wizard_starry_robe')"
        )

        MIGRATION_18_19.migrate(db)

        val cursor = db.query("SELECT generated_prompt, style, sprite_tag, variation_id FROM triggers")
        cursor.moveToFirst()
        assertEquals(1, cursor.count)
        assertEquals("sing", cursor.getString(0))
        assertEquals("SPRITE", cursor.getString(1))
        assertEquals("wizard_starry_robe", cursor.getString(2))
        assertTrue(cursor.isNull(3))
        cursor.close()

        db.close()
    }

    @Test
    fun `MIGRATION_18_19 allows storing a variation id after migration`() {
        val db = createV18Database()
        MIGRATION_18_19.migrate(db)

        db.execSQL(
            "INSERT INTO triggers (habit_id, scheduled_at, fired_at, status, generated_prompt, variation_id) " +
            "VALUES (1, 0, 10, 'FIRED', 'breathe', 77)"
        )

        val cursor = db.query("SELECT variation_id FROM triggers WHERE generated_prompt = 'breathe'")
        cursor.moveToFirst()
        assertEquals(77L, cursor.getLong(0))
        cursor.close()

        db.close()
    }
}
