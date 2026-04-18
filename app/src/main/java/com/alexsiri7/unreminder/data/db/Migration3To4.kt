package com.alexsiri7.unreminder.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `pending_feedback` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `screenshot_path` TEXT,
                `description` TEXT NOT NULL,
                `queued_at` INTEGER NOT NULL
            )
        """.trimIndent())
    }
}
