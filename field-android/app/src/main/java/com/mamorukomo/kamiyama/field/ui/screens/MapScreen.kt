package com.mamorukomo.kamiyama.field.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.mamorukomo.kamiyama.field.LocationStatus
import com.mamorukomo.kamiyama.field.data.KamiyamaCenter
import com.mamorukomo.kamiyama.field.data.LatLng
import com.mamorukomo.kamiyama.field.data.Observation
import com.mamorukomo.kamiyama.field.data.SpeciesCandidates
import com.mamorukomo.kamiyama.field.data.SpeciesCandidate
import com.mamorukomo.kamiyama.field.data.SpeciesCategory
import com.mamorukomo.kamiyama.field.ui.CandidateImage
import com.mamorukomo.kamiyama.field.ui.FieldForest
import com.mamorukomo.kamiyama.field.ui.FieldGreen
import com.mamorukomo.kamiyama.field.ui.FieldGreenSoft
import com.mamorukomo.kamiyama.field.ui.FieldInk
import com.mamorukomo.kamiyama.field.ui.FieldPanel
import com.mamorukomo.kamiyama.field.ui.FieldPanelAlt
import com.mamorukomo.kamiyama.field.ui.FieldSky
import com.mamorukomo.kamiyama.field.ui.FieldTextMuted
import com.mamorukomo.kamiyama.field.ui.ObservationImage
import com.mamorukomo.kamiyama.field.ui.RarityPill
import com.mamorukomo.kamiyama.field.ui.accentColor
import com.mamorukomo.kamiyama.field.ui.formatDate
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import kotlin.math.roundToInt

private val SheetPeekHeight = 96.dp
private const val InitialZoom = 13.2

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MapScreen(
    padding: PaddingValues,
    observations: List<Observation>,
    currentPoint: LatLng,
    locationStatus: LocationStatus,
    selectedObservation: Observation?,
    onObservationSelected: (Observation) -> Unit,
) {
    var activeFilter by remember { mutableStateOf(MapCategoryFilter.All) }
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var visibleBounds by remember { mutableStateOf<MapBounds?>(null) }
    var focusedItem by remember { mutableStateOf<MapFocus?>(null) }
    var actionFeedback by remember { mutableStateOf<String?>(null) }
    val sheetState = rememberBottomSheetScaffoldState()
    val scope = rememberCoroutineScope()
    val filteredObservations = observations.filter(activeFilter::matches)
    val visibleObservations = filteredObservations.filter { observation ->
        visibleBounds?.contains(observation.latitude, observation.longitude) ?: true
    }
    val filteredCandidates = SpeciesCandidates.filter { activeFilter.matches(it.category) }

    fun focusMapAt(point: LatLng) {
        val map = mapView ?: return
        map.controller.animateTo(GeoPoint(point.latitude, point.longitude))
        scope.launch {
            delay(320)
            map.controller.scrollBy(0, (map.height * 0.28f).roundToInt())
        }
    }

    fun selectObservation(observation: Observation) {
        focusedItem = MapFocus.ObservationItem(observation)
        onObservationSelected(observation)
        focusMapAt(LatLng(observation.latitude, observation.longitude))
        scope.launch { sheetState.bottomSheetState.expand() }
    }

    fun selectCandidate(candidate: SpeciesCandidate, point: LatLng) {
        focusedItem = MapFocus.CandidateItem(candidate, point)
        focusMapAt(point)
        scope.launch { sheetState.bottomSheetState.expand() }
    }

    LaunchedEffect(selectedObservation?.id) {
        selectedObservation?.let { focusedItem = MapFocus.ObservationItem(it) }
    }

    LaunchedEffect(actionFeedback) {
        if (actionFeedback != null) {
            delay(1400)
            actionFeedback = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FieldPanelAlt)
            .padding(padding),
    ) {
        MapHeader(observationCount = filteredObservations.size)
        BottomSheetScaffold(
            scaffoldState = sheetState,
            modifier = Modifier.fillMaxSize(),
            sheetPeekHeight = SheetPeekHeight,
            sheetContainerColor = FieldPanel,
            sheetContentColor = FieldInk,
            sheetShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            sheetShadowElevation = 6.dp,
            sheetDragHandle = null,
            sheetContent = {
                DiscoverySheet(
                    observations = visibleObservations,
                    selectedObservation = selectedObservation,
                    focusedItem = focusedItem,
                    expanded = sheetState.bottomSheetState.currentValue == SheetValue.Expanded,
                    onHeaderClick = {
                        scope.launch {
                            if (sheetState.bottomSheetState.currentValue == SheetValue.Expanded) {
                                sheetState.bottomSheetState.partialExpand()
                            } else {
                                sheetState.bottomSheetState.expand()
                            }
                        }
                    },
                    onObservationClick = ::selectObservation,
                    onClearFocus = { focusedItem = null },
                )
            },
        ) { contentPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            ) {
                FieldMap(
                    modifier = Modifier.fillMaxSize(),
                    observations = filteredObservations,
                    candidates = filteredCandidates,
                    focusedItem = focusedItem,
                    currentPoint = currentPoint,
                    showCurrentLocation = locationStatus == LocationStatus.Available,
                    onMapReady = { mapView = it },
                    onVisibleBoundsChanged = { visibleBounds = it },
                    onObservationSelected = ::selectObservation,
                    onCandidateSelected = ::selectCandidate,
                )
                CategoryFilters(
                    selected = activeFilter,
                    onSelected = {
                        activeFilter = it
                        focusedItem = null
                        scope.launch { sheetState.bottomSheetState.partialExpand() }
                    },
                    modifier = Modifier.align(Alignment.TopStart),
                )
                LocationStatusPill(
                    status = locationStatus,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 16.dp, top = 66.dp),
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = SheetPeekHeight + 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    MapActionButton(
                        icon = Icons.Rounded.MyLocation,
                        label = "現在地へ戻る",
                        enabled = locationStatus == LocationStatus.Available,
                        onClick = {
                            mapView?.controller?.animateTo(GeoPoint(currentPoint.latitude, currentPoint.longitude))
                            mapView?.controller?.setZoom(15.0)
                            actionFeedback = "現在地へ移動しました"
                        },
                    )
                    MapActionButton(
                        icon = Icons.Rounded.CenterFocusStrong,
                        label = "地図を初期位置へ戻す",
                        onClick = {
                            mapView?.controller?.animateTo(GeoPoint(KamiyamaCenter.latitude, KamiyamaCenter.longitude))
                            mapView?.controller?.setZoom(InitialZoom)
                            actionFeedback = "神山の中心へ戻りました"
                        },
                    )
                }
                MapActionFeedback(
                    message = actionFeedback,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 76.dp, bottom = SheetPeekHeight + 28.dp),
                )
            }
        }
    }
}

@Composable
private fun MapHeader(observationCount: Int) {
    Surface(color = FieldPanel, shadowElevation = 1.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "発見マップ",
                modifier = Modifier.weight(1f),
                color = FieldForest,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
            )
            Surface(color = FieldGreenSoft, shape = CircleShape) {
                Text(
                    text = "${observationCount}件",
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                    color = FieldForest,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun CategoryFilters(
    selected: MapCategoryFilter,
    onSelected: (MapCategoryFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MapCategoryFilter.entries.forEach { filter ->
            val isSelected = selected == filter
            Surface(
                onClick = { onSelected(filter) },
                color = if (isSelected) FieldForest else FieldPanel,
                contentColor = if (isSelected) Color.White else FieldInk,
                border = if (isSelected) null else BorderStroke(1.dp, Color(0xFFDDE4DF)),
                shape = CircleShape,
                shadowElevation = if (isSelected) 2.dp else 1.dp,
            ) {
                Text(
                    text = filter.label,
                    modifier = Modifier.padding(horizontal = 15.dp, vertical = 9.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun LocationStatusPill(status: LocationStatus, modifier: Modifier = Modifier) {
    val message = when (status) {
        LocationStatus.Loading -> "現在地を確認中"
        LocationStatus.PermissionDenied -> "位置情報が許可されていません"
        LocationStatus.Unavailable -> "現在地を取得できません"
        LocationStatus.Available -> null
    } ?: return
    Surface(
        modifier = modifier,
        color = FieldPanel,
        contentColor = FieldTextMuted,
        shape = CircleShape,
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Rounded.LocationOff, contentDescription = null, modifier = Modifier.size(17.dp))
            Text(message, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MapActionButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(48.dp),
        shape = CircleShape,
        color = FieldPanel,
        contentColor = if (enabled) FieldForest else FieldTextMuted.copy(alpha = 0.45f),
        shadowElevation = 4.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(23.dp))
        }
    }
}

@Composable
private fun MapActionFeedback(message: String?, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = message != null,
        modifier = modifier,
        enter = fadeIn() + slideInHorizontally(initialOffsetX = { it / 3 }),
        exit = fadeOut() + slideOutHorizontally(targetOffsetX = { it / 3 }),
    ) {
        Surface(
            color = FieldForest,
            contentColor = Color.White,
            shape = CircleShape,
            shadowElevation = 4.dp,
        ) {
            Text(
                text = message.orEmpty(),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun DiscoverySheet(
    observations: List<Observation>,
    selectedObservation: Observation?,
    focusedItem: MapFocus?,
    expanded: Boolean,
    onHeaderClick: () -> Unit,
    onObservationClick: (Observation) -> Unit,
    onClearFocus: () -> Unit,
) {
    val focusTint = focusedItem?.category?.accentColor()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SheetPeekHeight, max = 520.dp),
    ) {
        Surface(
            onClick = onHeaderClick,
            color = focusTint?.copy(alpha = 0.10f) ?: FieldPanel,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 9.dp)
                        .width(38.dp)
                        .height(4.dp)
                        .background(Color(0xFFC8D0CB), CircleShape),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (focusedItem != null) {
                        Surface(color = focusTint ?: FieldGreen, shape = CircleShape) {
                            Icon(
                                Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.padding(8.dp).size(19.dp),
                                tint = Color.White,
                            )
                        }
                    }
                    Column(
                        modifier = Modifier.padding(start = if (focusedItem != null) 10.dp else 0.dp).weight(1f),
                    ) {
                        Text(
                            text = focusedItem?.headline ?: "この範囲の発見",
                            color = FieldInk,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        Text(
                            text = focusedItem?.name ?: "${observations.size}件",
                            color = FieldTextMuted,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (focusedItem != null) {
                        Surface(
                            onClick = onClearFocus,
                            modifier = Modifier.size(44.dp),
                            color = Color.White.copy(alpha = 0.76f),
                            contentColor = FieldTextMuted,
                            shape = CircleShape,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Close, contentDescription = "選択を閉じる")
                            }
                        }
                    } else {
                        Icon(
                            imageVector = if (expanded) Icons.Rounded.ExpandMore else Icons.Rounded.ExpandLess,
                            contentDescription = if (expanded) "発見一覧を閉じる" else "発見一覧を開く",
                            tint = FieldGreen,
                        )
                    }
                }
            }
        }
        when (focusedItem) {
            is MapFocus.ObservationItem -> ObservationFocusCard(focusedItem.observation)
            is MapFocus.CandidateItem -> CandidateFocusCard(focusedItem.candidate)
            null -> {
                if (observations.isEmpty()) {
                    EmptyDiscoveryState()
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(observations, key = { it.id }) { observation ->
                            ObservationMapCard(
                                observation = observation,
                                selected = observation.id == selectedObservation?.id,
                                onClick = { onObservationClick(observation) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ObservationFocusCard(observation: Observation) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ObservationImage(observation.photoUri, Modifier.size(116.dp))
            Column(
                modifier = Modifier.padding(start = 14.dp).weight(1f),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CategoryBadge(observation.category)
                    RarityPill(observation.rarity)
                }
                Text(
                    observation.customName,
                    color = FieldInk,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    formatDate(observation.observedAtMillis),
                    color = FieldTextMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Surface(
            color = observation.category.accentColor().copy(alpha = 0.09f),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(13.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text("見つけた場所", color = FieldInk, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold)
                Text(
                    observation.environment.ifBlank { "撮影場所の記録なし" },
                    color = FieldTextMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    observation.aiConfidence?.let { "AI判定の信頼度 ${(it * 100).roundToInt()}%" }
                        ?: "AI判定の信頼度は記録されていません",
                    color = observation.category.accentColor(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun CandidateFocusCard(candidate: SpeciesCandidate) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CandidateImage(
                candidateId = candidate.id,
                modifier = Modifier.size(116.dp),
                category = candidate.category,
            )
            Column(
                modifier = Modifier.padding(start = 14.dp).weight(1f),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CategoryBadge(candidate.category)
                    RarityPill(candidate.rarity)
                }
                Text(
                    candidate.commonName,
                    color = FieldInk,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    candidate.scientificName,
                    color = FieldTextMuted,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Surface(
            color = candidate.category.accentColor().copy(alpha = 0.09f),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(13.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("探すヒント", color = FieldInk, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold)
                Text(candidate.hint, color = FieldTextMuted, style = MaterialTheme.typography.bodySmall)
                Text(
                    "観察根拠: GBIF / 神山周辺",
                    color = candidate.category.accentColor(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun CategoryBadge(category: SpeciesCategory) {
    Surface(
        color = category.accentColor().copy(alpha = 0.14f),
        contentColor = category.accentColor(),
        shape = CircleShape,
    ) {
        Text(
            category.label,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}

@Composable
private fun EmptyDiscoveryState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(color = FieldGreenSoft, shape = CircleShape) {
            Icon(
                imageVector = Icons.Rounded.LocationOn,
                contentDescription = null,
                modifier = Modifier.padding(11.dp).size(26.dp),
                tint = FieldGreen,
            )
        }
        Text("まだ発見がありません", color = FieldInk, fontWeight = FontWeight.ExtraBold)
        Text(
            text = "THINKLETで生き物を撮影すると、撮影した場所に発見ピンが表示されます。",
            color = FieldTextMuted,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun ObservationMapCard(
    observation: Observation,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = FieldPanel,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) FieldGreen else Color(0xFFE2E7E3),
        ),
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ObservationImage(observation.photoUri, Modifier.size(72.dp))
            Column(
                modifier = Modifier.padding(start = 11.dp).weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    observation.customName,
                    color = FieldInk,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    formatDate(observation.observedAtMillis),
                    color = FieldTextMuted,
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(
                    observation.environment.ifBlank { "撮影場所の記録なし" },
                    color = FieldTextMuted,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        observation.category.label,
                        color = observation.category.accentColor(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        observation.aiConfidence?.let { "AI ${(it * 100).roundToInt()}%" } ?: "AI --",
                        color = FieldTextMuted,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = "発見を表示", tint = FieldTextMuted)
        }
    }
}

@Composable
private fun FieldMap(
    modifier: Modifier,
    observations: List<Observation>,
    candidates: List<SpeciesCandidate>,
    focusedItem: MapFocus?,
    currentPoint: LatLng,
    showCurrentLocation: Boolean,
    onMapReady: (MapView) -> Unit,
    onVisibleBoundsChanged: (MapBounds) -> Unit,
    onObservationSelected: (Observation) -> Unit,
    onCandidateSelected: (SpeciesCandidate, LatLng) -> Unit,
) {
    val currentBoundsCallback by rememberUpdatedState(onVisibleBoundsChanged)
    var createdMap by remember { mutableStateOf<MapView?>(null) }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            MapView(context).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                minZoomLevel = 8.0
                maxZoomLevel = 19.0
                controller.setZoom(InitialZoom)
                controller.setCenter(GeoPoint(KamiyamaCenter.latitude, KamiyamaCenter.longitude))
                addMapListener(object : MapListener {
                    override fun onScroll(event: ScrollEvent?): Boolean {
                        currentBoundsCallback(boundingBox.toMapBounds())
                        return false
                    }

                    override fun onZoom(event: ZoomEvent?): Boolean {
                        currentBoundsCallback(boundingBox.toMapBounds())
                        return false
                    }
                })
                post { currentBoundsCallback(boundingBox.toMapBounds()) }
                createdMap = this
                onMapReady(this)
            }
        },
        update = { map ->
            map.overlays.clear()

            candidates.forEach { candidate ->
                candidate.knownLocations.forEach { location ->
                    map.overlays.add(
                        Marker(map).apply {
                            position = GeoPoint(location.latitude, location.longitude)
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            icon = map.context.categoryPinIcon(
                                category = candidate.category,
                                selected = (focusedItem as? MapFocus.CandidateItem)?.candidate?.id == candidate.id,
                            )
                            title = candidate.commonName
                            snippet = "GBIF記録 / ${candidate.rarity.label}"
                            setOnMarkerClickListener { _, _ ->
                                onCandidateSelected(candidate, location)
                                true
                            }
                        },
                    )
                }
            }

            observations.forEach { observation ->
                map.overlays.add(
                    Marker(map).apply {
                        position = GeoPoint(observation.latitude, observation.longitude)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        icon = map.context.categoryPinIcon(
                            category = observation.category,
                            selected = (focusedItem as? MapFocus.ObservationItem)?.observation?.id == observation.id,
                        )
                        title = observation.customName
                        snippet = "${observation.rarity.label} / ${observation.environment}"
                        setOnMarkerClickListener { _, _ ->
                            onObservationSelected(observation)
                            true
                        }
                    },
                )
            }

            if (showCurrentLocation) {
                map.overlays.add(
                    Marker(map).apply {
                        position = GeoPoint(currentPoint.latitude, currentPoint.longitude)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        icon = map.context.currentLocationIcon()
                        title = "現在地"
                    },
                )
            }
            map.invalidate()
        },
    )
    DisposableEffect(createdMap) {
        createdMap?.onResume()
        onDispose { createdMap?.onPause() }
    }
}

private sealed interface MapFocus {
    val category: SpeciesCategory
    val headline: String
    val name: String

    data class ObservationItem(val observation: Observation) : MapFocus {
        override val category: SpeciesCategory = observation.category
        override val headline: String = "発見した！"
        override val name: String = observation.customName
    }

    data class CandidateItem(
        val candidate: SpeciesCandidate,
        val point: LatLng,
    ) : MapFocus {
        override val category: SpeciesCategory = candidate.category
        override val headline: String = "この近くにいるかも"
        override val name: String = candidate.commonName
    }
}

private enum class MapCategoryFilter(
    val label: String,
    val category: SpeciesCategory?,
) {
    All("すべて", null),
    Plant("植物", SpeciesCategory.Plant),
    Insect("昆虫", SpeciesCategory.Insect),
    Bird("鳥", SpeciesCategory.Bird),
    Mushroom("きのこ", SpeciesCategory.Mushroom),
    ;

    fun matches(category: SpeciesCategory): Boolean = this.category == null || this.category == category
    fun matches(observation: Observation): Boolean = matches(observation.category)
}

private data class MapBounds(
    val north: Double,
    val east: Double,
    val south: Double,
    val west: Double,
) {
    fun contains(latitude: Double, longitude: Double): Boolean {
        return latitude in south..north && longitude in west..east
    }
}

private fun BoundingBox.toMapBounds(): MapBounds {
    return MapBounds(latNorth, lonEast, latSouth, lonWest)
}

private fun android.content.Context.categoryPinIcon(
    category: SpeciesCategory,
    selected: Boolean,
): BitmapDrawable {
    val inset = if (selected) 6f else 0f
    val bitmap = Bitmap.createBitmap(
        if (selected) 84 else 72,
        if (selected) 104 else 92,
        Bitmap.Config.ARGB_8888,
    )
    val canvas = Canvas(bitmap)
    canvas.translate(inset, inset)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    if (selected) {
        paint.color = android.graphics.Color.WHITE
        paint.setShadowLayer(5f, 0f, 2f, android.graphics.Color.argb(80, 0, 0, 0))
        canvas.drawCircle(36f, 32f, 33f, paint)
        paint.clearShadowLayer()
    }
    paint.color = category.accentColor().toArgbInt()
    canvas.drawCircle(36f, 32f, 28f, paint)
    canvas.drawPath(
        Path().apply {
            moveTo(20f, 53f)
            lineTo(36f, 89f)
            lineTo(52f, 53f)
            close()
        },
        paint,
    )
    paint.color = android.graphics.Color.WHITE
    paint.strokeWidth = 3.2f
    paint.strokeCap = Paint.Cap.ROUND
    when (category) {
        SpeciesCategory.Plant -> drawLeaf(canvas, paint)
        SpeciesCategory.Insect -> drawInsect(canvas, paint)
        SpeciesCategory.Bird -> drawBird(canvas, paint)
        SpeciesCategory.Mushroom -> drawMushroom(canvas, paint)
    }
    return BitmapDrawable(resources, bitmap)
}

private fun drawLeaf(canvas: Canvas, paint: Paint) {
    canvas.save()
    canvas.rotate(-35f, 36f, 32f)
    paint.style = Paint.Style.FILL
    canvas.drawOval(RectF(27f, 18f, 45f, 47f), paint)
    paint.style = Paint.Style.STROKE
    paint.color = android.graphics.Color.argb(170, 23, 61, 43)
    canvas.drawLine(36f, 24f, 36f, 48f, paint)
    canvas.restore()
}

private fun drawInsect(canvas: Canvas, paint: Paint) {
    paint.style = Paint.Style.FILL
    canvas.drawCircle(36f, 22f, 5f, paint)
    canvas.drawOval(RectF(29f, 27f, 43f, 46f), paint)
    paint.style = Paint.Style.STROKE
    listOf(29f, 35f, 41f).forEach { y ->
        canvas.drawLine(29f, y, 23f, y - 4f, paint)
        canvas.drawLine(43f, y, 49f, y - 4f, paint)
    }
}

private fun drawBird(canvas: Canvas, paint: Paint) {
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 5f
    val wings = Path().apply {
        moveTo(21f, 34f)
        quadTo(29f, 20f, 36f, 33f)
        quadTo(44f, 20f, 51f, 34f)
    }
    canvas.drawPath(wings, paint)
    paint.style = Paint.Style.FILL
    canvas.drawCircle(37f, 38f, 5f, paint)
}

private fun drawMushroom(canvas: Canvas, paint: Paint) {
    paint.style = Paint.Style.FILL
    canvas.drawOval(RectF(21f, 20f, 51f, 36f), paint)
    canvas.drawRoundRect(RectF(31f, 32f, 41f, 49f), 4f, 4f, paint)
    paint.color = android.graphics.Color.argb(110, 23, 61, 43)
    canvas.drawCircle(30f, 28f, 2.3f, paint)
    canvas.drawCircle(40f, 25f, 2.3f, paint)
}

private fun android.content.Context.currentLocationIcon(): BitmapDrawable {
    val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = android.graphics.Color.argb(55, 58, 130, 168)
    canvas.drawCircle(32f, 32f, 29f, paint)
    paint.color = FieldSky.toArgbInt()
    canvas.drawCircle(32f, 32f, 15f, paint)
    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(32f, 32f, 6f, paint)
    return BitmapDrawable(resources, bitmap)
}

private fun Color.toArgbInt(): Int {
    return android.graphics.Color.argb(
        (alpha * 255).roundToInt(),
        (red * 255).roundToInt(),
        (green * 255).roundToInt(),
        (blue * 255).roundToInt(),
    )
}
