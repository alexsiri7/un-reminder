package net.interstellarai.unreminder.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import net.interstellarai.unreminder.domain.model.ActivityMode
import net.interstellarai.unreminder.domain.model.VariantShape
import java.time.Instant

@Entity(
    tableName = "variations",
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
    val consumedAt: Instant? = null,
    @ColumnInfo(name = "action_url")
    val actionUrl: String? = null,
    @ColumnInfo(name = "sprite_tag")
    val spriteTag: String? = null,
    /** Null only on rows generated before shapes existed; every new row carries one. */
    val shape: VariantShape?,
    /** The activities the text was written for; empty when it reads naturally in any of them. */
    @ColumnInfo(name = "modes", defaultValue = "0")
    val modes: Set<ActivityMode> = emptySet(),
    /**
     * The Worker's generation version this row was produced under. A habit's unconsumed rows
     * all share one value; a refill at another version replaces them.
     */
    @ColumnInfo(name = "generation_version", defaultValue = "0")
    val generationVersion: Int = UNVERSIONED,
) {
    companion object {
        /**
         * Stamped on rows generated before the Worker reported a version. A live Worker
         * version is never 0, so these always read as stale against any real one.
         */
        const val UNVERSIONED = 0
    }
}
