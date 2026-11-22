@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.kiko.tracker.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiko.tracker.data.api.MalMessage
import com.kiko.tracker.data.api.MalMessageDetail
import com.kiko.tracker.data.api.MalSessionExpired
import com.kiko.tracker.data.api.MalSocialApi
import com.kiko.tracker.ui.components.KikoErrorState
import com.kiko.tracker.ui.components.KikoLoadingIndicator
import com.kiko.tracker.ui.theme.LocalKikoColors
import com.kiko.tracker.ui.theme.kikoClickable
import com.kiko.tracker.ui.theme.kikoCorner
import com.kiko.tracker.ui.theme.kikoPillShape
import com.kiko.tracker.util.openInBrowser
import kotlinx.coroutines.launch

private const val MAL_SEND = "https://myanimelist.net/mymessages.php?go=send&toname="

private fun messageError(t: Throwable): String =
    if (t is MalSessionExpired) "Your MAL login expired. Reconnect from Profile and try again." else t.message?.takeIf { it.isNotBlank() } ?: "Couldn't load your messages"

/**
 * MAL private messages. Reading and deleting happen here; MAL protects the Send button with reCAPTCHA, so "New message" and "Reply"
 * open MAL's own compose form in a Custom Tab where the check runs normally.
 */
@Composable fun MalMessagesScreen(onBack: () -> Unit, onUnreadChanged: () -> Unit = {}) {
    val c = LocalKikoColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val api = remember { MalSocialApi(context) }
    var sent by remember { mutableStateOf(false) }
    var items by remember { mutableStateOf<List<MalMessage>>(emptyList()) }
    var total by remember { mutableIntStateOf(0) }
    var hasMore by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var open by remember { mutableStateOf<MalMessage?>(null) }
    var newMessage by remember { mutableStateOf(false) }

    LaunchedEffect(sent, reloadKey) {
        loading = true; error = null
        runCatching { api.messages(sent) }
            .onSuccess { items = it.items; total = it.total; hasMore = it.hasMore }
            .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; error = messageError(it); items = emptyList() }
        loading = false
    }
    BackHandler { if (open != null) open = null else onBack() }

    open?.let { msg ->
        MalMessageDetailScreen(msg, sent = sent, onBack = { open = null }, onChanged = { open = null; reloadKey++; onUnreadChanged() })
        return
    }

    Column(Modifier.fillMaxSize()) {
        MalAccountHeader("Messages", onBack)
        // Inbox / Sent: the same connected button group as the Anime / Manga switch on Profile.
        val boxes = listOf(false to "Inbox", true to "Sent")
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp).padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
            boxes.forEachIndexed { index, (isSent, label) ->
                ToggleButton(
                    checked = sent == isSent, onCheckedChange = { if (sent != isSent) sent = isSent },
                    modifier = Modifier.weight(1f).height(40.dp).semantics { role = Role.RadioButton },
                    shapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        boxes.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                    colors = ToggleButtonDefaults.toggleButtonColors(containerColor = c.surfaceContainer, contentColor = c.onSurfaceVariant, checkedContainerColor = c.primary, checkedContentColor = c.onPrimary),
                ) { Text(label, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
            }
        }
        Box(Modifier.weight(1f)) {
            when {
                loading && items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { KikoLoadingIndicator(color = c.primary, size = 36.dp) }
                error != null -> KikoErrorState(error!!, onRetry = { reloadKey++ })
                items.isEmpty() -> Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.MailOutline, null, tint = c.muted, modifier = Modifier.size(44.dp))
                    Text(if (sent) "No sent messages" else "Your inbox is empty", color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 12.dp))
                    Text("Tap New message to write one on MyAnimeList.", color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                }
                else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp).padding(top = 14.dp, bottom = 96.dp)) {
                    Text("$total ${if (total == 1) "message" else "messages"}", color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp, bottom = 8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        items.forEachIndexed { i, m -> MessageRow(m, sent, forumGroupShape(i, items.size, 22.dp)) { open = m } }
                    }
                    if (hasMore) {
                        FilledTonalButton(
                            onClick = {
                                if (loadingMore) return@FilledTonalButton
                                loadingMore = true
                                scope.launch {
                                    runCatching { api.messages(sent, items.size) }
                                        .onSuccess { items = items + it.items; hasMore = it.hasMore }
                                        .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; Toast.makeText(context, messageError(it), Toast.LENGTH_SHORT).show() }
                                    loadingMore = false
                                }
                            },
                            shape = kikoPillShape(), modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 14.dp),
                        ) { if (loadingMore) KikoLoadingIndicator(color = c.primary, size = 18.dp) else Text("Load more") }
                    }
                }
            }
            ExtendedFloatingActionButton(
                onClick = { newMessage = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                containerColor = c.primary, contentColor = c.onPrimary, shape = RoundedCornerShape(kikoCorner(20.dp)),
            ) { Icon(Icons.Rounded.Edit, null, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("New message", fontWeight = FontWeight.Medium) }
        }
    }

    if (newMessage) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { newMessage = false }, containerColor = c.surfaceContainerHigh,
            title = { Text("New message", color = c.ink) },
            text = {
                Column {
                    Text("Who is it for? MAL's own form opens next, where you write it and send.", color = c.muted, fontSize = 14.sp)
                    TextField(value = name, onValueChange = { name = it.trim() }, singleLine = true, placeholder = { Text("MAL username") }, shape = RoundedCornerShape(kikoCorner(14.dp)), colors = settingsFieldColors(c), modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
                }
            },
            confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { newMessage = false; context.openInBrowser(MAL_SEND + java.net.URLEncoder.encode(name, "UTF-8")) }) { Text("Continue") } },
            dismissButton = { TextButton(onClick = { newMessage = false }) { Text("Cancel", color = c.muted) } },
        )
    }
}

@Composable private fun MessageRow(m: MalMessage, sent: Boolean, shape: androidx.compose.ui.graphics.Shape, onClick: () -> Unit) {
    val c = LocalKikoColors.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 72.dp).clip(shape).background(c.surfaceContainerHigh).kikoClickable(scale = 0.98f, onClick = onClick).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(50)).background(if (m.unread && !sent) c.primary else androidx.compose.ui.graphics.Color.Transparent))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (sent) "To ${m.user}" else m.user, color = c.ink, fontSize = 15.sp, fontWeight = if (m.unread && !sent) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Text("  ·  ${m.date}", color = c.muted, fontSize = 12.sp, maxLines = 1)
            }
            if (m.subject.isNotBlank()) Text(m.subject, color = c.ink, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (m.unread && !sent) FontWeight.Medium else FontWeight.Normal)
            if (m.preview.isNotBlank()) Text(m.preview, color = c.muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable private fun MalMessageDetailScreen(msg: MalMessage, sent: Boolean, onBack: () -> Unit, onChanged: () -> Unit) {
    val c = LocalKikoColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val api = remember { MalSocialApi(context) }
    var detail by remember { mutableStateOf<MalMessageDetail?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var confirmDelete by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var readMarked by remember { mutableStateOf(false) }
    LaunchedEffect(msg.readUrl, reloadKey) {
        error = null
        runCatching { api.readMessage(msg.readUrl) }
            .onSuccess { detail = it; if (msg.unread && !sent && !readMarked) { readMarked = true } }
            .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; error = messageError(it) }
    }
    // Opening a message marks it read on MAL, so the avatar-menu badge needs a refresh when the user leaves.
    val leave = { if (readMarked) onChanged() else onBack() }
    BackHandler(onBack = leave)
    Column(Modifier.fillMaxSize()) {
        MalAccountHeader(if (sent) "Sent message" else "Message", leave)
        val d = detail
        when {
            error != null && d == null -> KikoErrorState(error!!, onRetry = { reloadKey++ })
            d == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { KikoLoadingIndicator(color = c.primary, size = 36.dp) }
            else -> Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp).padding(top = 6.dp, bottom = 28.dp)) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(kikoCorner(24.dp))).background(c.surfaceContainerHigh).padding(20.dp)) {
                    Text(if (sent) "To ${msg.user}" else d.user.ifBlank { msg.user }, color = c.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    if (d.subject.isNotBlank()) Text(d.subject, color = c.ink, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp, modifier = Modifier.padding(top = 6.dp))
                    Text(d.date.ifBlank { msg.date }, color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                    ForumBody(d.body, modifier = Modifier.padding(top = 16.dp), background = c.surfaceContainerHigh)
                }
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                    if (d.canDelete) TextButton(onClick = { confirmDelete = true }, enabled = !busy) { Icon(Icons.Rounded.DeleteOutline, null, tint = c.danger, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Delete", color = c.danger) }
                    if (!sent && d.replyUrl.isNotBlank()) Button(
                        onClick = { context.openInBrowser(d.replyUrl) }, shape = kikoPillShape(),
                        colors = ButtonDefaults.buttonColors(containerColor = c.primary, contentColor = c.onPrimary), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                    ) { Icon(Icons.Rounded.Reply, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Reply", fontWeight = FontWeight.Medium) }
                }
                if (!sent) Text("Replies open MyAnimeList's own form, where its spam check runs.", color = c.muted, fontSize = 12.sp, modifier = Modifier.align(Alignment.End).padding(top = 8.dp, end = 4.dp))
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false }, containerColor = c.surfaceContainerHigh,
            title = { Text("Delete this message?", color = c.ink) },
            text = { Text("It will be removed from your MyAnimeList ${if (sent) "sent box" else "inbox"}.", color = c.muted, fontSize = 14.sp) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false; busy = true
                    scope.launch {
                        runCatching { api.deleteMessage(msg.readUrl) }
                            .onSuccess { busy = false; Toast.makeText(context, "Message deleted", Toast.LENGTH_SHORT).show(); onChanged() }
                            .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; busy = false; Toast.makeText(context, messageError(it), Toast.LENGTH_LONG).show() }
                    }
                }) { Text("Delete", color = c.danger) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel", color = c.muted) } },
        )
    }
}
