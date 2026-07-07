package com.mamorukomo.kamiyama.thinklet

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.Looper
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executor
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

private data class NatureGuess(
    val label: String,
    val category: String,
    val confidence: Float?,
)

class ThinkletObservationViewModel : ViewModel() {
    private val _state = MutableStateFlow(ThinkletObservationState())
    val state: StateFlow<ThinkletObservationState> = _state.asStateFlow()

    private val imageCapture = ImageCapture.Builder()
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
        .build()

    private var isBound = false
    private var lastButtonAtMs = 0L
    private val toneGenerator = runCatching {
        ToneGenerator(AudioManager.STREAM_NOTIFICATION, TONE_VOLUME)
    }.getOrNull()

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
                Log.i(TAG, "camera_ready")
            } catch (error: Throwable) {
                _state.value = _state.value.copy(status = "カメラ初期化に失敗: ${error.message}")
                Log.e(TAG, "camera_bind_failed", error)
            }
        }
    }

    fun captureObservation(context: Context, sendAfterCapture: Boolean = true) {
        if (_state.value.isCapturing) {
            return
        }
        playTone(ToneGenerator.TONE_PROP_BEEP, TONE_SHORT_MS)
        viewModelScope.launch {
            _state.value = _state.value.copy(
                status = if (sendAfterCapture) "撮影して同期APIへ送ります..." else "撮影しています...",
                isCapturing = true,
            )
            Log.i(TAG, "capture_started sendAfterCapture=$sendAfterCapture")
            try {
                val payload = captureObservationPayload(context)
                Log.i(
                    TAG,
                    "capture_payload_ready id=${payload.id} label=${payload.label} category=${payload.category} location=${payload.latitude},${payload.longitude}"
                )
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
                Log.e(TAG, "capture_failed", error)
                playTone(ToneGenerator.TONE_PROP_NACK, TONE_LONG_MS)
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
            Log.i(TAG, "sync_post_started id=${payload.id}")
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
                    Log.i(
                        TAG,
                        "sync_post_success id=${payload.id} label=${analyzedPayload.label} category=${analyzedPayload.category} confidence=${analyzedPayload.confidence}"
                    )
                    playSuccessTone()
                    Toast.makeText(context, "AI同期しました", Toast.LENGTH_SHORT).show()
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        status = "同期送信に失敗: ${error.message}",
                        isSending = false,
                    )
                    Log.e(TAG, "sync_post_failed id=${payload.id}", error)
                    playTone(ToneGenerator.TONE_PROP_NACK, TONE_LONG_MS)
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
        Log.i(TAG, "photo_saved path=${photoFile.absolutePath} bytes=${photoFile.length()}")
        playTone(ToneGenerator.TONE_PROP_ACK, TONE_SHORT_MS)
        val guess = classifyNature(context, photoFile)
        Log.i(TAG, "mlkit_guess label=${guess.label} category=${guess.category} confidence=${guess.confidence}")
        _state.value = _state.value.copy(status = "写真OK。位置情報を取得しています...")
        val location = readBestLocation(context)
        Log.i(
            TAG,
            "location_read lat=${location?.latitude} lon=${location?.longitude} accuracy=${location?.takeIf { it.hasAccuracy() }?.accuracy}"
        )
        val observedAt = System.currentTimeMillis()
        return ThinkletObservationPayload(
            id = "thinklet-$observedAt",
            category = guess.category,
            label = guess.label,
            confidence = guess.confidence,
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

    private suspend fun classifyNature(context: Context, file: File): NatureGuess {
        val label = classifyImage(context, file)
        val category = inferCategory(label?.text)
        return if (category == "unknown") {
            NatureGuess(
                label = "未同定",
                category = "unknown",
                confidence = label?.confidence,
            )
        } else {
            NatureGuess(
                label = label?.text ?: "未同定",
                category = category,
                confidence = label?.confidence,
            )
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun readBestLocation(context: Context): Location? {
        val hasFine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) {
            Log.w(TAG, "location_permission_missing fine=$hasFine coarse=$hasCoarse")
            return null
        }

        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val lastKnown = readLastKnownLocation(manager, hasFine)
        Log.i(
            TAG,
            "location_provider_status fine=$hasFine coarse=$hasCoarse all=${manager.allProviders} enabled=${enabledProviders(manager)} lastKnown=${lastKnown.toDebugString()}"
        )
        val fresh = requestFreshLocation(context, manager, hasFine)
        return listOfNotNull(fresh, lastKnown)
            .maxByOrNull { location -> location.time }
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestFreshLocation(
        context: Context,
        manager: LocationManager,
        hasFine: Boolean,
    ): Location? {
        val providers = preferredProviders(manager, hasFine)
        if (providers.isEmpty()) {
            Log.w(TAG, "location_no_enabled_provider")
            return null
        }
        for (provider in providers) {
            val timeoutMs = if (provider == LocationManager.GPS_PROVIDER) {
                GPS_LOCATION_TIMEOUT_MS
            } else {
                NETWORK_LOCATION_TIMEOUT_MS
            }
            Log.i(TAG, "location_request_start provider=$provider timeoutMs=$timeoutMs")
            val location = withTimeoutOrNull(timeoutMs) {
                requestSingleProviderLocation(context, manager, provider)
            }
            if (location != null) {
                Log.i(TAG, "location_request_success provider=$provider ${location.toDebugString()}")
                return location
            }
            Log.w(TAG, "location_request_timeout_or_null provider=$provider")
        }
        return null
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestSingleProviderLocation(
        context: Context,
        manager: LocationManager,
        provider: String,
    ): Location? = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val executor = Executor { command -> ContextCompat.getMainExecutor(context).execute(command) }
                manager.getCurrentLocation(provider, null, executor) { location ->
                    if (continuation.isActive) {
                        continuation.resume(location)
                    }
                }
            } else {
                val listener = LocationListener { location ->
                    if (continuation.isActive) {
                        continuation.resume(location)
                    }
                }
                manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                continuation.invokeOnCancellation { manager.removeUpdates(listener) }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun readLastKnownLocation(manager: LocationManager, hasFine: Boolean): Location? {
        return preferredProviders(manager, hasFine)
            .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull { location -> location.time }
    }

    private fun preferredProviders(manager: LocationManager, hasFine: Boolean): List<String> {
        val candidates = if (hasFine) {
            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        } else {
            listOf(LocationManager.NETWORK_PROVIDER)
        }
        return candidates.filter { provider ->
            runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false)
        }
    }

    private fun enabledProviders(manager: LocationManager): List<String> {
        return manager.allProviders.filter { provider ->
            runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false)
        }
    }

    private fun Location?.toDebugString(): String {
        if (this == null) {
            return "null"
        }
        val ageMs = System.currentTimeMillis() - time
        return "provider=$provider lat=$latitude lon=$longitude accuracy=${takeIf { it.hasAccuracy() }?.accuracy} ageMs=$ageMs"
    }

    private fun inferCategory(label: String?): String {
        val text = label.orEmpty().lowercase(Locale.US)
        val insectWords = listOf("insect", "butterfly", "bee", "wasp", "beetle", "bug", "dragonfly")
        val plantWords = listOf("plant", "flower", "tree", "leaf", "grass", "fern", "herb")
        return when {
            insectWords.any { word -> text.contains(word) } -> "insect"
            plantWords.any { word -> text.contains(word) } -> "plant"
            else -> "unknown"
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

    private fun playSuccessTone() {
        viewModelScope.launch {
            playTone(ToneGenerator.TONE_PROP_ACK, TONE_SHORT_MS)
            delay(140)
            playTone(ToneGenerator.TONE_PROP_ACK, TONE_SHORT_MS)
        }
    }

    private fun playTone(toneType: Int, durationMs: Int) {
        runCatching {
            toneGenerator?.startTone(toneType, durationMs)
        }.onFailure { error ->
            Log.w(TAG, "音フィードバックに失敗: ${error.message}")
        }
    }

    override fun onCleared() {
        toneGenerator?.release()
        super.onCleared()
    }

    private companion object {
        const val TAG = "KamiyamaThinklet"
        const val BUTTON_COOLDOWN_MS = 1500L
        const val GPS_LOCATION_TIMEOUT_MS = 20000L
        const val NETWORK_LOCATION_TIMEOUT_MS = 8000L
        const val TONE_VOLUME = 85
        const val TONE_SHORT_MS = 120
        const val TONE_LONG_MS = 260
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
    val category = analysis?.optString("category")?.takeIf { it == "plant" || it == "insect" || it == "unknown" }
        ?: observation?.optString("category")?.takeIf { it == "plant" || it == "insect" || it == "unknown" }
    val confidence = when {
        analysis?.has("confidence") == true -> analysis.optDouble("confidence").toFloat()
        observation?.has("confidence") == true -> observation.optDouble("confidence").toFloat()
        else -> null
    }
    return ServerObservationResult(label, category, confidence)
}
