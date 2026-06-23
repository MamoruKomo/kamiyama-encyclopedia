package com.mamorukomo.kamiyama.field.ui.screens

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mamorukomo.kamiyama.field.LocationFix
import com.mamorukomo.kamiyama.field.data.LatLng
import com.mamorukomo.kamiyama.field.data.Observation
import com.mamorukomo.kamiyama.field.data.SpeciesCandidate
import com.mamorukomo.kamiyama.field.data.SpeciesCategory
import com.mamorukomo.kamiyama.field.data.Suggestion
import com.mamorukomo.kamiyama.field.data.describeEnvironment
import com.mamorukomo.kamiyama.field.data.inferRarity
import com.mamorukomo.kamiyama.field.data.suggestCandidates
import com.mamorukomo.kamiyama.field.ui.CategorySelectorButton
import com.mamorukomo.kamiyama.field.ui.ExpeditionPanel
import com.mamorukomo.kamiyama.field.ui.FieldButton
import com.mamorukomo.kamiyama.field.ui.FieldCoral
import com.mamorukomo.kamiyama.field.ui.FieldGreen
import com.mamorukomo.kamiyama.field.ui.FieldInk
import com.mamorukomo.kamiyama.field.ui.FieldOutlineButton
import com.mamorukomo.kamiyama.field.ui.FieldPanel
import com.mamorukomo.kamiyama.field.ui.FieldSky
import com.mamorukomo.kamiyama.field.ui.FieldTextMuted
import com.mamorukomo.kamiyama.field.ui.ObservationImage
import com.mamorukomo.kamiyama.field.ui.RarityPill
import com.mamorukomo.kamiyama.field.ui.SectionLabel
import com.mamorukomo.kamiyama.field.ui.accentColor
import com.mamorukomo.kamiyama.field.ui.format5
import com.mamorukomo.kamiyama.field.ui.formatDate
import kotlin.math.roundToInt

private data class CapturedPhoto(
    val uri: Uri,
    val location: LatLng,
    val accuracy: Float?,
    val observedAtMillis: Long,
)

@Composable
internal fun CaptureScreen(
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
            onMessage("スキャン完了。候補を選ぶか、名前を入力して登録できます。")
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
            .background(Brush.verticalGradient(listOf(FieldInk, Color(0xFF14251F), FieldInk)))
            .padding(padding),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            ScannerConsole(
                category = category,
                onCapture = {
                    permissionsLauncher.launch(
                        arrayOf(
                            Manifest.permission.CAMERA,
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                        ),
                    )
                },
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SpeciesCategory.entries.forEach { item ->
                    CategorySelectorButton(
                        category = item,
                        selected = category == item,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            category = item
                            selectedCandidate = null
                        },
                    )
                }
            }
        }

        capturedPhoto?.let { photo ->
            item {
                CapturedPreview(photo)
            }
            item {
                SectionLabel("MATCH CANDIDATES")
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
                FieldButton(
                    text = "図鑑に登録",
                    modifier = Modifier.fillMaxWidth(),
                    tint = FieldGreen,
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
                )
            }
        }
    }
}

@Composable
private fun ScannerConsole(
    category: SpeciesCategory,
    onCapture: () -> Unit,
) {
    ExpeditionPanel(tint = category.accentColor(), contentPadding = PaddingValues(18.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    SectionLabel("FIELD SCANNER", category.accentColor())
                    Text(
                        "発見をロックオン",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        "写真、GPS、時刻をまとめて記録します。",
                        color = FieldTextMuted,
                    )
                }
                Surface(
                    color = category.accentColor().copy(alpha = 0.18f),
                    shape = CircleShape,
                    border = androidx.compose.foundation.BorderStroke(1.dp, category.accentColor()),
                ) {
                    Text(
                        category.chip,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                        color = category.accentColor(),
                        fontWeight = FontWeight.Black,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        Brush.radialGradient(
                            listOf(category.accentColor().copy(alpha = 0.34f), FieldPanel, FieldInk),
                        ),
                    )
                    .border(1.dp, category.accentColor().copy(alpha = 0.38f), RoundedCornerShape(28.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(28.dp)
                        .border(2.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(24.dp)),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(64.dp)
                        .border(2.dp, category.accentColor().copy(alpha = 0.54f), CircleShape),
                )
                FieldButton(
                    text = "撮影して地点を記録",
                    tint = FieldCoral,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 34.dp),
                    onClick = onCapture,
                )
            }
        }
    }
}

@Composable
private fun CapturedPreview(photo: CapturedPhoto) {
    ExpeditionPanel(tint = FieldSky, contentPadding = PaddingValues(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ObservationImage(
                uri = photo.uri.toString(),
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(24.dp)),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CaptureMetric("時刻", formatDate(photo.observedAtMillis), Modifier.weight(1f))
                CaptureMetric("環境", describeEnvironment(photo.location), Modifier.weight(1f))
            }
            Text(
                "${photo.location.latitude.format5()}, ${photo.location.longitude.format5()}",
                color = FieldTextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun CaptureMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .padding(12.dp),
    ) {
        Text(label, color = FieldSky, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
        Text(value, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SuggestionCard(
    suggestion: Suggestion,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val candidate = suggestion.candidate
    ExpeditionPanel(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        tint = if (selected) candidate.rarity.accentColor() else candidate.category.accentColor(),
        contentPadding = PaddingValues(14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .height(78.dp)
                    .weight(0.25f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(candidate.category.accentColor().copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(candidate.category.chip.take(1), color = candidate.category.accentColor(), fontWeight = FontWeight.Black)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(candidate.commonName, color = Color.White, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f), maxLines = 1)
                    RarityPill(candidate.rarity)
                }
                Text(candidate.scientificName, color = FieldTextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${suggestion.distanceMeters.roundToInt()}m / ${candidate.hint}",
                    color = FieldTextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
