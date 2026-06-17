package com.mamorukomo.kamiyama.field

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.mamorukomo.kamiyama.field.data.KamiyamaCenter
import com.mamorukomo.kamiyama.field.data.LatLng
import com.mamorukomo.kamiyama.field.data.ObservationStore
import com.mamorukomo.kamiyama.field.ui.KamiyamaFieldApp
import org.osmdroid.config.Configuration
import java.io.File

class MainActivity : ComponentActivity() {
    private lateinit var observationStore: ObservationStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().userAgentValue = packageName
        observationStore = ObservationStore(this)

        setContent {
            KamiyamaFieldApp(
                store = observationStore,
                createPhotoUri = ::createPhotoUri,
                currentLocation = ::currentLocation,
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun currentLocation(): LocationFix {
        if (!hasLocationPermission()) {
            return LocationFix(KamiyamaCenter, null)
        }

        val manager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        val location = providers
            .mapNotNull { provider ->
                runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
            }
            .maxByOrNull { it.time }

        return if (location != null) {
            LocationFix(
                point = LatLng(location.latitude, location.longitude),
                accuracy = location.accuracy.takeIf { location.hasAccuracy() },
            )
        } else {
            LocationFix(KamiyamaCenter, null)
        }
    }

    private fun createPhotoUri(): Uri {
        val directory = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "observations")
            .apply { mkdirs() }
        val file = File(directory, "observation-${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            file,
        )
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
