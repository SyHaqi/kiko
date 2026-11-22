package com.kiko.tracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiko.tracker.ui.theme.LocalKikoColors
import com.kiko.tracker.ui.theme.kikoCorner

// GitHub-flavoured markdown renderer for release notes: headings, bold / italic / strike, inline code,
// code fences, bullet / numbered / task lists (nested), quotes, rules, links, bare URLs, @mentions and #123
// references. Raw HTML is stripped down to its text so a stray <br> or <details> never shows up literally.

private sealed class MdBlock {
    class Heading(val level: Int, val text: String) : MdBlock()
    class Para(val text: String) : MdBlock()
    class Bullet(val level: Int, val text: String, val task: Boolean?) : MdBlock()
    class Numbered(val level: Int, val number: String, val text: String) : MdBlock()
    class Quote(val text: String) : MdBlock()
    class Code(val text: String) : MdBlock()
    class Table(val rows: List<List<String>>) : MdBlock()
    object Rule : MdBlock()
}

private val headingRe = Regex("^\\s{0,3}(#{1,6})\\s+(.*?)\\s*#*\\s*$")
private val bulletRe = Regex("^(\\s*)[-*+]\\s+(.*)$")
private val numberedRe = Regex("^(\\s*)(\\d{1,3})[.)]\\s+(.*)$")
private val ruleRe = Regex("^\\s{0,3}([-*_])(\\s*\\1){2,}\\s*$")
private val taskRe = Regex("^\\[([ xX])\\]\\s+(.*)$")
private val tableSepRe = Regex("^\\s*\\|?\\s*:?-{2,}:?\\s*(\\|\\s*:?-{2,}:?\\s*)*\\|?\\s*$")

private fun indentOf(raw: String): Int {
    var n = 0
    for (ch in raw) { if (ch == ' ') n += 1 else if (ch == '\t') n += 4 else break }
    return n / 2
}

private fun parseBlocks(source: String): List<MdBlock> {
    val text = source.replace("\r\n", "\n").replace('\r', '\n').replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
    val out = ArrayList<MdBlock>()
    val lines = text.split('\n')
    var i = 0
    val para = StringBuilder()
    fun flushPara() { if (para.isNotBlank()) out.add(MdBlock.Para(para.toString().trimEnd())); para.setLength(0) }
    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()
        when {
            trimmed.startsWith("```") || trimmed.startsWith("~~~") -> {
                flushPara()
                val fence = trimmed.take(3)
                val code = StringBuilder(); i++
                while (i < lines.size && !lines[i].trim().startsWith(fence)) { code.append(lines[i]).append('\n'); i++ }
                out.add(MdBlock.Code(code.toString().trimEnd('\n')))
            }
            trimmed.isEmpty() -> flushPara()
            ruleRe.matches(line) -> { flushPara(); out.add(MdBlock.Rule) }
            headingRe.matches(line) -> {
                flushPara()
                val m = headingRe.matchEntire(line)!!
                out.add(MdBlock.Heading(m.groupValues[1].length, m.groupValues[2]))
            }
            trimmed.startsWith(">") -> {
                flushPara()
                val q = StringBuilder()
                while (i < lines.size && lines[i].trim().startsWith(">")) { q.append(lines[i].trim().removePrefix(">").removePrefix(" ")).append('\n'); i++ }
                i--
                out.add(MdBlock.Quote(q.toString().trim()))
            }
            trimmed.startsWith("|") && trimmed.count { it == '|' } >= 2 -> {
                flushPara()
                val rows = ArrayList<List<String>>()
                while (i < lines.size && lines[i].trim().startsWith("|")) {
                    val l = lines[i].trim()
                    if (!tableSepRe.matches(l)) rows.add(l.trim('|').split('|').map { it.trim() })
                    i++
                }
                i--
                out.add(MdBlock.Table(rows))
            }
            bulletRe.matches(line) -> {
                flushPara()
                val m = bulletRe.matchEntire(line)!!
                var body = m.groupValues[2]
                var task: Boolean? = null
                taskRe.matchEntire(body)?.let { t -> task = t.groupValues[1] != " "; body = t.groupValues[2] }
                out.add(MdBlock.Bullet(indentOf(line).coerceAtMost(4), body, task))
            }
            numberedRe.matches(line) -> {
                flushPara()
                val m = numberedRe.matchEntire(line)!!
                out.add(MdBlock.Numbered(indentOf(line).coerceAtMost(4), m.groupValues[2], m.groupValues[3]))
            }
            else -> {
                // Continuation line of the previous list item (indented under it)? Fold it into that item.
                val last = out.lastOrNull()
                if (para.isEmpty() && line.startsWith("  ") && (last is MdBlock.Bullet || last is MdBlock.Numbered)) {
                    out[out.lastIndex] = when (last) {
                        is MdBlock.Bullet -> MdBlock.Bullet(last.level, last.text + "\n" + trimmed, last.task)
                        is MdBlock.Numbered -> MdBlock.Numbered(last.level, last.number, last.text + "\n" + trimmed)
                        else -> last
                    }
                } else {
                    if (para.isNotEmpty()) para.append('\n')
                    para.append(trimmed)
                }
            }
        }
        i++
    }
    flushPara()
    return out
}

private class InlineStyles(val link: SpanStyle, val code: SpanStyle, val repo: String)

private fun Char.isWordChar() = isLetterOrDigit()

private fun AnnotatedString.Builder.appendInline(s: String, st: InlineStyles, inLink: Boolean = false) {
    var i = 0
    val plain = StringBuilder()
    fun flush() { if (plain.isNotEmpty()) { append(plain.toString()); plain.setLength(0) } }
    fun url(u: String, label: String) {
        flush()
        withLink(LinkAnnotation.Url(u, TextLinkStyles(st.link))) { append(label) }
    }
    while (i < s.length) {
        val ch = s[i]
        val prev = if (i > 0) s[i - 1] else ' '
        // Backslash escape
        if (ch == '\\' && i + 1 < s.length && s[i + 1] in "\\`*_{}[]()#+-.!|~<>") { plain.append(s[i + 1]); i += 2; continue }
        // Inline code
        if (ch == '`') {
            var n = 0; while (i + n < s.length && s[i + n] == '`') n++
            val close = s.indexOf("`".repeat(n), i + n)
            if (close > 0) {
                flush()
                withStyle(st.code) { append(s.substring(i + n, close).trim()) }
                i = close + n; continue
            }
            plain.append("`".repeat(n)); i += n; continue
        }
        // Bold / strike
        if (s.startsWith("**", i) || s.startsWith("__", i) || s.startsWith("~~", i)) {
            val mark = s.substring(i, i + 2)
            val close = s.indexOf(mark, i + 2)
            if (close > i + 2 && !s[i + 2].isWhitespace() && (mark != "__" || !prev.isWordChar())) {
                flush()
                val style = if (mark == "~~") SpanStyle(textDecoration = TextDecoration.LineThrough) else SpanStyle(fontWeight = FontWeight.Bold)
                withStyle(style) { appendInline(s.substring(i + 2, close), st, inLink) }
                i = close + 2; continue
            }
        }
        // Italic
        if ((ch == '*' || ch == '_') && i + 1 < s.length && !s[i + 1].isWhitespace() && s[i + 1] != ch && (ch == '*' || !prev.isWordChar())) {
            var close = s.indexOf(ch, i + 1)
            while (close > 0 && ch == '_' && close + 1 < s.length && s[close + 1].isWordChar()) close = s.indexOf(ch, close + 1)
            if (close > i + 1 && !s[close - 1].isWhitespace()) {
                flush()
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { appendInline(s.substring(i + 1, close), st, inLink) }
                i = close + 1; continue
            }
        }
        // [text](url) and ![alt](url)
        if (ch == '[' || (ch == '!' && s.startsWith("![", i))) {
            val image = ch == '!'
            val open = if (image) i + 1 else i
            var depth = 0; var end = -1; var j = open
            while (j < s.length) {
                if (s[j] == '[') depth++ else if (s[j] == ']') { depth--; if (depth == 0) { end = j; break } }
                j++
            }
            if (end > 0 && end + 1 < s.length && s[end + 1] == '(') {
                val urlEnd = s.indexOf(')', end + 2)
                if (urlEnd > 0) {
                    val label = s.substring(open + 1, end)
                    val target = s.substring(end + 2, urlEnd).trim().substringBefore(' ').trim('<', '>')
                    if (image) {
                        // Images can't be shown inline in release notes; keep the alt text as a link.
                        if (label.isNotBlank() && target.startsWith("http")) url(target, label) else flush()
                    } else if (target.isNotBlank() && !inLink) {
                        flush()
                        withLink(LinkAnnotation.Url(target, TextLinkStyles(st.link))) { appendInline(label, st, inLink = true) }
                    } else appendInline(label, st, inLink)
                    i = urlEnd + 1; continue
                }
            }
        }
        // <https://...> and stray HTML tags
        if (ch == '<') {
            val close = s.indexOf('>', i)
            if (close > i) {
                val inner = s.substring(i + 1, close)
                if (inner.startsWith("http://") || inner.startsWith("https://")) { if (inLink) plain.append(inner) else url(inner, inner); i = close + 1; continue }
                if (inner.startsWith("br")) { plain.append('\n'); i = close + 1; continue }
                if (inner.firstOrNull()?.let { it.isLetter() || it == '/' } == true) { i = close + 1; continue }
            }
        }
        // Bare URL
        if (ch == 'h' && (s.startsWith("http://", i) || s.startsWith("https://", i)) && !prev.isWordChar()) {
            var end = i; while (end < s.length && !s[end].isWhitespace() && s[end] != '<' && s[end] != ')' ) end++
            while (end > i && s[end - 1] in ".,;:!?'\"") end--
            val u = s.substring(i, end)
            if (inLink) plain.append(u) else url(u, u.removePrefix("https://").removePrefix("http://"))
            i = end; continue
        }
        // @mention
        if (ch == '@' && !prev.isWordChar() && !inLink) {
            var end = i + 1; while (end < s.length && (s[end].isLetterOrDigit() || s[end] == '-')) end++
            if (end > i + 1) { url("https://github.com/${s.substring(i + 1, end)}", s.substring(i, end)); i = end; continue }
        }
        // #123 issue / pull request
        if (ch == '#' && !prev.isWordChar() && !inLink) {
            var end = i + 1; while (end < s.length && s[end].isDigit()) end++
            if (end > i + 1 && (end >= s.length || !s[end].isLetter())) { url("https://github.com/${st.repo}/issues/${s.substring(i + 1, end)}", s.substring(i, end)); i = end; continue }
        }
        plain.append(ch); i++
    }
    flush()
}

@Composable fun MarkdownText(text: String, modifier: Modifier = Modifier, repo: String = "SyHaqi/kiko") {
    val c = LocalKikoColors.current
    val blocks = remember(text) { parseBlocks(text) }
    val styles = remember(c, repo) {
        InlineStyles(
            link = SpanStyle(color = c.primary, textDecoration = TextDecoration.Underline),
            code = SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 12.5.sp, background = c.surfaceContainerHighest, color = c.ink),
            repo = repo,
        )
    }
    val body = TextStyle(color = c.ink, fontSize = 14.sp, lineHeight = 21.sp)
    fun inline(s: String): AnnotatedString = buildAnnotatedString { appendInline(s, styles) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEachIndexed { index, block ->
            when (block) {
                is MdBlock.Heading -> {
                    val size = when (block.level) { 1 -> 20.sp; 2 -> 17.sp; 3 -> 15.sp; else -> 14.sp }
                    Text(
                        inline(block.text), color = if (block.level <= 2) c.ink else c.primary,
                        fontSize = size, fontWeight = FontWeight.Bold, lineHeight = size * 1.3f,
                        modifier = Modifier.padding(top = if (index == 0) 0.dp else if (block.level <= 2) 8.dp else 4.dp),
                    )
                }
                is MdBlock.Para -> Text(inline(block.text), style = body)
                is MdBlock.Bullet -> Row(Modifier.padding(start = 6.dp + 16.dp * block.level)) {
                    when (block.task) {
                        null -> Text(if (block.level == 0) "•" else "◦", style = body, color = c.muted, modifier = Modifier.width(16.dp))
                        else -> Icon(
                            if (block.task) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank, null,
                            tint = if (block.task) c.primary else c.muted, modifier = Modifier.padding(end = 6.dp, top = 2.dp).size(16.dp),
                        )
                    }
                    Text(inline(block.text), style = body, modifier = Modifier.weight(1f))
                }
                is MdBlock.Numbered -> Row(Modifier.padding(start = 6.dp + 16.dp * block.level)) {
                    Text("${block.number}.", style = body, color = c.muted, modifier = Modifier.width(24.dp))
                    Text(inline(block.text), style = body, modifier = Modifier.weight(1f))
                }
                is MdBlock.Quote -> Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(RoundedCornerShape(kikoCorner(12.dp))).background(c.surfaceContainerHighest)) {
                    Box(Modifier.width(3.dp).fillMaxHeight().background(c.primary))
                    Text(inline(block.text), style = body.copy(color = c.muted), modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp))
                }
                is MdBlock.Code -> Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(kikoCorner(14.dp))).background(c.surfaceContainerHighest)
                        .horizontalScroll(rememberScrollState()).padding(12.dp),
                ) {
                    Text(block.text, color = c.ink, fontSize = 12.sp, lineHeight = 17.sp, fontFamily = FontFamily.Monospace)
                }
                is MdBlock.Table -> Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(kikoCorner(14.dp))).background(c.surfaceContainerHighest).padding(horizontal = 12.dp, vertical = 6.dp)) {
                    block.rows.forEachIndexed { r, row ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                            row.forEach { cell ->
                                Text(
                                    inline(cell), style = body.copy(fontSize = 13.sp, fontWeight = if (r == 0) FontWeight.Bold else FontWeight.Normal),
                                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                                )
                            }
                        }
                    }
                }
                MdBlock.Rule -> Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(1.dp).background(c.muted.copy(alpha = 0.25f)))
            }
        }
    }
}
