package com.mamorukomo.kamiyama.field.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mamorukomo.kamiyama.field.data.Observation
import com.mamorukomo.kamiyama.field.data.Rarity
import com.mamorukomo.kamiyama.field.ui.CandidateImage
import com.mamorukomo.kamiyama.field.ui.FieldCoral
import com.mamorukomo.kamiyama.field.ui.FieldForest
import com.mamorukomo.kamiyama.field.ui.FieldGreen
import com.mamorukomo.kamiyama.field.ui.FieldGreenSoft
import com.mamorukomo.kamiyama.field.ui.FieldInk
import com.mamorukomo.kamiyama.field.ui.FieldPanel
import com.mamorukomo.kamiyama.field.ui.FieldPanelAlt
import com.mamorukomo.kamiyama.field.ui.FieldSky
import com.mamorukomo.kamiyama.field.ui.FieldTextMuted
import com.mamorukomo.kamiyama.field.ui.FieldYellow
import com.mamorukomo.kamiyama.field.ui.ObservationImage
import com.mamorukomo.kamiyama.field.ui.SectionTitle

@Composable
internal fun HomeScreen(
    padding: PaddingValues,
    observations: List<Observation>,
    isSyncing: Boolean,
    syncEnabled: Boolean,
    message: String,
    onSync: () -> Unit,
    onOpenDex: () -> Unit,
    onOpenMap: () -> Unit,
    onOpenCandidates: () -> Unit,
) {
    val rareCount = observations.count { it.rarity == Rarity.Rare || it.rarity == Rarity.Special }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(FieldPanelAlt)
            .padding(padding),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            SyncHero(
                isSyncing = isSyncing,
                syncEnabled = syncEnabled,
                message = message,
                onSync = onSync,
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionTitle("きょうの発見")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DiscoveryMetric("見つけた", observations.size.toString(), FieldGreen, Modifier.weight(1f))
                    DiscoveryMetric("レア", rareCount.toString(), FieldYellow, Modifier.weight(1f))
                    DiscoveryMetric("AI判定", if (syncEnabled) "ON" else "--", FieldSky, Modifier.weight(1f))
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionTitle(if (observations.isEmpty()) "最初のミッション" else "最近の発見")
                if (observations.isEmpty()) {
                    FirstMission(onOpenCandidates)
                } else {
                    RecentDiscovery(observations.first(), onOpenDex)
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionTitle("たんけんする")
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    QuickAction("図鑑", Icons.Rounded.MenuBook, FieldGreen, Modifier.weight(1f), onOpenDex)
                    QuickAction("地図", Icons.Rounded.Map, FieldSky, Modifier.weight(1f), onOpenMap)
                    QuickAction("AI候補", Icons.Rounded.AutoAwesome, FieldYellow, Modifier.weight(1f), onOpenCandidates)
                }
            }
        }
    }
}

@Composable
private fun SyncHero(
    isSyncing: Boolean,
    syncEnabled: Boolean,
    message: String,
    onSync: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = FieldForest,
        shadowElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = Color.White.copy(alpha = 0.14f), shape = RoundedCornerShape(10.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Watch,
                        contentDescription = null,
                        modifier = Modifier.padding(10.dp).size(28.dp),
                        tint = Color.White,
                    )
                }
                Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                    Text("THINKLET 同期", color = Color.White, fontWeight = FontWeight.ExtraBold)
                    Text(
                        if (syncEnabled) "撮った写真を受け取れます" else "同期設定を確認してください",
                        color = Color.White.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Surface(color = if (syncEnabled) Color(0xFF55D692) else FieldCoral, shape = CircleShape) {
                    Box(modifier = Modifier.size(10.dp))
                }
            }
            Text(
                "山で見つけた生き物を\n図鑑に連れてこよう。",
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
            )
            Button(
                onClick = onSync,
                enabled = syncEnabled && !isSyncing,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(9.dp),
                colors = ButtonDefaults.buttonColors(containerColor = FieldCoral),
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = null)
                Text(
                    if (isSyncing) "さがしています" else "新しい発見をうけとる",
                    modifier = Modifier.padding(start = 8.dp),
                    fontWeight = FontWeight.ExtraBold,
                )
            }
            Text(
                message,
                color = Color.White.copy(alpha = 0.68f),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DiscoveryMetric(label: String, value: String, tint: Color, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = FieldPanel, shape = RoundedCornerShape(10.dp)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(value, color = tint, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            Text(label, color = FieldTextMuted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun FirstMission(onClick: () -> Unit) {
    Surface(onClick = onClick, color = FieldPanel, shape = RoundedCornerShape(12.dp), shadowElevation = 1.dp) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            CandidateImage("trypoxylus-dichotomus", Modifier.size(104.dp))
            Column(modifier = Modifier.padding(horizontal = 14.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("カブトムシを探せ", color = FieldInk, fontWeight = FontWeight.ExtraBold)
                Text("夏の木の近くにいるかも", color = FieldTextMuted, style = MaterialTheme.typography.bodySmall)
                Surface(color = FieldYellow.copy(alpha = 0.16f), shape = CircleShape) {
                    Text(
                        "レア発見 +3",
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        color = FieldYellow,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentDiscovery(observation: Observation, onClick: () -> Unit) {
    Surface(onClick = onClick, color = FieldPanel, shape = RoundedCornerShape(12.dp), shadowElevation = 1.dp) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            ObservationImage(observation.photoUri, Modifier.size(104.dp))
            Column(modifier = Modifier.padding(horizontal = 14.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(observation.customName, color = FieldInk, fontWeight = FontWeight.ExtraBold)
                Text(observation.environment, color = FieldTextMuted, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                Text("図鑑で見る", color = FieldGreen, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun QuickAction(
    label: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(onClick = onClick, modifier = modifier.aspectRatio(1f), color = FieldPanel, shape = RoundedCornerShape(10.dp)) {
        Column(
            modifier = Modifier.padding(9.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Surface(color = tint.copy(alpha = 0.13f), shape = CircleShape) {
                Icon(icon, contentDescription = null, modifier = Modifier.padding(8.dp).size(23.dp), tint = tint)
            }
            Text(label, modifier = Modifier.padding(top = 6.dp), color = FieldInk, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
    }
}
