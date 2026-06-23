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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
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
import com.mamorukomo.kamiyama.field.data.Observation
import com.mamorukomo.kamiyama.field.data.Rarity
import com.mamorukomo.kamiyama.field.data.SpeciesCandidates
import com.mamorukomo.kamiyama.field.ui.ExpeditionPanel
import com.mamorukomo.kamiyama.field.ui.FieldButton
import com.mamorukomo.kamiyama.field.ui.FieldCoral
import com.mamorukomo.kamiyama.field.ui.FieldGreen
import com.mamorukomo.kamiyama.field.ui.FieldInk
import com.mamorukomo.kamiyama.field.ui.FieldOutlineButton
import com.mamorukomo.kamiyama.field.ui.FieldPanel
import com.mamorukomo.kamiyama.field.ui.FieldSky
import com.mamorukomo.kamiyama.field.ui.FieldTextMuted
import com.mamorukomo.kamiyama.field.ui.FieldViolet
import com.mamorukomo.kamiyama.field.ui.MetricTile
import com.mamorukomo.kamiyama.field.ui.ObservationImage
import com.mamorukomo.kamiyama.field.ui.RarityPill
import com.mamorukomo.kamiyama.field.ui.SectionLabel
import com.mamorukomo.kamiyama.field.ui.accentColor
import com.mamorukomo.kamiyama.field.ui.format5
import com.mamorukomo.kamiyama.field.ui.formatDate

@Composable
internal fun DexScreen(
    padding: PaddingValues,
    observations: List<Observation>,
    isSyncing: Boolean,
    syncEnabled: Boolean,
    onSync: () -> Unit,
    onDelete: (Observation) -> Unit,
) {
    val discovered = observations.mapNotNull { it.candidateId }.toSet().size
    val rare = observations.count { it.rarity == Rarity.Rare || it.rarity == Rarity.Special }
    val progress = discovered / SpeciesCandidates.size.toFloat()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(FieldInk, Color(0xFF101C28), FieldInk)))
            .padding(padding),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            DexHero(
                progress = progress,
                observationCount = observations.size,
                discovered = discovered,
                rare = rare,
                isSyncing = isSyncing,
                syncEnabled = syncEnabled,
                onSync = onSync,
            )
        }
        if (observations.isEmpty()) {
            item {
                EmptyDex()
            }
        }
        items(observations, key = { it.id }) { observation ->
            ObservationCard(observation = observation, onDelete = { onDelete(observation) })
        }
    }
}

@Composable
private fun DexHero(
    progress: Float,
    observationCount: Int,
    discovered: Int,
    rare: Int,
    isSyncing: Boolean,
    syncEnabled: Boolean,
    onSync: () -> Unit,
) {
    ExpeditionPanel(tint = FieldViolet, contentPadding = PaddingValues(18.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    SectionLabel("BIO DEX", FieldViolet)
                    Text(
                        "発見コレクション",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        "写真と位置で、神山町の自分だけの分布図を育てます。",
                        color = FieldTextMuted,
                    )
                }
                Surface(color = FieldGreen, contentColor = FieldInk, shape = CircleShape) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("${(progress * 100).toInt()}%", fontWeight = FontWeight.Black)
                        Text("SYNC", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
                    }
                }
            }
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(CircleShape),
                color = FieldGreen,
                trackColor = Color.White.copy(alpha = 0.1f),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricTile("観察", observationCount.toString(), Modifier.weight(1f), FieldGreen)
                MetricTile("候補種", "$discovered/${SpeciesCandidates.size}", Modifier.weight(1f), FieldSky)
                MetricTile("レア", rare.toString(), Modifier.weight(1f), FieldCoral)
            }
            FieldOutlineButton(
                text = if (isSyncing) "同期中" else "AI同期を取り込む",
                enabled = syncEnabled && !isSyncing,
                tint = FieldGreen,
                modifier = Modifier.fillMaxWidth(),
                onClick = onSync,
            )
        }
    }
}

@Composable
private fun EmptyDex() {
    ExpeditionPanel(tint = FieldSky) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("NO DISCOVERIES", FieldSky)
            Text("最初の発見を待っています", color = Color.White, fontWeight = FontWeight.Black)
            Text(
                "撮影画面で植物や虫を記録すると、写真・日時・場所・レア度がここに並びます。",
                color = FieldTextMuted,
            )
        }
    }
}

@Composable
private fun ObservationCard(observation: Observation, onDelete: () -> Unit) {
    val species = SpeciesCandidates.find { it.id == observation.candidateId }
    ExpeditionPanel(
        tint = observation.rarity.accentColor(),
        contentPadding = PaddingValues(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .size(width = 116.dp, height = 148.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .border(1.dp, observation.rarity.accentColor().copy(alpha = 0.42f), RoundedCornerShape(24.dp)),
            ) {
                ObservationImage(uri = observation.photoUri, modifier = Modifier.fillMaxSize())
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                    color = observation.category.accentColor(),
                    shape = CircleShape,
                    contentColor = FieldInk,
                ) {
                    Text(
                        observation.category.chip.take(1),
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                        fontWeight = FontWeight.Black,
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        SectionLabel(observation.category.chip, observation.category.accentColor())
                        Text(
                            observation.customName,
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    RarityPill(observation.rarity)
                }
                Text(
                    species?.scientificName ?: "未同定",
                    color = FieldTextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(formatDate(observation.observedAtMillis), color = FieldTextMuted, style = MaterialTheme.typography.bodySmall)
                Text(
                    "${observation.environment} / ${observation.latitude.format5()}, ${observation.longitude.format5()}",
                    color = FieldTextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (observation.note.isNotBlank()) {
                    Text(
                        observation.note,
                        color = Color.White.copy(alpha = 0.82f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                FieldButton(
                    text = "削除",
                    tint = Color.White.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onDelete,
                )
            }
        }
    }
}
