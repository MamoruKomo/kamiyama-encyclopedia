package com.mamorukomo.kamiyama.field.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mamorukomo.kamiyama.field.data.Observation
import com.mamorukomo.kamiyama.field.data.Rarity
import com.mamorukomo.kamiyama.field.data.SpeciesCandidates
import com.mamorukomo.kamiyama.field.ui.HudStat
import com.mamorukomo.kamiyama.field.ui.ObservationImage
import com.mamorukomo.kamiyama.field.ui.RarityPill
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
            .padding(padding),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "BIO DEX",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                    )
                    Text("発見コレクション", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    Text(
                        "写真と位置で神山町の自分だけの分布図を育てます。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        enabled = syncEnabled && !isSyncing,
                        onClick = onSync,
                    ) {
                        Text(if (isSyncing) "同期中" else "AI同期を取り込む")
                    }
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape),
                        color = MaterialTheme.colorScheme.secondary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HudStat("観察", observations.size.toString(), Modifier.weight(1f))
                HudStat("候補種", "$discovered/${SpeciesCandidates.size}", Modifier.weight(1f))
                HudStat("レア以上", rare.toString(), Modifier.weight(1f), accent = true)
            }
        }
        if (observations.isEmpty()) {
            item {
                ElevatedCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("最初の発見を待っています", fontWeight = FontWeight.Black)
                        Text(
                            "撮影画面で植物や虫を記録すると、写真・日時・場所・レア度がここに並びます。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        items(observations, key = { it.id }) { observation ->
            ObservationCard(observation = observation, onDelete = { onDelete(observation) })
        }
    }
}

@Composable
private fun ObservationCard(observation: Observation, onDelete: () -> Unit) {
    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ObservationImage(
                uri = observation.photoUri,
                modifier = Modifier
                    .size(108.dp)
                    .clip(MaterialTheme.shapes.medium),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    observation.category.chip,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                )
                Text(observation.customName, fontWeight = FontWeight.Black, maxLines = 1)
                Text(
                    SpeciesCandidates.find { it.id == observation.candidateId }?.scientificName ?: "未同定",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                )
                RarityPill(observation.rarity)
                Text(formatDate(observation.observedAtMillis), style = MaterialTheme.typography.bodySmall)
                Text(
                    "${observation.environment} / ${observation.latitude.format5()}, ${observation.longitude.format5()}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                )
                if (observation.note.isNotBlank()) {
                    Text(observation.note, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                }
                OutlinedButton(onClick = onDelete) {
                    Text("削除")
                }
            }
        }
    }
}
