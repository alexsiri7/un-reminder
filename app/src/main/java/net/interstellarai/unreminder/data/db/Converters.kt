package net.interstellarai.unreminder.data.db

import androidx.room.TypeConverter
import net.interstellarai.unreminder.domain.model.TriggerStatus
import org.json.JSONArray
import java.time.Instant
import java.time.LocalTime

class Converters {
    @TypeConverter
    fun fromInstant(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun toInstant(value: Long?): Instant? = value?.let { Instant.ofEpochMilli(it) }

    @TypeConverter
    fun fromLocalTime(value: LocalTime?): Int? = value?.toSecondOfDay()

    @TypeConverter
    fun toLocalTime(value: Int?): LocalTime? = value?.let { LocalTime.ofSecondOfDay(it.toLong()) }

    @TypeConverter
    fun fromTriggerStatus(value: TriggerStatus?): String? = value?.name

    @TypeConverter
    fun toTriggerStatus(value: String?): TriggerStatus? = value?.let { TriggerStatus.valueOf(it) }

    @TypeConverter
    fun fromDescriptionLadder(list: List<String>?): String? =
        list?.let { JSONArray(it).toString() }

    @TypeConverter
    fun toDescriptionLadder(value: String?): List<String>? =
        value?.let {
            val arr = JSONArray(it)
            List(arr.length()) { i -> arr.getString(i) }
        }
}
