package com.mamorukomo.kamiyama.field

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.location.LocationListener
import android.os.Build
import android.os.Bundle
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import com.mamorukomo.kamiyama.field.data.KamiyamaCenter
import com.mamorukomo.kamiyama.field.data.LatLng
import com.mamorukomo.kamiyama.field.data.ObservationStore
import com.mamorukomo.kamiyama.field.ui.KamiyamaFieldApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.osmdroid.config.Configuration
import java.util.concurrent.Executor
import kotlin.coroutines.resume

class MainActivity : ComponentActivity() {
    private lateinit var observationStore: ObservationStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().userAgentValue = packageName
        observationStore = ObservationStore(this)

        setContent {
            KamiyamaFieldApp(
                store = observationStore,
                currentLocation = ::currentLocation,
            )
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun currentLocation(): LocationFix {
        if (!hasLocationPermission()) {
            return LocationFix(KamiyamaCenter, null)
        }

        val manager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val location = withTimeoutOrNull(1800) {
            requestFreshLocation(manager)
        } ?: readLastKnownLocation(manager)

        return if (location != null) {
            LocationFix(
                point = LatLng(location.latitude, location.longitude),
                accuracy = location.accuracy.takeIf { location.hasAccuracy() },
            )
        } else {
            LocationFix(KamiyamaCenter, null)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestFreshLocation(manager: LocationManager): Location? {
        val provider = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .firstOrNull { provider -> runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false) }
            ?: return null

        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val executor = Executor { command -> runOnUiThread(command) }
                    manager.getCurrentLocation(
                        provider,
                        null,
                        executor,
                    ) { location ->
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
    }

    @SuppressLint("MissingPermission")
    private fun readLastKnownLocation(manager: LocationManager): Location? {
        return listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .mapNotNull { provider ->
                runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
            }
            .maxByOrNull { it.time }
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }
}

data class LocationFix(
    val point: LatLng,
    val accuracy: Float?,
)
