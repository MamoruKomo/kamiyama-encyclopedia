package com.mamorukomo.kamiyama.field.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
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
import androidx.core.graphics.toColorInt
import com.mamorukomo.kamiyama.field.data.KamiyamaCenter
import com.mamorukomo.kamiyama.field.data.LatLng
import com.mamorukomo.kamiyama.field.data.NatureKind
import com.mamorukomo.kamiyama.field.data.NatureZones
import com.mamorukomo.kamiyama.field.data.Observation
import com.mamorukomo.kamiyama.field.data.SpeciesCategory
import com.mamorukomo.kamiyama.field.data.SpeciesCandidates
import com.mamorukomo.kamiyama.field.data.distanceMeters
import com.mamorukomo.kamiyama.field.ui.AdventureCard
import com.mamorukomo.kamiyama.field.ui.EmptyState
import com.mamorukomo.kamiyama.field.ui.FieldCoral
import com.mamorukomo.kamiyama.field.ui.FieldGreen
import com.mamorukomo.kamiyama.field.ui.FieldLeaf
import com.mamorukomo.kamiyama.field.ui.FieldPanelAlt
import com.mamorukomo.kamiyama.field.ui.FieldSky
import com.mamorukomo.kamiyama.field.ui.FieldTextMuted
import com.mamorukomo.kamiyama.field.ui.FieldYellow
import com.mamorukomo.kamiyama.field.ui.MetricTile
import com.mamorukomo.kamiyama.field.ui.RarityPill
import com.mamorukomo.kamiyama.field.ui.SectionTitle
import com.mamorukomo.kamiyama.field.ui.StatusPill
import com.mamorukomo.kamiyama.field.ui.accentColor
import com.mamorukomo.kamiyama.field.ui.format5
import com.mamorukomo.kamiyama.field.ui.formatDate
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
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
    val nearestZone = NatureZones.minByOrNull { zone ->
        distanceMeters(currentPoint, zone.polygon.center())
    }
    val nearCandidates = SpeciesCandidates
        .flatMap { candidate -> candidate.knownLocations.map { candidate to it } }
        .sortedBy { (_, location) -> distanceMeters(currentPoint, location) }
        .take(3)

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
                    SectionTitle("たんけんマップ", "発見ピン、候補スポット、自然エリアを重ねて見ます。")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetricTile("発見", observations.size.toString(), Modifier.weight(1f), FieldGreen)
                        MetricTile("候補", SpeciesCandidates.sumOf { it.knownLocations.size }.toString(), Modifier.weight(1f), FieldYellow)
                        MetricTile("エリア", NatureZones.size.toString(), Modifier.weight(1f), FieldSky)
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
        item {
            MapLegend()
        }
        nearestZone?.let { zone ->
            item {
                AdventureCard(tint = zone.kind.tint(), filled = false) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionTitle("いま近いエリア", zone.name)
                        Text(zone.description, color = FieldTextMuted)
                        Text(
                            currentPoint.shortLabel(),
                            color = FieldTextMuted,
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
        if (nearCandidates.isNotEmpty()) {
            item {
                AdventureCard(tint = FieldYellow, filled = false) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionTitle("近くの候補スポット", "見つかるかもしれない生き物の目安です。")
                        nearCandidates.forEach { (candidate, location) ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatusPill(candidate.category.label, candidate.category.accentColor())
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(candidate.commonName, color = Color(0xFF111816), fontWeight = FontWeight.ExtraBold)
                                    Text(
                                        "${distanceMeters(currentPoint, location).toInt()}m / ${candidate.rarity.label}",
                                        color = FieldTextMuted,
                                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                    }
                }
            }
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

            NatureZones.forEach { zone ->
                map.overlays.add(
                    Polygon(map).apply {
                        points = zone.polygon.map { GeoPoint(it.latitude, it.longitude) }
                        fillPaint.color = zone.kind.argb(alpha = 44)
                        outlinePaint.color = zone.kind.argb(alpha = 160)
                        outlinePaint.strokeWidth = 3f
                        title = zone.name
                        snippet = zone.description
                    },
                )
            }

            SpeciesCandidates.forEach { candidate ->
                candidate.knownLocations.forEach { location ->
                    map.overlays.add(
                        Marker(map).apply {
                            position = GeoPoint(location.latitude, location.longitude)
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            icon = map.context.pinIcon(
                                color = candidate.category.accentColor().toArgbInt(),
                                label = if (candidate.category == SpeciesCategory.Plant) "葉" else "虫",
                            )
                            title = candidate.commonName
                            snippet = "候補スポット / ${candidate.rarity.label} / ${candidate.hint}"
                        },
                    )
                }
            }

            observations.forEach { observation ->
                map.overlays.add(
                    Marker(map).apply {
                        position = GeoPoint(observation.latitude, observation.longitude)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        icon = map.context.pinIcon(
                            color = observation.category.accentColor().toArgbInt(),
                            label = "発",
                        )
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
                    icon = map.context.pinIcon(color = FieldSky.toArgbInt(), label = "今")
                    title = "現在地"
                    snippet = currentPoint.shortLabel()
                },
            )
            map.zoomToBoundingBox(buildMapBounds(observations, currentPoint), false, 48)
            map.invalidate()
        },
    )
}

@Composable
private fun MapLegend() {
    AdventureCard(tint = FieldSky, filled = false) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle("マップの見方", "色とマークで探検の状態を分けました。")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                StatusPill("発", FieldGreen)
                Text("自分たちがTHINKLETで見つけた場所", color = FieldTextMuted, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                StatusPill("葉/虫", FieldYellow)
                Text("GBIFなどから用意した候補スポット", color = FieldTextMuted, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                StatusPill("面", FieldSky)
                Text("川、里山、森、尾根、草地の自然エリア", color = FieldTextMuted, modifier = Modifier.weight(1f))
            }
        }
    }
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

private fun List<LatLng>.center(): LatLng {
    return LatLng(
        latitude = sumOf { it.latitude } / size,
        longitude = sumOf { it.longitude } / size,
    )
}

private fun buildMapBounds(observations: List<Observation>, currentPoint: LatLng): BoundingBox {
    val points = buildList {
        add(GeoPoint(currentPoint.latitude, currentPoint.longitude))
        add(GeoPoint(KamiyamaCenter.latitude, KamiyamaCenter.longitude))
        observations.forEach { add(GeoPoint(it.latitude, it.longitude)) }
        SpeciesCandidates.flatMap { it.knownLocations }.forEach { add(GeoPoint(it.latitude, it.longitude)) }
    }
    val north = points.maxOf { it.latitude } + 0.008
    val south = points.minOf { it.latitude } - 0.008
    val east = points.maxOf { it.longitude } + 0.008
    val west = points.minOf { it.longitude } - 0.008
    return BoundingBox(north, east, south, west)
}

private fun android.content.Context.pinIcon(color: Int, label: String): BitmapDrawable {
    val bitmap = Bitmap.createBitmap(72, 92, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = color
    canvas.drawCircle(36f, 32f, 28f, paint)
    val triangle = android.graphics.Path().apply {
        moveTo(21f, 54f)
        lineTo(36f, 88f)
        lineTo(51f, 54f)
        close()
    }
    canvas.drawPath(triangle, paint)
    paint.color = "#FFFFFF".toColorInt()
    paint.textAlign = Paint.Align.CENTER
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    paint.textSize = 24f
    canvas.drawText(label, 36f, 41f, paint)
    return BitmapDrawable(resources, bitmap)
}

private fun Color.toArgbInt(): Int {
    return android.graphics.Color.argb(
        (alpha * 255).toInt(),
        (red * 255).toInt(),
        (green * 255).toInt(),
        (blue * 255).toInt(),
    )
}

private fun NatureKind.tint(): Color {
    return when (this) {
        NatureKind.River -> FieldSky
        NatureKind.Forest -> FieldGreen
        NatureKind.Village -> FieldCoral
        NatureKind.Ridge -> FieldLeaf
        NatureKind.Field -> FieldYellow
    }
}

private fun NatureKind.argb(alpha: Int): Int {
    val color = tint()
    return android.graphics.Color.argb(
        alpha,
        (color.red * 255).toInt(),
        (color.green * 255).toInt(),
        (color.blue * 255).toInt(),
    )
}
