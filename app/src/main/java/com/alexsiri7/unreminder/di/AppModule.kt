package com.alexsiri7.unreminder.di

import android.content.Context
import androidx.room.Room
import com.alexsiri7.unreminder.data.db.AppDatabase
import com.alexsiri7.unreminder.data.db.HabitDao
import com.alexsiri7.unreminder.data.db.LocationDao
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

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "unreminder.db"
        ).build()
    }

    @Provides
    fun provideHabitDao(db: AppDatabase): HabitDao = db.habitDao()

    @Provides
    fun provideWindowDao(db: AppDatabase): WindowDao = db.windowDao()

    @Provides
    fun provideTriggerDao(db: AppDatabase): TriggerDao = db.triggerDao()

    @Provides
    fun provideLocationDao(db: AppDatabase): LocationDao = db.locationDao()
}
