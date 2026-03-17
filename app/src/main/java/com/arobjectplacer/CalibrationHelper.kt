package com.arobjectplacer

import android.content.Context
import com.arobjectplacer.utils.MeasurementUtils

class CalibrationHelper(private val context: Context) {
    data class CalibrationResult(val distanceToObject: Float, val pixelsPerMeter: Float, val isValid: Boolean)

    private val cameraIntrinsics = MeasurementUtils.getCameraIntrinsics(context)

    fun calculateWithManualDistance(
        distanceMeters: Float, objectPixelWidth: Float, objectPixelHeight: Float,
        imageWidth: Int, imageHeight: Int
    ): Pair<Float, Float> {
        val intrinsics = cameraIntrinsics
        return if (intrinsics != null) Pair(
            MeasurementUtils.pixelsToMeters(objectPixelWidth, distanceMeters, intrinsics.focalLengthPixelsX),
            MeasurementUtils.pixelsToMeters(objectPixelHeight, distanceMeters, intrinsics.focalLengthPixelsY)
        ) else {
            val viewWidth = 2f * distanceMeters * Math.tan(Math.toRadians(30.0)).toFloat()
            val ppm = imageWidth / viewWidth
            Pair(objectPixelWidth / ppm, objectPixelHeight / ppm)
        }
    }
}
