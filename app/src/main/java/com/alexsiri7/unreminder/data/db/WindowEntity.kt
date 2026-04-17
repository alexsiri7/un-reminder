package com.alexsiri7.unreminder.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalTime

@Entity(tableName = "windows")
data class WindowEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "start_time")
    val startTime: LocalTime,
    @ColumnInfo(name = "end_time")
    val endTime: LocalTime,
    @ColumnInfo(name = "days_of_week_bitmask")
    val daysOfWeekBitmask: Int,
    @ColumnInfo(name = "frequency_per_day")
    val frequencyPerDay: Int = 1,
    val active: Boolean = true
)
