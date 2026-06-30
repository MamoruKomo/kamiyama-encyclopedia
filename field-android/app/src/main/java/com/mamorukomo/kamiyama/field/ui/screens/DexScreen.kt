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
import com.mamorukomo.kamiyama.field.data.SpeciesCandidate
import com.mamorukomo.kamiyama.field.data.SpeciesCategory
import com.mamorukomo.kamiyama.field.data.SpeciesCandidates
import com.mamorukomo.kamiyama.field.ui.ExpeditionPanel
import com.mamorukomo.kamiyama.field.ui.FieldCoral
import com.mamorukomo.kamiyama.field.ui.FieldGreen
import com.mamorukomo.kamiyama.field.ui.FieldInk
import com.mamorukomo.kamiyama.field.ui.FieldOutlineButton
import com.mamorukomo.kamiyama.field.ui.FieldPanel
import com.mamorukomo.kamiyama.field.ui.FieldSky
import com.mamorukomo.kamiyama.field.ui.FieldTextMuted
import com.mamorukomo.kamiyama.field.ui.FieldViolet
import com.mamorukomo.kamiyama.field.ui.FieldYellow
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
    val discoveredIds = observations.mapNotNull { it.candidateId }.toSet()

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
        item {
            InsectRarityCatalog(discoveredIds = discoveredIds)
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
                Surface(
                    onClick = onSync,
                    enabled = syncEnabled && !isSyncing,
                    color = if (syncEnabled) FieldGreen else Color.White.copy(alpha = 0.12f),
                    contentColor = if (syncEnabled) FieldInk else FieldTextMuted,
                    shape = CircleShape,
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("${(progress * 100).toInt()}%", fontWeight = FontWeight.Black)
                        Text(
                            if (isSyncing) "WAIT" else "SYNC",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                        )
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
private fun InsectRarityCatalog(discoveredIds: Set<String>) {
    val insects = SpeciesCandidates
        .filter { it.category == SpeciesCategory.Insect }
        .sortedWith(compareByDescending<SpeciesCandidate> { it.rarity.score }.thenBy { it.commonName })
    val discoveredCount = insects.count { it.id in discoveredIds }

    ExpeditionPanel(tint = FieldYellow, contentPadding = PaddingValues(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    SectionLabel("INSECT RARITY", FieldYellow)
                    Text("昆虫レア図鑑", color = Color.White, fontWeight = FontWeight.Black)
                    Text(
                        "AI分類で一致した虫は、種ごとのレア度つきで記録されます。",
                        color = FieldTextMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                StatusPill("$discoveredCount/${insects.size}", FieldYellow)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                insects.forEach { insect ->
                    InsectRarityRow(
                        insect = insect,
                        discovered = insect.id in discoveredIds,
                    )
                }
            }
        }
    }
}

@Composable
private fun InsectRarityRow(insect: SpeciesCandidate, discovered: Boolean) {
    val tint = insect.rarity.accentColor()
    Surface(
        color = if (discovered) tint.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.055f),
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (discovered) tint.copy(alpha = 0.54f) else Color.White.copy(alpha = 0.1f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = if (discovered) 1f else 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("虫", color = if (discovered) FieldInk else tint, fontWeight = FontWeight.Black)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    insect.commonName,
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    insect.scientificName,
                    color = FieldTextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    insect.hint,
                    color = FieldTextMuted.copy(alpha = 0.84f),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                RarityPill(insect.rarity)
                StatusPill(if (discovered) "DISCOVERED" else "LOCKED", if (discovered) FieldGreen else FieldTextMuted)
            }
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
    val isThinklet = observation.note.contains("THINKLET")
    val hasAi = observation.note.contains("AI判定")
    val displayName = if (isThinklet && species == null) {
        "要確認: ${observation.customName}"
    } else {
        observation.customName
    }
    val speciesLine = species?.scientificName
        ?: if (isThinklet) "AI/端末ラベルから候補確認中" else "未同定"
    val locationLine = buildString {
        append(observation.environment)
        append(" / ")
        append(observation.latitude.format5())
        append(", ")
        append(observation.longitude.format5())
        observation.accuracy?.let { accuracy ->
            append(" +/-")
            append(accuracy.toInt())
            append("m")
        }
    }
    val notePreview = observation.note
        .lineSequence()
        .filterNot { it.startsWith("THINKLETから") }
        .take(2)
        .joinToString(" / ")

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
                if (isThinklet) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp),
                        color = FieldInk.copy(alpha = 0.82f),
                        shape = CircleShape,
                        contentColor = FieldGreen,
                    ) {
                        Text(
                            "T",
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        SectionLabel(
                            if (isThinklet) "THINKLET ${observation.category.chip}" else observation.category.chip,
                            observation.category.accentColor(),
                        )
                        Text(
                            displayName,
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    RarityPill(observation.rarity)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (isThinklet) {
                        StatusPill("SYNCED", FieldViolet)
                    }
                    if (hasAi) {
                        StatusPill("AI", FieldGreen)
                    }
                    StatusPill(
                        if (observation.accuracy != null) "GPS" else "NO GPS",
                        if (observation.accuracy != null) FieldSky else FieldCoral,
                    )
                }
                Text(
                    speciesLine,
                    color = FieldTextMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(formatDate(observation.observedAtMillis), color = FieldTextMuted, style = MaterialTheme.typography.bodySmall)
                Text(
                    locationLine,
                    color = FieldTextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (notePreview.isNotBlank()) {
                    Text(
                        notePreview,
                        color = Color.White.copy(alpha = 0.82f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                FieldOutlineButton(
                    text = "この記録を削除",
                    tint = FieldCoral,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onDelete,
                )
            }
        }
    }
}

@Composable
private fun StatusPill(text: String, tint: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = tint.copy(alpha = 0.16f),
        shape = CircleShape,
        border = androidx.compose.foundation.BorderStroke(1.dp, tint.copy(alpha = 0.34f)),
        contentColor = tint,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            maxLines = 1,
        )
    }
}
