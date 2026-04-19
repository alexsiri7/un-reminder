package net.interstellarai.unreminder.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import net.interstellarai.unreminder.domain.model.TriggerStatus
import java.time.Instant

@Entity(tableName = "triggers")
data class TriggerEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "window_id")
    val windowId: Long? = null,
    @ColumnInfo(name = "habit_id")
    val habitId: Long? = null,
    @ColumnInfo(name = "scheduled_at")
    val scheduledAt: Instant,
    @ColumnInfo(name = "fired_at")
    val firedAt: Instant? = null,
    val status: TriggerStatus = TriggerStatus.SCHEDULED,
    @ColumnInfo(name = "generated_prompt")
    val generatedPrompt: String? = null,
    @ColumnInfo(name = "source")
    val source: String? = null
)
