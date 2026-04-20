package net.interstellarai.unreminder.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "habit_window",
    primaryKeys = ["habit_id", "window_id"],
    indices = [Index("window_id")],
    foreignKeys = [
        ForeignKey(entity = HabitEntity::class, parentColumns = ["id"],
            childColumns = ["habit_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = WindowEntity::class, parentColumns = ["id"],
            childColumns = ["window_id"], onDelete = ForeignKey.CASCADE)
    ]
)
data class HabitWindowCrossRef(
    @ColumnInfo(name = "habit_id") val habitId: Long,
    @ColumnInfo(name = "window_id") val windowId: Long
)
