@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.kiko.tracker

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import coil.size.Size
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.UUID
import kotlin.math.roundToInt

@Composable fun UpdateDialog(
    info: AppUpdateInfo, downloadProgress: Float?, needsInstallPermission: Boolean, error: String?,
    onDownload: () -> Unit, onOpenInstallSettings: () -> Unit, onSkip: () -> Unit, onDismiss: () -> Unit,
) {
    val c = LocalKikoColors.current
    val downloading = downloadProgress != null
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
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
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(50)), color = c.primary, trackColor = c.surfaceLow)
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

@Composable fun ThemeSheet(current: ThemeMode, onDismiss: () -> Unit, onSelect: (ThemeMode) -> Unit) {
    val c = LocalKikoColors.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.background) {
        Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 28.dp)) {
            Text("Appearance", color = c.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text("Choose a theme", style = MaterialTheme.typography.headlineSmall, color = c.ink, modifier = Modifier.padding(top = 5.dp, bottom = 16.dp))
            ThemeMode.entries.forEach { mode ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(16.dp)).background(if (mode == current) c.primaryContainer else c.surface).clickable { onSelect(mode) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
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
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.background) {
        Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 28.dp)) {
            Text("Appearance", color = c.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text("Choose a color", style = MaterialTheme.typography.headlineSmall, color = c.ink, modifier = Modifier.padding(top = 5.dp, bottom = 16.dp))
            ColorSource.entries.forEach { source ->
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(16.dp)).background(if (source == current) c.primaryContainer else c.surface).animateContentSize()) {
                    Row(
                        Modifier.fillMaxWidth().clickable { onSelect(source); if (source != ColorSource.Custom) onDismiss() }.padding(16.dp),
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
                        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            val valid = parseHexColor(customHex) != null
                            Box(Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).background(if (valid) parseHexColor(customHex)!! else c.surfaceLow).border(1.dp, c.muted.copy(alpha = .4f), RoundedCornerShape(6.dp)))
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

@Composable fun PaletteStyleSheet(current: PaletteStyle, onDismiss: () -> Unit, onSelect: (PaletteStyle) -> Unit) {
    val c = LocalKikoColors.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.background) {
        Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 28.dp)) {
            Text("Appearance", color = c.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text("Choose a color palette", style = MaterialTheme.typography.headlineSmall, color = c.ink, modifier = Modifier.padding(top = 5.dp, bottom = 16.dp))
            PaletteStyle.entries.forEach { style ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(16.dp)).background(if (style == current) c.primaryContainer else c.surface).clickable { onSelect(style) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
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
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.background) {
        Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 28.dp)) {
            Text("Preferences", color = c.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text("Title language", style = MaterialTheme.typography.headlineSmall, color = c.ink, modifier = Modifier.padding(top = 5.dp, bottom = 16.dp))
            TitleLanguage.entries.forEach { lang ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(16.dp)).background(if (lang == current) c.primaryContainer else c.surface).clickable { onSelect(lang) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
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