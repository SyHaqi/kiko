package com.kiko.tracker.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiko.tracker.BuildConfig
import com.kiko.tracker.ui.components.KikoErrorState
import com.kiko.tracker.ui.components.KikoLoadingIndicator
import com.kiko.tracker.ui.components.MarkdownText
import com.kiko.tracker.ui.theme.LocalKikoColors
import com.kiko.tracker.ui.theme.kikoClickable
import com.kiko.tracker.ui.theme.kikoCorner
import com.kiko.tracker.ui.theme.kikoPillShape
import com.kiko.tracker.util.AppUpdateChecker
import com.kiko.tracker.util.AppUpdateInfo
import com.kiko.tracker.util.ReleaseNote
import com.kiko.tracker.util.openInBrowser

// Releases + scroll position survive leaving and re-entering the page, like the other screens.
private object ChangelogCache {
    var releases: List<ReleaseNote> = emptyList()
    var index = 0
    var offset = 0
}

private fun String.plainVersion() = removePrefix("v").removePrefix("V").substringBefore("-")

/**
 * "What's new": every GitHub release with its notes rendered as markdown. When a newer build exists, the update
 * card on top carries the download / install controls (progress, install-permission and error states included).
 */
@Composable fun ChangelogScreen(
    updateInfo: AppUpdateInfo?,
    downloadProgress: Float?,
    needsInstallPermission: Boolean,
    error: String?,
    onDownload: () -> Unit,
    onOpenInstallSettings: () -> Unit,
    onSkip: () -> Unit,
    onBack: () -> Unit,
) {
    val c = LocalKikoColors.current
    val context = LocalContext.current
    BackHandler(onBack = onBack)
    var releases by remember { mutableStateOf(ChangelogCache.releases) }
    var loading by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    val checker = remember { AppUpdateChecker(context.applicationContext) }
    val listState = remember { LazyListState(ChangelogCache.index, ChangelogCache.offset) }
    DisposableEffect(Unit) {
        onDispose { ChangelogCache.index = listState.firstVisibleItemIndex; ChangelogCache.offset = listState.firstVisibleItemScrollOffset }
    }
    LaunchedEffect(reload) {
        if (ChangelogCache.releases.isNotEmpty() && reload == 0) return@LaunchedEffect
        loading = true; loadError = null
        checker.fetchReleases()
            .onSuccess { releases = it; ChangelogCache.releases = it }
            .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; loadError = it.message ?: "Couldn't load the changelog" }
        loading = false
    }
    val installed = BuildConfig.VERSION_NAME.plainVersion()
    // If the list couldn't load but we still know about the pending update, show at least that release's notes.
    val shown = if (releases.isEmpty() && updateInfo != null && updateInfo.notes.isNotBlank())
        listOf(ReleaseNote(updateInfo.version, "", updateInfo.notes, "", updateInfo.htmlUrl, false)) else releases

    Column(Modifier.fillMaxSize()) {
        MalAccountHeader("What's new", onBack, subtitle = if (updateInfo != null) "Update ${updateInfo.version} available" else "You're on v${BuildConfig.VERSION_NAME}")
        when {
            loading && shown.isEmpty() && updateInfo == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { KikoLoadingIndicator(color = c.primary, size = 36.dp) }
            loadError != null && shown.isEmpty() && updateInfo == null -> KikoErrorState(loadError!!, onRetry = { reload++ })
            else -> LazyColumn(
                Modifier.fillMaxSize(), state = listState,
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (updateInfo != null) item(key = "update") {
                    UpdateCard(updateInfo, downloadProgress, needsInstallPermission, error, onDownload, onOpenInstallSettings, onSkip)
                }
                if (loading && shown.isEmpty()) item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) { KikoLoadingIndicator(color = c.primary, size = 28.dp) }
                }
                if (loadError != null && releases.isEmpty()) item(key = "loadError") {
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Couldn't load the full changelog", color = c.muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = { reload++ }, colors = ButtonDefaults.textButtonColors(contentColor = c.primary)) { Text("Retry") }
                    }
                }
                itemsIndexed(shown, key = { _, r -> r.tag }) { i, r ->
                    ReleaseCard(
                        r, latest = i == 0,
                        isNew = r.tag.plainVersion() != installed && checker.isStillNewer(r.tag),
                        isInstalled = r.tag.plainVersion() == installed,
                        startExpanded = i < 2 || checker.isStillNewer(r.tag),
                        onOpenGithub = { if (r.htmlUrl.isNotBlank()) context.openInBrowser(r.htmlUrl) },
                    )
                }
            }
        }
    }
}

@Composable private fun UpdateCard(
    info: AppUpdateInfo, progress: Float?, needsPermission: Boolean, error: String?,
    onDownload: () -> Unit, onOpenInstallSettings: () -> Unit, onSkip: () -> Unit,
) {
    val c = LocalKikoColors.current
    val downloading = progress != null
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(kikoCorner(28.dp))).background(c.primaryContainer).padding(18.dp)) {
        Text("Kiko ${info.version} is available", color = c.onPrimaryContainer, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        val size = if (info.apkSizeBytes > 0) " · %.1f MB".format(info.apkSizeBytes / 1_048_576f) else ""
        Text("You're on v${BuildConfig.VERSION_NAME}$size", color = c.onPrimaryContainer.copy(alpha = 0.75f), fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
        if (needsPermission) {
            Text("Kiko needs permission to install updates. Allow it from Settings, then try again.", color = c.danger, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
        } else if (error != null) {
            Text(error, color = c.danger, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
        }
        if (downloading) {
            val p = progress ?: 0f
            Text("Downloading update… ${(p * 100).toInt()}%", color = c.onPrimaryContainer, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
            LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth().clip(kikoPillShape()), color = c.primary, trackColor = c.background.copy(alpha = 0.5f))
        } else {
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.clip(kikoPillShape()).background(c.primary).kikoClickable { if (needsPermission) onOpenInstallSettings() else onDownload() }
                        .padding(horizontal = 22.dp, vertical = 11.dp),
                ) { Text(if (needsPermission) "Open settings" else "Update now", color = c.onPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                TextButton(onClick = onSkip, colors = ButtonDefaults.textButtonColors(contentColor = c.onPrimaryContainer)) { Text("Skip this version") }
            }
        }
    }
}

@Composable private fun ReleaseCard(
    r: ReleaseNote, latest: Boolean, isNew: Boolean, isInstalled: Boolean, startExpanded: Boolean, onOpenGithub: () -> Unit,
) {
    val c = LocalKikoColors.current
    var expanded by rememberSaveable(r.tag) { mutableStateOf(startExpanded) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(kikoCorner(28.dp))).background(c.surfaceContainerHigh)) {
        Row(
            Modifier.fillMaxWidth().kikoClickable(scale = 0.99f) { expanded = !expanded }.padding(start = 18.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(r.tag, color = c.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    when {
                        isNew -> Tag("New", c.primary, c.onPrimary)
                        isInstalled -> Tag("Installed", c.primaryContainer, c.onPrimaryContainer)
                        latest -> Tag("Latest", c.primaryContainer, c.onPrimaryContainer)
                    }
                    if (r.prerelease) Tag("Pre-release", c.surfaceContainerHighest, c.muted)
                }
                val sub = listOf(r.title, r.publishedAt).filter { it.isNotBlank() }.joinToString(" · ")
                if (sub.isNotBlank()) Text(sub, color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = c.muted, modifier = Modifier.size(24.dp))
        }
        if (expanded) {
            Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 12.dp)) {
                if (r.body.isBlank()) Text("No release notes provided.", color = c.muted, fontSize = 13.sp)
                else MarkdownText(r.body)
                if (r.htmlUrl.isNotBlank()) TextButton(
                    onClick = onOpenGithub, modifier = Modifier.padding(top = 4.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = c.primary),
                ) { Text("View on GitHub") }
            }
        }
    }
}

@Composable private fun Tag(text: String, bg: androidx.compose.ui.graphics.Color, fg: androidx.compose.ui.graphics.Color) {
    Box(Modifier.clip(kikoPillShape()).background(bg).padding(horizontal = 8.dp, vertical = 2.dp)) {
        Text(text, color = fg, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}
