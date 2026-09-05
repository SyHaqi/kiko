@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.kiko.tracker.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import com.kiko.tracker.data.api.MalProfile
import com.kiko.tracker.ui.theme.LocalKikoColors
import com.kiko.tracker.ui.theme.kikoCircleShape
import com.kiko.tracker.ui.theme.kikoClickable
import com.kiko.tracker.ui.theme.kikoCorner

// Avatar popup menu, opened
// under the avatar rather
// behind it dims, the
// it reads as "lifted"
// pops out from that
// profile stats page; Row
// destinations the old slider
@Composable fun AvatarMenu(
    connected: Boolean, profile: MalProfile?, anchor: Rect?,
    onOpenProfile: () -> Unit, onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = LocalKikoColors.current
    val density = LocalDensity.current
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    // Wait for the fade/scale-out
    LaunchedEffect(visible) { if (!visible) { delay(160); onDismiss() } }

    Dialog(onDismissRequest = { visible = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // This Dialog is its
        // absolute screen (0,0) (system
        // anchor (now in absolute
        // to be translated into
        // for an offset inside
        // what let the redrawn
        var dialogRootOnScreen by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
        BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { dialogRootOnScreen = it.positionOnScreen() }) {
            val screenWidthPx = with(density) { maxWidth.toPx() }
            // Fallback anchor (top-right, roughly
            // case this ever opens
            val rawAnchor = anchor ?: with(density) {
                Rect(screenWidthPx - 20.dp.toPx() - 43.dp.toPx(), 56.dp.toPx(), screenWidthPx - 20.dp.toPx(), 56.dp.toPx() + 43.dp.toPx())
            }
            val a = if (anchor != null) rawAnchor.translate(-dialogRootOnScreen.x, -dialogRootOnScreen.y) else rawAnchor
            val menuTopPad = with(density) { (a.bottom + 10.dp.toPx()).toDp() }
            val menuEndPad = with(density) { (screenWidthPx - a.right).coerceAtLeast(0f).toDp() }.coerceAtLeast(16.dp)

            // Scrim — dims everything
            AnimatedVisibility(visible = visible, enter = fadeIn(tween(180)), exit = fadeOut(tween(140))) {
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = .5f))
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { visible = false },
                )
            }

            // The avatar itself, redrawn
            // the scrim rather than
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(140)),
                modifier = Modifier.offset { IntOffset(a.left.toInt(), a.top.toInt()) },
            ) {
                Avatar(profile?.picture.orEmpty(), profile?.name.orEmpty()) { visible = false }
            }

            // The menu card, popping
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(200)) + scaleIn(tween(200), initialScale = 0.85f, transformOrigin = TransformOrigin(1f, 0f)),
                exit = fadeOut(tween(150)) + scaleOut(tween(150), targetScale = 0.9f, transformOrigin = TransformOrigin(1f, 0f)),
                modifier = Modifier.align(Alignment.TopEnd).padding(top = menuTopPad, end = menuEndPad).widthIn(max = 280.dp),
            ) {
                Column(
                    Modifier.shadow(16.dp, RoundedCornerShape(kikoCorner(24.dp))).clip(RoundedCornerShape(kikoCorner(24.dp))).background(c.surfaceContainerHigh).padding(8.dp),
                ) {
                    // Row 1 — avatar
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(kikoCorner(18.dp))).background(c.surfaceContainerHighest)
                            .kikoClickable { visible = false; onOpenProfile() }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (profile?.picture?.isNotBlank() == true) {
                            AsyncImage(model = profile.picture, contentDescription = profile.name, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.size(43.dp).clip(kikoCircleShape()).background(c.warm))
                        } else {
                            Box(Modifier.size(43.dp).clip(kikoCircleShape()).background(c.warm), contentAlignment = Alignment.Center) {
                                Text(profile?.name?.take(1)?.uppercase()?.ifBlank { "M" } ?: "M", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = c.ink)
                            }
                        }
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(profile?.name?.ifBlank { "MyAnimeList" } ?: (if (connected) "MyAnimeList" else "Not signed in"), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = c.ink, maxLines = 1)
                            Text(if (connected) "View profile & stats" else "Sign in to see your stats", color = c.muted, fontSize = 11.sp, maxLines = 1, modifier = Modifier.padding(top = 1.dp))
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = c.muted, modifier = Modifier.size(18.dp))
                    }

                    Spacer(Modifier.height(6.dp))

                    // Row 2 — Settings,
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(kikoCorner(18.dp))).background(c.surfaceContainerHighest)
                            .kikoClickable { visible = false; onOpenSettings() }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(43.dp).clip(RoundedCornerShape(kikoCorner(14.dp))).background(c.primaryContainer), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Settings, null, tint = c.onPrimaryContainer, modifier = Modifier.size(20.dp))
                        }
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text("Settings", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = c.ink)
                            Text("Appearance, titles, adult content, about", color = c.muted, fontSize = 11.sp, maxLines = 1, modifier = Modifier.padding(top = 1.dp))
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = c.muted, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}