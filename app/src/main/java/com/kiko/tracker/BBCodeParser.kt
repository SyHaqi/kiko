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

val bbTagRegex = Regex("""\[(/?)([a-zA-Z*]+)(=[^\]]*)?\]""")
// Match img attribute forms

val bbBlockRegex = Regex("""\[(img|list|quote|center)(?:[^\]]*)?\](.*?)\[/\1\]""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))


fun tokenizeBb(raw: String): List<BbToken> {
    val tokens = mutableListOf<BbToken>()
    var last = 0
    for (m in bbTagRegex.findAll(raw)) {
        if (m.range.first > last) tokens += BbToken.Text(raw.substring(last, m.range.first))
        val closing = m.groupValues[1] == "/"
        val name = m.groupValues[2].lowercase()
        val attr = m.groupValues[3].removePrefix("=").ifBlank { null }
        tokens += if (closing) BbToken.Close(name) else BbToken.Open(name, attr)
        last = m.range.last + 1
    }
    if (last < raw.length) tokens += BbToken.Text(raw.substring(last))
    return tokens
}
// Recursive nested tag parsing

fun AnnotatedString.Builder.appendBb(tokens: List<BbToken>, from: Int, stopAt: String?, linkColor: Color): Int {
    var i = from
    while (i < tokens.size) {
        when (val t = tokens[i]) {
            is BbToken.Text -> { append(t.text); i++ }
            is BbToken.Close -> { i++; if (t.name == stopAt) return i }
            is BbToken.Open -> {
                i = when (t.name) {
                    "b" -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { appendBb(tokens, i + 1, "b", linkColor) }
                    "i" -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { appendBb(tokens, i + 1, "i", linkColor) }
                    "u" -> withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) { appendBb(tokens, i + 1, "u", linkColor) }
                    "s", "strike" -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { appendBb(tokens, i + 1, t.name, linkColor) }
                    "url" -> {
                        // URL from tag attribute
                        val href = t.attr ?: (tokens.getOrNull(i + 1) as? BbToken.Text)?.text?.trim()
                        if (href != null) pushStringAnnotation("URL", href)
                        val next = withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) { appendBb(tokens, i + 1, "url", linkColor) }
                        if (href != null) pop()
                        next
                    }
                    else -> appendBb(tokens, i + 1, t.name, linkColor) // unrecognized tag: drop the markup, keep its text
                }
            }
        }
    }
    return i
}

fun inlineAnnotated(raw: String, linkColor: Color): AnnotatedString = buildAnnotatedString { appendBb(tokenizeBb(raw), 0, null, linkColor) }


fun paragraphsFrom(text: String, linkColor: Color, center: Boolean = false): List<ForumBlock> {
    val trimmed = text.trim('\n', '\r')
    if (trimmed.isBlank()) return emptyList()
    return trimmed.split(Regex("\n{2,}")).map { it.trim() }.filter { it.isNotBlank() }.map { ForumBlock.Paragraph(inlineAnnotated(it, linkColor), center) }
}
// Convert stray HTML fragments

val brTagRegex = Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE)

val htmlEntityRegex = Regex("""&(#x[0-9a-fA-F]+|#\d+|[a-zA-Z]+);""")

val namedHtmlEntities = mapOf(
    "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ",
    "darr" to "↓", "uarr" to "↑", "larr" to "←", "rarr" to "→", "harr" to "↔",
    "hellip" to "…", "mdash" to "—", "ndash" to "–", "copy" to "©", "reg" to "®", "trade" to "™",
    "middot" to "·", "bull" to "•", "deg" to "°", "sect" to "§", "para" to "¶",
    "dagger" to "†", "Dagger" to "‡", "spades" to "♠", "clubs" to "♣", "hearts" to "♥", "diams" to "♦",
    // Fix curly-quote entities
    "rsquo" to "\u2019", "lsquo" to "\u2018", "rdquo" to "\u201D", "ldquo" to "\u201C",
)

fun decodeHtmlEntities(text: String): String = htmlEntityRegex.replace(text) { m ->
    val body = m.groupValues[1]
    when {
        body.startsWith("#x", ignoreCase = true) -> body.drop(2).toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: m.value
        body.startsWith("#") -> body.drop(1).toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value
        else -> namedHtmlEntities[body] ?: m.value
    }
}
// Rewrite bare image links

val bareImageLinkRegex = Regex("""\[url\]\s*(https?://\S*?\.(?:png|jpe?g|gif|webp)(?:\?\S*)?|https?://cdn\.myanimelist\.net/s/common/bbcode/\S+?)\s*\[/url\]""", RegexOption.IGNORE_CASE)
// Fix unclosed img tags

val unclosedImgRegex = Regex("""\[img(?:[^\]]*)\]\s*(https?://[^\s\[\]]++)(?!\s*\[/img\])""", RegexOption.IGNORE_CASE)
// Wrap bare image URLs

val bareUrlRegex = Regex(
    """(?<!\[img\])(?<!\[img\][ \t]{0,10})(?:https?://\S*?\.(?:png|jpe?g|gif|webp)(?:\?\S*)?|https?://cdn\.myanimelist\.net/s/common/bbcode/\S+?)(?=\s|$)(?!\[/img\])""",
    setOf(RegexOption.IGNORE_CASE),
)
// Wrap Tenor GIF shares

val bareTenorLinkRegex = Regex(
    """\[url\]\s*(https?://tenor\.com/view/\S*?)\s*\[/url\]|(?<!\[img\]tenor:)(https?://tenor\.com/view/\S+?)(?=\s|\[|$)""",
    RegexOption.IGNORE_CASE,
)

fun normalizeMalMarkup(raw: String): String =
    decodeHtmlEntities(brTagRegex.replace(raw, "\n"))
        .let { bareImageLinkRegex.replace(it) { m -> "[img]${m.groupValues[1]}[/img]" } }
        .let { unclosedImgRegex.replace(it) { m -> "[img]${m.groupValues[1]}[/img]" } }
        .let { bareTenorLinkRegex.replace(it) { m -> "[img]tenor:${m.groupValues[1].ifBlank { m.groupValues[2] }}[/img]" } }
        .let { bareUrlRegex.replace(it) { m -> "[img]${m.value}[/img]" } }


fun parseBBCode(rawIn: String, linkColor: Color): List<ForumBlock> {
    if (rawIn.isBlank()) return emptyList()
    return parseBlocks(normalizeMalMarkup(rawIn), linkColor)
}
// Recurse into center/quote blocks

fun parseBlocks(raw: String, linkColor: Color): List<ForumBlock> {
    val blocks = mutableListOf<ForumBlock>()
    var pos = 0
    for (m in bbBlockRegex.findAll(raw)) {
        if (m.range.first > pos) blocks += paragraphsFrom(raw.substring(pos, m.range.first), linkColor)
        val tag = m.groupValues[1].lowercase()
        val inner = m.groupValues[2]
        when (tag) {
            "img" -> inner.trim().takeIf { it.isNotBlank() }?.let {
                if (it.startsWith("tenor:", ignoreCase = true)) blocks += ForumBlock.ImageBlock(it.substring(6), resolveTenor = true)
                else blocks += ForumBlock.ImageBlock(it)
            }
            "list" -> {
                val items = inner.split(Regex("""\[\*\]""", RegexOption.IGNORE_CASE)).map { it.trim() }.filter { it.isNotBlank() }
                if (items.isNotEmpty()) blocks += ForumBlock.ListBlock(items.map { inlineAnnotated(it, linkColor) }, ordered = m.value.take(20).contains("=1"))
            }
            "quote" -> inner.trim().takeIf { it.isNotBlank() }?.let { blocks += ForumBlock.Quote(parseBlocks(it, linkColor)) }
            // No extra box wrapper
            "center" -> parseBlocks(inner, linkColor).forEach { nested ->
                blocks += if (nested is ForumBlock.Paragraph) nested.copy(center = true) else nested
            }
        }
        pos = m.range.last + 1
    }
    if (pos < raw.length) blocks += paragraphsFrom(raw.substring(pos), linkColor)
    return blocks
}
// Forum image with fallback
