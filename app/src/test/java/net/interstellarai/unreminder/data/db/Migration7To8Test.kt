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
class Migration7To8Test {

    private fun createV7Database(): SupportSQLiteDatabase {
        val config = SupportSQLiteOpenHelper.Configuration.builder(RuntimeEnvironment.getApplication())
            .name(null) // in-memory
            .callback(object : SupportSQLiteOpenHelper.Callback(7) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `habits` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`dedication_level` INTEGER NOT NULL DEFAULT 2, " +
                        "`description_ladder` TEXT NOT NULL DEFAULT '[]', " +
                        "`auto_adjust_level` INTEGER NOT NULL DEFAULT 1, " +
                        "`active` INTEGER NOT NULL DEFAULT 1, " +
                        "`created_at` INTEGER NOT NULL DEFAULT 0, " +
                        "`updated_at` INTEGER NOT NULL DEFAULT 0)"
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        return FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase
    }

    @Test
    fun `MIGRATION_7_8 adds daily_limit column with default 1`() {
        val db = createV7Database()

        db.execSQL(
            "INSERT INTO habits (name, dedication_level, description_ladder, auto_adjust_level, active, created_at, updated_at) " +
            "VALUES ('meditate', 2, '[]', 1, 1, 0, 0)"
        )

        MIGRATION_7_8.migrate(db)

        val cursor = db.query("SELECT daily_limit FROM habits WHERE name = 'meditate'")
        cursor.moveToFirst()
        assertEquals(1, cursor.getInt(0))
        cursor.close()

        db.close()
    }
}
