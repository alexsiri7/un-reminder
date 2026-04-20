package net.interstellarai.unreminder.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import net.interstellarai.unreminder.data.db.AppDatabase
import net.interstellarai.unreminder.data.db.HabitDao
import net.interstellarai.unreminder.data.db.HabitLocationCrossRefDao
import net.interstellarai.unreminder.data.db.LocationDao
import net.interstellarai.unreminder.data.db.MIGRATION_1_2
import net.interstellarai.unreminder.data.db.MIGRATION_2_3
import net.interstellarai.unreminder.data.db.MIGRATION_3_4
import net.interstellarai.unreminder.data.db.MIGRATION_4_5
import net.interstellarai.unreminder.data.db.MIGRATION_5_6
import net.interstellarai.unreminder.data.db.HabitWindowCrossRefDao
import net.interstellarai.unreminder.data.db.PendingFeedbackDao
import net.interstellarai.unreminder.data.db.TriggerDao
import net.interstellarai.unreminder.data.db.VariationDao
import net.interstellarai.unreminder.data.db.WindowDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
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
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6).build()
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

    @Provides
    fun provideHabitWindowCrossRefDao(db: AppDatabase): HabitWindowCrossRefDao =
        db.habitWindowCrossRefDao()

    @Provides
    fun providePendingFeedbackDao(db: AppDatabase): PendingFeedbackDao = db.pendingFeedbackDao()

    @Provides
    fun provideVariationDao(db: AppDatabase): VariationDao = db.variationDao()

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default
}
