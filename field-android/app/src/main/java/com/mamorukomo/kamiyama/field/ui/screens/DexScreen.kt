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
import com.mamorukomo.kamiyama.field.data.SpeciesCandidates
import com.mamorukomo.kamiyama.field.ui.AdventureCard
import com.mamorukomo.kamiyama.field.ui.EmptyState
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
            AdventureCard(tint = FieldGreen) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionTitle("発見ずかん")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetricTile("発見", observations.size.toString(), Modifier.weight(1f), FieldGreen)
                        MetricTile("図鑑", "${discoveredIds.size}/${SpeciesCandidates.size}", Modifier.weight(1f), FieldYellow)
                        MetricTile("レア", rare.toString(), Modifier.weight(1f), FieldCoral)
                    }
                    FieldButton(
                        text = if (isSyncing) "さがし中..." else "うけとる",
                        enabled = syncEnabled && !isSyncing,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onSync,
                    )
                }
            }
        }
        if (observations.isEmpty()) {
            item {
                EmptyState(
                    title = "まだ発見がありません",
                    body = "THINKLETで撮ると写真が並びます。",
                    tint = FieldCoral,
                )
            }
        }
        items(observations, key = { it.id }) { observation ->
            ObservationCard(observation = observation, onDelete = { onDelete(observation) })
        }
    }
}

@Composable
private fun ObservationCard(observation: Observation, onDelete: () -> Unit) {
    val species = SpeciesCandidates.find { it.id == observation.candidateId }
    val isThinklet = observation.note.contains("THINKLET")

    AdventureCard(tint = observation.category.accentColor(), filled = false) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ObservationImage(
                uri = observation.photoUri,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusPill(observation.category.label, observation.category.accentColor())
                if (isThinklet) StatusPill("AI", FieldYellow)
                RarityPill(observation.rarity)
            }
            Text(
                if (isThinklet && species == null) observation.customName else observation.customName,
                color = Color(0xFF111816),
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(formatDate(observation.observedAtMillis), color = FieldTextMuted, maxLines = 1)
            FieldButton(
                text = "削除",
                tint = FieldCoral,
                modifier = Modifier.fillMaxWidth(),
                onClick = onDelete,
            )
        }
    }
}
