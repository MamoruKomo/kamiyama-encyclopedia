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
import com.mamorukomo.kamiyama.field.ui.AdventureCard
import com.mamorukomo.kamiyama.field.ui.FieldButton
import com.mamorukomo.kamiyama.field.ui.FieldCoral
import com.mamorukomo.kamiyama.field.ui.FieldGreen
import com.mamorukomo.kamiyama.field.ui.FieldLeaf
import com.mamorukomo.kamiyama.field.ui.FieldPanelAlt
import com.mamorukomo.kamiyama.field.ui.FieldSky
import com.mamorukomo.kamiyama.field.ui.FieldTextMuted
import com.mamorukomo.kamiyama.field.ui.FieldYellow
import com.mamorukomo.kamiyama.field.ui.MetricTile
import com.mamorukomo.kamiyama.field.ui.QuestStep
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
            AdventureCard(tint = FieldSky) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusPill("THINKLET", FieldSky)
                        StatusPill("AIのよそう", FieldYellow)
                        StatusPill("ずかん", FieldGreen)
                    }
                    Text(
                        "発見をうけとろう",
                        color = Color(0xFF111816),
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "THINKLETで撮った写真を、AIのよそうつきでマップとずかんに入れます。",
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
                            "先生へ: field-android/local.properties に kamiyamaSyncApiUrl を設定してください。",
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
        item {
            AdventureCard(tint = FieldLeaf, filled = false) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle("たんけんのながれ", "音が鳴ったら、スマホで受け取ります。")
                    QuestStep("1", "THINKLETで撮る", "写真、時刻、位置をまとめて記録します。", FieldCoral)
                    QuestStep("2", "AIがよそう", "虫か植物か、名前とレア度を見ます。", FieldYellow)
                    QuestStep("3", "ずかんに入る", "見つけた場所がマップのピンになります。", FieldGreen)
                }
            }
        }
    }
}
