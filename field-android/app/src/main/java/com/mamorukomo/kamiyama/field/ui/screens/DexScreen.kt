package com.mamorukomo.kamiyama.field.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mamorukomo.kamiyama.field.data.Observation
import com.mamorukomo.kamiyama.field.data.SpeciesCandidates
import com.mamorukomo.kamiyama.field.ui.FieldCoral
import com.mamorukomo.kamiyama.field.ui.FieldGreen
import com.mamorukomo.kamiyama.field.ui.FieldGreenSoft
import com.mamorukomo.kamiyama.field.ui.FieldInk
import com.mamorukomo.kamiyama.field.ui.FieldPanel
import com.mamorukomo.kamiyama.field.ui.FieldPanelAlt
import com.mamorukomo.kamiyama.field.ui.FieldTextMuted
import com.mamorukomo.kamiyama.field.ui.ObservationImage
import com.mamorukomo.kamiyama.field.ui.RarityPill
import com.mamorukomo.kamiyama.field.ui.StatusPill
import com.mamorukomo.kamiyama.field.ui.accentColor
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
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(FieldPanelAlt)
            .padding(padding),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("見つけたいきもの", color = FieldInk, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                    Text("${discoveredIds.size} / ${SpeciesCandidates.size} 種", color = FieldTextMuted, style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = onSync, enabled = syncEnabled && !isSyncing) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "同期", tint = FieldGreen)
                }
            }
        }
        if (observations.isEmpty()) {
            item { DexEmptyState(onSync, syncEnabled && !isSyncing) }
        }
        items(observations, key = { it.id }) { observation ->
            ObservationRow(observation = observation, onDelete = { onDelete(observation) })
        }
    }
}

@Composable
private fun DexEmptyState(onSync: () -> Unit, enabled: Boolean) {
    Surface(
        onClick = onSync,
        enabled = enabled,
        color = FieldPanel,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 38.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(color = FieldGreenSoft, shape = CircleShape) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.padding(14.dp).size(30.dp), tint = FieldGreen)
            }
            Text("最初の1匹を見つけよう", color = FieldInk, fontWeight = FontWeight.ExtraBold)
            Text("THINKLETで撮ったら、ここに届きます", color = FieldTextMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ObservationRow(observation: Observation, onDelete: () -> Unit) {
    val isThinklet = observation.note.contains("THINKLET")
    Surface(color = FieldPanel, shape = RoundedCornerShape(12.dp), shadowElevation = 1.dp) {
        Row(modifier = Modifier.padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
            ObservationImage(observation.photoUri, Modifier.size(104.dp))
            Column(
                modifier = Modifier.padding(start = 12.dp).weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    StatusPill(observation.category.label, observation.category.accentColor())
                    RarityPill(observation.rarity)
                }
                Text(
                    observation.customName,
                    color = FieldInk,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(formatDate(observation.observedAtMillis), color = FieldTextMuted, style = MaterialTheme.typography.bodySmall)
                if (isThinklet) Text("AIで判定", color = FieldGreen, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = "削除", tint = FieldCoral.copy(alpha = 0.72f))
            }
        }
    }
}
