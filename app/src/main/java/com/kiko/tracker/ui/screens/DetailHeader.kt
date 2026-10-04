package com.kiko.tracker.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiko.tracker.ui.components.FavoriteHeartButton
import com.kiko.tracker.ui.components.headerEdgeEnd
import com.kiko.tracker.ui.components.headerEdgeStart
import com.kiko.tracker.ui.theme.LocalKikoColors
import com.kiko.tracker.ui.theme.kikoCorner
import com.kiko.tracker.util.openInBrowser

// Header shared by the Person, Character and Company pages so they match the anime/manga
// DetailScreen: eyebrow label + title, then the cover with one tonal "fact" row per line beside it.

/** One row beside the cover: icon, icon tint, text. */
data class DetailFact(val icon: ImageVector, val tint: Color, val text: String)

/** "1 voice role" / "5 voice roles" — [plural] defaults to singular + "s". */
fun countLabel(n: Int, singular: String, plural: String = singular + "s"): String = "$n ${if (n == 1) singular else plural}"

/**
 * Plain page header (no banner/backdrop): back / favorite / ⋮ buttons in the top row, then the [eyebrow] label
 * and [title] on the normal page background. Tap the title to see it in full when it's cut off (3-line cap),
 * long-press to copy it.
 */
@Composable
fun DetailHeader(
    eyebrow: String,
    title: String,
    shareUrl: String,
    favorited: Boolean,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val c = LocalKikoColors.current
    val context = LocalContext.current
    var showFullTitle by remember(title) { mutableStateOf(false) }
    var titleOverflows by remember(title) { mutableStateOf(false) }
    var moreOpen by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 13.dp, bottom = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, modifier = Modifier.headerEdgeStart()) { Icon(Icons.Default.ArrowBack, "Back", tint = c.ink, modifier = Modifier.size(24.dp)) }
        Spacer(Modifier.weight(1f))
        // Favorite sits right beside the 3-dot menu in the header row.
        FavoriteHeartButton(favorited = favorited, onClick = onToggleFavorite, size = 48.dp, iconSize = 24.dp, background = Color.Transparent, modifier = Modifier.headerEdgeEnd())
        Box(Modifier.headerEdgeEnd()) {
            IconButton(onClick = { moreOpen = true }) { Icon(Icons.Default.MoreVert, "More options", tint = c.ink, modifier = Modifier.size(24.dp)) }
            DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }, shape = RoundedCornerShape(kikoCorner(18.dp)), containerColor = c.surfaceContainer) {
                DropdownMenuItem(
                    text = { Text("Share") },
                    leadingIcon = { Icon(Icons.Default.Share, null) },
                    onClick = {
                        moreOpen = false
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareUrl)
                        }
                        context.startActivity(Intent.createChooser(sendIntent, title))
                    },
                )
                DropdownMenuItem(
                    text = { Text("Open in browser") },
                    leadingIcon = { Icon(Icons.Default.OpenInNew, null) },
                    onClick = { moreOpen = false; context.openInBrowser(shareUrl) },
                )
            }
        }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
        Text(eyebrow, color = c.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.5.sp)
        Text(
            title, color = c.ink, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp,
            maxLines = 3, overflow = TextOverflow.Ellipsis,
            onTextLayout = { titleOverflows = it.hasVisualOverflow },
            modifier = Modifier.padding(top = 4.dp).pointerInput(titleOverflows, title) {
                detectTapGestures(
                    onTap = { if (titleOverflows) showFullTitle = true },
                    onLongPress = { copyTitleToClipboard(context, title) },
                )
            },
        )
    }
    if (showFullTitle) {
        AlertDialog(
            onDismissRequest = { showFullTitle = false },
            containerColor = c.surfaceContainerHigh,
            title = { Text("Full title", color = c.ink) },
            text = { Text(title, color = c.ink, fontSize = 16.sp, lineHeight = 22.sp, modifier = Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = { showFullTitle = false }, colors = ButtonDefaults.textButtonColors(contentColor = c.primary)) { Text("Close") } },
            dismissButton = { TextButton(onClick = { copyTitleToClipboard(context, title) }, colors = ButtonDefaults.textButtonColors(contentColor = c.muted)) { Text("Copy") } },
        )
    }
}

/**
 * Cover on the left, one tonal row per [facts] entry on the right. The rows column is exactly as tall as the
 * cover and the rows split that height evenly. [coverModifier] adds background/click behavior to the cover box;
 * [cover] draws its content.
 */
@Composable
fun DetailCoverRow(
    coverWidth: Dp,
    coverHeight: Dp,
    coverCorner: Dp,
    facts: List<DetailFact>,
    modifier: Modifier = Modifier,
    coverModifier: Modifier = Modifier,
    cover: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(kikoCorner(coverCorner))
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(width = coverWidth, height = coverHeight).shadow(6.dp, shape).clip(shape).then(coverModifier), content = cover)
        Column(Modifier.weight(1f).height(coverHeight), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            facts.forEachIndexed { i, f -> DetailInfoRow(i, facts.size, f.icon, f.tint, f.text) }
        }
    }
}

/** "Details" label/value pairs as the same grouped tonal rows the anime/manga Info tab uses. */
@Composable
fun DetailFieldRows(fields: List<Pair<String, String>>) {
    val c = LocalKikoColors.current
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        fields.forEachIndexed { i, (label, value) ->
            DetailGroupRow(i, fields.size) {
                Text(label, color = c.muted, fontSize = 14.sp)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f).padding(start = 16.dp), horizontalArrangement = Arrangement.End) {
                    Text(value, color = c.ink, fontWeight = FontWeight.Medium, fontSize = 14.sp, textAlign = TextAlign.End, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}