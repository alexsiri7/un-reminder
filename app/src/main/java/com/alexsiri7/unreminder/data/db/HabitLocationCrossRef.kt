package com.alexsiri7.unreminder.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "habit_location",
    primaryKeys = ["habit_id", "location_id"],
    indices = [Index("location_id")],
    foreignKeys = [
        ForeignKey(entity = HabitEntity::class, parentColumns = ["id"],
            childColumns = ["habit_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = LocationEntity::class, parentColumns = ["id"],
            childColumns = ["location_id"], onDelete = ForeignKey.CASCADE)
    ]
)
data class HabitLocationCrossRef(
    @ColumnInfo(name = "habit_id") val habitId: Long,
    @ColumnInfo(name = "location_id") val locationId: Long
)
