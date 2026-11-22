package com.kiko.tracker.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kiko.tracker.data.model.onSentiment
import com.kiko.tracker.ui.theme.LocalKikoColors

/**
 * Standard Material 3 single-choice segmented button row. One control for every "pick one of a few"
 * switch in the app (Anime/Manga, review sentiment, list status ...), so they all look and behave the same.
 *
 * [tints] optionally gives a selected segment its own container colour (otherwise the secondary container).
 * [iconOnly] shows just the icon; the label stays as its content description.
 */
@Composable
fun KikoSegmentedRow(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icons: List<ImageVector> = emptyList(),
    tints: List<Color> = emptyList(),
    iconOnly: Boolean = false,
    height: Dp = 40.dp,
) {
    val c = LocalKikoColors.current
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        options.forEachIndexed { i, label ->
            val tint = tints.getOrNull(i)
            val icon = icons.getOrNull(i)
            val active = i == selected
            SegmentedButton(
                selected = active,
                onClick = { onSelect(i) },
                enabled = enabled,
                shape = SegmentedButtonDefaults.itemShape(index = i, count = options.size),
                modifier = Modifier.height(height),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = tint ?: c.secondaryContainer,
                    activeContentColor = if (tint != null) onSentiment(tint) else c.onSecondaryContainer,
                    inactiveContainerColor = Color.Transparent,
                    inactiveContentColor = c.onSurfaceVariant,
                    activeBorderColor = tint ?: c.outline,
                    inactiveBorderColor = c.outline,
                ),
                icon = {
                    when {
                        iconOnly -> {}
                        icon != null -> Icon(icon, null, modifier = Modifier.size(SegmentedButtonDefaults.IconSize))
                        else -> SegmentedButtonDefaults.Icon(active)
                    }
                },
                label = {
                    if (iconOnly && icon != null) Icon(icon, label, modifier = Modifier.size(20.dp))
                    else Text(label, maxLines = 1)
                },
            )
        }
    }
}
