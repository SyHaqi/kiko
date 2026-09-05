@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.kiko.tracker.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.kiko.tracker.data.model.next
import com.kiko.tracker.ui.screens.BbToken
import com.kiko.tracker.ui.screens.ForumBlock

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
                    else -> appendBb(tokens, i + 1, t.name, linkColor) // unrecognized tag: drop the
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

val bareImageLinkRegex = Regex("""\[url\]\s*(https?://\S*?\.(?:png|jpe?g|gif|webp)(?:\?\S*)?|https?://cdn\.myanimelist\.net/s/common/bbcode/\S+?|https?://image\.myanimelist\.net/ui/\S+?)\s*\[/url\]""", RegexOption.IGNORE_CASE)
// Some club/profile descriptions paste
// the next tag with
// which renders as literal
// names. Strip the stray
val strayListMarkerTagRegex = Regex("""\[\*(/?)(url|img|list|quote|center|b|i|u|s|strike)\b""", RegexOption.IGNORE_CASE)
// Fix unclosed img tags

val unclosedImgRegex = Regex("""\[img(?:[^\]]*)\]\s*(https?://[^\s\[\]]++)(?!\s*\[/img\])""", RegexOption.IGNORE_CASE)
// Wrap bare image URLs
// Also covers image.myanimelist.net/ui/<hash> links
// some uploaded images use
// fell through to the
// to-open text instead of
val bareUrlRegex = Regex(
    """(?<!\[img\])(?<!\[img\][ \t]{0,10})(?:https?://\S*?\.(?:png|jpe?g|gif|webp)(?:\?\S*)?|https?://cdn\.myanimelist\.net/s/common/bbcode/\S+?|https?://image\.myanimelist\.net/ui/\S+?)(?=\s|$)(?!\[/img\])""",
    setOf(RegexOption.IGNORE_CASE),
)
// Wrap Tenor GIF shares

val bareTenorLinkRegex = Regex(
    """\[url\]\s*(https?://tenor\.com/view/\S*?)\s*\[/url\]|(?<!\[img\]tenor:)(https?://tenor\.com/view/\S+?)(?=\s|\[|$)""",
    RegexOption.IGNORE_CASE,
)
// Auto-link any remaining plain-text
// with no [url] tag
// of dead text. Runs
// claimed and bracketed anything
// stop this from re-wrapping
// already handled. The trailing-char
// like a period or

val bareGenericUrlRegex = Regex(
    """(?<!\[img\])(?<!\[url\])(?<!\[url=)(?<!tenor:)(https?://[^\s\[\]<>"']+[^\s\[\]<>"'.,!?:;)])""",
    RegexOption.IGNORE_CASE,
)

fun normalizeMalMarkup(raw: String): String =
    decodeHtmlEntities(brTagRegex.replace(raw, "\n"))
        .let { strayListMarkerTagRegex.replace(it) { m -> "[${m.groupValues[1]}${m.groupValues[2]}" } }
        .let { bareImageLinkRegex.replace(it) { m -> "[img]${m.groupValues[1]}[/img]" } }
        .let { unclosedImgRegex.replace(it) { m -> "[img]${m.groupValues[1]}[/img]" } }
        .let { bareTenorLinkRegex.replace(it) { m -> "[img]tenor:${m.groupValues[1].ifBlank { m.groupValues[2] }}[/img]" } }
        .let { bareUrlRegex.replace(it) { m -> "[img]${m.value}[/img]" } }
        .let { bareGenericUrlRegex.replace(it) { m -> "[url]${m.value}[/url]" } }


fun parseBBCode(rawIn: String, linkColor: Color): List<ForumBlock> {
    if (rawIn.isBlank()) return emptyList()
    return parseBlocks(normalizeMalMarkup(rawIn), linkColor)
}
// Old pasted links are
// requests by default (API
// though the same host
// Some older MAL-hosted uploads
// links seen from the
// website's own template silently
// page, so the underlying
// fallback for that and
// cdn.myanimelist.net/..."), and the tap-to-open
// CustomTabs nor the URI
// trace back to this
fun httpsUpgrade(url: String): String = when {
    url.startsWith("https://", ignoreCase = true) -> url
    url.startsWith("http://", ignoreCase = true) -> "https://" + url.substring(7)
    url.startsWith("//") -> "https:$url"
    url.isNotBlank() && !url.contains("://") -> "https://$url"
    else -> url
}
// Recurse into center/quote blocks

// Strip any nested BBCode
// only the plain text
// double-wrap a pasted link
// inside an [img] block
// parseBlocks used that inner
// resulting ForumBlock.ImageBlock.url was literally
// "[url]https://...[/url]", which Coil then
// a fetcher that supports:
// amount of resizing/cropping the
// before the request was
// tokenizer used everywhere else
// wrapping tag (and its
fun stripBbTags(raw: String): String = tokenizeBb(raw).filterIsInstance<BbToken.Text>().joinToString("") { it.text }.trim()

fun parseBlocks(raw: String, linkColor: Color): List<ForumBlock> {
    val blocks = mutableListOf<ForumBlock>()
    var pos = 0
    for (m in bbBlockRegex.findAll(raw)) {
        if (m.range.first > pos) blocks += paragraphsFrom(raw.substring(pos, m.range.first), linkColor)
        val tag = m.groupValues[1].lowercase()
        val inner = m.groupValues[2]
        when (tag) {
            "img" -> inner.trim().takeIf { it.isNotBlank() }?.let { imgInner ->
                val cleaned = stripBbTags(imgInner).ifBlank { imgInner }
                if (cleaned.startsWith("tenor:", ignoreCase = true)) blocks += ForumBlock.ImageBlock(cleaned.substring(6), resolveTenor = true)
                else blocks += ForumBlock.ImageBlock(httpsUpgrade(cleaned))
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