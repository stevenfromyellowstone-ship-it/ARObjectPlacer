package com.arobjectplacer.utils

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.SizeF

object MeasurementUtils {
    data class CameraIntrinsics(
        val focalLengthPixelsX: Float, val focalLengthPixelsY: Float,
        val principalPointX: Float, val principalPointY: Float,
        val focalLengthMm: Float, val sensorSizeMm: SizeF
    )

    fun getCameraIntrinsics(context: Context): CameraIntrinsics? {
        return try {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val chars = cm.getCameraCharacteristics(cm.cameraIdList[0])
            val fl = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)!![0]
            val ss = chars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)!!
            val pa = chars.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)!!
            CameraIntrinsics(
                fl * pa.width / ss.width, fl * pa.height / ss.height,
                pa.width / 2f, pa.height / 2f, fl, SizeF(ss.width, ss.height)
            )
        } catch (e: Exception) { null }
    }

    fun pixelsToMeters(sizePixels: Float, distanceMeters: Float, focalLengthPixels: Float): Float =
        (sizePixels * distanceMeters) / focalLengthPixels

    fun calculateDistance(referenceRealSize: Float, referencePixelSize: Float, focalLengthPixels: Float): Float =
        (referenceRealSize * focalLengthPixels) / referencePixelSize

    val CREDIT_CARD_WIDTH_M = 0.0856f
    val CREDIT_CARD_HEIGHT_M = 0.05398f
}
