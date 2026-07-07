package com.mamorukomo.kamiyama.field.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.mamorukomo.kamiyama.field.data.KamiyamaCenter
import com.mamorukomo.kamiyama.field.data.LatLng
import com.mamorukomo.kamiyama.field.data.Observation
import com.mamorukomo.kamiyama.field.ui.AdventureCard
import com.mamorukomo.kamiyama.field.ui.EmptyState
import com.mamorukomo.kamiyama.field.ui.FieldCoral
import com.mamorukomo.kamiyama.field.ui.FieldGreen
import com.mamorukomo.kamiyama.field.ui.FieldLeaf
import com.mamorukomo.kamiyama.field.ui.FieldPanelAlt
import com.mamorukomo.kamiyama.field.ui.FieldSky
import com.mamorukomo.kamiyama.field.ui.FieldTextMuted
import com.mamorukomo.kamiyama.field.ui.MetricTile
import com.mamorukomo.kamiyama.field.ui.RarityPill
import com.mamorukomo.kamiyama.field.ui.SectionTitle
import com.mamorukomo.kamiyama.field.ui.StatusPill
import com.mamorukomo.kamiyama.field.ui.accentColor
import com.mamorukomo.kamiyama.field.ui.format5
import com.mamorukomo.kamiyama.field.ui.formatDate
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

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
            .background(FieldPanelAlt)
            .padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            AdventureCard(tint = FieldLeaf) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionTitle("たんけんマップ", "THINKLETで撮った場所が、発見ピンになります。")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetricTile("発見", observations.size.toString(), Modifier.weight(1f), FieldGreen)
                        MetricTile("いま", currentPoint.shortLabel(), Modifier.weight(1f), FieldSky)
                    }
                }
            }
        }
        item {
            FieldMap(
                observations = observations,
                currentPoint = currentPoint,
                onObservationSelected = onObservationSelected,
            )
        }
        selectedObservation?.let { observation ->
            item {
                SelectedObservationCard(observation)
            }
        }
        if (observations.isEmpty()) {
            item {
                EmptyState(
                    title = "まだピンがありません",
                    body = "THINKLETで撮ってから、うけとる画面で発見を取り込みます。",
                    tint = FieldCoral,
                )
            }
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
            .height(390.dp)
            .clip(RoundedCornerShape(8.dp)),
        factory = { context ->
            MapView(context).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                controller.setZoom(13.5)
                controller.setCenter(GeoPoint(KamiyamaCenter.latitude, KamiyamaCenter.longitude))
            }
        },
        update = { map ->
            map.overlays.clear()

            observations.forEach { observation ->
                map.overlays.add(
                    Marker(map).apply {
                        position = GeoPoint(observation.latitude, observation.longitude)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        title = observation.customName
                        snippet = "${observation.rarity.label} / ${observation.environment}"
                        setOnMarkerClickListener { clicked, _ ->
                            clicked.showInfoWindow()
                            onObservationSelected(observation)
                            true
                        }
                    },
                )
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
private fun SelectedObservationCard(observation: Observation) {
    AdventureCard(tint = observation.category.accentColor(), filled = false) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill(observation.category.label, observation.category.accentColor())
                RarityPill(observation.rarity)
            }
            Text(observation.customName, color = Color(0xFF111816), fontWeight = FontWeight.ExtraBold)
            Text(formatDate(observation.observedAtMillis), color = FieldTextMuted)
            Text(
                "${observation.environment} / ${observation.latitude.format5()}, ${observation.longitude.format5()}",
                color = FieldTextMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun LatLng.shortLabel(): String = "${latitude.format5()}, ${longitude.format5()}"
