@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.kiko.tracker.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.kiko.tracker.data.api.MalProfile
import com.kiko.tracker.ui.theme.LocalKikoColors
import com.kiko.tracker.ui.theme.kikoCircleShape
import com.kiko.tracker.ui.theme.kikoClickable

// Avatar account sheet (Play Store style). A full-page screen in the app's own
// navigation (TopScreen.AccountSheet) rather than a dialog: pages opened from
// it (History, Settings, ...) push on top of it, so backing out of them lands
// right back here with no close/reopen flicker. Back / the X close the sheet.
@Composable fun AccountSheet(
    connected: Boolean, profile: MalProfile?,
    onClose: () -> Unit,
    onOpenProfile: () -> Unit, onOpenSettings: () -> Unit,
    onOpenHistory: (() -> Unit)? = null,
    onOpenFriends: (() -> Unit)? = null,
    onOpenAbout: (() -> Unit)? = null,
    onSignIn: (() -> Unit)? = null,
    updateVersion: String? = null,
    onOpenUpdate: (() -> Unit)? = null,
) {
    val c = LocalKikoColors.current
    BackHandler(onBack = onClose)

    Column(Modifier.fillMaxSize().background(c.background)) {
        // Close button
        Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), contentAlignment = Alignment.CenterEnd) {
            Box(
                Modifier.size(44.dp).clip(kikoCircleShape()).kikoClickable { onClose() },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.Close, "Close", tint = c.ink, modifier = Modifier.size(26.dp)) }
        }

        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Profile card
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(36.dp)).background(c.primaryContainer)
                    .kikoClickable(scale = 0.98f) { if (connected) onOpenProfile() else onSignIn?.invoke() }
                    .padding(horizontal = 18.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (profile?.picture?.isNotBlank() == true) {
                    AsyncImage(model = profile.picture, contentDescription = profile.name, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.size(58.dp).clip(kikoCircleShape()).background(c.warm))
                } else {
                    Box(Modifier.size(58.dp).clip(kikoCircleShape()).background(c.warm), contentAlignment = Alignment.Center) {
                        Text(profile?.name?.take(1)?.uppercase()?.ifBlank { "M" } ?: "M", fontWeight = FontWeight.Bold, fontSize = 24.sp, color = c.ink)
                    }
                }
                Column(Modifier.weight(1f).padding(start = 16.dp)) {
                    Text(
                        profile?.name?.ifBlank { "MyAnimeList" } ?: (if (connected) "MyAnimeList" else "Not signed in"),
                        fontWeight = FontWeight.Medium, fontSize = 22.sp, color = c.onPrimaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (connected) "View profile & stats" else "Sign in to see your stats",
                        color = c.onPrimaryContainer.copy(alpha = 0.75f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Box(Modifier.size(width = 44.dp, height = 44.dp).clip(RoundedCornerShape(50)).background(c.onPrimaryContainer.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.ChevronRight, null, tint = c.onPrimaryContainer, modifier = Modifier.size(24.dp))
                }
            }

            // Stand-alone pill rows
            if (updateVersion != null && onOpenUpdate != null) {
                MenuRow(Icons.Default.SystemUpdate, "Update available", subtitle = "Version $updateVersion", shape = RoundedCornerShape(50), container = c.tertiaryContainer, content = c.onTertiaryContainer, iconTint = c.onTertiaryContainer) { onOpenUpdate() }
            }
            if (!connected && onSignIn != null) {
                MenuRow(Icons.Default.Login, "Sign in to MyAnimeList", shape = RoundedCornerShape(50), container = c.secondaryContainer, content = c.onSecondaryContainer, iconTint = c.onSecondaryContainer) { onSignIn() }
            }

            Spacer(Modifier.height(4.dp))

            // Group 1 — library pages
            val pages = buildList<Triple<ImageVector, String, () -> Unit>> {
                if (onOpenHistory != null) add(Triple(Icons.Default.History, "History", onOpenHistory))
                if (connected && onOpenFriends != null) add(Triple(Icons.Default.People, "Friends & favorites", onOpenFriends))
            }
            if (pages.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    pages.forEachIndexed { i, (icon, label, action) ->
                        MenuRow(icon, label, shape = groupShape(i, pages.size)) { action() }
                    }
                }
            }

            // Group 2 — app
            val app = buildList<Triple<ImageVector, String, () -> Unit>> {
                add(Triple(Icons.Default.Settings, "Settings", onOpenSettings))
                if (onOpenAbout != null) add(Triple(Icons.Default.Info, "About", onOpenAbout))
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                app.forEachIndexed { i, (icon, label, action) ->
                    MenuRow(icon, label, shape = groupShape(i, app.size)) { action() }
                }
            }
        }
    }
}

// Big outer corners, tight inner ones — same grouped look as the Play Store list.
private fun groupShape(index: Int, count: Int): Shape {
    val outer = 28.dp
    val inner = 4.dp
    if (count == 1) return RoundedCornerShape(outer)
    return when (index) {
        0 -> RoundedCornerShape(topStart = outer, topEnd = outer, bottomStart = inner, bottomEnd = inner)
        count - 1 -> RoundedCornerShape(topStart = inner, topEnd = inner, bottomStart = outer, bottomEnd = outer)
        else -> RoundedCornerShape(inner)
    }
}

@Composable private fun MenuRow(
    icon: ImageVector,
    title: String,
    shape: Shape,
    subtitle: String? = null,
    container: Color = LocalKikoColors.current.surfaceContainerHigh,
    content: Color = LocalKikoColors.current.ink,
    iconTint: Color = LocalKikoColors.current.primary,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(shape).background(container)
            .kikoClickable(scale = 0.98f, onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = iconTint, modifier = Modifier.size(26.dp))
        Column(Modifier.weight(1f).padding(start = 18.dp)) {
            Text(title, fontSize = 17.sp, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, fontSize = 13.sp, color = content.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}