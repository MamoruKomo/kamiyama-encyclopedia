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
import com.mamorukomo.kamiyama.field.ui.AdventureCard
import com.mamorukomo.kamiyama.field.ui.FieldCoral
import com.mamorukomo.kamiyama.field.ui.FieldGreen
import com.mamorukomo.kamiyama.field.ui.FieldLeaf
import com.mamorukomo.kamiyama.field.ui.FieldPanelAlt
import com.mamorukomo.kamiyama.field.ui.FieldSky
import com.mamorukomo.kamiyama.field.ui.FieldTextMuted
import com.mamorukomo.kamiyama.field.ui.FieldYellow
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
            .background(FieldPanelAlt)
            .padding(padding),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            AdventureCard(tint = FieldLeaf, filled = false) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusPill("MAP", FieldLeaf)
                        StatusPill("発見 ${observations.size}", FieldGreen)
                        StatusPill("候補 ${SpeciesCandidates.sumOf { it.knownLocations.size }}", FieldYellow)
                    }
                    SectionTitle("たんけんマップ")
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
                AdventureCard(tint = FieldCoral, filled = false) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusPill("NEXT", FieldCoral)
                        Text("THINKLETで撮ると、ここに発見ピンが立ちます。", color = FieldTextMuted)
                    }
                }
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
            .height(560.dp)
            .clip(RoundedCornerShape(8.dp)),
        factory = { context ->
            MapView(context).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                controller.setZoom(13.2)
                controller.setCenter(GeoPoint(KamiyamaCenter.latitude, KamiyamaCenter.longitude))
            }
        },
        update = { map ->
            map.overlays.clear()

            NatureZones.forEach { zone ->
                map.overlays.add(
                    Polygon(map).apply {
                        points = zone.polygon.map { GeoPoint(it.latitude, it.longitude) }
                        fillPaint.color = zone.kind.argb(alpha = 24)
                        outlinePaint.color = zone.kind.argb(alpha = 110)
                        outlinePaint.strokeWidth = 2f
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
            val focus = observations.lastOrNull()
                ?.let { GeoPoint(it.latitude, it.longitude) }
                ?: GeoPoint(currentPoint.latitude, currentPoint.longitude)
            map.controller.setZoom(if (observations.isEmpty()) 13.1 else 13.8)
            map.controller.setCenter(focus)
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
