package net.interstellarai.unreminder.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        HabitEntity::class,
        WindowEntity::class,
        TriggerEntity::class,
        LocationEntity::class,
        HabitLocationCrossRef::class,
        PendingFeedbackEntity::class
    ],
    version = 4,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun habitDao(): HabitDao
    abstract fun windowDao(): WindowDao
    abstract fun triggerDao(): TriggerDao
    abstract fun locationDao(): LocationDao
    abstract fun habitLocationCrossRefDao(): HabitLocationCrossRefDao
    abstract fun pendingFeedbackDao(): PendingFeedbackDao
}
