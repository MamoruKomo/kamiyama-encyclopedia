package com.mamorukomo.kamiyama.field.ui.screens

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.mamorukomo.kamiyama.field.data.describeEnvironment
import com.mamorukomo.kamiyama.field.data.inferRarity
import com.mamorukomo.kamiyama.field.data.suggestCandidates
import com.mamorukomo.kamiyama.field.ui.AppCard
import com.mamorukomo.kamiyama.field.ui.CategorySelectorButton
import com.mamorukomo.kamiyama.field.ui.FieldButton
import com.mamorukomo.kamiyama.field.ui.FieldCoral
import com.mamorukomo.kamiyama.field.ui.FieldGreen
import com.mamorukomo.kamiyama.field.ui.FieldPanelAlt
import com.mamorukomo.kamiyama.field.ui.FieldSky
import com.mamorukomo.kamiyama.field.ui.FieldTextMuted
import com.mamorukomo.kamiyama.field.ui.ObservationImage
import com.mamorukomo.kamiyama.field.ui.RarityPill
import com.mamorukomo.kamiyama.field.ui.SectionTitle
import com.mamorukomo.kamiyama.field.ui.StatusPill
import com.mamorukomo.kamiyama.field.ui.accentColor
import com.mamorukomo.kamiyama.field.ui.format5
import com.mamorukomo.kamiyama.field.ui.formatDate
import kotlinx.coroutines.launch

private data class CapturedPhoto(
    val uri: Uri,
    val location: LatLng,
    val accuracy: Float?,
    val observedAtMillis: Long,
)

@Composable
internal fun CaptureScreen(
    padding: PaddingValues,
    currentLocation: suspend () -> LocationFix,
    createPhotoUri: () -> Uri,
    onSaved: (Observation) -> Unit,
    onMessage: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var category by remember { mutableStateOf(SpeciesCategory.Plant) }
    var capturedPhoto by remember { mutableStateOf<CapturedPhoto?>(null) }
    var pendingPhoto by remember { mutableStateOf<CapturedPhoto?>(null) }
    var selectedCandidate by remember { mutableStateOf<SpeciesCandidate?>(null) }
    var customName by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var isPreparing by remember { mutableStateOf(false) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val photo = pendingPhoto
        pendingPhoto = null
        isPreparing = false
        if (success && photo != null) {
            capturedPhoto = photo
            selectedCandidate = null
            customName = ""
            note = ""
            onMessage("写真と位置情報を取得しました。内容を確認して保存してください。")
        } else {
            onMessage("撮影がキャンセルされました。")
        }
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants[Manifest.permission.CAMERA] != true) {
            isPreparing = false
            onMessage("カメラ権限がないため撮影できません。")
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            isPreparing = true
            val fix = runCatching { currentLocation() }
                .onFailure { onMessage("位置情報を取得できませんでした。神山町中心を仮の位置として使います。") }
                .getOrElse { LocationFix(LatLng(33.9676, 134.3503), null) }
            val uri = createPhotoUri()
            pendingPhoto = CapturedPhoto(
                uri = uri,
                location = fix.point,
                accuracy = fix.accuracy,
                observedAtMillis = System.currentTimeMillis(),
            )
            cameraLauncher.launch(uri)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(FieldPanelAlt)
            .padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            CaptureIntro(
                category = category,
                isPreparing = isPreparing,
                onCapture = {
                    isPreparing = true
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
            item { CapturedPhotoCard(photo) }

            val suggestions = suggestCandidates(category, photo.location, photo.observedAtMillis).take(3)
            if (suggestions.isNotEmpty()) {
                item {
                    SectionTitle("近い候補", "候補を押すと名前とレア度を設定します。")
                }
                items(suggestions, key = { it.candidate.id }) { suggestion ->
                    CandidateRow(
                        candidate = suggestion.candidate,
                        selected = selectedCandidate?.id == suggestion.candidate.id,
                        onClick = {
                            selectedCandidate = suggestion.candidate
                            customName = suggestion.candidate.commonName
                        },
                    )
                }
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
                    minLines = 2,
                )
            }
            item {
                FieldButton(
                    text = "写真と位置を保存",
                    modifier = Modifier.fillMaxWidth(),
                    tint = FieldGreen,
                    onClick = {
                        val candidate = selectedCandidate
                        val observedAt = photo.observedAtMillis
                        val name = customName.trim().ifBlank { candidate?.commonName ?: "未同定" }
                        onSaved(
                            Observation(
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
                            ),
                        )
                        capturedPhoto = null
                        selectedCandidate = null
                        customName = ""
                        note = ""
                    },
                )
            }
        }
    }
}

@Composable
private fun CaptureIntro(
    category: SpeciesCategory,
    isPreparing: Boolean,
    onCapture: () -> Unit,
) {
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionTitle(
                title = "写真と位置を記録",
                subtitle = "撮影前に現在地を取得し、写真・日時・座標をまとめて保存します。",
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill(category.label, category.accentColor())
                StatusPill("GPS", FieldSky)
                StatusPill("PHOTO", FieldGreen)
            }
            FieldButton(
                text = if (isPreparing) "準備中..." else "撮影する",
                enabled = !isPreparing,
                tint = FieldCoral,
                modifier = Modifier.fillMaxWidth(),
                onClick = onCapture,
            )
        }
    }
}

@Composable
private fun CapturedPhotoCard(photo: CapturedPhoto) {
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ObservationImage(
                uri = photo.uri.toString(),
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp)),
            )
            SectionTitle("取得した情報")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoBlock("日時", formatDate(photo.observedAtMillis), Modifier.weight(1f))
                InfoBlock("場所", describeEnvironment(photo.location), Modifier.weight(1f))
            }
            Text(
                "${photo.location.latitude.format5()}, ${photo.location.longitude.format5()}" +
                    (photo.accuracy?.let { " / 精度 ${it.toInt()}m" } ?: " / 精度不明"),
                color = FieldTextMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun InfoBlock(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(Color(0xFFF3F6F2), RoundedCornerShape(8.dp))
            .padding(10.dp),
    ) {
        Text(label, color = FieldTextMuted, fontWeight = FontWeight.Bold)
        Text(value, color = Color(0xFF111816), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun CandidateRow(
    candidate: SpeciesCandidate,
    selected: Boolean,
    onClick: () -> Unit,
) {
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        contentPadding = PaddingValues(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(candidate.commonName, fontWeight = FontWeight.Bold, color = Color(0xFF111816))
                Text(
                    candidate.scientificName,
                    color = FieldTextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(candidate.hint, color = FieldTextMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                RarityPill(candidate.rarity)
                if (selected) {
                    StatusPill("選択中", FieldGreen)
                }
            }
        }
    }
}
