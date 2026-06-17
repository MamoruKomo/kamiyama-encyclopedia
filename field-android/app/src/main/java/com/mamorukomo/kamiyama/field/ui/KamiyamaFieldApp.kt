package com.mamorukomo.kamiyama.field.ui

import android.Manifest
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import com.mamorukomo.kamiyama.field.LocationFix
import com.mamorukomo.kamiyama.field.data.KamiyamaCenter
import com.mamorukomo.kamiyama.field.data.LatLng
import com.mamorukomo.kamiyama.field.data.NatureKind
import com.mamorukomo.kamiyama.field.data.NatureZones
import com.mamorukomo.kamiyama.field.data.Observation
import com.mamorukomo.kamiyama.field.data.ObservationStore
import com.mamorukomo.kamiyama.field.data.Rarity
import com.mamorukomo.kamiyama.field.data.SpeciesCandidate
import com.mamorukomo.kamiyama.field.data.SpeciesCandidates
import com.mamorukomo.kamiyama.field.data.SpeciesCategory
import com.mamorukomo.kamiyama.field.data.Suggestion
import com.mamorukomo.kamiyama.field.data.describeEnvironment
import com.mamorukomo.kamiyama.field.data.inferRarity
import com.mamorukomo.kamiyama.field.data.suggestCandidates
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

private enum class AppTab(val label: String, val token: String) {
    Map("地図", "MAP"),
    Capture("撮影", "SCAN"),
    Dex("図鑑", "DEX"),
}

private data class CapturedPhoto(
    val uri: Uri,
    val location: LatLng,
    val accuracy: Float?,
    val observedAtMillis: Long,
)

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

@Composable
private fun FieldHeader(
    observations: List<Observation>,
    message: String,
) {
    val discovered = observations.mapNotNull { it.candidateId }.toSet().size
    val rare = observations.count { it.rarity == Rarity.Rare || it.rarity == Rarity.Special }
    val progress = discovered / SpeciesCandidates.size.toFloat()
    val rank = when {
        observations.size >= 12 -> "MASTER"
        observations.size >= 6 -> "ACE"
        else -> "ROOKIE"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "KAMIYAMA FIELD GUIDE",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = "神山生物図鑑",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = "歩いて、撮って、発見ピンを増やす",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            DiscoveryBadge(discovered, SpeciesCandidates.size)
        }
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape),
            color = MaterialTheme.colorScheme.secondary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HudStat("RANK", rank, Modifier.weight(1f))
            HudStat("観察", observations.size.toString(), Modifier.weight(1f))
            HudStat("レア", rare.toString(), Modifier.weight(1f), accent = true)
        }
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun DiscoveryBadge(discovered: Int, total: Int) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "$discovered/$total",
                color = MaterialTheme.colorScheme.onSecondary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = "DISCOVERED",
                color = MaterialTheme.colorScheme.onSecondary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

@Composable
private fun HudStat(label: String, value: String, modifier: Modifier = Modifier, accent: Boolean = false) {
    ElevatedCard(
        modifier = modifier,
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (accent) Color(0xFF2E263F) else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = value,
                color = if (accent) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

@Composable
private fun MapScreen(
    padding: PaddingValues,
    observations: List<Observation>,
    currentPoint: LatLng,
    selectedObservation: Observation?,
    onObservationSelected: (Observation) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            FieldMap(
                observations = observations,
                currentPoint = currentPoint,
                onObservationSelected = onObservationSelected,
            )
        }
        item {
            SelectedObservationCard(selectedObservation)
        }
    }
}

@Composable
private fun FieldMap(
    observations: List<Observation>,
    currentPoint: LatLng,
    onObservationSelected: (Observation) -> Unit,
) {
    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .height(430.dp)
            .clip(MaterialTheme.shapes.medium),
        factory = { context ->
            MapView(context).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                controller.setZoom(13.0)
                controller.setCenter(GeoPoint(KamiyamaCenter.latitude, KamiyamaCenter.longitude))
            }
        },
        update = { map ->
            map.overlays.clear()
            NatureZones.forEach { zone ->
                val polygon = Polygon().apply {
                    points = zone.polygon.map { GeoPoint(it.latitude, it.longitude) }
                    fillColor = zone.kind.zoneFillColor()
                    strokeColor = zone.kind.zoneStrokeColor()
                    strokeWidth = 3f
                    title = zone.name
                    snippet = zone.description
                }
                map.overlays.add(polygon)
            }

            SpeciesCandidates.forEach { candidate ->
                candidate.knownLocations.forEach { location ->
                    val marker = Marker(map).apply {
                        position = GeoPoint(location.latitude, location.longitude)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        title = candidate.commonName
                        snippet = "${candidate.scientificName}\nGBIF由来の周辺記録"
                    }
                    map.overlays.add(marker)
                }
            }

            observations.forEach { observation ->
                val marker = Marker(map).apply {
                    position = GeoPoint(observation.latitude, observation.longitude)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    title = observation.customName
                    snippet = "${observation.rarity.label}\n${observation.environment}"
                    setOnMarkerClickListener { clicked, _ ->
                        clicked.showInfoWindow()
                        onObservationSelected(observation)
                        true
                    }
                }
                map.overlays.add(marker)
            }

            map.overlays.add(
                Marker(map).apply {
                    position = GeoPoint(currentPoint.latitude, currentPoint.longitude)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    title = "現在地"
                },
            )
            map.invalidate()
        },
    )
}

@Composable
private fun SelectedObservationCard(observation: Observation?) {
    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFF102233))) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = if (observation == null) "FIELD RADAR" else "DISCOVERY LOCKED",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = observation?.customName ?: "探索マップ",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = if (observation == null) {
                    "既知スポットを起点に歩き、自分の写真で発見ピンを増やします。"
                } else {
                    "${formatDate(observation.observedAtMillis)} / ${observation.environment}"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (observation != null) {
                AssistChip(
                    onClick = {},
                    label = { Text(observation.rarity.label, fontWeight = FontWeight.Black) },
                )
            }
        }
    }
}

@Composable
private fun CaptureScreen(
    padding: PaddingValues,
    currentLocation: () -> LocationFix,
    createPhotoUri: () -> Uri,
    onSaved: (Observation) -> Unit,
    onMessage: (String) -> Unit,
) {
    var category by remember { mutableStateOf(SpeciesCategory.Plant) }
    var capturedPhoto by remember { mutableStateOf<CapturedPhoto?>(null) }
    var selectedCandidate by remember { mutableStateOf<SpeciesCandidate?>(null) }
    var customName by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var pendingPhoto by remember { mutableStateOf<CapturedPhoto?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            capturedPhoto = pendingPhoto
            selectedCandidate = null
            customName = ""
            note = ""
            onMessage("候補を選ぶか、名前を入力して登録できます。")
        } else {
            onMessage("撮影がキャンセルされました。")
        }
    }
    val permissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants[Manifest.permission.CAMERA] == true) {
            val fix = currentLocation()
            val uri = createPhotoUri()
            pendingPhoto = CapturedPhoto(
                uri = uri,
                location = fix.point,
                accuracy = fix.accuracy,
                observedAtMillis = System.currentTimeMillis(),
            )
            cameraLauncher.launch(uri)
        } else {
            onMessage("カメラ権限がないため撮影できません。")
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            ScannerHero()
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SpeciesCategory.entries.forEach { item ->
                    FilterChip(
                        selected = category == item,
                        onClick = {
                            category = item
                            selectedCandidate = null
                        },
                        label = { Text("${item.label} / ${item.chip}") },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        item {
            Button(
                onClick = {
                    permissionsLauncher.launch(
                        arrayOf(
                            Manifest.permission.CAMERA,
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                        ),
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
            ) {
                Text("撮影して地点を記録", fontWeight = FontWeight.Black)
            }
        }

        capturedPhoto?.let { photo ->
            item {
                ObservationImage(
                    uri = photo.uri.toString(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(MaterialTheme.shapes.medium),
                )
            }
            item {
                CaptureMeta(photo)
            }
            item {
                Text("候補", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            }
            items(suggestCandidates(category, photo.location, photo.observedAtMillis)) { suggestion ->
                SuggestionCard(
                    suggestion = suggestion,
                    selected = selectedCandidate?.id == suggestion.candidate.id,
                    onClick = {
                        selectedCandidate = suggestion.candidate
                        customName = suggestion.candidate.commonName
                    },
                )
            }
            item {
                OutlinedTextField(
                    value = customName,
                    onValueChange = { customName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("生き物名") },
                    singleLine = true,
                )
            }
            item {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("メモ") },
                    minLines = 3,
                )
            }
            item {
                Button(
                    onClick = {
                        val observedAt = photo.observedAtMillis
                        val candidate = selectedCandidate
                        val name = customName.trim()
                            .ifBlank { candidate?.commonName ?: "未同定" }
                        val observation = Observation(
                            id = "android-$observedAt",
                            photoUri = photo.uri.toString(),
                            category = category,
                            candidateId = candidate?.id,
                            customName = name,
                            note = note.trim(),
                            latitude = photo.location.latitude,
                            longitude = photo.location.longitude,
                            accuracy = photo.accuracy,
                            observedAtMillis = observedAt,
                            environment = describeEnvironment(photo.location),
                            rarity = inferRarity(candidate, photo.location, observedAt),
                        )
                        capturedPhoto = null
                        selectedCandidate = null
                        customName = ""
                        note = ""
                        onSaved(observation)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("図鑑に登録", fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
private fun ScannerHero() {
    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "FIELD SCANNER",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
            )
            Text("いま見つけた一瞬を登録", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text(
                text = "写真、GPS、時刻をまとめてSQLiteに保存します。候補は場所と季節から近い順に表示します。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CaptureMeta(photo: CapturedPhoto) {
    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                "CAPTURE DATA",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
            )
            Text("${formatDate(photo.observedAtMillis)} / ${describeEnvironment(photo.location)}")
            Text("${photo.location.latitude.format5()}, ${photo.location.longitude.format5()}")
        }
    }
}

@Composable
private fun SuggestionCard(
    suggestion: Suggestion,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val candidate = suggestion.candidate
    ElevatedCard(
        onClick = onClick,
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (selected) Color(0xFF2E263F) else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(candidate.commonName, fontWeight = FontWeight.Black)
                    Text(
                        candidate.scientificName,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                RarityPill(candidate.rarity)
            }
            Text(
                "${suggestion.distanceMeters.roundToInt()}m / ${candidate.hint}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun DexScreen(
    padding: PaddingValues,
    observations: List<Observation>,
    onDelete: (Observation) -> Unit,
) {
    val discovered = observations.mapNotNull { it.candidateId }.toSet().size
    val rare = observations.count { it.rarity == Rarity.Rare || it.rarity == Rarity.Special }
    val progress = discovered / SpeciesCandidates.size.toFloat()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "BIO DEX",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                    )
                    Text("発見コレクション", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    Text(
                        "写真と位置で神山町の自分だけの分布図を育てます。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape),
                        color = MaterialTheme.colorScheme.secondary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HudStat("観察", observations.size.toString(), Modifier.weight(1f))
                HudStat("候補種", "$discovered/${SpeciesCandidates.size}", Modifier.weight(1f))
                HudStat("レア以上", rare.toString(), Modifier.weight(1f), accent = true)
            }
        }
        if (observations.isEmpty()) {
            item {
                ElevatedCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("最初の発見を待っています", fontWeight = FontWeight.Black)
                        Text(
                            "撮影画面で植物や虫を記録すると、写真・日時・場所・レア度がここに並びます。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        items(observations, key = { it.id }) { observation ->
            ObservationCard(observation = observation, onDelete = { onDelete(observation) })
        }
    }
}

@Composable
private fun ObservationCard(observation: Observation, onDelete: () -> Unit) {
    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ObservationImage(
                uri = observation.photoUri,
                modifier = Modifier
                    .size(108.dp)
                    .clip(MaterialTheme.shapes.medium),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    observation.category.chip,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                )
                Text(observation.customName, fontWeight = FontWeight.Black, maxLines = 1)
                Text(
                    SpeciesCandidates.find { it.id == observation.candidateId }?.scientificName ?: "未同定",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                )
                RarityPill(observation.rarity)
                Text(formatDate(observation.observedAtMillis), style = MaterialTheme.typography.bodySmall)
                Text(
                    "${observation.environment} / ${observation.latitude.format5()}, ${observation.longitude.format5()}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                )
                if (observation.note.isNotBlank()) {
                    Text(observation.note, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                }
                OutlinedButton(onClick = onDelete) {
                    Text("削除")
                }
            }
        }
    }
}

@Composable
private fun RarityPill(rarity: Rarity) {
    AssistChip(
        onClick = {},
        label = { Text(rarity.label, fontWeight = FontWeight.Black) },
    )
}

@Composable
private fun ObservationImage(uri: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap = remember(uri) {
        runCatching {
            context.contentResolver.openInputStream(uri.toUri())?.use { stream ->
                BitmapFactory.decodeStream(stream)?.asImageBitmap()
            }
        }.getOrNull()
    }
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text("NO IMAGE", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun NatureKind.zoneFillColor(): Int {
    return when (this) {
        NatureKind.River -> 0x334AA3DF
        NatureKind.Forest -> 0x332E7D54
        NatureKind.Village -> 0x33D08745
        NatureKind.Ridge -> 0x337D6CC8
        NatureKind.Field -> 0x33D0A833
    }
}

private fun NatureKind.zoneStrokeColor(): Int {
    return when (this) {
        NatureKind.River -> 0xCC4AA3DF.toInt()
        NatureKind.Forest -> 0xCC2E7D54.toInt()
        NatureKind.Village -> 0xCCD08745.toInt()
        NatureKind.Ridge -> 0xCC7D6CC8.toInt()
        NatureKind.Field -> 0xCCD0A833.toInt()
    }
}

private fun formatDate(millis: Long): String {
    return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(millis))
}

private fun Double.format5(): String {
    return "%.5f".format(this)
}
