package com.mamorukomo.kamiyama.field.ui

import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.mamorukomo.kamiyama.field.LocationFix
import com.mamorukomo.kamiyama.field.data.LatLng
import com.mamorukomo.kamiyama.field.data.Observation
import com.mamorukomo.kamiyama.field.data.ObservationStore
import com.mamorukomo.kamiyama.field.ui.screens.CaptureScreen
import com.mamorukomo.kamiyama.field.ui.screens.DexScreen
import com.mamorukomo.kamiyama.field.ui.screens.MapScreen

private enum class AppTab(val label: String, val token: String) {
    Map("地図", "MAP"),
    Capture("撮影", "SCAN"),
    Dex("図鑑", "DEX"),
}

@Composable
fun KamiyamaFieldApp(
    store: ObservationStore,
    createPhotoUri: () -> Uri,
    currentLocation: () -> LocationFix,
) {
    val colorScheme = darkColorScheme(
        primary = Color(0xFF68DCB0),
        secondary = Color(0xFFFFD24A),
        tertiary = Color(0xFFE85D4F),
        background = Color(0xFF07131D),
        surface = Color(0xFF102233),
        surfaceVariant = Color(0xFF173149),
        onPrimary = Color(0xFF07131D),
        onSecondary = Color(0xFF102233),
        onTertiary = Color.White,
        onBackground = Color(0xFFEAF4F7),
        onSurface = Color(0xFFEAF4F7),
        onSurfaceVariant = Color(0xFFB9CEDA),
    )

    MaterialTheme(colorScheme = colorScheme) {
        var observations by remember { mutableStateOf(store.loadObservations()) }
        var activeTab by remember { mutableStateOf(AppTab.Map) }
        var selectedObservation by remember { mutableStateOf<Observation?>(null) }
        var currentPoint by remember { mutableStateOf(currentLocation().point) }
        var message by remember {
            mutableStateOf("Android Studio版です。撮影するとGPSと時刻つきで図鑑に保存します。")
        }

        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Scaffold(
                topBar = {
                    FieldHeader(
                        observations = observations,
                        message = message,
                    )
                },
                bottomBar = {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        AppTab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = activeTab == tab,
                                onClick = { activeTab = tab },
                                icon = { Text(tab.token, fontWeight = FontWeight.Black) },
                                label = { Text(tab.label) },
                            )
                        }
                    }
                },
                containerColor = MaterialTheme.colorScheme.background,
            ) { padding ->
                when (activeTab) {
                    AppTab.Map -> MapScreen(
                        padding = padding,
                        observations = observations,
                        currentPoint = currentPoint,
                        selectedObservation = selectedObservation,
                        onObservationSelected = {
                            selectedObservation = it
                            message = "${it.customName} を選択しました。"
                        },
                    )

                    AppTab.Capture -> CaptureScreen(
                        padding = padding,
                        currentLocation = {
                            currentLocation().also { fix ->
                                currentPoint = fix.point
                            }
                        },
                        createPhotoUri = createPhotoUri,
                        onSaved = { observation ->
                            store.saveObservation(observation)
                            observations = store.loadObservations()
                            selectedObservation = observation
                            currentPoint = LatLng(observation.latitude, observation.longitude)
                            activeTab = AppTab.Map
                            message = "${observation.customName} を図鑑に登録しました。"
                        },
                        onMessage = { message = it },
                    )

                    AppTab.Dex -> DexScreen(
                        padding = padding,
                        observations = observations,
                        onDelete = { observation ->
                            store.deleteObservation(observation.id)
                            observations = store.loadObservations()
                            if (selectedObservation?.id == observation.id) {
                                selectedObservation = null
                            }
                            message = "${observation.customName} を削除しました。"
                        },
                    )
                }
            }
        }
    }
}
