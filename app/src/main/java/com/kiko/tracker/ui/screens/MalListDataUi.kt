@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.kiko.tracker.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiko.tracker.data.api.MalListDataApi
import com.kiko.tracker.data.api.MalSessionExpired
import com.kiko.tracker.ui.components.KikoLoadingIndicator
import com.kiko.tracker.ui.theme.LocalKikoColors
import com.kiko.tracker.ui.theme.kikoCorner
import kotlinx.coroutines.launch

private fun listDataError(t: Throwable): String =
    if (t is MalSessionExpired) "Your MAL login expired. Reconnect from Profile and try again." else t.message?.takeIf { it.isNotBlank() } ?: "Something went wrong talking to MyAnimeList"

/** Export your anime / manga list to an XML file, or import one (yours, or another site's MAL-format export) back into MyAnimeList. */
@Composable fun MalListDataScreen(onBack: () -> Unit, onListChanged: () -> Unit) {
    val c = LocalKikoColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val api = remember { MalListDataApi(context) }
    var busy by remember { mutableStateOf<String?>(null) }
    var updateExisting by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var pendingExport by remember { mutableStateOf<ByteArray?>(null) }
    var pendingImport by remember { mutableStateOf<Pair<String, ByteArray>?>(null) }
    BackHandler(onBack = onBack)

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/xml")) { uri ->
        val bytes = pendingExport; pendingExport = null
        if (uri == null || bytes == null) return@rememberLauncherForActivityResult
        runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("Couldn't open that file for writing") }
            .onSuccess { result = true to "Saved your list to the file you chose." }
            .onFailure { result = false to (it.message ?: "Couldn't save the file") }
    }
    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val name = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null } ?: "list.xml"
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Couldn't read that file")
            if (bytes.size > 20_000_000) error("That file is too large to be a list export")
            name to bytes
        }.onSuccess { pendingImport = it }.onFailure { result = false to (it.message ?: "Couldn't read the file") }
    }

    fun export(manga: Boolean) {
        if (busy != null) return
        busy = if (manga) "Exporting your manga list…" else "Exporting your anime list…"; result = null
        scope.launch {
            runCatching { api.export(manga) }
                .onSuccess { pendingExport = it; saveLauncher.launch(if (manga) "mangalist.xml" else "animelist.xml") }
                .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; result = false to listDataError(it) }
            busy = null
        }
    }

    Column(Modifier.fillMaxSize()) {
        MalAccountHeader("Import & export", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp).padding(bottom = 28.dp)) {
            Text("Back up your list as an XML file, or bring one in. It's MyAnimeList's own format, so it also works with other tracking sites.", color = c.muted, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(horizontal = 6.dp))
            GroupLabel("Export")
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                NavRow(Icons.Rounded.Download, "Anime list", "Save as an XML file", forumGroupShape(0, 2)) { export(false) }
                NavRow(Icons.Rounded.Download, "Manga list", "Save as an XML file", forumGroupShape(1, 2)) { export(true) }
            }
            GroupLabel("Import")
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    Modifier.fillMaxWidth().clip(forumGroupShape(0, 2)).background(c.surfaceContainerHigh).padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Update existing entries", color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text("Off: only titles that aren't on your list yet are added", color = c.muted, fontSize = 12.sp, lineHeight = 17.sp)
                    }
                    Switch(checked = updateExisting, onCheckedChange = { updateExisting = it })
                }
                NavRow(Icons.Rounded.Upload, "Choose a file", "An XML export from MyAnimeList", forumGroupShape(1, 2)) { if (busy == null) pickLauncher.launch(arrayOf("text/xml", "application/xml", "application/gzip", "application/x-gzip", "application/octet-stream")) }
            }
            busy?.let { msg ->
                Row(Modifier.fillMaxWidth().padding(top = 22.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    KikoLoadingIndicator(color = c.primary, size = 20.dp)
                    Text(msg, color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(start = 10.dp))
                }
            }
            result?.let { (ok, msg) ->
                Row(
                    Modifier.fillMaxWidth().padding(top = 22.dp).clip(RoundedCornerShape(kikoCorner(28.dp))).background(if (ok) c.primaryContainer else c.surfaceContainerHigh).padding(18.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline, null, tint = if (ok) c.onPrimaryContainer else c.danger, modifier = Modifier.size(22.dp))
                    Text(msg, color = if (ok) c.onPrimaryContainer else c.ink, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(start = 12.dp))
                }
            }
        }
    }

    pendingImport?.let { (name, bytes) ->
        AlertDialog(
            onDismissRequest = { pendingImport = null }, containerColor = c.surfaceContainerHigh,
            title = { Text("Import this file?", color = c.ink) },
            text = { Text("$name will be added to your MyAnimeList. " + if (updateExisting) "Titles already on your list will be overwritten with the file's values." else "Titles already on your list are left alone.", color = c.muted, fontSize = 14.sp) },
            confirmButton = {
                TextButton(onClick = {
                    pendingImport = null; busy = "Importing…"; result = null
                    scope.launch {
                        runCatching { api.import(bytes, updateExisting) }
                            .onSuccess { result = true to it; onListChanged() }
                            .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; result = false to listDataError(it) }
                        busy = null
                    }
                }) { Text("Import") }
            },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("Cancel", color = c.muted) } },
        )
    }
}
