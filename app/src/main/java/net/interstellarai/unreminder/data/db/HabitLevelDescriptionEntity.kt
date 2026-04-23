package net.interstellarai.unreminder.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "habit_level_descriptions",
    primaryKeys = ["habit_id", "level"],
    indices = [Index("habit_id")],
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habit_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class HabitLevelDescriptionEntity(
    @ColumnInfo(name = "habit_id") val habitId: Long,
    val level: Int,
    val description: String
)
