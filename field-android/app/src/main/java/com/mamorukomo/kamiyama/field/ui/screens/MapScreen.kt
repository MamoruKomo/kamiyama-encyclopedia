package com.mamorukomo.kamiyama.field.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.mamorukomo.kamiyama.field.data.KamiyamaCenter
import com.mamorukomo.kamiyama.field.data.LatLng
import com.mamorukomo.kamiyama.field.data.NatureKind
import com.mamorukomo.kamiyama.field.data.NatureZones
import com.mamorukomo.kamiyama.field.data.Observation
import com.mamorukomo.kamiyama.field.data.SpeciesCandidates
import com.mamorukomo.kamiyama.field.ui.RarityPill
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
                RarityPill(observation.rarity)
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
