package com.alexsiri7.unreminder.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "locations")
data class LocationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val lat: Double,
    val lng: Double,
    @ColumnInfo(name = "radius_m")
    val radiusM: Float = 100f
)
