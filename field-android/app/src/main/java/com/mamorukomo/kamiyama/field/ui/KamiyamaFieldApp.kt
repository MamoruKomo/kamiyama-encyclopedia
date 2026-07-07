package com.mamorukomo.kamiyama.field.ui

import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.mamorukomo.kamiyama.field.BuildConfig
import com.mamorukomo.kamiyama.field.LocationFix
import com.mamorukomo.kamiyama.field.data.LatLng
import com.mamorukomo.kamiyama.field.data.Observation
import com.mamorukomo.kamiyama.field.data.ObservationStore
import com.mamorukomo.kamiyama.field.data.SyncClient
import com.mamorukomo.kamiyama.field.ui.screens.CaptureScreen
import com.mamorukomo.kamiyama.field.ui.screens.DexScreen
import com.mamorukomo.kamiyama.field.ui.screens.MapScreen
import kotlinx.coroutines.launch

internal enum class AppTab(val label: String, val token: String, val title: String) {
    Capture("記録", "REC", "写真と位置を記録"),
    Map("地図", "MAP", "記録地点"),
    Dex("図鑑", "DEX", "発見一覧"),
}

@Composable
fun KamiyamaFieldApp(
    store: ObservationStore,
    createPhotoUri: () -> Uri,
    currentLocation: suspend () -> LocationFix,
) {
    val colorScheme = lightColorScheme(
        primary = FieldGreen,
        secondary = FieldYellow,
        tertiary = FieldCoral,
        background = FieldPanelAlt,
        surface = FieldPanel,
        surfaceVariant = FieldPanelAlt,
        onPrimary = Color.White,
        onSecondary = Color.White,
        onTertiary = Color.White,
        onBackground = FieldInk,
        onSurface = FieldInk,
        onSurfaceVariant = FieldTextMuted,
    )

    MaterialTheme(colorScheme = colorScheme) {
        var observations by remember { mutableStateOf(store.loadObservations()) }
        var activeTab by remember { mutableStateOf(AppTab.Capture) }
        var selectedObservation by remember { mutableStateOf<Observation?>(null) }
        var currentPoint by remember { mutableStateOf(LatLng(33.9676, 134.3503)) }
        var isSyncing by remember { mutableStateOf(false) }
        val syncClient = remember { SyncClient(BuildConfig.SYNC_API_URL.trim()) }
        val scope = rememberCoroutineScope()
        var message by remember {
            mutableStateOf("写真を撮ると、撮影時刻と位置情報を一緒に保存します。")
        }

        LaunchedEffect(Unit) {
            runCatching { currentLocation() }
                .onSuccess { fix -> currentPoint = fix.point }
        }

        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Scaffold(
                topBar = {
                    FieldHeader(
                        observations = observations,
                        activeTab = activeTab,
                        message = message,
                    )
                },
                bottomBar = {
                    FieldBottomBar(activeTab = activeTab, onTabSelected = { activeTab = it })
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
                        isSyncing = isSyncing,
                        syncEnabled = syncClient.isConfigured,
                        onSync = {
                            if (!syncClient.isConfigured) {
                                message = "同期API URLが未設定です。field-android/local.properties に kamiyamaSyncApiUrl を設定してください。"
                            } else {
                                scope.launch {
                                    isSyncing = true
                                    message = "同期APIからAI判定済み観察を取り込んでいます..."
                                    runCatching {
                                        val synced = syncClient.pullObservations()
                                        val knownIds = observations.map { it.id }.toSet()
                                        val fresh = synced.filter { it.id !in knownIds }
                                        synced.forEach(store::saveObservation)
                                        observations = store.loadObservations()
                                        fresh.firstOrNull()?.let { observation ->
                                            selectedObservation = observation
                                            currentPoint = LatLng(observation.latitude, observation.longitude)
                                            activeTab = AppTab.Map
                                        }
                                        message = if (fresh.isEmpty()) {
                                            "同期APIに新しい観察はありません。"
                                        } else {
                                            "${fresh.size}件のAI判定済み観察を図鑑に登録しました。"
                                        }
                                    }.onFailure { error ->
                                        message = "同期取り込みに失敗: ${error.message}"
                                    }
                                    isSyncing = false
                                }
                            }
                        },
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
