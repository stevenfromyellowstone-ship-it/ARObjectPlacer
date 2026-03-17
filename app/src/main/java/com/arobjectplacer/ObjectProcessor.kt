package com.arobjectplacer

import android.content.Context
import android.graphics.*
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class ObjectProcessor(private val context: Context) {
    data class ProcessedObject(
        val croppedImage: Bitmap, val mask: Bitmap, val boundingBox: RectF,
        val contourPoints: List<PointF>, val objectWidthPixels: Float, val objectHeightPixels: Float
    )

    suspend fun processImage(bitmap: Bitmap): ProcessedObject? = withContext(Dispatchers.Default) {
        try {
            val detectedBox = detectObject(bitmap) ?: RectF(
                bitmap.width * 0.1f, bitmap.height * 0.1f,
                bitmap.width * 0.9f, bitmap.height * 0.9f
            )
            val mask = createSimpleMask(bitmap, detectedBox)
            val contour = findContourPoints(mask)
            val bounds = computeMaskBounds(mask)
            ProcessedObject(bitmap, mask, bounds, contour, bounds.width(), bounds.height())
        } catch (e: Exception) {
            Log.e("ObjectProcessor", "Error", e)
            null
        }
    }

    private suspend fun detectObject(bitmap: Bitmap): RectF? = suspendCancellableCoroutine { cont ->
        val options = ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
            .enableMultipleObjects(false).build()
        ObjectDetection.getClient(options).process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { objects ->
                cont.resume(objects.maxByOrNull {
                    it.boundingBox.width() * it.boundingBox.height()
                }?.boundingBox?.let { RectF(it) })
            }
            .addOnFailureListener { cont.resume(null) }
    }

    private fun createSimpleMask(bitmap: Bitmap, roi: RectF): Bitmap {
        val mask = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(mask)
        val paint = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL }
        canvas.drawRect(roi, paint)
        return mask
    }

    private fun findContourPoints(mask: Bitmap): List<PointF> {
        val points = mutableListOf<PointF>()
        val pixels = IntArray(mask.width * mask.height)
        mask.getPixels(pixels, 0, mask.width, 0, 0, mask.width, mask.height)
        for (y in 1 until mask.height - 1) {
            for (x in 1 until mask.width - 1) {
                if (Color.alpha(pixels[y * mask.width + x]) > 128) {
                    val neighbors = listOf(
                        pixels[(y-1)*mask.width+x], pixels[(y+1)*mask.width+x],
                        pixels[y*mask.width+(x-1)], pixels[y*mask.width+(x+1)]
                    )
                    if (neighbors.any { Color.alpha(it) < 128 })
                        points.add(PointF(x.toFloat(), y.toFloat()))
                }
            }
        }
        return if (points.size > 200) points.filterIndexed { i, _ -> i % (points.size/200) == 0 } else points
    }

    private fun computeMaskBounds(mask: Bitmap): RectF {
        val pixels = IntArray(mask.width * mask.height)
        mask.getPixels(pixels, 0, mask.width, 0, 0, mask.width, mask.height)
        var minX = mask.width; var minY = mask.height; var maxX = 0; var maxY = 0
        for (y in 0 until mask.height) for (x in 0 until mask.width)
            if (Color.alpha(pixels[y * mask.width + x]) > 128) {
                minX = minOf(minX, x); minY = minOf(minY, y)
                maxX = maxOf(maxX, x); maxY = maxOf(maxY, y)
            }
        return RectF(minX.toFloat(), minY.toFloat(), maxX.toFloat(), maxY.toFloat())
    }
}
