@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.kiko.tracker.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.kiko.tracker.ui.theme.kikoCircleShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiko.tracker.data.api.MalSessionExpired
import com.kiko.tracker.data.api.MalSettingsApi
import com.kiko.tracker.data.api.MalSettingsPage
import com.kiko.tracker.data.api.SettingsControl
import com.kiko.tracker.data.api.SettingsRow
import com.kiko.tracker.data.api.SettingsSection
import com.kiko.tracker.ui.components.KikoErrorState
import com.kiko.tracker.ui.components.KikoHeader
import com.kiko.tracker.ui.components.KikoLoadingIndicator
import com.kiko.tracker.ui.components.kikoFilterChipColors
import com.kiko.tracker.ui.components.headerEdgeStart
import com.kiko.tracker.ui.components.headerTitleStart
import com.kiko.tracker.ui.theme.LocalKikoColors
import com.kiko.tracker.ui.theme.kikoClickable
import com.kiko.tracker.ui.theme.kikoCorner
import com.kiko.tracker.ui.theme.kikoPillShape
import com.kiko.tracker.util.openInBrowser
import kotlinx.coroutines.launch

private data class MalWebLink(val icon: ImageVector, val title: String, val subtitle: String, val url: String)

private val MalWebLinks = listOf(
    MalWebLink(Icons.Rounded.Favorite, "Favorites", "Reorder or remove your favorites", "https://myanimelist.net/editprofile.php?go=favorites"),
    MalWebLink(Icons.Rounded.Block, "Blocked users", "Block or unblock people", "https://myanimelist.net/editprofile.php?go=privacy"),
    MalWebLink(Icons.Rounded.Security, "Account & security", "Password, email, 2FA, username", "https://myanimelist.net/editprofile.php?go=myoptions"),
    MalWebLink(Icons.Rounded.Star, "MAL Supporter", "Membership and payment", "https://myanimelist.net/account/membership"),
)

private fun settingsPageIcon(page: MalSettingsPage): ImageVector = when (page) {
    MalSettingsPage.Profile -> Icons.Rounded.Person
    MalSettingsPage.Notifications -> Icons.Rounded.Notifications
    MalSettingsPage.ForumSignature -> Icons.Rounded.Edit
}

/** "Manage your MAL account": a list of the account-settings pages; the editable ones open as forms in the app. */
@Composable fun MalAccountScreen(avatarUrl: String?, onBack: () -> Unit, onPictureChanged: () -> Unit = {}) {
    val c = LocalKikoColors.current
    val context = LocalContext.current
    var page by remember { mutableStateOf<MalSettingsPage?>(null) }
    var pictureOpen by remember { mutableStateOf(false) }
    var recsOpen by remember { mutableStateOf(false) }
    BackHandler { if (page != null) page = null else if (pictureOpen) pictureOpen = false else if (recsOpen) recsOpen = false else onBack() }
    val open = page
    if (open != null) {
        MalSettingsFormScreen(open, onBack = { page = null })
        return
    }
    if (recsOpen) {
        MalMyRecommendationsScreen(onBack = { recsOpen = false })
        return
    }
    if (pictureOpen) {
        MalPictureScreen(avatarUrl, onBack = { pictureOpen = false }, onChanged = onPictureChanged)
        return
    }
    Column(Modifier.fillMaxSize()) {
        MalAccountHeader("Manage your MAL account", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp).padding(bottom = 28.dp)) {
            Text(
                "Heads up: this section is mostly for changes to your MyAnimeList website account (profile, notifications, forum signature, privacy). Most of these won't change how anything looks or works inside Kiko. Changes are saved to your MAL account.",
                color = c.muted, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(start = 6.dp, end = 6.dp, bottom = 6.dp),
            )
            GroupLabel("Edit here")
            val pages = MalSettingsPage.values().toList()
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                NavRow(Icons.Rounded.Image, "Profile picture", "Change or remove your avatar", forumGroupShape(0, pages.size + 1, 22.dp)) { pictureOpen = true }
                pages.forEachIndexed { i, p ->
                    NavRow(settingsPageIcon(p), p.title, p.subtitle, forumGroupShape(i + 1, pages.size + 1, 22.dp)) { page = p }
                }
            }
            GroupLabel("Your activity")
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                NavRow(Icons.Rounded.ThumbUp, "Your recommendations", "Edit or remove titles you've recommended", forumGroupShape(0, 1, 22.dp)) { recsOpen = true }
            }
            GroupLabel("Open on MyAnimeList")
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                MalWebLinks.forEachIndexed { i, l ->
                    NavRow(l.icon, l.title, l.subtitle, forumGroupShape(i, MalWebLinks.size, 22.dp), trailing = Icons.Rounded.OpenInNew) { context.openInBrowser(l.url) }
                }
            }
        }
    }
}

@Composable internal fun MalAccountHeader(title: String, onBack: () -> Unit) {
    val c = LocalKikoColors.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp).padding(top = KikoHeader.BackTop, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, modifier = Modifier.headerEdgeStart()) { Icon(Icons.Rounded.ArrowBack, "Back", tint = c.ink, modifier = Modifier.size(24.dp)) }
        Text(title, style = MaterialTheme.typography.titleLarge, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).headerTitleStart())
    }
}

@Composable internal fun GroupLabel(text: String) {
    val c = LocalKikoColors.current
    Text(text, color = c.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(start = 6.dp, top = 22.dp, bottom = 10.dp))
}

@Composable internal fun NavRow(
    icon: ImageVector, title: String, subtitle: String, shape: androidx.compose.ui.graphics.Shape,
    trailing: ImageVector = Icons.Rounded.ChevronRight, onClick: () -> Unit,
) {
    val c = LocalKikoColors.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(shape).background(c.surfaceContainerHigh).kikoClickable(scale = 0.98f, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = c.primary, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text(title, fontSize = 16.sp, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, fontSize = 13.sp, color = c.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Icon(trailing, null, tint = c.muted, modifier = Modifier.padding(start = 8.dp).size(20.dp))
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Profile picture
// ---------------------------------------------------------------------------------------------------------------------

private class PickedImage(val bytes: ByteArray, val mime: String, val fileName: String, val preview: Bitmap)

/** Reads the picked photo and prepares it for MAL: jpg / png / gif, shrunk to at most 1024 px (MAL's limit is 2048), EXIF-rotated. */
private fun prepareAvatar(context: Context, uri: Uri): PickedImage? {
    val cr = context.contentResolver
    val type = cr.getType(uri).orEmpty().lowercase()
    val raw = cr.openInputStream(uri)?.use { it.readBytes() } ?: return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / sample > 1024 || bounds.outHeight / sample > 1024) sample *= 2
    var bmp = BitmapFactory.decodeByteArray(raw, 0, raw.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
    val orientation = runCatching {
        android.media.ExifInterface(ByteArrayInputStream(raw)).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL)
    }.getOrDefault(android.media.ExifInterface.ORIENTATION_NORMAL)
    val degrees = when (orientation) {
        android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }
    if (degrees != 0f) bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(degrees) }, true)
    // A GIF keeps its original bytes (re-encoding would drop the animation).
    if (type == "image/gif" && raw.size <= 4 * 1024 * 1024) return PickedImage(raw, "image/gif", "avatar.gif", bmp)
    val png = type == "image/png"
    val out = ByteArrayOutputStream()
    bmp.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 92, out)
    return PickedImage(out.toByteArray(), if (png) "image/png" else "image/jpeg", if (png) "avatar.png" else "avatar.jpg", bmp)
}

@Composable private fun MalPictureScreen(avatarUrl: String?, onBack: () -> Unit, onChanged: () -> Unit) {
    val c = LocalKikoColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var picked by remember { mutableStateOf<PickedImage?>(null) }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    var confirmRemove by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val img = withContext(Dispatchers.IO) { runCatching { prepareAvatar(context, uri) }.getOrNull() }
            if (img == null) Toast.makeText(context, "Couldn't read that image", Toast.LENGTH_SHORT).show() else { picked = img; note = null }
        }
    }
    fun run(block: suspend () -> String, clearPick: Boolean) {
        if (busy) return
        scope.launch {
            busy = true
            runCatching { block() }
                .onSuccess { msg -> note = msg; if (clearPick) picked = null; onChanged() }
                .onFailure {
                    if (it is kotlinx.coroutines.CancellationException) throw it
                    Toast.makeText(context, if (it is MalSessionExpired) "Your MAL login expired. Reconnect from Profile and try again." else it.message ?: "That didn't work", Toast.LENGTH_LONG).show()
                }
            busy = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        MalAccountHeader("Profile picture", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp).padding(bottom = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(12.dp))
            Box(Modifier.size(168.dp).clip(kikoCircleShape()).background(c.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                val p = picked
                when {
                    p != null -> Image(p.preview.asImageBitmap(), "New picture", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    !avatarUrl.isNullOrBlank() -> AsyncImage(avatarUrl, "Current picture", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    else -> Icon(Icons.Rounded.Person, null, tint = c.muted, modifier = Modifier.size(72.dp))
                }
            }
            Text(if (picked != null) "New picture" else "Current picture", color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))

            Spacer(Modifier.height(20.dp))
            if (picked == null) {
                Button(
                    onClick = { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !busy, shape = kikoPillShape(),
                    colors = ButtonDefaults.buttonColors(containerColor = c.primary, contentColor = c.onPrimary),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                ) {
                    Icon(Icons.Rounded.Image, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Choose a photo", fontWeight = FontWeight.Medium)
                }
                FilledTonalButton(
                    onClick = { confirmRemove = true }, enabled = !busy, shape = kikoPillShape(), modifier = Modifier.padding(top = 10.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp),
                ) { Text("Remove picture") }
            } else {
                val p = picked!!
                Button(
                    onClick = { run({ MalSettingsApi(context).uploadPicture(p.bytes, p.mime, p.fileName) }, clearPick = true) }, enabled = !busy, shape = kikoPillShape(),
                    colors = ButtonDefaults.buttonColors(containerColor = c.primary, contentColor = c.onPrimary),
                    contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp),
                ) { if (busy) KikoLoadingIndicator(color = c.onPrimary, size = 18.dp) else Text("Upload", fontWeight = FontWeight.Medium) }
                Row(Modifier.padding(top = 6.dp)) {
                    TextButton(onClick = { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !busy) { Text("Choose another") }
                    TextButton(onClick = { picked = null }, enabled = !busy) { Text("Cancel", color = c.muted) }
                }
            }

            note?.let {
                Text(it, color = c.ink, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 18.dp).fillMaxWidth().clip(RoundedCornerShape(kikoCorner(22.dp))).background(c.surfaceContainerHigh).padding(18.dp))
            }
            Text(
                "JPG, GIF or PNG, up to 2048 × 2048 px (larger photos are shrunk before upload). No NSFW or copyrighted images. MAL can take a few minutes to show a new picture.",
                color = c.muted, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 18.dp, start = 8.dp, end = 8.dp),
            )
        }
    }
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false }, containerColor = c.surfaceContainerHigh,
            title = { Text("Remove your picture?", color = c.ink) },
            text = { Text("Your MAL profile will show MAL's blank image until you upload another.", color = c.muted, fontSize = 14.sp) },
            confirmButton = { TextButton(onClick = { confirmRemove = false; run({ MalSettingsApi(context).removePicture() }, clearPick = false) }) { Text("Remove", color = c.danger) } },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancel", color = c.muted) } },
        )
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// One settings page as an editable form
// ---------------------------------------------------------------------------------------------------------------------

@Composable private fun MalSettingsFormScreen(page: MalSettingsPage, onBack: () -> Unit) {
    val c = LocalKikoColors.current
    val context = LocalContext.current
    var reloadKey by remember(page) { mutableStateOf(0) }
    var loading by remember(page) { mutableStateOf(true) }
    var error by remember(page) { mutableStateOf<String?>(null) }
    var sections by remember(page) { mutableStateOf<List<SettingsSection>>(emptyList()) }
    // control key -> edited value; a control only appears here while it differs from what MAL has.
    val edits = remember(page) { mutableStateMapOf<String, String>() }
    var saving by remember(page) { mutableStateOf(false) }

    LaunchedEffect(page, reloadKey) {
        loading = true; error = null
        runCatching { MalSettingsApi(context).load(page) }
            .onSuccess { sections = it; edits.clear() }
            .onFailure {
                if (it is kotlinx.coroutines.CancellationException) throw it
                error = if (it is MalSessionExpired) "Your MAL login expired. Reconnect from Profile and try again." else it.message ?: "Could not load ${page.title}"
            }
        loading = false
    }

    val scope = rememberCoroutineScope()
    fun save() {
        if (saving || edits.isEmpty()) return
        scope.launch {
            saving = true
            runCatching { MalSettingsApi(context).save(page, edits.toMap()) }
                .onSuccess { msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show(); reloadKey++ }
                .onFailure {
                    if (it is kotlinx.coroutines.CancellationException) throw it
                    val msg = if (it is MalSessionExpired) "Your MAL login expired. Reconnect from Profile and try again." else it.message ?: "Couldn't save"
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
            saving = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        MalAccountHeader(page.title, onBack)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { KikoLoadingIndicator(color = c.primary) }
                error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { KikoErrorState(error!!, onRetry = { reloadKey++ }) }
                else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp).padding(bottom = 110.dp)) {
                    sections.forEach { section ->
                        if (section.title.isNotBlank()) {
                            Text(section.title, color = c.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(start = 6.dp, top = 22.dp, bottom = 10.dp))
                        } else Spacer(Modifier.height(10.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            section.rows.forEachIndexed { i, row ->
                                SettingsRowCard(row, forumGroupShape(i, section.rows.size, 22.dp), edits)
                            }
                        }
                    }
                }
            }
            SaveBar(
                visible = !loading && error == null && edits.isNotEmpty(), saving = saving,
                onDiscard = { edits.clear() }, onSave = { save() },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/** Floating "Discard / Save changes" pill. Its own function so AnimatedVisibility has no Row/Column scope to bind to. */
@Composable private fun SaveBar(visible: Boolean, saving: Boolean, onDiscard: () -> Unit, onSave: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalKikoColors.current
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = modifier,
    ) {
        Row(
            Modifier.padding(bottom = 20.dp).clip(kikoPillShape()).background(c.surfaceContainerHigh).padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onDiscard, enabled = !saving) { Text("Discard", color = c.muted) }
            Spacer(Modifier.width(4.dp))
            Button(
                onClick = onSave, enabled = !saving, shape = kikoPillShape(),
                colors = ButtonDefaults.buttonColors(containerColor = c.primary, contentColor = c.onPrimary),
                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 10.dp),
            ) {
                if (saving) KikoLoadingIndicator(color = c.onPrimary, size = 18.dp) else Text("Save changes", fontWeight = FontWeight.Medium)
            }
        }
    }
}

/** Text fields sit on the row, which is already the tonal surface, so they use the darker page colour to stay visible. */
@Composable internal fun settingsFieldColors(c: com.kiko.tracker.ui.theme.KikoColors) = TextFieldDefaults.colors(
    focusedContainerColor = c.background.copy(alpha = 0.7f), unfocusedContainerColor = c.background.copy(alpha = 0.7f), disabledContainerColor = c.background.copy(alpha = 0.35f),
    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent, unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
    disabledIndicatorColor = androidx.compose.ui.graphics.Color.Transparent, errorIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
    focusedTextColor = c.ink, unfocusedTextColor = c.ink, disabledTextColor = c.muted, cursorColor = c.primary,
    focusedLabelColor = c.primary, unfocusedLabelColor = c.muted, disabledLabelColor = c.muted,
    focusedPlaceholderColor = c.muted, unfocusedPlaceholderColor = c.muted,
)

@Composable private fun SettingsRowCard(row: SettingsRow, shape: androidx.compose.ui.graphics.Shape, edits: MutableMap<String, String>) {
    val c = LocalKikoColors.current
    fun valueOf(ctrl: SettingsControl) = edits[ctrl.key] ?: ctrl.initial
    fun setValue(ctrl: SettingsControl, v: String) { if (v == ctrl.initial) edits.remove(ctrl.key) else edits[ctrl.key] = v }

    val base = Modifier.fillMaxWidth().clip(shape).background(c.surfaceContainerHigh)
    val controls = row.controls
    when {
        controls.isEmpty() -> Text(row.hint, color = c.muted, fontSize = 13.sp, lineHeight = 19.sp, modifier = base.padding(horizontal = 20.dp, vertical = 14.dp))

        // Many switches (list columns): the label on top, then the switches two to a line as small inset tiles.
        controls.size > 2 && controls.all { it is SettingsControl.Toggle } -> Column(base.padding(horizontal = 20.dp, vertical = 14.dp)) {
            if (row.label.isNotBlank()) Text(row.label, color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            if (row.hint.isNotBlank()) Text(row.hint, color = c.muted, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 2.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
                controls.filterIsInstance<SettingsControl.Toggle>().chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { t ->
                            Row(
                                Modifier.weight(1f).clip(RoundedCornerShape(kikoCorner(14.dp))).background(c.background.copy(alpha = 0.6f))
                                    .then(if (t.enabled) Modifier.clickable { setValue(t, if (valueOf(t).isNotEmpty()) "" else t.onValue) } else Modifier)
                                    .padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(t.caption.ifBlank { t.name }, color = c.ink.copy(alpha = if (t.enabled) 1f else 0.5f), fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(end = 4.dp))
                                Switch(
                                    checked = valueOf(t).isNotEmpty(), onCheckedChange = { setValue(t, if (it) t.onValue else "") }, enabled = t.enabled,
                                    modifier = Modifier.scale(0.8f),
                                )
                            }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        // One switch, or a few (Web / Email): label on the left, switches on the right.
        controls.all { it is SettingsControl.Toggle } -> {
            val toggles = controls.filterIsInstance<SettingsControl.Toggle>()
            val single = toggles.size == 1
            Row(
                (if (single && toggles[0].enabled) base.clickable { setValue(toggles[0], if (valueOf(toggles[0]).isNotEmpty()) "" else toggles[0].onValue) } else base)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    val title = row.label.ifBlank { toggles[0].caption }
                    Text(title, color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    if (row.hint.isNotBlank()) Text(row.hint, color = c.muted, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 2.dp))
                }
                toggles.forEach { t ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(start = 6.dp)) {
                        if (!single && t.caption.isNotBlank()) Text(t.caption, color = c.muted, fontSize = 11.sp)
                        Switch(checked = valueOf(t).isNotEmpty(), onCheckedChange = { setValue(t, if (it) t.onValue else "") }, enabled = t.enabled)
                    }
                }
            }
        }

        else -> Column(base.padding(horizontal = 20.dp, vertical = 14.dp)) {
            if (row.label.isNotBlank()) Text(row.label, color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            if (row.hint.isNotBlank()) Text(row.hint, color = c.muted, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 2.dp))
            val onlySelects = controls.all { it is SettingsControl.Choice }
            if (onlySelects) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
                    controls.forEach { ctrl -> ControlView(ctrl, ::valueOf, ::setValue, compact = true) }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 10.dp)) {
                    controls.forEach { ctrl -> ControlView(ctrl, ::valueOf, ::setValue, compact = false) }
                }
            }
        }
    }
}

@Composable private fun ControlView(
    ctrl: SettingsControl, valueOf: (SettingsControl) -> String, setValue: (SettingsControl, String) -> Unit, compact: Boolean,
) {
    val c = LocalKikoColors.current
    val alpha = if (ctrl.enabled) 1f else 0.5f
    when (ctrl) {
        is SettingsControl.Choice -> Row(verticalAlignment = Alignment.CenterVertically) {
            if (ctrl.caption.isNotBlank()) Text(ctrl.caption, color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(end = 8.dp))
            ChoicePill(ctrl, valueOf(ctrl), alpha) { setValue(ctrl, it) }
        }
        is SettingsControl.Radios -> Column {
            if (ctrl.caption.isNotBlank()) Text(ctrl.caption, color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ctrl.options.forEach { o ->
                    FilterChip(
                        selected = valueOf(ctrl) == o.value, onClick = { setValue(ctrl, o.value) }, enabled = ctrl.enabled && o.enabled,
                        label = { Text(o.label) }, colors = kikoFilterChipColors(),
                    )
                }
            }
        }
        is SettingsControl.Toggle -> Row(
            Modifier.fillMaxWidth().then(if (ctrl.enabled) Modifier.clickable { setValue(ctrl, if (valueOf(ctrl).isNotEmpty()) "" else ctrl.onValue) } else Modifier),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(ctrl.caption, color = c.ink.copy(alpha = alpha), fontSize = 14.sp, modifier = Modifier.weight(1f))
            Switch(checked = valueOf(ctrl).isNotEmpty(), onCheckedChange = { setValue(ctrl, if (it) ctrl.onValue else "") }, enabled = ctrl.enabled)
        }
        is SettingsControl.Field -> TextField(
            value = valueOf(ctrl), onValueChange = { setValue(ctrl, it) }, enabled = ctrl.enabled,
            label = if (ctrl.caption.isNotBlank()) ({ Text(ctrl.caption) }) else null,
            minLines = if (ctrl.multiline) 4 else 1, maxLines = if (ctrl.multiline) 12 else 1, singleLine = !ctrl.multiline,
            keyboardOptions = KeyboardOptions(keyboardType = if (ctrl.numeric) KeyboardType.Number else KeyboardType.Text),
            shape = RoundedCornerShape(kikoCorner(16.dp)),
            colors = settingsFieldColors(c),
            modifier = if (compact) Modifier else Modifier.fillMaxWidth(),
        )
    }
}

/** A dropdown showing the current option; opens a list (with a search box when there are many options). */
@Composable private fun ChoicePill(ctrl: SettingsControl.Choice, value: String, alpha: Float, onPick: (String) -> Unit) {
    val c = LocalKikoColors.current
    var open by remember { mutableStateOf(false) }
    val shown = ctrl.options.firstOrNull { it.value == value }?.label ?: value
    Row(
        Modifier.clip(RoundedCornerShape(kikoCorner(14.dp))).background(c.background.copy(alpha = 0.6f))
            .then(if (ctrl.enabled) Modifier.clickable { open = true } else Modifier)
            .padding(start = 14.dp, end = 8.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(shown.ifBlank { "-" }, color = c.ink.copy(alpha = alpha), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 240.dp))
        Icon(Icons.Rounded.KeyboardArrowDown, null, tint = c.muted.copy(alpha = alpha), modifier = Modifier.padding(start = 4.dp).size(20.dp))
    }
    if (open) {
        var query by remember { mutableStateOf("") }
        val many = ctrl.options.size > 14
        val filtered = remember(query, ctrl.options) { if (query.isBlank()) ctrl.options else ctrl.options.filter { it.label.contains(query, ignoreCase = true) } }
        AlertDialog(
            onDismissRequest = { open = false },
            containerColor = c.surfaceContainerHigh,
            title = if (ctrl.caption.isNotBlank()) ({ Text(ctrl.caption, color = c.ink) }) else null,
            text = {
                Column {
                    if (many) {
                        TextField(
                            value = query, onValueChange = { query = it }, singleLine = true, placeholder = { Text("Search") },
                            shape = RoundedCornerShape(kikoCorner(16.dp)), colors = settingsFieldColors(c),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        )
                    }
                    LazyColumn(Modifier.heightIn(max = 380.dp)) {
                        items(filtered, key = { it.value + "\u0000" + it.label }) { o ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(kikoCorner(12.dp))).clickable { onPick(o.value); open = false }.padding(horizontal = 4.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = o.value == value, onClick = { onPick(o.value); open = false })
                                Text(o.label.ifBlank { "-" }, color = c.ink, fontSize = 15.sp, modifier = Modifier.padding(start = 4.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Close", color = c.primary) } },
        )
    }
}
