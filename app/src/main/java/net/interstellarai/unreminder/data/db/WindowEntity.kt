package net.interstellarai.unreminder.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Entity(tableName = "windows")
data class WindowEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(defaultValue = "")
    val name: String = "",
    @ColumnInfo(name = "start_time")
    val startTime: LocalTime,
    @ColumnInfo(name = "end_time")
    val endTime: LocalTime,
    @ColumnInfo(name = "days_of_week_bitmask")
    val daysOfWeekBitmask: Int,
    @ColumnInfo(name = "frequency_per_day")
    val frequencyPerDay: Int = 1,
    val active: Boolean = true
) {
    fun label(): String =
        if (name.isNotBlank()) name
        else "${startTime.format(TIME_FMT)}–${endTime.format(TIME_FMT)}"

    companion object {
        private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm")
    }
}
