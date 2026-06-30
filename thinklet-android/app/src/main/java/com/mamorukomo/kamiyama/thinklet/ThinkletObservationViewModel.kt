package com.mamorukomo.kamiyama.thinklet

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.SystemClock
import android.util.Base64
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabel
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
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
    val photoBase64: String? = null,
    val photoMimeType: String? = null,
    val aiLabel: String? = null,
    val aiConfidence: Float? = null,
) {
    fun toJson(includePhoto: Boolean = false): JSONObject = JSONObject().apply {
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
        put("aiLabel", aiLabel)
        put("aiConfidence", aiConfidence)
        if (includePhoto) {
            put("photoBase64", photoBase64)
            put("photoMimeType", photoMimeType)
        }
    }
}

data class ThinkletObservationState(
    val status: String = "カメラ準備中",
    val latestPayload: ThinkletObservationPayload? = null,
    val isCameraReady: Boolean = false,
    val isCapturing: Boolean = false,
    val isSending: Boolean = false,
)

class ThinkletObservationViewModel : ViewModel() {
    private val _state = MutableStateFlow(ThinkletObservationState())
    val state: StateFlow<ThinkletObservationState> = _state.asStateFlow()

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
                    imageCapture
                )
                isBound = true
                _state.value = _state.value.copy(
                    status = "採集カメラ準備完了。サイドボタンで撮影して同期します。",
                    isCameraReady = true
                )
            } catch (error: Throwable) {
                _state.value = _state.value.copy(status = "カメラ初期化に失敗: ${error.message}")
            }
        }
    }

    fun captureObservation(context: Context, sendAfterCapture: Boolean = true) {
        if (_state.value.isCapturing) {
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(
                status = if (sendAfterCapture) "撮影して同期APIへ送ります..." else "撮影しています...",
                isCapturing = true,
            )
            try {
                val payload = captureObservationPayload(context)
                _state.value = ThinkletObservationState(
                    status = "撮影完了: ${payload.label}",
                    latestPayload = payload,
                    isCameraReady = true,
                    isCapturing = false,
                )
                if (sendAfterCapture) {
                    sendObservationToSyncApi(context)
                }
            } catch (error: Throwable) {
                _state.value = _state.value.copy(
                    status = "撮影に失敗: ${error.message}",
                    isCapturing = false,
                )
            }
        }
    }

    fun sendObservationToSyncApi(context: Context) {
        val payload = _state.value.latestPayload
        if (payload == null) {
            Toast.makeText(context, "先に観察を撮影してください", Toast.LENGTH_SHORT).show()
            return
        }
        val endpoint = BuildConfig.SYNC_API_URL.trim().trimEnd('/')
        if (endpoint.isBlank()) {
            _state.value = _state.value.copy(status = "同期API URLが未設定です。スマホ側で見るにはAPI設定が必要です。")
            Toast.makeText(context, "同期API URLが未設定です", Toast.LENGTH_SHORT).show()
            return
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(status = "同期APIへ送信しています...", isSending = true)
            val result = runCatching { postObservation(endpoint, payload) }
            result
                .onSuccess { serverResult ->
                    val analyzedPayload = payload.copy(
                        label = serverResult.label ?: payload.label,
                        category = serverResult.category ?: payload.category,
                        confidence = serverResult.confidence ?: payload.confidence,
                        aiLabel = serverResult.label,
                        aiConfidence = serverResult.confidence,
                    )
                    _state.value = _state.value.copy(
                        latestPayload = analyzedPayload,
                        status = "AI判定して同期しました: ${analyzedPayload.label}",
                        isSending = false,
                    )
                    Toast.makeText(context, "AI同期しました", Toast.LENGTH_SHORT).show()
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        status = "同期送信に失敗: ${error.message}",
                        isSending = false,
                    )
                    Toast.makeText(context, "同期送信に失敗しました", Toast.LENGTH_SHORT).show()
                }
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
                    captureObservation(context, sendAfterCapture = true)
                }
                true
            }
            else -> false
        }
    }

    private suspend fun captureObservationPayload(context: Context): ThinkletObservationPayload {
        val photoFile = createPhotoFile(context)
        takePicture(photoFile, context)
        val label = classifyImage(context, photoFile)
        val location = readBestLocation(context)
        val observedAt = System.currentTimeMillis()
        return ThinkletObservationPayload(
            id = "thinklet-$observedAt",
            category = inferCategory(label?.text),
            label = label?.text ?: "Thinklet観察",
            confidence = label?.confidence,
            latitude = location?.latitude,
            longitude = location?.longitude,
            accuracyMeters = location?.takeIf { it.hasAccuracy() }?.accuracy,
            observedAt = observedAt,
            photoUri = Uri.fromFile(photoFile).toString(),
            photoBase64 = createAnalysisImageBase64(photoFile),
            photoMimeType = "image/jpeg",
        )
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

    private suspend fun createAnalysisImageBase64(file: File): String = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, 1280)
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, options)
            ?: throw IllegalStateException("写真を読み込めませんでした")
        val scaled = scaleDown(bitmap, 1280)
        val output = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 82, output)
        if (scaled !== bitmap) {
            scaled.recycle()
        }
        bitmap.recycle()
        Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxSide: Int): Int {
        var sample = 1
        var currentWidth = width
        var currentHeight = height
        while (currentWidth / 2 >= maxSide || currentHeight / 2 >= maxSide) {
            sample *= 2
            currentWidth /= 2
            currentHeight /= 2
        }
        return sample.coerceAtLeast(1)
    }

    private fun scaleDown(bitmap: Bitmap, maxSide: Int): Bitmap {
        val longestSide = maxOf(bitmap.width, bitmap.height)
        if (longestSide <= maxSide) {
            return bitmap
        }
        val scale = maxSide.toFloat() / longestSide
        val width = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private companion object {
        const val TAG = "KamiyamaThinklet"
        const val BUTTON_COOLDOWN_MS = 1500L
    }
}

private data class ServerObservationResult(
    val label: String?,
    val category: String?,
    val confidence: Float?,
)

private suspend fun postObservation(endpoint: String, payload: ThinkletObservationPayload): ServerObservationResult {
    return withContext(Dispatchers.IO) {
        val connection = (URL("$endpoint/observations").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10000
            readTimeout = 45000
            doOutput = true
            setRequestProperty("content-type", "application/json; charset=utf-8")
            if (BuildConfig.SYNC_WRITE_TOKEN.isNotBlank()) {
                setRequestProperty("authorization", "Bearer ${BuildConfig.SYNC_WRITE_TOKEN}")
            }
        }

        try {
            val body = payload.toJson(includePhoto = true).toString().toByteArray(Charsets.UTF_8)
            connection.outputStream.use { output -> output.write(body) }
            val status = connection.responseCode
            if (status !in 200..299) {
                val error = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw IllegalStateException("HTTP $status $error")
            }
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            parseServerObservationResult(response)
        } finally {
            connection.disconnect()
        }
    }
}

private fun parseServerObservationResult(raw: String): ServerObservationResult {
    val root = JSONObject(raw)
    val observation = root.optJSONObject("observation")
    val analysis = observation?.optJSONObject("aiAnalysis")
    val label = analysis?.optString("commonName")?.takeIf { it.isNotBlank() }
        ?: observation?.optString("label")?.takeIf { it.isNotBlank() }
    val category = analysis?.optString("category")?.takeIf { it == "plant" || it == "insect" }
        ?: observation?.optString("category")?.takeIf { it == "plant" || it == "insect" }
    val confidence = when {
        analysis?.has("confidence") == true -> analysis.optDouble("confidence").toFloat()
        observation?.has("confidence") == true -> observation.optDouble("confidence").toFloat()
        else -> null
    }
    return ServerObservationResult(label, category, confidence)
}
