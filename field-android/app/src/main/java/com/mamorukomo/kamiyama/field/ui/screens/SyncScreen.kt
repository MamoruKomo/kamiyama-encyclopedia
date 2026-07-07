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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mamorukomo.kamiyama.field.data.Observation
import com.mamorukomo.kamiyama.field.data.Rarity
import com.mamorukomo.kamiyama.field.ui.AppCard
import com.mamorukomo.kamiyama.field.ui.FieldButton
import com.mamorukomo.kamiyama.field.ui.FieldCoral
import com.mamorukomo.kamiyama.field.ui.FieldGreen
import com.mamorukomo.kamiyama.field.ui.FieldPanelAlt
import com.mamorukomo.kamiyama.field.ui.FieldSky
import com.mamorukomo.kamiyama.field.ui.FieldTextMuted
import com.mamorukomo.kamiyama.field.ui.FieldYellow
import com.mamorukomo.kamiyama.field.ui.MissionCard
import com.mamorukomo.kamiyama.field.ui.MetricTile
import com.mamorukomo.kamiyama.field.ui.SectionTitle
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
            MissionCard(tint = FieldSky) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusPill("THINKLET", FieldSky)
                        StatusPill("AIのよそう", FieldYellow)
                        StatusPill("ずかん", FieldGreen)
                    }
                    Text(
                        "発見をうけとろう",
                        color = Color(0xFF111816),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "THINKLETで撮った写真を受け取ると、AIが「なんの生き物かな?」をよそうして、ずかんに入れます。",
                        color = FieldTextMuted,
                    )
                    FieldButton(
                        text = if (isSyncing) "さがしています..." else "THINKLETからうけとる",
                        enabled = syncEnabled && !isSyncing,
                        tint = FieldCoral,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onSync,
                    )
                    if (!syncEnabled) {
                        Text(
                            "先生へ: 同期APIのURLを設定すると使えます。",
                            color = FieldTextMuted,
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
        item {
            MissionCard(tint = FieldGreen) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionTitle("たんけんのながれ")
                    StepRow("1", "THINKLETで写真を撮る", FieldCoral)
                    StepRow("2", "AIが虫・植物をよそうする", FieldYellow)
                    StepRow("3", "スマホのマップとずかんに入る", FieldGreen)
                }
            }
        }
    }
}

@Composable
private fun StepRow(number: String, text: String, tint: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        StatusPill(number, tint)
        Text(
            text,
            color = Color(0xFF111816),
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
