@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.kiko.tracker.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiko.tracker.data.model.ColorSource
import com.kiko.tracker.data.model.PaletteStyle
import com.kiko.tracker.data.model.ThemeMode
import com.kiko.tracker.data.model.TitleLanguage
import com.kiko.tracker.ui.screens.HomeActionButton
import com.kiko.tracker.ui.theme.HsvColorPicker
import com.kiko.tracker.ui.theme.LocalKikoColors
import com.kiko.tracker.ui.theme.kikoClickable
import com.kiko.tracker.ui.theme.kikoCorner
import com.kiko.tracker.ui.theme.kikoPillShape
import com.kiko.tracker.ui.theme.parseHexColor
import com.kiko.tracker.util.AppUpdateInfo

@Composable fun UpdateDialog(
    info: AppUpdateInfo, downloadProgress: Float?, needsInstallPermission: Boolean, error: String?,
    onDownload: () -> Unit, onOpenInstallSettings: () -> Unit, onSkip: () -> Unit, onDismiss: () -> Unit,
) {
    val c = LocalKikoColors.current
    val downloading = downloadProgress != null
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surfaceContainerHigh,
        title = { Text("Kiko ${info.version} is available", color = c.ink) },
        text = {
            Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                if (needsInstallPermission) {
                    Text("Kiko needs permission to install updates. Allow it from Settings, then try again.", color = c.danger, fontSize = 13.sp, modifier = Modifier.padding(bottom = 12.dp))
                } else if (error != null) {
                    Text(error, color = c.danger, fontSize = 13.sp, modifier = Modifier.padding(bottom = 12.dp))
                }
                if (downloading) {
                    val progress = downloadProgress ?: 0f
                    Text("Downloading update…", color = c.ink, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().clip(kikoPillShape()), color = c.primary, trackColor = c.surfaceLow)
                    Text("${(progress * 100).toInt()}%", color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                } else {
                    Text(info.notes.ifBlank { "No release notes provided." }, color = c.muted, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            when {
                needsInstallPermission -> TextButton(onClick = onOpenInstallSettings, colors = ButtonDefaults.textButtonColors(contentColor = c.primary)) { Text("Open settings") }
                else -> TextButton(onClick = onDownload, enabled = !downloading, colors = ButtonDefaults.textButtonColors(contentColor = c.primary)) { Text(if (downloading) "Downloading…" else "Update now") }
            }
        },
        dismissButton = {
            if (!downloading) {
                Row {
                    TextButton(onClick = onSkip, colors = ButtonDefaults.textButtonColors(contentColor = c.muted)) { Text("Skip") }
                    TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = c.muted)) { Text("Later") }
                }
            }
        },
    )
}

// Themed replacement for the
// through here so it
// or a stray line
@Composable fun ErrorDialog(message: String, onDismiss: () -> Unit) {
    val c = LocalKikoColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surfaceContainerHigh,
        icon = { Icon(Icons.Default.ErrorOutline, null, tint = c.danger) },
        title = { Text("Something went wrong", color = c.ink) },
        text = { Text(message, color = c.muted, fontSize = 13.sp) },
        confirmButton = { TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = c.primary)) { Text("OK") } },
    )
}

// Shown once, right after
// handler). Gives the log
// it out of the
// support server on Discord.
@Composable fun CrashDialog(crashText: String, onDismiss: () -> Unit, onCopy: () -> Unit, onDownload: () -> Unit, onSendDiscord: () -> Unit) {
    val c = LocalKikoColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surfaceContainerHigh,
        icon = { Icon(Icons.Default.WarningAmber, null, tint = c.danger) },
        title = { Text("Kiko crashed last time", color = c.ink) },
        text = {
            Column {
                Text(
                    "Here's what went wrong. Copy it, save it as a file, or send it straight to the support server.",
                    color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 12.dp),
                )
                Box(
                    Modifier
                        .heightIn(max = 240.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(kikoCorner(14.dp)))
                        .background(c.surfaceContainerHighest)
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp),
                ) {
                    Text(crashText, color = c.ink, fontSize = 11.sp, lineHeight = 15.sp, fontFamily = FontFamily.Monospace)
                }
                Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    HomeActionButton(modifier = Modifier.weight(1f), label = "Copy", icon = Icons.Default.ContentCopy, onClick = onCopy)
                    HomeActionButton(modifier = Modifier.weight(1f), label = "Save .txt", icon = Icons.Default.Download, onClick = onDownload)
                    HomeActionButton(modifier = Modifier.weight(1f), label = "Discord", icon = Icons.AutoMirrored.Filled.Send, onClick = onSendDiscord)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = c.primary)) { Text("Dismiss") } },
    )
}

/** One radio row, shared by the settings dialogs: radio on the left, label + optional description. */
@Composable private fun ChoiceRow(selected: Boolean, label: String, description: String?, onClick: () -> Unit) {
    val c = LocalKikoColors.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(kikoCorner(16.dp))).kikoClickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = c.primary, unselectedColor = c.muted))
        Column(Modifier.padding(start = 14.dp)) {
            Text(label, color = c.ink, fontSize = 16.sp)
            if (description != null) Text(description, color = c.muted, fontSize = 13.sp)
        }
    }
}

/** Play Store-style single-choice dialog: rounded card, title, radio rows, Cancel. Picking a row applies it. */
@Composable fun <T> ChoiceDialog(
    title: String, options: List<T>, selected: T,
    label: (T) -> String, description: ((T) -> String)? = null,
    onSelect: (T) -> Unit, onDismiss: () -> Unit,
) {
    val c = LocalKikoColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surfaceContainerHigh,
        shape = RoundedCornerShape(28.dp),
        title = { Text(title, color = c.ink) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { option -> ChoiceRow(option == selected, label(option), description?.invoke(option)) { onSelect(option) } }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = c.primary)) { Text("Cancel") } },
    )
}

@Composable fun ThemeDialog(current: ThemeMode, onDismiss: () -> Unit, onSelect: (ThemeMode) -> Unit) {
    ChoiceDialog(
        title = "Choose theme", options = ThemeMode.entries, selected = current, label = { it.label },
        description = { when (it) { ThemeMode.System -> "Matches your device setting"; ThemeMode.Light -> "Always light"; ThemeMode.Dark -> "Always dark" } },
        onSelect = onSelect, onDismiss = onDismiss,
    )
}

@Composable fun ColorSourceDialog(current: ColorSource, customHex: String, onDismiss: () -> Unit, onSelect: (ColorSource) -> Unit, onCustomHexChange: (String) -> Unit) {
    val c = LocalKikoColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surfaceContainerHigh,
        shape = RoundedCornerShape(28.dp),
        title = { Text("Choose color", color = c.ink) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).animateContentSize()) {
                ColorSource.entries.forEach { source ->
                    ChoiceRow(
                        selected = source == current, label = source.label,
                        description = when (source) { ColorSource.AppDefault -> "Kiko's default indigo"; ColorSource.Dynamic -> "Matches your device wallpaper"; ColorSource.Custom -> "Pick your own color" },
                    ) { onSelect(source); if (source != ColorSource.Custom) onDismiss() }
                }
                // Custom picker only shows while Custom is the active source.
                AnimatedVisibility(visible = current == ColorSource.Custom, enter = fadeIn(tween(180)), exit = fadeOut(tween(140))) {
                    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                        val valid = parseHexColor(customHex) != null
                        val liveColor = parseHexColor(customHex) ?: c.primary
                        // Every drag frame and the final value both feed onCustomHexChange, so the whole app
                        // re-themes live while dragging (setCustomColor debounces the disk write itself).
                        HsvColorPicker(
                            color = liveColor,
                            onColorChange = { picked -> onCustomHexChange(String.format("%06X", 0xFFFFFF and picked.toArgb())) },
                            onColorChangeFinished = { picked -> onCustomHexChange(String.format("%06X", 0xFFFFFF and picked.toArgb())) },
                            modifier = Modifier.padding(bottom = 14.dp),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(22.dp).clip(RoundedCornerShape(kikoCorner(6.dp))).background(if (valid) liveColor else c.surfaceLow).border(1.dp, c.muted.copy(alpha = .4f), RoundedCornerShape(kikoCorner(6.dp))))
                            OutlinedTextField(
                                value = customHex, onValueChange = { onCustomHexChange(it.take(7)) },
                                modifier = Modifier.weight(1f).padding(start = 12.dp),
                                singleLine = true, prefix = { Text("#", color = c.muted) },
                                isError = !valid,
                                supportingText = { if (!valid) Text("6-digit hex, e.g. 2E51A2", color = c.danger, fontSize = 11.sp) },
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.primary, focusedTextColor = c.ink, unfocusedTextColor = c.ink),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = c.primary)) { Text("Done") } },
    )
}

@Composable fun PaletteStyleDialog(current: PaletteStyle, onDismiss: () -> Unit, onSelect: (PaletteStyle) -> Unit) {
    ChoiceDialog(
        title = "Choose color palette", options = PaletteStyle.entries, selected = current, label = { it.label },
        description = {
            when (it) {
                PaletteStyle.TonalSpot -> "Balanced, vivid accent color"
                PaletteStyle.Neutral -> "Softer, more muted colors"
                PaletteStyle.Monochrome -> "Greyscale — the same in every color"
            }
        },
        onSelect = onSelect, onDismiss = onDismiss,
    )
}

@Composable fun TitleLanguageDialog(current: TitleLanguage, onDismiss: () -> Unit, onSelect: (TitleLanguage) -> Unit) {
    ChoiceDialog(
        title = "Title language", options = TitleLanguage.entries, selected = current, label = { it.label },
        description = { when (it) { TitleLanguage.Romaji -> "e.g. Sousou no Frieren"; TitleLanguage.English -> "e.g. Frieren: Beyond Journey's End" } },
        onSelect = onSelect, onDismiss = onDismiss,
    )
}