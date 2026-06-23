package com.mamorukomo.kamiyama.field.ui.screens

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.mamorukomo.kamiyama.field.ui.ObservationImage
import com.mamorukomo.kamiyama.field.ui.RarityPill
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
