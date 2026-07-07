package com.mamorukomo.kamiyama.field.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mamorukomo.kamiyama.field.data.Observation
import com.mamorukomo.kamiyama.field.data.Rarity
import com.mamorukomo.kamiyama.field.ui.AdventureCard
import com.mamorukomo.kamiyama.field.ui.FieldButton
import com.mamorukomo.kamiyama.field.ui.FieldCoral
import com.mamorukomo.kamiyama.field.ui.FieldGreen
import com.mamorukomo.kamiyama.field.ui.FieldPanelAlt
import com.mamorukomo.kamiyama.field.ui.FieldSky
import com.mamorukomo.kamiyama.field.ui.FieldTextMuted
import com.mamorukomo.kamiyama.field.ui.FieldYellow
import com.mamorukomo.kamiyama.field.ui.MetricTile
import com.mamorukomo.kamiyama.field.ui.StatusPill

@Composable
internal fun SyncScreen(
    padding: PaddingValues,
    observations: List<Observation>,
    isSyncing: Boolean,
    syncEnabled: Boolean,
    onSync: () -> Unit,
) {
    val rareCount = observations.count { it.rarity == Rarity.Rare || it.rarity == Rarity.Special }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(FieldPanelAlt)
            .padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            AdventureCard(tint = FieldSky) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusPill("THINKLET", FieldSky)
                        StatusPill("AI", FieldYellow)
                    }
                    Text(
                        "発見をキャッチ",
                        color = Color(0xFF111816),
                        fontWeight = FontWeight.ExtraBold,
                        style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
                    )
                    FieldButton(
                        text = if (isSyncing) "さがし中..." else "うけとる",
                        enabled = syncEnabled && !isSyncing,
                        tint = FieldCoral,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onSync,
                    )
                    if (!syncEnabled) {
                        Text(
                            "同期API未設定",
                            color = FieldCoral,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricTile("発見", observations.size.toString(), Modifier.weight(1f), FieldGreen)
                MetricTile("レア", rareCount.toString(), Modifier.weight(1f), FieldYellow)
                MetricTile("AI", "ON", Modifier.weight(1f), FieldSky)
            }
        }
    }
}
