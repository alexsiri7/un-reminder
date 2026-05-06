package net.interstellarai.unreminder.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `windows` ADD COLUMN `name` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `variations` ADD COLUMN `action_url` TEXT")
    }
}
