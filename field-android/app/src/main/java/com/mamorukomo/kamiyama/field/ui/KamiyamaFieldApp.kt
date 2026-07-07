package com.mamorukomo.kamiyama.field.ui

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
import com.mamorukomo.kamiyama.field.ui.screens.DexScreen
import com.mamorukomo.kamiyama.field.ui.screens.MapScreen
import com.mamorukomo.kamiyama.field.ui.screens.SyncScreen
import kotlinx.coroutines.launch

internal enum class AppTab(val label: String, val token: String, val title: String) {
    Sync("うけとる", "AI", "THINKLETから発見をうけとる"),
    Map("マップ", "MAP", "たんけんマップ"),
    Dex("ずかん", "DEX", "発見ずかん"),
}

@Composable
fun KamiyamaFieldApp(
    store: ObservationStore,
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
        var activeTab by remember { mutableStateOf(AppTab.Sync) }
        var selectedObservation by remember { mutableStateOf<Observation?>(null) }
        var currentPoint by remember { mutableStateOf(LatLng(33.9676, 134.3503)) }
        var isSyncing by remember { mutableStateOf(false) }
        val syncClient = remember { SyncClient(BuildConfig.SYNC_API_URL.trim()) }
        val scope = rememberCoroutineScope()
        var message by remember {
            mutableStateOf("THINKLETで撮った発見を、ここで受け取ります。")
        }

        fun importFromThinklet() {
            if (!syncClient.isConfigured) {
                message = "先生へ: field-android/local.properties に kamiyamaSyncApiUrl を設定してください。"
                return
            }
            scope.launch {
                isSyncing = true
                message = "THINKLETの発見をさがしています..."
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
                        "新しい発見はまだありません。THINKLETで撮ってみよう。"
                    } else {
                        "${fresh.size}こ発見! AIのよそうを図鑑に入れました。"
                    }
                }.onFailure { error ->
                    message = "受け取りに失敗しました: ${error.message}"
                }
                isSyncing = false
            }
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
                    AppTab.Sync -> SyncScreen(
                        padding = padding,
                        observations = observations,
                        isSyncing = isSyncing,
                        syncEnabled = syncClient.isConfigured,
                        onSync = ::importFromThinklet,
                    )

                    AppTab.Map -> MapScreen(
                        padding = padding,
                        observations = observations,
                        currentPoint = currentPoint,
                        selectedObservation = selectedObservation,
                        onObservationSelected = {
                            selectedObservation = it
                            message = "${it.customName} を見つけた場所です。"
                        },
                    )

                    AppTab.Dex -> DexScreen(
                        padding = padding,
                        observations = observations,
                        isSyncing = isSyncing,
                        syncEnabled = syncClient.isConfigured,
                        onSync = ::importFromThinklet,
                        onDelete = { observation ->
                            store.deleteObservation(observation.id)
                            observations = store.loadObservations()
                            if (selectedObservation?.id == observation.id) {
                                selectedObservation = null
                            }
                            message = "${observation.customName} をずかんから外しました。"
                        },
                    )
                }
            }
        }
    }
}
