package net.interstellarai.unreminder.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `habits` ADD COLUMN `supported_modes` INTEGER NOT NULL DEFAULT 0")
    }
}
