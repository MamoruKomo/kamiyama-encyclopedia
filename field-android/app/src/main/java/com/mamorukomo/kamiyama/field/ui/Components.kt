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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.mamorukomo.kamiyama.field.data.Rarity
import com.mamorukomo.kamiyama.field.data.SpeciesCategory

internal val FieldGreen = Color(0xFF63E6BE)
internal val FieldYellow = Color(0xFFFFD75A)
internal val FieldCoral = Color(0xFFFF6A5E)
internal val FieldSky = Color(0xFF66B7FF)
internal val FieldViolet = Color(0xFF9B8CFF)
internal val FieldInk = Color(0xFF07130F)
internal val FieldPanel = Color(0xFF10231D)
internal val FieldPanelAlt = Color(0xFF17352B)
internal val FieldTextMuted = Color(0xFFC2D7CF)

private val panelShape = RoundedCornerShape(26.dp)

@Composable
internal fun ExpeditionPanel(
    modifier: Modifier = Modifier,
    tint: Color = FieldGreen,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier
            .border(1.dp, tint.copy(alpha = 0.25f), panelShape),
        shape = panelShape,
        color = FieldPanel.copy(alpha = 0.96f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            tint.copy(alpha = 0.12f),
                            Color.Transparent,
                            FieldPanelAlt.copy(alpha = 0.34f),
                        ),
                    ),
                )
                .padding(contentPadding),
        ) {
            content()
        }
    }
}

@Composable
internal fun SectionLabel(text: String, color: Color = FieldGreen) {
    Text(
        text = text,
        color = color,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Black,
    )
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
        color = Color.White.copy(alpha = 0.07f),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(value, color = tint, fontWeight = FontWeight.Black, maxLines = 1)
            Text(
                label,
                color = FieldTextMuted,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun RarityPill(rarity: Rarity, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = rarity.accentColor().copy(alpha = 0.18f),
        shape = CircleShape,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            Brush.linearGradient(listOf(rarity.accentColor(), rarity.accentColor().copy(alpha = 0.42f))),
        ),
    ) {
        Text(
            text = rarity.label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            color = rarity.accentColor(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            maxLines = 1,
        )
    }
}

@Composable
internal fun FieldButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = FieldCoral,
    onClick: () -> Unit,
) {
    Button(
        enabled = enabled,
        onClick = onClick,
        modifier = modifier.height(58.dp),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = tint,
            contentColor = Color.White,
            disabledContainerColor = Color.White.copy(alpha = 0.08f),
            disabledContentColor = FieldTextMuted,
        ),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        Text(text, fontWeight = FontWeight.Black, maxLines = 1)
    }
}

@Composable
internal fun FieldOutlineButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = FieldGreen,
    onClick: () -> Unit,
) {
    OutlinedButton(
        enabled = enabled,
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = tint),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            Brush.linearGradient(listOf(tint, tint.copy(alpha = 0.45f))),
        ),
        contentPadding = PaddingValues(horizontal = 14.dp),
    ) {
        Text(text, fontWeight = FontWeight.Black, maxLines = 1)
    }
}

@Composable
internal fun CategorySelectorButton(
    category: SpeciesCategory,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val tint = category.accentColor()
    Surface(
        onClick = onClick,
        modifier = modifier
            .height(74.dp)
            .border(
                width = 1.dp,
                color = if (selected) tint else Color.White.copy(alpha = 0.12f),
                shape = RoundedCornerShape(22.dp),
            ),
        shape = RoundedCornerShape(22.dp),
        color = if (selected) tint.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.06f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = if (selected) 1f else 0.28f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(category.chip.take(1), color = Color.White, fontWeight = FontWeight.Black)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(category.label, color = Color.White, fontWeight = FontWeight.Black, maxLines = 1)
                Text(
                    category.chip,
                    color = FieldTextMuted,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
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
            .background(
                Brush.linearGradient(
                    listOf(FieldPanelAlt, FieldPanel, FieldInk),
                ),
            ),
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
                Text("NO IMAGE", color = FieldTextMuted, fontWeight = FontWeight.Black)
                Text("FIELD LOG", color = Color.White.copy(alpha = 0.42f), style = MaterialTheme.typography.labelSmall)
            }
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
