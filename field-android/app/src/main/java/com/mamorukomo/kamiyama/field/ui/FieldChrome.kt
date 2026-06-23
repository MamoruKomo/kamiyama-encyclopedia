package com.mamorukomo.kamiyama.field.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
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
import com.mamorukomo.kamiyama.field.data.SpeciesCandidates

@Composable
internal fun FieldHeader(
    observations: List<Observation>,
    activeTab: AppTab,
    message: String,
) {
    val discovered = observations.mapNotNull { it.candidateId }.toSet().size
    val rare = observations.count { it.rarity == Rarity.Rare || it.rarity == Rarity.Special }
    val progress = discovered / SpeciesCandidates.size.toFloat()
    val rank = when {
        observations.size >= 12 -> "MASTER"
        observations.size >= 6 -> "ACE"
        observations.size >= 2 -> "SCOUT"
        else -> "ROOKIE"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF08251E),
                        Color(0xFF0E2F29),
                        Color(0xFF101D23),
                    ),
                ),
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .height(58.dp)
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White.copy(alpha = 0.07f))
                    .border(1.dp, FieldGreen.copy(alpha = 0.24f), RoundedCornerShape(24.dp))
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Column {
                    Text(
                        text = "KAMIYAMA FIELD",
                        color = FieldGreen,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        text = activeTab.title,
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Surface(
                shape = CircleShape,
                color = FieldYellow,
                contentColor = FieldInk,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("$discovered/${SpeciesCandidates.size}", fontWeight = FontWeight.Black)
                    Text("DEX", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
                }
            }
        }

        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(9.dp)
                .clip(CircleShape),
            color = FieldYellow,
            trackColor = Color.White.copy(alpha = 0.11f),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricTile("RANK", rank, Modifier.weight(1f), FieldGreen)
            MetricTile("観察", observations.size.toString(), Modifier.weight(1f), FieldSky)
            MetricTile("レア", rare.toString(), Modifier.weight(1f), FieldYellow)
        }

        Text(
            text = message,
            color = FieldTextMuted,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
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
            .background(FieldInk)
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        shape = RoundedCornerShape(26.dp),
        color = Color(0xEE112720),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
    ) {
        Row(
            modifier = Modifier.padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
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
        modifier = modifier.height(58.dp),
        shape = RoundedCornerShape(20.dp),
        color = if (selected) FieldGreen else Color.Transparent,
        contentColor = if (selected) FieldInk else FieldTextMuted,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(tab.token, fontWeight = FontWeight.Black, maxLines = 1)
            Text(tab.label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}
