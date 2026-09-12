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
class Migration12To13Test {

    private fun createV12Database(): SupportSQLiteDatabase {
        val config = SupportSQLiteOpenHelper.Configuration.builder(RuntimeEnvironment.getApplication())
            .name(null) // in-memory
            .callback(object : SupportSQLiteOpenHelper.Callback(12) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `habits` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`dedication_level` INTEGER NOT NULL, " +
                        "`description_ladder` TEXT NOT NULL, " +
                        "`auto_adjust_level` INTEGER NOT NULL, " +
                        "`daily_limit` INTEGER NOT NULL, " +
                        "`cooldown_minutes` INTEGER NOT NULL DEFAULT 180, " +
                        "`active` INTEGER NOT NULL, " +
                        "`created_at` INTEGER NOT NULL, " +
                        "`updated_at` INTEGER NOT NULL)"
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        return FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase
    }

    @Test
    fun `MIGRATION_12_13 keeps existing habits and lets them support any mode`() {
        val db = createV12Database()

        db.execSQL(
            "INSERT INTO habits (name, dedication_level, description_ladder, auto_adjust_level, daily_limit, active, created_at, updated_at) " +
            "VALUES ('meditation', 2, '[]', 1, 1, 1, 0, 0)"
        )

        MIGRATION_12_13.migrate(db)

        val cursor = db.query("SELECT name, supported_modes FROM habits")
        cursor.moveToFirst()
        assertEquals(1, cursor.count)
        assertEquals("meditation", cursor.getString(0))
        assertEquals(0, cursor.getInt(1))
        cursor.close()

        db.close()
    }

    @Test
    fun `MIGRATION_12_13 allows storing a mode set after migration`() {
        val db = createV12Database()
        MIGRATION_12_13.migrate(db)

        db.execSQL(
            "INSERT INTO habits (name, dedication_level, description_ladder, auto_adjust_level, daily_limit, active, created_at, updated_at, supported_modes) " +
            "VALUES ('podcast', 2, '[]', 1, 1, 1, 0, 0, 5)"
        )

        val cursor = db.query("SELECT supported_modes FROM habits WHERE name = 'podcast'")
        cursor.moveToFirst()
        assertEquals(5, cursor.getInt(0))
        cursor.close()

        db.close()
    }
}
