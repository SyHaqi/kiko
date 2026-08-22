@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.kiko.tracker

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

// Themed replacement for the bare vm.error banner — every error in the app funnels
// through here so it always reads as a proper Kiko dialog instead of an ad-hoc toast
// or a stray line of red text.
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

// Shown once, right after a crash-and-relaunch (see MainActivity's uncaught-exception
// handler). Gives the log itself in a scrollable monospace panel, plus three ways to get
// it out of the phone: onto the clipboard, saved as a .txt, or straight into the Kiko
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

@Composable fun ThemeSheet(current: ThemeMode, onDismiss: () -> Unit, onSelect: (ThemeMode) -> Unit) {
    val c = LocalKikoColors.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 28.dp)) {
            Text("Appearance", color = c.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text("Choose a theme", style = MaterialTheme.typography.headlineSmall, color = c.ink, modifier = Modifier.padding(top = 5.dp, bottom = 16.dp))
            ThemeMode.entries.forEach { mode ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(kikoCorner(16.dp))).background(if (mode == current) c.primaryContainer else Color.Transparent).kikoClickable { onSelect(mode) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(mode.label, fontWeight = FontWeight.Bold, color = c.ink)
                        Text(when (mode) { ThemeMode.System -> "Matches your device setting"; ThemeMode.Light -> "Always light"; ThemeMode.Dark -> "Always dark" }, color = c.muted, fontSize = 12.sp)
                    }
                    if (mode == current) Icon(Icons.Default.Check, null, tint = c.primary)
                }
            }
        }
    }
}

@Composable fun ColorSourceSheet(current: ColorSource, customHex: String, onDismiss: () -> Unit, onSelect: (ColorSource) -> Unit, onCustomHexChange: (String) -> Unit) {
    val c = LocalKikoColors.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 28.dp)) {
            Text("Appearance", color = c.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text("Choose a color", style = MaterialTheme.typography.headlineSmall, color = c.ink, modifier = Modifier.padding(top = 5.dp, bottom = 16.dp))
            ColorSource.entries.forEach { source ->
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(kikoCorner(16.dp))).background(if (source == current) c.primaryContainer else Color.Transparent).animateContentSize()) {
                    Row(
                        Modifier.fillMaxWidth().kikoClickable { onSelect(source); if (source != ColorSource.Custom) onDismiss() }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(source.label, fontWeight = FontWeight.Bold, color = c.ink)
                            Text(
                                when (source) { ColorSource.AppDefault -> "Kiko's default indigo"; ColorSource.Dynamic -> "Matches your device wallpaper"; ColorSource.Custom -> "Pick your own hex color" },
                                color = c.muted, fontSize = 12.sp,
                            )
                        }
                        if (source == current) Icon(Icons.Default.Check, null, tint = c.primary)
                    }
                    // Expand only Custom row — basic fade/size transition, matching the app's other reveals
                    AnimatedVisibility(visible = source == ColorSource.Custom && current == ColorSource.Custom, enter = fadeIn(tween(180)), exit = fadeOut(tween(140))) {
                        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                            val valid = parseHexColor(customHex) != null
                            val liveColor = parseHexColor(customHex) ?: c.primary

                            HsvColorPicker(
                                color = liveColor,
                                // Both the in-progress drag and the final position feed straight
                                // into onCustomHexChange (-> vm.customColorHex -> the
                                // remember(..., vm.customColorHex, ...) that builds the app's whole
                                // color palette in Navigation.kt), so the entire app's theme --
                                // not just this sheet's own swatch/thumb -- updates live as you
                                // drag, instead of only once the finger lifts. This is safe to do
                                // per-frame because Compose's snapshot system coalesces multiple
                                // state writes within the same frame into a single recomposition,
                                // and setCustomColor already debounces the actual disk write
                                // separately, so we're not adding any new I/O pressure -- only the
                                // in-memory theme rebuild now tracks the drag live.
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
            }
        }
    }
}

@Composable fun PaletteStyleSheet(current: PaletteStyle, onDismiss: () -> Unit, onSelect: (PaletteStyle) -> Unit) {
    val c = LocalKikoColors.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 28.dp)) {
            Text("Appearance", color = c.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text("Choose a color palette", style = MaterialTheme.typography.headlineSmall, color = c.ink, modifier = Modifier.padding(top = 5.dp, bottom = 16.dp))
            PaletteStyle.entries.forEach { style ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(kikoCorner(16.dp))).background(if (style == current) c.primaryContainer else Color.Transparent).kikoClickable { onSelect(style) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(style.label, fontWeight = FontWeight.Bold, color = c.ink)
                        Text(
                            when (style) {
                                PaletteStyle.TonalSpot -> "Balanced, vivid accent color"
                                PaletteStyle.Neutral -> "Softer, more muted colors"
                                PaletteStyle.Monochrome -> "Greyscale — the same in every color"
                            },
                            color = c.muted, fontSize = 12.sp,
                        )
                    }
                    if (style == current) Icon(Icons.Default.Check, null, tint = c.primary)
                }
            }
        }
    }
}

@Composable fun TitleLanguageSheet(current: TitleLanguage, onDismiss: () -> Unit, onSelect: (TitleLanguage) -> Unit) {
    val c = LocalKikoColors.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 28.dp)) {
            Text("Preferences", color = c.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text("Title language", style = MaterialTheme.typography.headlineSmall, color = c.ink, modifier = Modifier.padding(top = 5.dp, bottom = 16.dp))
            TitleLanguage.entries.forEach { lang ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(kikoCorner(16.dp))).background(if (lang == current) c.primaryContainer else Color.Transparent).kikoClickable { onSelect(lang) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(lang.label, fontWeight = FontWeight.Bold, color = c.ink)
                        Text(when (lang) { TitleLanguage.Romaji -> "e.g. Sousou no Frieren"; TitleLanguage.English -> "e.g. Frieren: Beyond Journey's End" }, color = c.muted, fontSize = 12.sp)
                    }
                    if (lang == current) Icon(Icons.Default.Check, null, tint = c.primary)
                }
            }
        }
    }
}