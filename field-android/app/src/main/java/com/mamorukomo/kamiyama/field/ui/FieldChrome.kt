package com.mamorukomo.kamiyama.field.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mamorukomo.kamiyama.field.data.Observation

@Composable
internal fun FieldHeader(
    observations: List<Observation>,
    activeTab: AppTab,
) {
    Surface(color = FieldPanel, shadowElevation = 1.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = "神山図鑑",
                    color = FieldForest,
                    style = MaterialTheme.typography.headlineSmall,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = activeTab.title,
                    color = FieldTextMuted,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Surface(
                color = FieldGreenSoft,
                shape = CircleShapeLarge,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Surface(color = FieldGreen, shape = CircleShapeSmall) {
                        Box(modifier = Modifier.padding(4.dp))
                    }
                    Text(
                        text = "${observations.size} 発見",
                        color = FieldForest,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@Composable
internal fun FieldBottomBar(
    activeTab: AppTab,
    onTabSelected: (AppTab) -> Unit,
) {
    Surface(color = FieldPanel, shadowElevation = 8.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
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
    val icon = when (tab) {
        AppTab.Home -> Icons.Rounded.Home
        AppTab.Dex -> Icons.Rounded.MenuBook
        AppTab.Map -> Icons.Rounded.Map
        AppTab.Candidates -> Icons.Rounded.AutoAwesome
    }
    Surface(
        onClick = onClick,
        modifier = modifier.height(58.dp),
        color = Color.Transparent,
        contentColor = if (selected) FieldGreen else FieldTextMuted,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Icon(imageVector = icon, contentDescription = tab.label)
                Text(
                    text = tab.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                )
            }
        }
    }
}

private val CircleShapeLarge = androidx.compose.foundation.shape.RoundedCornerShape(99.dp)
private val CircleShapeSmall = androidx.compose.foundation.shape.RoundedCornerShape(99.dp)
