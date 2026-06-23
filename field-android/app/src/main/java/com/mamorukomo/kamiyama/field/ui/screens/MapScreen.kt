package com.mamorukomo.kamiyama.field.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.mamorukomo.kamiyama.field.data.KamiyamaCenter
import com.mamorukomo.kamiyama.field.data.LatLng
import com.mamorukomo.kamiyama.field.data.NatureKind
import com.mamorukomo.kamiyama.field.data.NatureZones
import com.mamorukomo.kamiyama.field.data.Observation
import com.mamorukomo.kamiyama.field.data.SpeciesCandidates
import com.mamorukomo.kamiyama.field.ui.ExpeditionPanel
import com.mamorukomo.kamiyama.field.ui.FieldGreen
import com.mamorukomo.kamiyama.field.ui.FieldInk
import com.mamorukomo.kamiyama.field.ui.FieldPanel
import com.mamorukomo.kamiyama.field.ui.FieldSky
import com.mamorukomo.kamiyama.field.ui.FieldTextMuted
import com.mamorukomo.kamiyama.field.ui.FieldViolet
import com.mamorukomo.kamiyama.field.ui.MetricTile
import com.mamorukomo.kamiyama.field.ui.RarityPill
import com.mamorukomo.kamiyama.field.ui.SectionLabel
import com.mamorukomo.kamiyama.field.ui.format5
import com.mamorukomo.kamiyama.field.ui.formatDate
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon

@Composable
internal fun MapScreen(
    padding: PaddingValues,
    observations: List<Observation>,
    currentPoint: LatLng,
    selectedObservation: Observation?,
    onObservationSelected: (Observation) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(FieldInk, Color(0xFF08211B), FieldInk),
                ),
            )
            .padding(padding),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            MapHero(
                observations = observations,
                currentPoint = currentPoint,
                selectedObservation = selectedObservation,
                onObservationSelected = onObservationSelected,
            )
        }
        item {
            BiomeStrip()
        }
    }
}

@Composable
private fun MapHero(
    observations: List<Observation>,
    currentPoint: LatLng,
    selectedObservation: Observation?,
    onObservationSelected: (Observation) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(600.dp)
            .clip(RoundedCornerShape(30.dp))
            .border(1.dp, FieldGreen.copy(alpha = 0.34f), RoundedCornerShape(30.dp)),
    ) {
        FieldMap(
            observations = observations,
            currentPoint = currentPoint,
            onObservationSelected = onObservationSelected,
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            FieldInk.copy(alpha = 0.62f),
                            Color.Transparent,
                            FieldInk.copy(alpha = 0.82f),
                        ),
                    ),
                ),
        )
        MapRadarHud(
            observations = observations,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(14.dp),
        )
        CurrentLocationChip(
            currentPoint = currentPoint,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(14.dp),
        )
        SelectedObservationPanel(
            observation = selectedObservation,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(14.dp),
        )
    }
}

@Composable
private fun FieldMap(
    observations: List<Observation>,
    currentPoint: LatLng,
    onObservationSelected: (Observation) -> Unit,
) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            MapView(context).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                controller.setZoom(13.4)
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
private fun MapRadarHud(observations: List<Observation>, modifier: Modifier = Modifier) {
    ExpeditionPanel(
        modifier = modifier.fillMaxWidth(0.62f),
        tint = FieldSky,
        contentPadding = PaddingValues(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("FIELD RADAR", FieldSky)
            Text("神山探索中", color = Color.White, fontWeight = FontWeight.Black)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricTile("発見", observations.size.toString(), Modifier.weight(1f), FieldGreen)
                MetricTile("候補", SpeciesCandidates.size.toString(), Modifier.weight(1f), FieldViolet)
            }
        }
    }
}

@Composable
private fun CurrentLocationChip(currentPoint: LatLng, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = FieldPanel.copy(alpha = 0.88f),
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, FieldGreen.copy(alpha = 0.28f)),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text("CURRENT", color = FieldGreen, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
            Text(
                "${currentPoint.latitude.format5()}, ${currentPoint.longitude.format5()}",
                color = FieldTextMuted,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun SelectedObservationPanel(observation: Observation?, modifier: Modifier = Modifier) {
    ExpeditionPanel(
        modifier = modifier.fillMaxWidth(),
        tint = observation?.rarity?.let { FieldGreen } ?: FieldSky,
        contentPadding = PaddingValues(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    SectionLabel(if (observation == null) "NEXT DISCOVERY" else "DISCOVERY LOCKED")
                    Text(
                        observation?.customName ?: "未踏の発見ピンを増やす",
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (observation != null) {
                    RarityPill(observation.rarity)
                }
            }
            Text(
                if (observation == null) {
                    "既知スポットはヒントです。実際に撮影した地点が、あなたの図鑑マップになります。"
                } else {
                    "${formatDate(observation.observedAtMillis)} / ${observation.environment}"
                },
                color = FieldTextMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun BiomeStrip() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel("KAMIYAMA BIOMES")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(NatureZones, key = { it.id }) { zone ->
                Surface(
                    color = zone.kind.zoneChipColor().copy(alpha = 0.18f),
                    shape = RoundedCornerShape(18.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        zone.kind.zoneChipColor().copy(alpha = 0.42f),
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(zone.name, color = Color.White, fontWeight = FontWeight.Black, maxLines = 1)
                        Text(
                            zone.description,
                            color = FieldTextMuted,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
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

private fun NatureKind.zoneChipColor(): Color {
    return when (this) {
        NatureKind.River -> FieldSky
        NatureKind.Forest -> FieldGreen
        NatureKind.Village -> Color(0xFFFFA066)
        NatureKind.Ridge -> FieldViolet
        NatureKind.Field -> Color(0xFFEFD85B)
    }
}
