package com.alexsiri7.unreminder.data.db

import androidx.room.TypeConverter
import com.alexsiri7.unreminder.domain.model.LocationTag
import com.alexsiri7.unreminder.domain.model.TriggerStatus
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
    fun fromLocationTag(value: LocationTag?): String? = value?.name

    @TypeConverter
    fun toLocationTag(value: String?): LocationTag? = value?.let { LocationTag.valueOf(it) }

    @TypeConverter
    fun fromTriggerStatus(value: TriggerStatus?): String? = value?.name

    @TypeConverter
    fun toTriggerStatus(value: String?): TriggerStatus? = value?.let { TriggerStatus.valueOf(it) }
}
