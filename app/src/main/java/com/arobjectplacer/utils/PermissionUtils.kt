package com.arobjectplacer.utils

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

object PermissionUtils {
    const val CAMERA_PERMISSION_CODE = 1001

    fun hasCameraPermission(activity: Activity): Boolean =
        ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    fun requestCameraPermission(activity: Activity) =
        ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_CODE)

    fun requestAllPermissions(activity: Activity) {
        val perms = mutableListOf(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            perms.add(Manifest.permission.READ_MEDIA_IMAGES)
        ActivityCompat.requestPermissions(activity, perms.toTypedArray(), CAMERA_PERMISSION_CODE)
    }
}
