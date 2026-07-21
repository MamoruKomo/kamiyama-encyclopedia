package com.mamorukomo.kamiyama.field.ui

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.mamorukomo.kamiyama.field.data.Rarity
import com.mamorukomo.kamiyama.field.data.SpeciesCategory
import com.mamorukomo.kamiyama.field.R

internal val FieldForest = Color(0xFF173D2B)
internal val FieldGreen = Color(0xFF2D7A52)
internal val FieldGreenSoft = Color(0xFFE5F2E9)
internal val FieldYellow = Color(0xFFD99A2B)
internal val FieldCoral = Color(0xFFE56F51)
internal val FieldSky = Color(0xFF3A82A8)
internal val FieldLeaf = Color(0xFF668F45)
internal val FieldBerry = Color(0xFFB44B68)
internal val FieldInk = Color(0xFF18221D)
internal val FieldPanel = Color(0xFFFFFFFF)
internal val FieldPanelAlt = Color(0xFFF7F8F3)
internal val FieldTextMuted = Color(0xFF68736C)

private val cardShape = RoundedCornerShape(12.dp)

@Composable
internal fun AppCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = cardShape,
        color = FieldPanel,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE1E7E2)),
        tonalElevation = 0.dp,
        shadowElevation = 1.dp,
    ) {
        Box(modifier = Modifier.padding(contentPadding)) {
            content()
        }
    }
}

@Composable
internal fun AdventureCard(
    modifier: Modifier = Modifier,
    tint: Color = FieldGreen,
    filled: Boolean = true,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = cardShape,
        color = if (filled) tint.copy(alpha = 0.08f) else FieldPanel,
        border = androidx.compose.foundation.BorderStroke(1.dp, tint.copy(alpha = 0.18f)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Box(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}

@Composable
internal fun MissionCard(
    modifier: Modifier = Modifier,
    tint: Color = FieldGreen,
    content: @Composable () -> Unit,
) {
    AdventureCard(modifier = modifier, tint = tint, content = content)
}

@Composable
internal fun SectionTitle(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, color = FieldInk, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
        if (subtitle != null) {
            Text(subtitle, color = FieldTextMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
internal fun MetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    tint: Color = FieldGreen,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = tint.copy(alpha = 0.10f),
        border = androidx.compose.foundation.BorderStroke(1.dp, tint.copy(alpha = 0.22f)),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Box(modifier = Modifier.padding(12.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(value, color = tint, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                    Text(label, color = FieldTextMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
            }
        }
    }
}

@Composable
internal fun FieldButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = FieldGreen,
    onClick: () -> Unit,
) {
    Button(
        enabled = enabled,
        onClick = onClick,
        modifier = modifier.height(50.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = tint,
            contentColor = Color.White,
            disabledContainerColor = Color(0xFFDDE5DF),
            disabledContentColor = FieldTextMuted,
        ),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        Text(text, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun StatusPill(
    text: String,
    tint: Color = FieldGreen,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = tint.copy(alpha = 0.14f),
        shape = CircleShape,
        contentColor = tint,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
        )
    }
}

@Composable
internal fun QuestStep(
    number: String,
    title: String,
    detail: String,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = FieldPanel,
        border = androidx.compose.foundation.BorderStroke(1.dp, tint.copy(alpha = 0.24f)),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            StatusPill(number, tint)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, color = FieldInk, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                Text(detail, color = FieldTextMuted, style = MaterialTheme.typography.bodySmall, maxLines = 2)
            }
        }
    }
}

@Composable
internal fun EmptyState(
    title: String,
    body: String,
    tint: Color = FieldGreen,
    modifier: Modifier = Modifier,
) {
    AdventureCard(modifier = modifier, tint = tint, filled = false) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            StatusPill("NEXT", tint)
            Text(title, color = FieldInk, fontWeight = FontWeight.ExtraBold)
            Text(body, color = FieldTextMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
internal fun RarityPill(rarity: Rarity, modifier: Modifier = Modifier) {
    StatusPill(text = rarity.label, tint = rarity.accentColor(), modifier = modifier)
}

@Composable
internal fun CategorySelectorButton(
    category: SpeciesCategory,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .height(56.dp)
            .border(
                width = 1.dp,
                color = if (selected) category.accentColor() else Color(0xFFE0E6E1),
                shape = RoundedCornerShape(8.dp),
            ),
        shape = RoundedCornerShape(8.dp),
        color = if (selected) category.accentColor().copy(alpha = 0.12f) else FieldPanel,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatusPill(category.chip, category.accentColor())
            Text(category.label, color = FieldInk, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        }
    }
}

@Composable
internal fun ObservationImage(uri: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap = remember(uri) {
        runCatching {
            if (uri.startsWith("data:image/") && uri.contains("base64,")) {
                val bytes = Base64.decode(uri.substringAfter("base64,"), Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            } else {
                context.contentResolver.openInputStream(uri.toUri())?.use { stream ->
                    BitmapFactory.decodeStream(stream)?.asImageBitmap()
                }
            }
        }.getOrNull()
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(FieldPanelAlt),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("NO IMAGE", color = FieldTextMuted, fontWeight = FontWeight.ExtraBold)
                Text("写真なし", color = FieldTextMuted, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
internal fun CandidateImage(
    candidateId: String,
    modifier: Modifier = Modifier,
    category: SpeciesCategory? = null,
) {
    val resource = when (candidateId) {
        "trypoxylus-dichotomus" -> R.drawable.species_kabutomushi
        "prosopocoilus-inclinatus" -> R.drawable.species_nokogiri_kuwagata
        "ardisia-crenata" -> R.drawable.species_manryo
        "argynnis-hyperbius" -> R.drawable.species_tsumaguro
        else -> null
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(FieldGreenSoft),
        contentAlignment = Alignment.Center,
    ) {
        if (resource != null) {
            Image(
                painter = painterResource(resource),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                imageVector = if (category == SpeciesCategory.Insect) Icons.Rounded.BugReport else Icons.Rounded.Eco,
                contentDescription = null,
                modifier = Modifier.size(34.dp),
                tint = category?.accentColor() ?: FieldGreen,
            )
        }
    }
}

internal fun Rarity.accentColor(): Color {
    return when (this) {
        Rarity.Common -> FieldGreen
        Rarity.Uncommon -> FieldSky
        Rarity.Rare -> FieldYellow
        Rarity.Special -> FieldCoral
    }
}

internal fun SpeciesCategory.accentColor(): Color {
    return when (this) {
        SpeciesCategory.Plant -> FieldGreen
        SpeciesCategory.Insect -> FieldYellow
    }
}
