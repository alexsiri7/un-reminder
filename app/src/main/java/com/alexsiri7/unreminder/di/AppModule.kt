package com.alexsiri7.unreminder.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.alexsiri7.unreminder.data.db.AppDatabase
import com.alexsiri7.unreminder.data.db.HabitDao
import com.alexsiri7.unreminder.data.db.LocationDao
import com.alexsiri7.unreminder.data.db.PendingFeedbackDao
import com.alexsiri7.unreminder.data.db.TriggerDao
import com.alexsiri7.unreminder.data.db.WindowDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS pending_feedback (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "screenshot_path TEXT NOT NULL, " +
                "description TEXT NOT NULL, " +
                "created_at INTEGER NOT NULL DEFAULT 0)"
            )
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "unreminder.db"
        ).addMigrations(MIGRATION_1_2).build()
    }

    @Provides
    fun provideHabitDao(db: AppDatabase): HabitDao = db.habitDao()

    @Provides
    fun provideWindowDao(db: AppDatabase): WindowDao = db.windowDao()

    @Provides
    fun provideTriggerDao(db: AppDatabase): TriggerDao = db.triggerDao()

    @Provides
    fun provideLocationDao(db: AppDatabase): LocationDao = db.locationDao()

    @Provides
    fun providePendingFeedbackDao(db: AppDatabase): PendingFeedbackDao = db.pendingFeedbackDao()
}
