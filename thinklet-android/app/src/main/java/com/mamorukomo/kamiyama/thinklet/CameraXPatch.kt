package com.mamorukomo.kamiyama.thinklet

import android.os.Build
import android.util.Log
import androidx.camera.camera2.Camera2Config
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraXConfig
import androidx.camera.lifecycle.ExperimentalCameraProviderConfiguration
import androidx.camera.lifecycle.ProcessCameraProvider

object CameraXPatch {
    private var patched = false

    @androidx.annotation.OptIn(ExperimentalCameraProviderConfiguration::class)
    fun apply() {
        if (!patched && Build.MODEL.contains("THINKLET")) {
            ProcessCameraProvider.configureInstance(
                CameraXConfig.Builder.fromConfig(Camera2Config.defaultConfig())
                    .setAvailableCamerasLimiter(CameraSelector.DEFAULT_BACK_CAMERA)
                    .setMinimumLoggingLevel(Log.WARN)
                    .build()
            )
            patched = true
        }
    }
}
