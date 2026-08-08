@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.kiko.tracker

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

// Right-side profile/settings slider, opened by tapping the avatar in any tab header.
// Row 1 (avatar + name) opens the full profile stats page; Row 2 (Settings) opens the
// app preferences page that used to live on the old Profile tab. Each opens as its own
// full page rather than expanding inline, since the slider itself is fairly narrow.
@Composable fun ProfileDrawer(
    connected: Boolean, profile: MalProfile?,
    onOpenProfile: () -> Unit, onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = LocalKikoColors.current
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    // Wait for the slide-out animation before actually tearing the Dialog down
    LaunchedEffect(visible) { if (!visible) { delay(220); onDismiss() } }

    Dialog(onDismissRequest = { visible = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize()) {
            AnimatedVisibility(visible = visible, enter = fadeIn(tween(220)), exit = fadeOut(tween(200))) {
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = .5f))
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { visible = false },
                )
            }
            AnimatedVisibility(
                visible = visible,
                enter = slideInHorizontally(tween(280)) { it } + fadeIn(tween(220)),
                exit = slideOutHorizontally(tween(240)) { it } + fadeOut(tween(180)),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(0.87f),
            ) {
                Column(Modifier.fillMaxSize().background(c.background).windowInsetsPadding(WindowInsets.systemBars)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.End) {
                        IconButton(onClick = { visible = false }, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(13.dp)).background(c.surfaceLow)) {
                            Icon(Icons.Default.Close, "Close", tint = c.ink)
                        }
                    }
                    Column(Modifier.padding(horizontal = 20.dp)) {
                        // Row 1 — avatar + name, opens the full profile stats page
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.surface)
                                .clickable { visible = false; onOpenProfile() }.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (profile?.picture?.isNotBlank() == true) {
                                AsyncImage(model = profile.picture, contentDescription = profile.name, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.size(52.dp).clip(CircleShape).background(c.warm))
                            } else {
                                Box(Modifier.size(52.dp).clip(CircleShape).background(c.warm), contentAlignment = Alignment.Center) {
                                    Text(profile?.name?.take(1)?.uppercase()?.ifBlank { "M" } ?: "M", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = c.ink)
                                }
                            }
                            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                                Text(profile?.name?.ifBlank { "MyAnimeList" } ?: (if (connected) "MyAnimeList" else "Not signed in"), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = c.ink)
                                Text(if (connected) "View profile & stats" else "Sign in to see your stats", color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                            }
                            Icon(Icons.Default.ChevronRight, null, tint = c.muted)
                        }

                        Spacer(Modifier.height(12.dp))

                        // Row 2 — Settings, opens the full settings page
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.surface)
                                .clickable { visible = false; onOpenSettings() }.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(c.primaryContainer), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Settings, null, tint = c.primary)
                            }
                            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                                Text("Settings", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = c.ink)
                                Text("Appearance, titles, adult content, about", color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                            }
                            Icon(Icons.Default.ChevronRight, null, tint = c.muted)
                        }
                    }
                }
            }
        }
    }
}