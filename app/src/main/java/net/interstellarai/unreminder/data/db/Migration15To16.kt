package net.interstellarai.unreminder.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `variations` ADD COLUMN `generation_version` INTEGER NOT NULL DEFAULT 0")
    }
}
