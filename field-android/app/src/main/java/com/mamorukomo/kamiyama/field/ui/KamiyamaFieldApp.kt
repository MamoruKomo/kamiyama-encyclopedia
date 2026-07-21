package com.mamorukomo.kamiyama.field.ui

import android.content.Context
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
import androidx.compose.ui.platform.LocalContext
import com.mamorukomo.kamiyama.field.BuildConfig
import com.mamorukomo.kamiyama.field.LocationFix
import com.mamorukomo.kamiyama.field.LocationStatus
import com.mamorukomo.kamiyama.field.data.LatLng
import com.mamorukomo.kamiyama.field.data.Observation
import com.mamorukomo.kamiyama.field.data.ObservationStore
import com.mamorukomo.kamiyama.field.data.SyncClient
import com.mamorukomo.kamiyama.field.ui.screens.CandidatesScreen
import com.mamorukomo.kamiyama.field.ui.screens.DexScreen
import com.mamorukomo.kamiyama.field.ui.screens.HomeScreen
import com.mamorukomo.kamiyama.field.ui.screens.MapScreen
import kotlinx.coroutines.launch

internal enum class AppTab(val label: String, val title: String) {
    Home("ホーム", "THINKLETで見つける、神山のいきもの"),
    Dex("図鑑", "見つけた生き物"),
    Map("地図", "発見した場所"),
    Candidates("AI候補", "次に探したい生き物"),
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
        val context = LocalContext.current
        var observations by remember { mutableStateOf(store.loadObservations()) }
        var activeTab by remember { mutableStateOf(AppTab.Home) }
        var selectedObservation by remember { mutableStateOf<Observation?>(null) }
        var currentPoint by remember { mutableStateOf(LatLng(33.9676, 134.3503)) }
        var locationStatus by remember { mutableStateOf(LocationStatus.Loading) }
        var isSyncing by remember { mutableStateOf(false) }
        val syncClient = remember { SyncClient(BuildConfig.SYNC_API_URL.trim()) }
        val syncPrefs = remember(context) {
            context.getSharedPreferences(SYNC_PREFS_NAME, Context.MODE_PRIVATE)
        }
        var lastObservationSyncAt by remember {
            mutableStateOf(syncPrefs.getLong(LAST_OBSERVATION_SYNC_AT_KEY, 0L))
        }
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
                    val result = syncClient.pullObservations(lastObservationSyncAt)
                    val synced = result.observations
                    val knownIds = observations.map { it.id }.toSet()
                    val fresh = synced.filter { it.id !in knownIds }
                    fresh.forEach(store::saveObservation)
                    if (result.serverTimeMillis > lastObservationSyncAt) {
                        lastObservationSyncAt = result.serverTimeMillis
                        syncPrefs.edit()
                            .putLong(LAST_OBSERVATION_SYNC_AT_KEY, result.serverTimeMillis)
                            .apply()
                    }
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
                .onSuccess { fix ->
                    currentPoint = fix.point
                    locationStatus = fix.status
                }
                .onFailure { locationStatus = LocationStatus.Unavailable }
        }

        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Scaffold(
                topBar = {
                    if (activeTab != AppTab.Map) {
                        FieldHeader(
                            observations = observations,
                            activeTab = activeTab,
                        )
                    }
                },
                bottomBar = {
                    FieldBottomBar(activeTab = activeTab, onTabSelected = { activeTab = it })
                },
                containerColor = MaterialTheme.colorScheme.background,
            ) { padding ->
                when (activeTab) {
                    AppTab.Home -> HomeScreen(
                        padding = padding,
                        observations = observations,
                        isSyncing = isSyncing,
                        syncEnabled = syncClient.isConfigured,
                        message = message,
                        onSync = ::importFromThinklet,
                        onOpenDex = { activeTab = AppTab.Dex },
                        onOpenMap = { activeTab = AppTab.Map },
                        onOpenCandidates = { activeTab = AppTab.Candidates },
                    )

                    AppTab.Map -> MapScreen(
                        padding = padding,
                        observations = observations,
                        currentPoint = currentPoint,
                        locationStatus = locationStatus,
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

                    AppTab.Candidates -> CandidatesScreen(padding = padding)
                }
            }
        }
    }
}

private const val SYNC_PREFS_NAME = "kamiyama-sync"
private const val LAST_OBSERVATION_SYNC_AT_KEY = "last_observation_sync_at"
