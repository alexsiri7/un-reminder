package net.interstellarai.unreminder.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import net.interstellarai.unreminder.domain.model.ActivityMode
import java.time.Instant

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "dedication_level")
    val dedicationLevel: Int = 2,
    @ColumnInfo(name = "description_ladder")
    val descriptionLadder: List<String> = List(6) { "" },
    @ColumnInfo(name = "auto_adjust_level")
    val autoAdjustLevel: Boolean = true,
    @ColumnInfo(name = "daily_limit")
    val dailyLimit: Int = 1,
    @ColumnInfo(name = "cooldown_minutes", defaultValue = "180")
    val cooldownMinutes: Int = 180,
    /** Activity modes the habit can be done in; empty means any mode. */
    @ColumnInfo(name = "supported_modes", defaultValue = "0")
    val supportedModes: Set<ActivityMode> = emptySet(),
    val active: Boolean = true,
    @ColumnInfo(name = "created_at")
    val createdAt: Instant = Instant.now(),
    @ColumnInfo(name = "updated_at")
    val updatedAt: Instant = Instant.now()
)
