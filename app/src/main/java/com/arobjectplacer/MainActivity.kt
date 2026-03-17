package com.arobjectplacer

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.arobjectplacer.databinding.ActivityMainBinding
import com.arobjectplacer.utils.PermissionUtils
import com.google.ar.core.ArCoreApk

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var arCoreAvailable = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        checkARCore()
        if (!PermissionUtils.hasCameraPermission(this)) PermissionUtils.requestAllPermissions(this)

        binding.btnCapture.setOnClickListener {
            if (PermissionUtils.hasCameraPermission(this)) startActivity(Intent(this, CaptureActivity::class.java))
            else PermissionUtils.requestCameraPermission(this)
        }
        binding.btnArPlace.setOnClickListener {
            if (!arCoreAvailable) { Toast.makeText(this, "ARCore not available", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            if (PermissionUtils.hasCameraPermission(this))
                startActivity(Intent(this, GalleryActivity::class.java).putExtra("mode", "ar_placement"))
            else PermissionUtils.requestCameraPermission(this)
        }
        binding.btnGallery.setOnClickListener { startActivity(Intent(this, GalleryActivity::class.java)) }
    }

    private fun checkARCore() {
        val availability = ArCoreApk.getInstance().checkAvailability(this)
        arCoreAvailable = availability.isSupported
        if (availability.isTransient) binding.root.postDelayed({ checkARCore() }, 200)
        if (!availability.isSupported && !availability.isTransient) {
            binding.tvArStatus.text = "ARCore not available"
            binding.tvArStatus.visibility = View.VISIBLE
        }
        binding.btnArPlace.isEnabled = arCoreAvailable
        binding.btnArPlace.alpha = if (arCoreAvailable) 1f else 0.5f
    }
}
