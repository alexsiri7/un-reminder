package net.interstellarai.unreminder.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "variation",
    indices = [
        Index("habit_id"),
        Index(value = ["habit_id", "prompt_fingerprint", "text"], unique = true)
    ],
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habit_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class VariationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "habit_id")
    val habitId: Long,
    val text: String,
    @ColumnInfo(name = "prompt_fingerprint")
    val promptFingerprint: String,
    @ColumnInfo(name = "generated_at")
    val generatedAt: Instant,
    @ColumnInfo(name = "consumed_at")
    val consumedAt: Instant? = null
)
