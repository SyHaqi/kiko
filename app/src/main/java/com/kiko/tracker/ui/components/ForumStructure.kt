package com.kiko.tracker.ui.components

// ---------------------------------------------------------------------------
// Line-structure analysis for forum text
//
// MAL posts (news reposts above all) are full of hard-broken lines that are
// really *structure*, not prose:
//
//     Staff
//     Director: Naoya Fukushi (Nazotokine)
//     Series Composition: Hiroshi Hosokawa
//     ...
//
// On the website that reads as a labelled section. Dumped into one text block
// it turns into a wall where a wrapped line looks exactly like a new entry.
// This file looks at ONE blank-line-free paragraph and says which lines are
//   - a "Label: value" group  (-> ForumBlock.FactList, titled by the line above it)
//   - a run of short lines    (-> ForumBlock.LineList)
//   - ordinary prose          (-> ForumBlock.Paragraph)
//
// It only works on plain strings and index ranges (no Compose types) so the
// rules can be reasoned about, and tested, in isolation. Ranges are INCLUSIVE
// (IntRange.last is the last character), relative to the string passed in.
// ---------------------------------------------------------------------------

internal sealed class LineSegment {
    /** Ordinary text; may span several lines (newlines are kept). */
    class Prose(val range: IntRange) : LineSegment()

    /** A "Label: value" group with an optional section title taken from the line above it. */
    class Facts(val title: IntRange?, val rows: List<FactRange>) : LineSegment()

    /** Three or more short lines that read as a list (cast names, theme songs, ...). */
    class Lines(val items: List<IntRange>) : LineSegment()
}

internal class FactRange(val label: IntRange, val value: IntRange)

private val FactLineRegex = Regex("""^([^:\n]{1,40}):[ \t]+(\S.*)$""")
private val WhitespaceRun = Regex("""\s+""")
private const val SentenceEnders = ".!?,;…"

// Trimmed, non-blank line ranges of `s`.
private fun lineRanges(s: String): List<IntRange> {
    val out = ArrayList<IntRange>()
    var start = 0
    while (start <= s.length) {
        var end = s.indexOf('\n', start)
        if (end < 0) end = s.length
        var lo = start
        var hi = end
        while (lo < hi && s[lo].isWhitespace()) lo++
        while (hi > lo && s[hi - 1].isWhitespace()) hi--
        if (lo < hi) out += lo until hi
        if (end >= s.length) break
        start = end + 1
    }
    return out
}

private fun IntRange.len() = last - first + 1

// "Director: Naoya Fukushi (Nazotokine)" -> label / value ranges, or null when the line isn't one.
// Deliberately strict: a URL ("https://x"), a clock time ("10:30") or a sentence that happens to
// contain a colon must not turn into a table row.
private fun parseFact(s: String, line: IntRange): FactRange? {
    val text = s.substring(line.first, line.last + 1)
    val m = FactLineRegex.find(text) ?: return null
    val labelGroup = m.groups[1] ?: return null
    val valueGroup = m.groups[2] ?: return null
    val label = labelGroup.value.trimEnd()
    if (label.isEmpty()) return null
    if (label.split(WhitespaceRun).size > 6) return null
    if (label.contains("//") || label.startsWith("http", ignoreCase = true)) return null
    if (label.last() in SentenceEnders) return null
    if (!(label.first().isLetterOrDigit() || label.first() in "([\"'")) return null
    val labelStart = line.first + labelGroup.range.first
    val valueStart = line.first + valueGroup.range.first
    return FactRange(labelStart until (labelStart + label.length), valueStart..line.last)
}

// A short line that can plausibly be a section title ("Staff", "Cast:", "Theme Songs").
private fun titleRange(s: String, line: IntRange): IntRange? {
    var text = s.substring(line.first, line.last + 1)
    var range = line
    if (text.endsWith(":")) {
        text = text.dropLast(1).trimEnd()
        if (text.isEmpty()) return null
        range = line.first until (line.first + text.length)
    }
    if (text.length > 32) return null
    if (text.split(WhitespaceRun).size > 4) return null
    if (text.last() in SentenceEnders) return null
    if (text.none { it.isLetter() }) return null
    return range
}

private fun flushLines(pending: MutableList<IntRange>, out: MutableList<LineSegment>) {
    if (pending.isEmpty()) return
    val allShort = pending.all { it.len() <= 90 }
    if (pending.size >= 3 && allShort) out += LineSegment.Lines(pending.toList())
    else out += LineSegment.Prose(pending.first().first..pending.last().last)
    pending.clear()
}

/**
 * Splits one paragraph (no blank lines) into prose, "Label: value" groups and short-line lists.
 * Returns an empty list for blank input.
 */
internal fun segmentLines(s: String): List<LineSegment> {
    val lines = lineRanges(s)
    if (lines.isEmpty()) return emptyList()
    if (lines.size == 1) return listOf(LineSegment.Prose(lines[0]))

    val facts = lines.map { parseFact(s, it) }
    val out = ArrayList<LineSegment>()
    val pending = ArrayList<IntRange>()
    var i = 0
    while (i < lines.size) {
        if (facts[i] == null) { pending += lines[i]; i++; continue }

        var j = i
        while (j < lines.size && facts[j] != null) j++
        val run = j - i
        // Two long "facts" are almost certainly prose with colons in it, not a table.
        val proseLike = (i until j).any { facts[it]!!.value.len() > 140 }
        if (run >= 3 || (run == 2 && !proseLike)) {
            // The short line right above the group names it ("Staff", "Cast:").
            var title: IntRange? = null
            if (pending.isNotEmpty()) {
                val candidate = titleRange(s, pending.last())
                if (candidate != null) { title = candidate; pending.removeAt(pending.lastIndex) }
            }
            flushLines(pending, out)
            out += LineSegment.Facts(title, (i until j).map { facts[it]!! })
        } else {
            for (k in i until j) pending += lines[k]
        }
        i = j
    }
    flushLines(pending, out)
    return out
}