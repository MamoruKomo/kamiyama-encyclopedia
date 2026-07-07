package com.mamorukomo.kamiyama.field.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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

@Composable
internal fun FieldHeader(
    observations: List<Observation>,
    activeTab: AppTab,
    message: String,
) {
    Surface(color = FieldPanel, shadowElevation = 2.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(FieldPanel)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    StatusPill("KAMIYAMA QUEST", FieldSky)
                    Text(
                        "神山いきものずかん",
                        color = FieldInk,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Text(
                        activeTab.title,
                        color = FieldTextMuted,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                DiscoveryCounter(observations.size)
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = FieldGreen.copy(alpha = 0.08f),
                border = androidx.compose.foundation.BorderStroke(1.dp, FieldGreen.copy(alpha = 0.18f)),
            ) {
                Text(
                    text = "ミッション: $message",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    color = FieldTextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun DiscoveryCounter(count: Int) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = FieldYellow.copy(alpha = 0.16f),
        border = androidx.compose.foundation.BorderStroke(1.dp, FieldYellow.copy(alpha = 0.30f)),
        contentColor = FieldInk,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(count.toString(), fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge)
            Text("発見", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
internal fun FieldBottomBar(
    activeTab: AppTab,
    onTabSelected: (AppTab) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .background(FieldPanel)
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        color = FieldPanel,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppTab.entries.forEach { tab ->
                FieldTabButton(
                    tab = tab,
                    selected = activeTab == tab,
                    onClick = { onTabSelected(tab) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun FieldTabButton(
    tab: AppTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(54.dp),
        shape = RoundedCornerShape(8.dp),
        color = if (selected) FieldGreen else Color(0xFFF1F6F2),
        contentColor = if (selected) Color.White else FieldInk,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    tab.token,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                Text(tab.label, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            }
        }
    }
}
