package com.arobjectplacer

import androidx.room.*
import java.io.Serializable

@Entity(tableName = "captured_objects")
data class CapturedObject(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "image_path") val imagePath: String,
    @ColumnInfo(name = "mask_path") val maskPath: String?,
    @ColumnInfo(name = "width_meters") val widthMeters: Float,
    @ColumnInfo(name = "height_meters") val heightMeters: Float,
    @ColumnInfo(name = "depth_meters") val depthMeters: Float,
    @ColumnInfo(name = "capture_timestamp") val captureTimestamp: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "thumbnail_path") val thumbnailPath: String? = null,
    @ColumnInfo(name = "texture_path") val texturePath: String? = null,
    @ColumnInfo(name = "contour_data") val contourData: String? = null
) : Serializable {
    val aspectRatio: Float
        get() = if (heightMeters > 0) widthMeters / heightMeters else 1f
}
