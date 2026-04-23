package net.interstellarai.unreminder.data.db

import androidx.room.TypeConverter
import net.interstellarai.unreminder.domain.model.TriggerStatus
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
    fun fromStringList(value: List<String>?): String? =
        value?.let { org.json.JSONArray(it).toString() }

    @TypeConverter
    fun toStringList(value: String?): List<String>? =
        value?.let { json ->
            try {
                val arr = org.json.JSONArray(json)
                (0 until arr.length()).map { arr.getString(it) }
            } catch (e: org.json.JSONException) {
                android.util.Log.w("Converters", "toStringList: malformed JSON, falling back to empty list: $json", e)
                List(6) { "" }
            }
        }
}
