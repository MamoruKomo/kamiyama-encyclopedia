package com.mamorukomo.kamiyama.thinklet

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabel
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class ThinkletObservationPayload(
    val id: String,
    val category: String,
    val label: String,
    val confidence: Float?,
    val latitude: Double?,
    val longitude: Double?,
    val accuracyMeters: Float?,
    val observedAt: Long,
    val photoUri: String?,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("source", "THINKLET")
        put("category", category)
        put("label", label)
        put("confidence", confidence)
        put("latitude", latitude)
        put("longitude", longitude)
        put("accuracyMeters", accuracyMeters)
        put("observedAt", observedAt)
        put("photoUri", photoUri)
    }
}

data class ThinkletObservationState(
    val status: String = "カメラ準備中",
    val latestPayload: ThinkletObservationPayload? = null,
    val isCameraReady: Boolean = false,
    val isCapturing: Boolean = false,
)

class ThinkletObservationViewModel : ViewModel() {
    private val _surfaceRequest = MutableStateFlow<SurfaceRequest?>(null)
    val surfaceRequest: StateFlow<SurfaceRequest?> = _surfaceRequest.asStateFlow()

    private val _state = MutableStateFlow(ThinkletObservationState())
    val state: StateFlow<ThinkletObservationState> = _state.asStateFlow()

    private val preview = Preview.Builder().build().apply {
        setSurfaceProvider { request -> _surfaceRequest.value = request }
    }
    private val imageCapture = ImageCapture.Builder()
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
        .build()

    private var isBound = false
    private var lastButtonAtMs = 0L

    private val sideButtonKeyCodes = setOf(
        KeyEvent.KEYCODE_STEM_PRIMARY,
        KeyEvent.KEYCODE_STEM_1,
        KeyEvent.KEYCODE_STEM_2,
        KeyEvent.KEYCODE_STEM_3,
        KeyEvent.KEYCODE_CAMERA,
        KeyEvent.KEYCODE_FOCUS,
        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_HEADSETHOOK,
        KeyEvent.KEYCODE_BUTTON_A,
    )

    init {
        CameraXPatch.apply()
    }

    fun bindCamera(context: Context, lifecycleOwner: LifecycleOwner) {
        if (isBound) {
            return
        }
        viewModelScope.launch {
            try {
                val provider = ProcessCameraProvider.getInstance(context).await()
                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture
                )
                isBound = true
                _state.value = _state.value.copy(
                    status = "準備完了。サイドボタンで観察を撮影できます。",
                    isCameraReady = true
                )
            } catch (error: Throwable) {
                _state.value = _state.value.copy(status = "カメラ初期化に失敗: ${error.message}")
            }
        }
    }

    fun captureObservation(context: Context) {
        if (_state.value.isCapturing) {
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(status = "撮影しています...", isCapturing = true)
            try {
                val photoFile = createPhotoFile(context)
                takePicture(photoFile, context)
                val label = classifyImage(context, photoFile)
                val location = readBestLocation(context)
                val observedAt = System.currentTimeMillis()
                val payload = ThinkletObservationPayload(
                    id = "thinklet-$observedAt",
                    category = inferCategory(label?.text),
                    label = label?.text ?: "Thinklet観察",
                    confidence = label?.confidence,
                    latitude = location?.latitude,
                    longitude = location?.longitude,
                    accuracyMeters = location?.takeIf { it.hasAccuracy() }?.accuracy,
                    observedAt = observedAt,
                    photoUri = Uri.fromFile(photoFile).toString(),
                )
                _state.value = ThinkletObservationState(
                    status = "撮影完了: ${payload.label}",
                    latestPayload = payload,
                    isCameraReady = true,
                    isCapturing = false,
                )
            } catch (error: Throwable) {
                _state.value = _state.value.copy(
                    status = "撮影に失敗: ${error.message}",
                    isCapturing = false,
                )
            }
        }
    }

    fun openWebApp(context: Context): Boolean {
        val payload = _state.value.latestPayload
        if (payload == null) {
            Toast.makeText(context, "先に観察を撮影してください", Toast.LENGTH_SHORT).show()
            return false
        }
        val url = Uri.parse(WEB_APP_URL).buildUpon()
            .appendQueryParameter("thinkletObservation", payload.toJson().toString())
            .build()
            .toString()
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        return try {
            context.startActivity(intent)
            Toast.makeText(context, "Web図鑑へ観察を送ります", Toast.LENGTH_SHORT).show()
            true
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "Webを開けるアプリが見つかりません", Toast.LENGTH_SHORT).show()
            false
        }
    }

    fun handleThinkletButton(event: KeyEvent, context: Context): Boolean {
        Log.d(TAG, "keyEvent action=${event.action} keyCode=${event.keyCode}")
        if (event.keyCode !in sideButtonKeyCodes) {
            return false
        }
        return when (event.action) {
            KeyEvent.ACTION_DOWN -> true
            KeyEvent.ACTION_UP -> {
                val now = SystemClock.elapsedRealtime()
                if (now - lastButtonAtMs > BUTTON_COOLDOWN_MS) {
                    lastButtonAtMs = now
                    captureObservation(context)
                }
                true
            }
            else -> false
        }
    }

    private fun createPhotoFile(context: Context): File {
        val outputDir = File(context.getExternalFilesDir(null), "observations")
        outputDir.mkdirs()
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        return File(outputDir, "kamiyama-$stamp.jpg")
    }

    private suspend fun takePicture(file: File, context: Context) {
        suspendCancellableCoroutine { continuation ->
            val options = ImageCapture.OutputFileOptions.Builder(file).build()
            imageCapture.takePicture(
                options,
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        continuation.resume(Unit)
                    }

                    override fun onError(exception: ImageCaptureException) {
                        continuation.resumeWithException(exception)
                    }
                }
            )
        }
    }

    private suspend fun classifyImage(context: Context, file: File): ImageLabel? {
        return suspendCancellableCoroutine { continuation ->
            val image = InputImage.fromFilePath(context, Uri.fromFile(file))
            val labeler = ImageLabeling.getClient(
                ImageLabelerOptions.Builder()
                    .setConfidenceThreshold(0.45f)
                    .build()
            )
            labeler.process(image)
                .addOnSuccessListener { labels -> continuation.resume(labels.maxByOrNull { it.confidence }) }
                .addOnFailureListener { error -> continuation.resumeWithException(error) }
        }
    }

    @SuppressLint("MissingPermission")
    private fun readBestLocation(context: Context): Location? {
        val hasFine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) {
            return null
        }

        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { provider -> manager.isProviderEnabled(provider) }

        return providers
            .mapNotNull { provider -> manager.getLastKnownLocation(provider) }
            .maxByOrNull { location -> location.time }
    }

    private fun inferCategory(label: String?): String {
        val text = label.orEmpty().lowercase(Locale.US)
        val insectWords = listOf("insect", "butterfly", "bee", "wasp", "beetle", "bug", "dragonfly")
        val plantWords = listOf("plant", "flower", "tree", "leaf", "grass", "fern", "herb")
        return when {
            insectWords.any { word -> text.contains(word) } -> "insect"
            plantWords.any { word -> text.contains(word) } -> "plant"
            else -> "plant"
        }
    }

    private companion object {
        const val TAG = "KamiyamaThinklet"
        const val BUTTON_COOLDOWN_MS = 1500L
        const val WEB_APP_URL = "https://mamorukomo.github.io/kamiyama-encyclopedia/"
    }
}
