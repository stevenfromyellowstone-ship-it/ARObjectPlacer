package com.arobjectplacer

import android.graphics.*
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.arobjectplacer.databinding.ActivityCaptureBinding
import com.arobjectplacer.databinding.DialogDimensionsBinding
import com.arobjectplacer.utils.MeasurementUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CaptureActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCaptureBinding
    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null
    private lateinit var objectProcessor: ObjectProcessor
    private lateinit var objectStore: ObjectStore
    private var capturedBitmap: Bitmap? = null
    private var processedResult: ObjectProcessor.ProcessedObject? = null
    private var isProcessing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCaptureBinding.inflate(layoutInflater)
        setContentView(binding.root)
        objectProcessor = ObjectProcessor(this)
        objectStore = (application as ARObjectPlacerApp).objectStore
        cameraExecutor = Executors.newSingleThreadExecutor()
        startCamera()
        binding.btnCapture.setOnClickListener { if (!isProcessing) takePhoto() }
        binding.btnRetake.setOnClickListener { showCameraMode() }
        binding.btnSave.setOnClickListener { showDimensionsDialog() }
        binding.btnBack.setOnClickListener { finish() }
        showCameraMode()
    }

    private fun showCameraMode() {
        binding.previewView.visibility = View.VISIBLE
        binding.capturedImageView.visibility = View.GONE
        binding.btnCapture.visibility = View.VISIBLE
        binding.btnRetake.visibility = View.GONE
        binding.btnSave.visibility = View.GONE
        binding.progressBar.visibility = View.GONE
        binding.tvInstructions.text = "Position the object in the frame"
        capturedBitmap?.recycle(); capturedBitmap = null; processedResult = null
    }

    private fun showPreviewMode() {
        binding.previewView.visibility = View.GONE
        binding.capturedImageView.visibility = View.VISIBLE
        binding.btnCapture.visibility = View.GONE
        binding.btnRetake.visibility = View.VISIBLE
        binding.btnSave.visibility = View.VISIBLE
        binding.progressBar.visibility = View.GONE
        binding.tvInstructions.text = "Object detected! Tap Save to set dimensions."
    }

    private fun startCamera() {
        ProcessCameraProvider.getInstance(this).addListener({
            val cp = ProcessCameraProvider.getInstance(this).get()
            val preview = Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3).build()
                .also { it.setSurfaceProvider(binding.previewView.surfaceProvider) }
            imageCapture = ImageCapture.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build()
            try { cp.unbindAll(); cp.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture) }
            catch (e: Exception) { Log.e("Capture", "Bind failed", e) }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val ic = imageCapture ?: return
        isProcessing = true; binding.progressBar.visibility = View.VISIBLE; binding.btnCapture.isEnabled = false
        val file = File(cacheDir, "temp_${System.currentTimeMillis()}.jpg")
        ic.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(),
            ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    lifecycleScope.launch {
                        val bmp = withContext(Dispatchers.IO) {
                            com.arobjectplacer.utils.BitmapUtils.correctOrientation(
                                BitmapFactory.decodeFile(file.absolutePath), file.absolutePath)
                        }
                        capturedBitmap = bmp
                        processedResult = objectProcessor.processImage(bmp)
                        binding.capturedImageView.setImageBitmap(bmp)
                        showPreviewMode(); isProcessing = false
                    }
                }
                override fun onError(e: ImageCaptureException) {
                    isProcessing = false; binding.progressBar.visibility = View.GONE; binding.btnCapture.isEnabled = true
                    Toast.makeText(this@CaptureActivity, "Capture failed", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun showDimensionsDialog() {
        val db = DialogDimensionsBinding.inflate(layoutInflater)
        db.spinnerReferenceType.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            arrayOf("Manual Input", "Credit Card", "A4 Paper"))
        AlertDialog.Builder(this).setTitle("Object Dimensions").setView(db.root)
            .setPositiveButton("Save") { _, _ ->
                val name = db.etName.text.toString().ifEmpty { "Object ${System.currentTimeMillis() % 10000}" }
                val w = db.etWidth.text.toString().toFloatOrNull() ?: 30f
                val h = db.etHeight.text.toString().toFloatOrNull() ?: 30f
                val d = db.etDepth.text.toString().toFloatOrNull() ?: 5f
                saveObject(name, w / 100f, h / 100f, d / 100f)
            }.setNegativeButton("Cancel", null).show()
    }

    private fun saveObject(name: String, w: Float, h: Float, d: Float) {
        val bmp = capturedBitmap ?: return
        lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            objectStore.saveObject(name, bmp, processedResult?.mask, w, h, d, processedResult?.contourPoints)
            Toast.makeText(this@CaptureActivity, "Saved: $name", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onDestroy() { super.onDestroy(); cameraExecutor.shutdown(); capturedBitmap?.recycle() }
}
