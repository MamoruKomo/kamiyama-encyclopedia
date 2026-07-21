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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mamorukomo.kamiyama.field.data.SpeciesCandidate
import com.mamorukomo.kamiyama.field.data.SpeciesCandidates
import com.mamorukomo.kamiyama.field.data.SpeciesCategory
import com.mamorukomo.kamiyama.field.ui.CandidateImage
import com.mamorukomo.kamiyama.field.ui.FieldGreen
import com.mamorukomo.kamiyama.field.ui.FieldInk
import com.mamorukomo.kamiyama.field.ui.FieldPanel
import com.mamorukomo.kamiyama.field.ui.FieldPanelAlt
import com.mamorukomo.kamiyama.field.ui.FieldTextMuted
import com.mamorukomo.kamiyama.field.ui.RarityPill
import com.mamorukomo.kamiyama.field.ui.accentColor

@Composable
internal fun CandidatesScreen(padding: PaddingValues) {
    var selected by remember { mutableStateOf<SpeciesCategory?>(null) }
    val candidates = SpeciesCandidates
        .filter { selected == null || it.category == selected }
        .sortedBy { candidate ->
            FeaturedCandidateIds.indexOf(candidate.id).takeIf { it >= 0 } ?: Int.MAX_VALUE
        }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FieldPanelAlt)
            .padding(padding),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip("すべて", selected == null, FieldGreen) { selected = null }
            FilterChip("植物", selected == SpeciesCategory.Plant, SpeciesCategory.Plant.accentColor()) {
                selected = SpeciesCategory.Plant
            }
            FilterChip("昆虫", selected == SpeciesCategory.Insect, SpeciesCategory.Insect.accentColor()) {
                selected = SpeciesCategory.Insect
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(candidates, key = { it.id }) { candidate ->
                CandidateCard(candidate)
            }
        }
    }
}

@Composable
private fun FilterChip(label: String, active: Boolean, tint: Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (active) tint else FieldPanel,
        contentColor = if (active) Color.White else FieldTextMuted,
        shape = RoundedCornerShape(99.dp),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun CandidateCard(candidate: SpeciesCandidate) {
    Surface(color = FieldPanel, shape = RoundedCornerShape(12.dp), shadowElevation = 1.dp) {
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            CandidateImage(
                candidateId = candidate.id,
                modifier = Modifier.fillMaxWidth().aspectRatio(1.18f),
                category = candidate.category,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                RarityPill(candidate.rarity)
                Icon(
                    Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = candidate.category.accentColor(),
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            Text(candidate.commonName, color = FieldInk, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                candidate.scientificName,
                color = FieldTextMuted,
                style = MaterialTheme.typography.labelSmall,
                fontStyle = FontStyle.Italic,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private val FeaturedCandidateIds = listOf(
    "trypoxylus-dichotomus",
    "prosopocoilus-inclinatus",
    "ardisia-crenata",
    "argynnis-hyperbius",
)
