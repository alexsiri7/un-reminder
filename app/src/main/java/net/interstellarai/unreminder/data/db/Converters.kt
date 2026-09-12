package net.interstellarai.unreminder.data.db

import androidx.room.TypeConverter
import net.interstellarai.unreminder.domain.model.ActivityMode
import net.interstellarai.unreminder.domain.model.TriggerStatus
import net.interstellarai.unreminder.domain.model.VariantShape
import org.json.JSONArray
import org.json.JSONException
import java.time.Instant
import java.time.LocalTime

/**
 * The bit each mode occupies in `habits.supported_modes`. Persisted, so never renumbered:
 * an exhaustive `when` rather than `1 shl ordinal` so reordering the enum cannot remap rows.
 */
internal val ActivityMode.bit: Int
    get() = when (this) {
        ActivityMode.WALKING -> 1
        ActivityMode.SITTING -> 2
        ActivityMode.TRANSPORT -> 4
    }

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
    fun toTriggerStatus(value: String?): TriggerStatus? = value?.let {
        when (it) {
            "COMPLETED_FULL", "COMPLETED_LOW_FLOOR" -> TriggerStatus.COMPLETED
            else -> TriggerStatus.valueOf(it)
        }
    }

    @TypeConverter
    fun fromVariantShape(value: VariantShape?): String? = value?.name

    /**
     * A shape this build does not know (written by a newer or older worker deploy) reads back
     * as null, the same as a pre-migration row.
     */
    @TypeConverter
    fun toVariantShape(value: String?): VariantShape? = VariantShape.entries.firstOrNull { it.name == value }

    @TypeConverter
    fun fromDescriptionLadder(list: List<String>?): String? =
        list?.let { JSONArray(it).toString() }

    @TypeConverter
    fun toDescriptionLadder(value: String?): List<String>? =
        value?.let {
            try {
                val arr = JSONArray(it)
                List(arr.length()) { i -> arr.getString(i) }
            } catch (e: JSONException) {
                List(6) { "" }
            }
        }

    @TypeConverter
    fun fromActivityModes(value: Set<ActivityMode>): Int = value.fold(0) { acc, mode -> acc or mode.bit }

    @TypeConverter
    fun toActivityModes(value: Int): Set<ActivityMode> =
        ActivityMode.entries.filter { value and it.bit != 0 }.toSet()
}
