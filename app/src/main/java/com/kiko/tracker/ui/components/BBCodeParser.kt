package com.kiko.tracker.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import com.kiko.tracker.ui.screens.BbToken
import com.kiko.tracker.ui.screens.ForumBlock
import com.kiko.tracker.ui.screens.ForumFactRow

// ---------------------------------------------------------------------------
// BBCode -> ForumBlock pipeline
//
//   raw text --normalizeMalMarkup--> cleaned text --tokenizeBb--> tokens
//            --buildTree--> node tree --BbWalker--> List<ForumBlock>
//
// The old implementation matched block tags with a non-greedy regex, which
// broke on nested [quote]s / [list]s, silently swallowed any "[word]" that
// looked like a tag (e.g. "[sic]", "[WIP]"), and let one badly nested tag
// bleed its style over the rest of the post. This one builds a real tree:
//   - only KNOWN tag names are treated as markup, everything else stays text;
//   - a closing tag closes the nearest matching open tag (and anything left
//     open inside it), stray closing tags are ignored, unclosed tags are
//     closed at the end of the post;
//   - [code] content is taken literally.
// ---------------------------------------------------------------------------

// Tag names that count as markup. Anything else in square brackets is plain text.
private val KnownBbTags = setOf(
    "b", "i", "u", "s", "strike", "del", "url", "img", "list", "quote", "spoiler",
    "center", "left", "right", "align", "color", "size", "font", "sup", "sub",
    "yt", "youtube", "code", "pre", "hr", "*",
)

// Tags allowed to carry space-separated attributes ("[img width=100]", "[quote=Name message=1]").
private val SpaceAttrTags = setOf("img", "quote", "spoiler", "yt", "youtube")

// Tags whose body is taken literally (no nested markup).
private val RawTags = setOf("code", "pre")

// separator ("=" or whitespace) + attribute text
val bbTagRegex = Regex("""\[(/?)([a-zA-Z*]+)(?:([=\s])([^\]]*))?\]""")

// yt/youtube: MAL's YouTube embed tag ([yt]VIDEO_ID[/yt]) — becomes a tappable thumbnail block.

// Pulls the 11-char YouTube video id out of whatever ended up between [yt]...[/yt]: MAL's own
// bare id ("qOyrumtlOGA", optionally with trailing "&feature=..." junk), or a full
// watch / youtu.be / embed / shorts URL (what the scraped <iframe src> gives us; playlist embeds
// like embed/videoseries?list=... have no single video id, so they're deliberately not matched). By the time
// this runs, normalizeMalMarkup may have wrapped a URL in [url]..[/url], so strip tags first.
private val youTubeUrlIdRegex = Regex(
    """(?:youtube(?:-nocookie)?\.com/(?:watch\?(?:[^#\s]*&)?v=|embed/(?!videoseries)|v/|shorts/|live/)|youtu\.be/)([A-Za-z0-9_-]{11})""",
    RegexOption.IGNORE_CASE,
)
private val youTubeBareIdRegex = Regex("""^([A-Za-z0-9_-]{11})(?:[&?#].*)?$""")

fun youTubeIdFrom(raw: String): String? {
    val s = stripBbTags(raw).trim()
    youTubeUrlIdRegex.find(s)?.let { return it.groupValues[1] }
    return youTubeBareIdRegex.find(s)?.groupValues?.get(1)
}

// ---------------------------------------------------------------------------
// Tokenizer
// ---------------------------------------------------------------------------

fun tokenizeBb(raw: String): List<BbToken> {
    val tokens = mutableListOf<BbToken>()
    var cursor = 0   // start of text not yet emitted
    var searchFrom = 0
    while (true) {
        val m = bbTagRegex.find(raw, searchFrom) ?: break
        val closing = m.groupValues[1] == "/"
        val name = m.groupValues[2].lowercase()
        val sep = m.groupValues[3]
        val attrRaw = m.groupValues[4]
        val validName = name in KnownBbTags
        // "[i love this]" is a sentence, not an italic tag: whitespace attributes are only
        // allowed on the few tags that really use them.
        val validAttr = closing || sep.isEmpty() || sep == "=" || name in SpaceAttrTags
        if (!validName || !validAttr) { searchFrom = m.range.first + 1; continue }

        if (m.range.first > cursor) tokens += BbToken.Text(raw.substring(cursor, m.range.first))
        if (closing) {
            tokens += BbToken.Close(name)
            cursor = m.range.last + 1
            searchFrom = cursor
            continue
        }
        val attr = attrRaw.trim().ifBlank { null }
        tokens += BbToken.Open(name, attr)
        cursor = m.range.last + 1
        searchFrom = cursor
        if (name in RawTags) {
            val closeTag = "[/$name]"
            val end = raw.indexOf(closeTag, cursor, ignoreCase = true)
            val inner = if (end >= 0) raw.substring(cursor, end) else raw.substring(cursor)
            if (inner.isNotEmpty()) tokens += BbToken.Text(inner)
            if (end >= 0) {
                tokens += BbToken.Close(name)
                cursor = end + closeTag.length
            } else {
                cursor = raw.length
            }
            searchFrom = cursor
        }
    }
    if (cursor < raw.length) tokens += BbToken.Text(raw.substring(cursor))
    return tokens
}

// Plain text of a string with every tag removed (used for [img]/[yt] bodies and previews).
fun stripBbTags(raw: String): String =
    tokenizeBb(raw).filterIsInstance<BbToken.Text>().joinToString("") { it.text }.trim()

// ---------------------------------------------------------------------------
// Tree
// ---------------------------------------------------------------------------

private sealed class BbNode {
    class Text(val text: String) : BbNode()
    class Tag(val name: String, val attr: String?) : BbNode() { val children = ArrayList<BbNode>() }
}

private val LeafTags = setOf("hr", "*")

private fun canonicalTag(name: String): String = when (name) {
    "strike", "del" -> "s"
    "youtube" -> "yt"
    "pre" -> "code"
    else -> name
}

private fun buildTree(tokens: List<BbToken>): List<BbNode> {
    val root = BbNode.Tag("", null)
    val stack = ArrayList<BbNode.Tag>()
    stack += root
    for (t in tokens) {
        when (t) {
            is BbToken.Text -> stack.last().children += BbNode.Text(t.text)
            is BbToken.Open -> {
                val node = BbNode.Tag(canonicalTag(t.name), t.attr)
                stack.last().children += node
                // Depth guard so a pathological post can't overflow the stack while rendering.
                if (node.name !in LeafTags && stack.size < 40) stack += node
            }
            is BbToken.Close -> {
                val name = canonicalTag(t.name)
                val idx = stack.indexOfLast { it.name == name }
                if (idx > 0) while (stack.size > idx) stack.removeAt(stack.size - 1)
            }
        }
    }
    return root.children
}

private fun plainText(nodes: List<BbNode>): String {
    val sb = StringBuilder()
    fun go(list: List<BbNode>) {
        for (n in list) when (n) {
            is BbNode.Text -> sb.append(n.text)
            is BbNode.Tag -> go(n.children)
        }
    }
    go(nodes)
    return sb.toString()
}

// ---------------------------------------------------------------------------
// Style helpers
// ---------------------------------------------------------------------------

private data class BbStyle(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strike: Boolean = false,
    val color: Color? = null,
    val scale: Float = 1f,
    val href: String? = null,
    val mono: Boolean = false,
    val align: TextAlign = TextAlign.Start,
)

private class BbRun(val text: String, val style: BbStyle)

private val NamedColors: Map<String, Long> = mapOf(
    "red" to 0xFFFF0000, "darkred" to 0xFF8B0000, "crimson" to 0xFFDC143C, "orange" to 0xFFFFA500,
    "darkorange" to 0xFFFF8C00, "gold" to 0xFFFFD700, "yellow" to 0xFFFFFF00, "olive" to 0xFF808000,
    "lime" to 0xFF00FF00, "green" to 0xFF008000, "darkgreen" to 0xFF006400, "teal" to 0xFF008080,
    "cyan" to 0xFF00FFFF, "aqua" to 0xFF00FFFF, "blue" to 0xFF0000FF, "darkblue" to 0xFF00008B,
    "navy" to 0xFF000080, "skyblue" to 0xFF87CEEB, "indigo" to 0xFF4B0082, "purple" to 0xFF800080,
    "violet" to 0xFFEE82EE, "magenta" to 0xFFFF00FF, "pink" to 0xFFFFC0CB, "hotpink" to 0xFFFF69B4,
    "brown" to 0xFFA52A2A, "maroon" to 0xFF800000, "gray" to 0xFF808080, "grey" to 0xFF808080,
    "silver" to 0xFFC0C0C0, "black" to 0xFF000000, "white" to 0xFFFFFFFF,
)

internal fun parseBbColor(raw: String?): Color? {
    val s = raw?.trim()?.trim('"', '\'')?.lowercase().orEmpty()
    if (s.isEmpty()) return null
    NamedColors[s]?.let { return Color(it) }
    val hex = s.removePrefix("#")
    if (hex.isEmpty() || !hex.all { it in "0123456789abcdef" }) return null
    return when (hex.length) {
        3 -> Color(0xFF000000 or hex.map { "$it$it" }.joinToString("").toLong(16))
        6 -> Color(0xFF000000 or hex.toLong(16))
        else -> null
    }
}

private fun contrastRatio(a: Color, b: Color): Float {
    val l1 = a.luminance()
    val l2 = b.luminance()
    val hi = maxOf(l1, l2)
    val lo = minOf(l1, l2)
    return (hi + 0.05f) / (lo + 0.05f)
}

// [size=150] is a percentage; old-school [size=1..7] maps onto the HTML font-size steps.
internal fun parseBbScale(raw: String?): Float? {
    val s = raw?.trim()?.trim('"', '\'')?.removeSuffix("%")?.trim() ?: return null
    val v = s.toFloatOrNull() ?: return null
    val scale = when {
        v > 7f -> v / 100f
        v >= 7f -> 2.4f
        v >= 6f -> 1.9f
        v >= 5f -> 1.5f
        v >= 4f -> 1.2f
        v >= 3f -> 1f
        v >= 2f -> 0.85f
        v >= 1f -> 0.7f
        else -> return null
    }
    return scale.coerceIn(0.4f, 2.5f)
}

private fun alignFrom(tag: String, attr: String?): TextAlign? = when (tag) {
    "center" -> TextAlign.Center
    "right" -> TextAlign.End
    "left" -> TextAlign.Start
    "align" -> when (attr?.trim()?.trim('"', '\'')?.lowercase()) {
        "center", "centre", "middle" -> TextAlign.Center
        "right", "end" -> TextAlign.End
        "left", "start" -> TextAlign.Start
        "justify" -> TextAlign.Justify
        else -> null
    }
    else -> null
}

// href from [url=...] / [url]...[/url]; null when it doesn't look like a link at all.
private fun linkHref(raw: String?): String? {
    val s = raw?.trim()?.trim('"', '\'')?.trim().orEmpty()
    if (s.isEmpty() || s.any { it.isWhitespace() }) return null
    return when {
        s.startsWith("mailto:", ignoreCase = true) -> s
        s.startsWith("/") && !s.startsWith("//") -> "https://myanimelist.net$s"
        else -> httpsUpgrade(s)
    }
}

// "Name message=12345" -> "Name"
private val QuoteMessageAttr = Regex("""\s*\bmessage\s*=\s*\S+""", RegexOption.IGNORE_CASE)
private fun quoteAuthor(attr: String?): String =
    attr?.replace(QuoteMessageAttr, "")?.trim()?.trim('"', '\'')?.trim().orEmpty()

private fun imgWidthHint(attr: String?): Int? {
    val s = attr?.lowercase() ?: return null
    Regex("""width\s*=\s*"?(\d{1,4})""").find(s)?.let { return it.groupValues[1].toIntOrNull() }
    Regex("""^\s*(\d{1,4})(?:x\d{1,4})?\s*$""").find(s)?.let { return it.groupValues[1].toIntOrNull() }
    return null
}

private val ParagraphBreak = Regex("""\n(?:[ \t]*\n)+""")

// Trimmed ranges of each blank-line-separated paragraph in `s`.
private fun splitParagraphRanges(s: String): List<IntRange> {
    val out = ArrayList<IntRange>()
    fun add(from: Int, to: Int) {
        var lo = from
        var hi = to
        while (lo < hi && (s[lo].isWhitespace() || s[lo] == ' ')) lo++
        while (hi > lo && (s[hi - 1].isWhitespace() || s[hi - 1] == ' ')) hi--
        if (lo < hi) out += lo until hi
    }
    var start = 0
    for (m in ParagraphBreak.findAll(s)) {
        add(start, m.range.first)
        start = m.range.last + 1
    }
    add(start, s.length)
    return out
}

// ---------------------------------------------------------------------------
// Tree -> blocks
// ---------------------------------------------------------------------------

private class BbWalker(private val linkColor: Color, private val background: Color) {

    fun blocksFrom(nodes: List<BbNode>, style: BbStyle = BbStyle()): List<ForumBlock> {
        val out = ArrayList<ForumBlock>()
        val runs = ArrayList<BbRun>()
        walk(nodes, style, out, runs)
        flush(out, runs)
        return out
    }

    private fun flush(out: MutableList<ForumBlock>, runs: MutableList<BbRun>) {
        if (runs.isEmpty()) return
        val visible = runs.filter { it.text.isNotBlank() }
        if (visible.isEmpty()) { runs.clear(); return }
        // Where each run landed in the combined text, so every paragraph takes its own size/alignment
        // from the runs inside it (one heading must not enlarge the paragraphs around it).
        val runRanges = ArrayList<IntRange>(runs.size)
        val annotated = buildAnnotatedString {
            for (r in runs) {
                val st = r.style
                val href = st.href
                val start = length
                val decorations = ArrayList<TextDecoration>(2)
                // Links are colour-only: underlining every name/title made dense posts look noisy.
                if (st.underline) decorations += TextDecoration.Underline
                if (st.strike) decorations += TextDecoration.LineThrough
                val span = SpanStyle(
                    color = if (href != null) linkColor else (st.color ?: Color.Unspecified),
                    fontWeight = if (st.bold) FontWeight.Bold else null,
                    fontStyle = if (st.italic) FontStyle.Italic else null,
                    textDecoration = if (decorations.isEmpty()) null else TextDecoration.combine(decorations),
                    fontSize = if (st.scale != 1f) st.scale.em else TextUnit.Unspecified,
                    fontFamily = if (st.mono) FontFamily.Monospace else null,
                )
                withStyle(span) { append(r.text) }
                if (href != null) addStringAnnotation("URL", href, start, length)
                runRanges += start until length
            }
        }
        fun stylesIn(from: Int, toInclusive: Int): List<BbStyle> = runs.indices
            .filter { i -> runs[i].text.isNotBlank() && runRanges[i].first <= toInclusive && runRanges[i].last >= from }
            .map { runs[it].style }

        for (range in splitParagraphRanges(annotated.text)) {
            val styles = stylesIn(range.first, range.last)
            val align = styles.firstOrNull()?.align ?: TextAlign.Start
            val scale = styles.maxOfOrNull { it.scale } ?: 1f
            val paraText = annotated.text.substring(range.first, range.last + 1)
            val whole = annotated.subSequence(range.first, range.last + 1)

            // Centred / enlarged text is deliberate layout by the author: leave it exactly as written.
            if (align != TextAlign.Start || scale > 1.05f) {
                out += ForumBlock.Paragraph(whole, align = align, scale = scale)
                continue
            }

            // A short, fully bold line on its own is a section heading.
            val singleLine = '\n' !in paraText
            if (singleLine && paraText.length <= 40 && paraText.last() !in ".!?,;" &&
                styles.isNotEmpty() && styles.all { it.bold && it.href == null }
            ) {
                out += ForumBlock.Heading(whole)
                continue
            }

            // Recreate MAL's structure: "Label: value" groups, titled sections, short-line lists.
            for (seg in segmentLines(paraText)) {
                val base = range.first
                fun slice(r: IntRange) = annotated.subSequence(base + r.first, base + r.last + 1)
                when (seg) {
                    is LineSegment.Prose -> out += ForumBlock.Paragraph(slice(seg.range), align = align, scale = scale)
                    is LineSegment.Lines -> out += ForumBlock.LineList(seg.items.map(::slice))
                    is LineSegment.Facts -> out += ForumBlock.FactList(
                        title = seg.title?.let { paraText.substring(it.first, it.last + 1) },
                        rows = seg.rows.map { ForumFactRow(label = paraText.substring(it.label.first, it.label.last + 1), value = slice(it.value)) },
                    )
                }
            }
        }
        runs.clear()
    }

    private fun walk(nodes: List<BbNode>, st: BbStyle, out: MutableList<ForumBlock>, runs: MutableList<BbRun>) {
        for (node in nodes) {
            when (node) {
                is BbNode.Text -> {
                    val t = node.text.replace("\r\n", "\n").replace('\r', '\n')
                    if (t.isNotEmpty()) runs += BbRun(t, st)
                }
                is BbNode.Tag -> walkTag(node, st, out, runs)
            }
        }
    }

    private fun walkTag(node: BbNode.Tag, st: BbStyle, out: MutableList<ForumBlock>, runs: MutableList<BbRun>) {
        when (node.name) {
            "b" -> walk(node.children, st.copy(bold = true), out, runs)
            "i" -> walk(node.children, st.copy(italic = true), out, runs)
            "u" -> walk(node.children, st.copy(underline = true), out, runs)
            "s" -> walk(node.children, st.copy(strike = true), out, runs)
            "color" -> {
                val parsed = parseBbColor(node.attr)
                // Drop colours that would be unreadable on the card (black text in dark theme etc.).
                val usable = parsed?.takeIf { background == Color.Unspecified || contrastRatio(it, background) >= 2.2f }
                walk(node.children, if (usable != null) st.copy(color = usable) else st, out, runs)
            }
            "size" -> {
                val scale = parseBbScale(node.attr)
                walk(node.children, if (scale != null) st.copy(scale = scale) else st, out, runs)
            }
            "url" -> {
                val href = linkHref(node.attr ?: plainText(node.children))
                walk(node.children, if (href != null) st.copy(href = href) else st, out, runs)
            }
            "font", "sup", "sub" -> walk(node.children, st, out, runs)
            "center", "left", "right", "align" -> {
                val a = alignFrom(node.name, node.attr)
                flush(out, runs)
                walk(node.children, if (a != null) st.copy(align = a) else st, out, runs)
                flush(out, runs)
            }
            "img" -> {
                flush(out, runs)
                val inner = plainText(node.children).trim()
                if (inner.isNotBlank()) {
                    if (inner.startsWith("tenor:", ignoreCase = true)) out += ForumBlock.ImageBlock(inner.substring(6), resolveTenor = true)
                    else out += ForumBlock.ImageBlock(httpsUpgrade(inner), widthDp = imgWidthHint(node.attr))
                }
            }
            "yt" -> {
                flush(out, runs)
                val inner = plainText(node.children)
                val id = youTubeIdFrom(inner)
                if (id != null) out += ForumBlock.VideoBlock(id)
                else if (inner.isNotBlank()) runs += BbRun(inner.trim(), st)
            }
            "hr" -> {
                flush(out, runs)
                out += ForumBlock.Rule
            }
            "code" -> {
                flush(out, runs)
                val code = plainText(node.children).trim('\n', '\r')
                if (code.isNotBlank()) out += ForumBlock.Code(code)
            }
            "quote" -> {
                flush(out, runs)
                val inner = blocksFrom(node.children, st)
                if (inner.isNotEmpty()) out += ForumBlock.Quote(inner, quoteAuthor(node.attr))
            }
            "spoiler" -> {
                flush(out, runs)
                val inner = blocksFrom(node.children, st)
                if (inner.isNotEmpty()) out += ForumBlock.Spoiler(node.attr?.trim('"', '\'')?.trim().orEmpty(), inner)
            }
            "list" -> {
                flush(out, runs)
                val items = ArrayList<List<ForumBlock>>()
                var current = ArrayList<BbNode>()
                fun closeItem() {
                    if (current.isNotEmpty()) {
                        val blocks = blocksFrom(current, st)
                        if (blocks.isNotEmpty()) items += blocks
                        current = ArrayList()
                    }
                }
                for (child in node.children) {
                    if (child is BbNode.Tag && child.name == "*") closeItem() else current += child
                }
                closeItem()
                if (items.isNotEmpty()) {
                    val kind = node.attr?.trim('"', '\'')?.trim().orEmpty()
                    out += ForumBlock.ListBlock(items, ordered = kind.isNotEmpty(), kind = kind.ifEmpty { "1" })
                }
            }
            // "[*]" outside a [list]: keep it readable instead of dropping it.
            "*" -> runs += BbRun("\n• ", st)
            else -> walk(node.children, st, out, runs)
        }
    }
}

// ---------------------------------------------------------------------------
// HTML leftovers / MAL quirks (applied before tokenizing)
// ---------------------------------------------------------------------------

// Convert stray HTML fragments

val brTagRegex = Regex("""<br\s*/?>[ \t]*(?:\r?\n)?""", RegexOption.IGNORE_CASE)

val htmlEntityRegex = Regex("""&(#x[0-9a-fA-F]+|#\d+|[a-zA-Z]+);""")

val namedHtmlEntities = mapOf(
    "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ",
    "darr" to "↓", "uarr" to "↑", "larr" to "←", "rarr" to "→", "harr" to "↔",
    "hellip" to "…", "mdash" to "—", "ndash" to "–", "copy" to "©", "reg" to "®", "trade" to "™",
    "middot" to "·", "bull" to "•", "deg" to "°", "sect" to "§", "para" to "¶",
    "dagger" to "†", "Dagger" to "‡", "spades" to "♠", "clubs" to "♣", "hearts" to "♥", "diams" to "♦",
    // Curly-quote entities
    "rsquo" to "’", "lsquo" to "‘", "rdquo" to "”", "ldquo" to "“",
)

fun decodeHtmlEntities(text: String): String = htmlEntityRegex.replace(text) { m ->
    val body = m.groupValues[1]
    when {
        body.startsWith("#x", ignoreCase = true) -> body.drop(2).toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: m.value
        body.startsWith("#") -> body.drop(1).toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value
        else -> namedHtmlEntities[body] ?: m.value
    }
}

// "[url]https://x/y.png[/url]" and similar bare image links become real images.
val bareImageLinkRegex = Regex("""\[url\]\s*(https?://\S*?\.(?:png|jpe?g|gif|webp)(?:\?\S*)?|https?://cdn\.myanimelist\.net/s/common/bbcode/\S+?|https?://image\.myanimelist\.net/ui/\S+?)\s*\[/url\]""", RegexOption.IGNORE_CASE)

// Some club/profile descriptions paste list items as "[*[url]" / "[*[b]" (the closing bracket of
// the marker is missing) which would render as literal text; drop the stray "*".
val strayListMarkerTagRegex = Regex("""\[\*(/?)(url|img|list|quote|center|b|i|u|s|strike)\b""", RegexOption.IGNORE_CASE)

// "[img]https://..." with no closing tag.
val unclosedImgRegex = Regex("""\[img(?:[^\]]*)\]\s*(https?://[^\s\[\]]++)(?!\s*\[/img\])""", RegexOption.IGNORE_CASE)

// Wrap bare image URLs (also image.myanimelist.net/ui/<hash> links, which have no extension).
// The (?<!=) keeps it from touching "[url=https://x/y.png]" attributes.
private val bareImageUrlRegex = Regex(
    """(?<!\[img\])(?<!\[img\][ \t]{0,10})(?<!=)(?:https?://\S*?\.(?:png|jpe?g|gif|webp)(?:\?\S*)?|https?://cdn\.myanimelist\.net/s/common/bbcode/\S+?|https?://image\.myanimelist\.net/ui/\S+?)(?=\s|$)(?!\[/img\])""",
    setOf(RegexOption.IGNORE_CASE),
)

// Tenor GIF shares become "[img]tenor:<url>[/img]" and are resolved to the real GIF at render time.
val bareTenorLinkRegex = Regex(
    """\[url\]\s*(https?://tenor\.com/view/\S*?)\s*\[/url\]|(?<!\[img\]tenor:)(https?://tenor\.com/view/\S+?)(?=\s|\[|$)""",
    RegexOption.IGNORE_CASE,
)

// Auto-link any remaining plain-text URL that has no [url] tag. Runs last so the image/tenor
// rules above have already claimed what they wanted; the final character class keeps trailing
// punctuation (a period, comma, bracket) out of the link.
val bareGenericUrlRegex = Regex(
    """(?<!\[img\])(?<!\[url\])(?<!\[url=)(?<!=)(?<!tenor:)(https?://[^\s\[\]<>"']+[^\s\[\]<>"'.,!?:;)])""",
    RegexOption.IGNORE_CASE,
)

private val codeSegmentRegex = Regex("""\[(code|pre)\].*?\[/\1\]""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

private fun normalizeOutsideCode(raw: String): String =
    decodeHtmlEntities(brTagRegex.replace(raw, "\n"))
        .let { strayListMarkerTagRegex.replace(it) { m -> "[${m.groupValues[1]}${m.groupValues[2]}" } }
        .let { bareImageLinkRegex.replace(it) { m -> "[img]${m.groupValues[1]}[/img]" } }
        .let { unclosedImgRegex.replace(it) { m -> "[img]${m.groupValues[1]}[/img]" } }
        .let { bareTenorLinkRegex.replace(it) { m -> "[img]tenor:${m.groupValues[1].ifBlank { m.groupValues[2] }}[/img]" } }
        .let { bareImageUrlRegex.replace(it) { m -> "[img]${m.value}[/img]" } }
        .let { bareGenericUrlRegex.replace(it) { m -> "[url]${m.value}[/url]" } }

// Runs the clean-up above on everything except [code] blocks, whose content must stay literal.
fun normalizeMalMarkup(raw: String): String {
    if (!raw.contains("[code]", ignoreCase = true) && !raw.contains("[pre]", ignoreCase = true)) return normalizeOutsideCode(raw)
    val sb = StringBuilder()
    var pos = 0
    for (m in codeSegmentRegex.findAll(raw)) {
        sb.append(normalizeOutsideCode(raw.substring(pos, m.range.first)))
        sb.append(m.value)
        pos = m.range.last + 1
    }
    sb.append(normalizeOutsideCode(raw.substring(pos)))
    return sb.toString()
}

// Old pasted links are plain http:// (blocked as cleartext traffic by default on Android), and a
// few old MAL-hosted uploads come without any scheme at all. Upgrade everything to https.
fun httpsUpgrade(url: String): String = when {
    url.startsWith("https://", ignoreCase = true) -> url
    url.startsWith("http://", ignoreCase = true) -> "https://" + url.substring(7)
    url.startsWith("//") -> "https:$url"
    url.isNotBlank() && !url.contains("://") -> "https://$url"
    else -> url
}

// ---------------------------------------------------------------------------
// Entry points
// ---------------------------------------------------------------------------

/**
 * Parses already-normalized BBCode (see [normalizeMalMarkup]) into renderable blocks.
 *
 * [background] is the colour the text will sit on; it is only used to throw away [color] tags
 * that would be unreadable there. Leave it Unspecified when no rendering is involved.
 */
fun parseBlocks(raw: String, linkColor: Color, background: Color = Color.Unspecified): List<ForumBlock> {
    if (raw.isBlank()) return emptyList()
    return BbWalker(linkColor, background).blocksFrom(buildTree(tokenizeBb(raw)))
}

fun parseBBCode(rawIn: String, linkColor: Color, background: Color = Color.Unspecified): List<ForumBlock> {
    if (rawIn.isBlank()) return emptyList()
    return parseBlocks(normalizeMalMarkup(rawIn), linkColor, background)
}