package com.mamorukomo.kamiyama.field.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mamorukomo.kamiyama.field.data.Observation
import com.mamorukomo.kamiyama.field.data.Rarity
import com.mamorukomo.kamiyama.field.data.SpeciesCandidate
import com.mamorukomo.kamiyama.field.data.SpeciesCategory
import com.mamorukomo.kamiyama.field.data.SpeciesCandidates
import com.mamorukomo.kamiyama.field.ui.AppCard
import com.mamorukomo.kamiyama.field.ui.FieldButton
import com.mamorukomo.kamiyama.field.ui.FieldCoral
import com.mamorukomo.kamiyama.field.ui.FieldGreen
import com.mamorukomo.kamiyama.field.ui.FieldPanelAlt
import com.mamorukomo.kamiyama.field.ui.FieldTextMuted
import com.mamorukomo.kamiyama.field.ui.FieldYellow
import com.mamorukomo.kamiyama.field.ui.MetricTile
import com.mamorukomo.kamiyama.field.ui.ObservationImage
import com.mamorukomo.kamiyama.field.ui.RarityPill
import com.mamorukomo.kamiyama.field.ui.SectionTitle
import com.mamorukomo.kamiyama.field.ui.StatusPill
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
    val discoveredIds = observations.mapNotNull { it.candidateId }.toSet()
    val rare = observations.count { it.rarity == Rarity.Rare || it.rarity == Rarity.Special }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(FieldPanelAlt)
            .padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionTitle("発見一覧", "スマホ撮影とTHINKLET同期の記録をここで確認します。")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetricTile("観察", observations.size.toString(), Modifier.weight(1f), FieldGreen)
                        MetricTile("候補種", "${discoveredIds.size}/${SpeciesCandidates.size}", Modifier.weight(1f), FieldYellow)
                        MetricTile("レア", rare.toString(), Modifier.weight(1f), FieldCoral)
                    }
                    FieldButton(
                        text = if (isSyncing) "同期中..." else "THINKLET同期",
                        enabled = syncEnabled && !isSyncing,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onSync,
                    )
                }
            }
        }
        item {
            InsectRarityCatalog(discoveredIds)
        }
        if (observations.isEmpty()) {
            item {
                AppCard {
                    SectionTitle("まだ記録がありません", "記録タブで写真と位置情報を保存してください。")
                }
            }
        }
        items(observations, key = { it.id }) { observation ->
            ObservationCard(observation = observation, onDelete = { onDelete(observation) })
        }
    }
}

@Composable
private fun InsectRarityCatalog(discoveredIds: Set<String>) {
    val insects = SpeciesCandidates
        .filter { it.category == SpeciesCategory.Insect }
        .sortedWith(compareByDescending<SpeciesCandidate> { it.rarity.score }.thenBy { it.commonName })

    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle(
                title = "昆虫レア図鑑",
                subtitle = "AI分類で一致した昆虫は、種ごとのレア度で記録されます。",
            )
            insects.forEach { insect ->
                InsectRow(insect, discovered = insect.id in discoveredIds)
            }
        }
    }
}

@Composable
private fun InsectRow(insect: SpeciesCandidate, discovered: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        StatusPill(if (discovered) "発見" else "未発見", if (discovered) FieldGreen else FieldTextMuted)
        Column(modifier = Modifier.weight(1f)) {
            Text(insect.commonName, color = Color(0xFF111816), fontWeight = FontWeight.Bold)
            Text(insect.scientificName, color = FieldTextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        RarityPill(insect.rarity)
    }
}

@Composable
private fun ObservationCard(observation: Observation, onDelete: () -> Unit) {
    val species = SpeciesCandidates.find { it.id == observation.candidateId }
    val isThinklet = observation.note.contains("THINKLET")
    val notePreview = observation.note
        .lineSequence()
        .filterNot { it.startsWith("THINKLETから") }
        .take(2)
        .joinToString(" / ")

    AppCard {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ObservationImage(
                uri = observation.photoUri,
                modifier = Modifier
                    .size(96.dp)
                    .aspectRatio(1f),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusPill(observation.category.label, observation.category.accentColor())
                    if (isThinklet) StatusPill("THINKLET", FieldYellow)
                    RarityPill(observation.rarity)
                }
                Text(
                    if (isThinklet && species == null) "要確認: ${observation.customName}" else observation.customName,
                    color = Color(0xFF111816),
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    species?.scientificName ?: "未同定",
                    color = FieldTextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(formatDate(observation.observedAtMillis), color = FieldTextMuted, maxLines = 1)
                Text(
                    "${observation.environment} / ${observation.latitude.format5()}, ${observation.longitude.format5()}",
                    color = FieldTextMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (notePreview.isNotBlank()) {
                    Text(notePreview, color = FieldTextMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                FieldButton(
                    text = "削除",
                    tint = FieldCoral,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onDelete,
                )
            }
        }
    }
}
