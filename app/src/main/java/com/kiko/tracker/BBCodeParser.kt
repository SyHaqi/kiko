@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.kiko.tracker

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

val bareImageLinkRegex = Regex("""\[url\]\s*(https?://\S*?\.(?:png|jpe?g|gif|webp)(?:\?\S*)?|https?://cdn\.myanimelist\.net/s/common/bbcode/\S+?|https?://image\.myanimelist\.net/ui/\S+?)\s*\[/url\]""", RegexOption.IGNORE_CASE)
// Some club/profile descriptions paste a [*] list-item marker directly against
// the next tag with no space (e.g. "[*url=...]...[*IMG]...[*/IMG][*/url]"),
// which renders as literal bracket text since "*url"/"*img" aren't real tag
// names. Strip the stray asterisk so the underlying url/img tags parse normally.
val strayListMarkerTagRegex = Regex("""\[\*(/?)(url|img|list|quote|center|b|i|u|s|strike)\b""", RegexOption.IGNORE_CASE)
// Fix unclosed img tags

val unclosedImgRegex = Regex("""\[img(?:[^\]]*)\]\s*(https?://[^\s\[\]]++)(?!\s*\[/img\])""", RegexOption.IGNORE_CASE)
// Wrap bare image URLs
// Also covers image.myanimelist.net/ui/<hash> links (stickers/reactions and
// some uploaded images use this extensionless CDN path), which previously
// fell through to the generic link-wrapper below and rendered as plain tap-
// to-open text instead of an inline image.
val bareUrlRegex = Regex(
    """(?<!\[img\])(?<!\[img\][ \t]{0,10})(?:https?://\S*?\.(?:png|jpe?g|gif|webp)(?:\?\S*)?|https?://cdn\.myanimelist\.net/s/common/bbcode/\S+?|https?://image\.myanimelist\.net/ui/\S+?)(?=\s|$)(?!\[/img\])""",
    setOf(RegexOption.IGNORE_CASE),
)
// Wrap Tenor GIF shares

val bareTenorLinkRegex = Regex(
    """\[url\]\s*(https?://tenor\.com/view/\S*?)\s*\[/url\]|(?<!\[img\]tenor:)(https?://tenor\.com/view/\S+?)(?=\s|\[|$)""",
    RegexOption.IGNORE_CASE,
)
// Auto-link any remaining plain-text URL (one MAL posters paste directly,
// with no [url] tag at all) so it renders as a tappable hyperlink instead
// of dead text. Runs last, after the image/tenor passes above have already
// claimed and bracketed anything that belongs to them — the lookbehinds
// stop this from re-wrapping URLs those passes (or an explicit [url] tag)
// already handled. The trailing-char exclusion keeps sentence punctuation
// like a period or closing paren out of the link.

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
// Old pasted links are often plain http:// — Android blocks cleartext network
// requests by default (API 28+) so those images silently fail to load even
// though the same host serves the same image fine over https.
// Some older MAL-hosted uploads (the .../uploaded_files/<timestamp>-<hash>.jpeg style
// links seen from the news-submission tool) are stored with no scheme at all — the
// website's own template silently prepends a plain "http://" only when rendering the
// page, so the underlying data is just "cdn.myanimelist.net/...". Coil has no implicit
// fallback for that and rejects it outright ("Unable to create a fetcher that supports:
// cdn.myanimelist.net/..."), and the tap-to-open handler fails identically since neither
// CustomTabs nor the URI handler can resolve a relative link either — both symptoms
// trace back to this same missing scheme, not two separate bugs.
fun httpsUpgrade(url: String): String = when {
    url.startsWith("https://", ignoreCase = true) -> url
    url.startsWith("http://", ignoreCase = true) -> "https://" + url.substring(7)
    url.startsWith("//") -> "https:$url"
    url.isNotBlank() && !url.contains("://") -> "https://$url"
    else -> url
}
// Recurse into center/quote blocks

// Strip any nested BBCode tags from an [img]...[/img] tag's inner content, keeping
// only the plain text runs. MAL's own news-image tool has been observed to
// double-wrap a pasted link — [img][url]https://...[/url][/img] — so the "url"
// inside an [img] block isn't always a bare link. Previously the img handler in
// parseBlocks used that inner content as-is, so for a doubly-wrapped post the
// resulting ForumBlock.ImageBlock.url was literally the string
// "[url]https://...[/url]", which Coil then rejected outright ("Unable to create
// a fetcher that supports: [url]https://...") since it isn't a URL at all — no
// amount of resizing/cropping the request fixes a URL string that was broken
// before the request was ever built. Running the inner content through the same
// tokenizer used everywhere else and keeping only the Text tokens discards any
// wrapping tag (and its attributes) and leaves the bare link behind.
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