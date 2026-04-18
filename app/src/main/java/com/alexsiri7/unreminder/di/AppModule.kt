package com.alexsiri7.unreminder.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.alexsiri7.unreminder.data.db.AppDatabase
import com.alexsiri7.unreminder.data.db.HabitDao
import com.alexsiri7.unreminder.data.db.HabitLocationCrossRefDao
import com.alexsiri7.unreminder.data.db.LocationDao
import com.alexsiri7.unreminder.data.db.MIGRATION_1_2
import com.alexsiri7.unreminder.data.db.MIGRATION_2_3
import com.alexsiri7.unreminder.data.db.TriggerDao
import com.alexsiri7.unreminder.data.db.WindowDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.dataStore

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "unreminder.db"
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
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
    fun provideHabitLocationCrossRefDao(db: AppDatabase): HabitLocationCrossRefDao =
        db.habitLocationCrossRefDao()
}
