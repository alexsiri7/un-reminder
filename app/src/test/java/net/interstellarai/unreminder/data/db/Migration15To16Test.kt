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
class Migration15To16Test {

    private fun createV15Database(): SupportSQLiteDatabase {
        val config = SupportSQLiteOpenHelper.Configuration.builder(RuntimeEnvironment.getApplication())
            .name(null) // in-memory
            .callback(object : SupportSQLiteOpenHelper.Callback(15) {
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
                        "`shape` TEXT, " +
                        "`modes` INTEGER NOT NULL DEFAULT 0)"
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        return FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase
    }

    @Test
    fun `MIGRATION_15_16 keeps existing variations and reads them as unversioned`() {
        val db = createV15Database()

        db.execSQL(
            "INSERT INTO variations (habit_id, text, prompt_fingerprint, generated_at, shape) " +
            "VALUES (1, 'breathe slowly', 'fp', 0, 'STATEMENT')"
        )

        MIGRATION_15_16.migrate(db)

        val cursor = db.query("SELECT text, generation_version FROM variations")
        cursor.moveToFirst()
        assertEquals(1, cursor.count)
        assertEquals("breathe slowly", cursor.getString(0))
        assertEquals(VariationEntity.UNVERSIONED, cursor.getInt(1))
        cursor.close()

        db.close()
    }

    @Test
    fun `MIGRATION_15_16 allows storing a generation version after migration`() {
        val db = createV15Database()
        MIGRATION_15_16.migrate(db)

        db.execSQL(
            "INSERT INTO variations (habit_id, text, prompt_fingerprint, generated_at, shape, generation_version) " +
            "VALUES (1, 'count 20 steps', 'fp', 0, 'TERSE', 2)"
        )

        val cursor = db.query("SELECT generation_version FROM variations WHERE text = 'count 20 steps'")
        cursor.moveToFirst()
        assertEquals(2, cursor.getInt(0))
        cursor.close()

        db.close()
    }
}
