package net.interstellarai.unreminder.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
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
    val active: Boolean = true,
    @ColumnInfo(name = "created_at")
    val createdAt: Instant = Instant.now(),
    @ColumnInfo(name = "updated_at")
    val updatedAt: Instant = Instant.now()
)
