package net.interstellarai.unreminder.data.db

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class Migration6To7Test {

    private fun createV6Database(): SupportSQLiteDatabase {
        val config = SupportSQLiteOpenHelper.Configuration.builder(RuntimeEnvironment.getApplication())
            .name(null) // in-memory
            .callback(object : SupportSQLiteOpenHelper.Callback(6) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `habits` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `full_description` TEXT NOT NULL, " +
                        "`low_floor_description` TEXT NOT NULL, `active` INTEGER NOT NULL DEFAULT 1, " +
                        "`created_at` INTEGER NOT NULL DEFAULT 0, `updated_at` INTEGER NOT NULL DEFAULT 0)"
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        return FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase
    }

    @Test
    fun `MIGRATION_6_7 adds dedication_level column with default 2`() {
        val db = createV6Database()

        db.execSQL(
            "INSERT INTO habits (name, full_description, low_floor_description, active, created_at, updated_at) " +
            "VALUES ('meditate', '20 min', '3 breaths', 1, 0, 0)"
        )

        MIGRATION_6_7.migrate(db)

        val cursor = db.query("SELECT dedication_level FROM habits WHERE name = 'meditate'")
        cursor.moveToFirst()
        assertEquals(2, cursor.getInt(0))
        cursor.close()

        db.close()
    }

    @Test
    fun `MIGRATION_6_7 adds description_ladder column with empty default`() {
        val db = createV6Database()

        db.execSQL(
            "INSERT INTO habits (name, full_description, low_floor_description, active, created_at, updated_at) " +
            "VALUES ('meditate', '20 min', '3 breaths', 1, 0, 0)"
        )

        MIGRATION_6_7.migrate(db)

        val cursor = db.query("SELECT description_ladder FROM habits WHERE name = 'meditate'")
        cursor.moveToFirst()
        val ladderJson = cursor.getString(0)
        cursor.close()
        db.close()

        assertEquals("[]", ladderJson)
    }

    @Test
    fun `MIGRATION_6_7 adds auto_adjust_level column with default 1`() {
        val db = createV6Database()

        db.execSQL(
            "INSERT INTO habits (name, full_description, low_floor_description, active, created_at, updated_at) " +
            "VALUES ('meditate', '20 min', '3 breaths', 1, 0, 0)"
        )

        MIGRATION_6_7.migrate(db)

        val cursor = db.query("SELECT auto_adjust_level FROM habits WHERE name = 'meditate'")
        cursor.moveToFirst()
        assertEquals(1, cursor.getInt(0))
        cursor.close()

        db.close()
    }
}
