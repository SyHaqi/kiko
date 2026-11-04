@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.kiko.tracker.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiko.tracker.data.model.MediaItem
import com.kiko.tracker.data.model.MediaType
import com.kiko.tracker.data.model.RelatedEntry
import com.kiko.tracker.data.model.RelationEdge
import com.kiko.tracker.data.model.WatchStatus
import com.kiko.tracker.data.model.displayTitle
import com.kiko.tracker.data.model.graphKey
import com.kiko.tracker.ui.components.CoverStatusMark
import com.kiko.tracker.ui.components.KikoDropdownMenu
import com.kiko.tracker.ui.components.KikoHeader
import com.kiko.tracker.ui.components.KikoLoadingIndicator
import com.kiko.tracker.ui.components.headerEdgeEnd
import com.kiko.tracker.ui.components.headerEdgeStart
import com.kiko.tracker.ui.components.headerTitleStart
import com.kiko.tracker.ui.components.kikoFilterChipColors
import com.kiko.tracker.ui.theme.LocalKikoColors
import com.kiko.tracker.ui.theme.kikoCorner
import com.kiko.tracker.viewmodel.LibraryViewModel
import kotlin.math.abs
import kotlin.math.roundToInt

// Full-page Related graph, opened from the "See more" button on Detail's Related row. Walks the franchise
// through LibraryViewModel.buildRelationGraph and draws it as a left-to-right pipeline map.
//
// Layout: every title is the same card as in Detail's Related row, and the map reads from left to right in story
// order: a sequel (or side story, summary, ...) sits in a column to the right of the title it follows, so the
// arrow of nearly every pipe points right. Titles that only hang off a story by a looser link (spin-off,
// alternative version, character, ...) go one column beside the title they are linked to. Every relation is a
// pipe from the right side of one card to the left side of the other, with its own coloured lane in the gutter
// between two columns; a pipe that skips columns passes between the cards of the columns in between.
//
// Keeping it easy to follow: the pipes of one gutter get as few crossings as possible (lanes are ordered so a
// pipe's vertical stretch doesn't cut through another's horizontal one wherever that can be avoided), every card
// side gives each pipe its own spot, every pipe is outlined in the background colour so wherever two cross one is
// clearly over the other, and tapping a card lights up its own pipes and its sequels on to the end of the story,
// and dims everything else.
//
// Pan / zoom as before; the Anime / Manga chips flip between the two halves of the franchise; the round-square
// buttons recentre on the title you came from and explain the colours. Which half you were on and where you had
// panned / zoomed are remembered, so coming back from an opened title lands you where you left the map.

private const val CardW = 100f           // dp: same card as Detail's Related row, slightly narrower
private const val CoverH = 150f          // dp: 2:3 cover for CardW
private const val TextH = 80f            // dp: fixed text block under the cover
private const val CardH = CoverH + TextH // dp
private const val ItemGap = 34f          // dp between two items (cards or passing pipes) of one column
private const val GutterMin = 90f        // dp: the least room between two columns (arrow heads need some)
private const val GutterEdge = 26f       // dp kept free next to the cards in a gutter
private const val LaneStep = 24f         // dp between two pipes side by side in a gutter
private const val LanePad = 26f          // dp of free space between two pipes that share one lane
private const val WorldMargin = 80f      // dp around the whole map
private const val InitialScale = 0.85f

// One colour per kind of relation. MAL's relation names are folded into these groups.
private enum class RelKind(val label: String, val color: Color) {
    Sequel("Sequel / prequel", Color(0xFF43A047)),
    SideStory("Side story / parent story", Color(0xFFFB8C00)),
    SpinOff("Spin-off", Color(0xFF8E24AA)),
    Adaptation("Adaptation", Color(0xFFD81B60)),
    Alternative("Alternative version / setting", Color(0xFF00ACC1)),
    Summary("Summary / full story", Color(0xFF5C6BC0)),
    Other("Character / other", Color(0xFF78909C)),
}

private fun relKindOf(relation: String, sequel: Boolean): RelKind {
    val k = relation.lowercase().filter { it.isLetter() }
    return when {
        k == "sequel" || k == "prequel" -> RelKind.Sequel
        k == "sidestory" || k == "parentstory" -> RelKind.SideStory
        k == "spinoff" -> RelKind.SpinOff
        k == "adaptation" -> RelKind.Adaptation
        k.startsWith("alternative") -> RelKind.Alternative
        k == "summary" || k == "fullstory" -> RelKind.Summary
        sequel -> RelKind.Sequel
        else -> RelKind.Other
    }
}

// Relations that put a title in a later column than the one it follows.
private fun isStory(k: RelKind): Boolean = k == RelKind.Sequel || k == RelKind.SideStory || k == RelKind.Summary

// What the map remembers while you look at an opened title: which half you were on, and the pan / zoom of each.
private val savedGraphKind = HashMap<String, Boolean>()
private val savedGraphView = HashMap<String, FloatArray>()

// One relation, in world dp, running from the "from" title to the "to" title; the arrow head is at the end.
private class Pipe(val pts: List<Offset>, val kind: RelKind, val fromKey: String, val toKey: String)

private class Flow(
    val cardPos: Map<String, Offset>, // top-left of every card
    val pipes: List<Pipe>,
    val width: Float,
    val height: Float,
    val rootCenter: Offset,
)

private val EmptyFlow = Flow(emptyMap(), emptyList(), 0f, 0f, Offset.Zero)

// A relation between titles of two columns (or of one), with the "passing" items it uses in the columns between.
private class FlowEdge(val e: RelationEdge, val u: Int, val v: Int, val chain: List<Int>)

// One stretch of a pipe through one gutter: out of the right side of an item at y1, into the left side of the next
// one at y2 (a pipe between two cards of one column leaves and returns on the right side: [same]).
private class Hop(val fi: Int, val g: Int, val y1: Float, val y2: Float, val same: Boolean)

// Gives each pipe of one gutter a lane (0 = the one nearest the column on its left). Two pipes whose vertical
// stretches overlap never share a lane, but pipes that don't overlap do, which keeps the gutter narrow. Lanes are
// also ordered so that a pipe's horizontal stretches don't cut through another pipe's vertical one wherever that
// can be avoided: [firstEnds] are the y of the ends that leave from the left side of the gutter, [secondEnds]
// those that arrive at its right side. Returns the lane of every pipe and the number of lanes used.
private fun assignLanes(lo: FloatArray, hi: FloatArray, firstEnds: List<FloatArray>, secondEnds: List<FloatArray>): Pair<IntArray, Int> {
    val n = lo.size
    val succ = Array(n) { ArrayList<Int>() }
    val indeg = IntArray(n)
    val seen = HashSet<Long>()
    // add(a, b): pipe a has to sit left of pipe b.
    fun add(a: Int, b: Int) { if (a != b && seen.add(a.toLong() * n + b)) { succ[a].add(b); indeg[b]++ } }
    for (i in 0 until n) for (j in 0 until n) {
        if (i == j) continue
        for (t in firstEnds[i]) if (t > lo[j] + 1f && t < hi[j] - 1f) add(i, j)
        for (b in secondEnds[i]) if (b > lo[j] + 1f && b < hi[j] - 1f) add(j, i)
    }
    val lane = IntArray(n)
    val minLane = IntArray(n)
    val done = BooleanArray(n)
    val used = ArrayList<MutableList<FloatArray>>()
    repeat(n) {
        var pick = -1
        for (i in 0 until n) if (!done[i] && indeg[i] == 0 && (pick == -1 || lo[i] < lo[pick])) pick = i
        // A cycle of constraints can't be satisfied: break it at the pipe with the fewest open constraints.
        if (pick == -1) for (i in 0 until n) if (!done[i] && (pick == -1 || indeg[i] < indeg[pick])) pick = i
        var l = minLane[pick]
        while (true) {
            if (l >= used.size) used.add(mutableListOf())
            val clash = used[l].any { it[0] < hi[pick] + LanePad && lo[pick] < it[1] + LanePad }
            if (!clash) break
            l++
        }
        used[l].add(floatArrayOf(lo[pick], hi[pick]))
        lane[pick] = l
        done[pick] = true
        for (j in succ[pick]) { indeg[j]--; minLane[j] = maxOf(minLane[j], l + 1) }
    }
    return lane to used.size
}

// Least-squares fit of a non-decreasing sequence to [v] (pool adjacent violators).
private fun isotonic(v: FloatArray): FloatArray {
    val sum = ArrayList<Float>()
    val cnt = ArrayList<Int>()
    for (x in v) {
        sum.add(x); cnt.add(1)
        while (sum.size >= 2 && sum[sum.size - 2] / cnt[cnt.size - 2] > sum[sum.size - 1] / cnt[cnt.size - 1]) {
            val s = sum.removeAt(sum.size - 1)
            val k = cnt.removeAt(cnt.size - 1)
            sum[sum.size - 1] = sum[sum.size - 1] + s
            cnt[cnt.size - 1] = cnt[cnt.size - 1] + k
        }
    }
    val out = FloatArray(v.size)
    var p = 0
    for (b in sum.indices) {
        val m = sum[b] / cnt[b]
        repeat(cnt[b]) { out[p++] = m }
    }
    return out
}

// Builds the whole map in dp: columns, card positions, and the route of every pipe.
private fun buildFlow(nodes: List<RelatedEntry>, edges: List<RelationEdge>, rootKey: String): Flow {
    if (nodes.isEmpty()) return EmptyFlow
    val keys = nodes.map { it.graphKey() }
    val n = keys.size
    val index = keys.withIndex().associate { it.value to it.index }
    val usable = edges.filter { it.from != it.to && it.from in index && it.to in index }
    fun kindOf(e: RelationEdge): RelKind = relKindOf(e.relation, e.sequel)
    val rootIdx = index[rootKey]

    // --- Columns. Story relations (sequel, side story, summary) run left to right along from -> to; a cycle of them
    // is broken wherever the walk meets itself.
    val hard = usable.filter { isStory(kindOf(it)) }
    val outH = Array(n) { ArrayList<RelationEdge>() }
    for (e in hard) outH[index.getValue(e.from)].add(e)
    val state = IntArray(n)
    val back = HashSet<RelationEdge>()
    fun dfs(u: Int) {
        state[u] = 1
        for (e in outH[u]) {
            val v = index.getValue(e.to)
            if (state[v] == 1) back.add(e) else if (state[v] == 0) dfs(v)
        }
        state[u] = 2
    }
    if (rootIdx != null) dfs(rootIdx)
    for (i in 0 until n) if (state[i] == 0) dfs(i)
    val dagOut = Array(n) { ArrayList<Int>() }
    val indeg = IntArray(n)
    for (e in hard) if (e !in back) {
        val a = index.getValue(e.from)
        val b = index.getValue(e.to)
        dagOut[a].add(b); indeg[b]++
    }
    val rank = IntArray(n)
    val deg = indeg.copyOf()
    val topo = ArrayList<Int>()
    for (i in 0 until n) if (deg[i] == 0) topo.add(i)
    var th = 0
    while (th < topo.size) {
        val u = topo[th++]
        for (v in dagOut[u]) { rank[v] = maxOf(rank[v], rank[u] + 1); deg[v]--; if (deg[v] == 0) topo.add(v) }
    }
    // A title nothing precedes sits right next to what it leads to, not at the far left.
    for (u in topo.reversed()) if (indeg[u] == 0 && dagOut[u].isNotEmpty()) rank[u] = dagOut[u].minOf { rank[it] } - 1
    // Titles joined by story relations form a group; the groups are laid next to each other by the looser links.
    val uf = IntArray(n) { it }
    fun find(a: Int): Int {
        var r = a
        while (uf[r] != r) { uf[r] = uf[uf[r]]; r = uf[r] }
        return r
    }
    for (e in hard) {
        val ra = find(index.getValue(e.from))
        val rb = find(index.getValue(e.to))
        if (ra != rb) uf[maxOf(ra, rb)] = minOf(ra, rb)
    }
    val groupMembers = (0 until n).groupBy { find(it) }.values.toList()
    val groupOf = IntArray(n)
    val localRank = IntArray(n)
    groupMembers.forEachIndexed { gi, ms ->
        val lowest = ms.minOf { rank[it] }
        for (m in ms) { groupOf[m] = gi; localRank[m] = rank[m] - lowest }
    }
    val adjLoose = Array(n) { ArrayList<RelationEdge>() }
    for (e in usable) if (!isStory(kindOf(e))) { adjLoose[index.getValue(e.from)].add(e); adjLoose[index.getValue(e.to)].add(e) }
    val unset = Int.MIN_VALUE
    val offset = IntArray(groupMembers.size) { unset }
    val startOrder = (if (rootIdx != null) listOf(groupOf[rootIdx]) else emptyList()) + groupMembers.indices
    for (start in startOrder) {
        if (offset[start] != unset) continue
        offset[start] = 0
        val queue = arrayListOf(start)
        var qh = 0
        while (qh < queue.size) {
            val a = queue[qh++]
            for (f in groupMembers[a]) for (e in adjLoose[f]) {
                val g = index.getValue(if (e.from == keys[f]) e.to else e.from)
                val b = groupOf[g]
                if (b == a || offset[b] != unset) continue
                // The linked group is placed so that this link points right.
                offset[b] = if (e.from == keys[f]) offset[a] + localRank[f] + 1 - localRank[g] else offset[a] + localRank[f] - 1 - localRank[g]
                queue.add(b)
            }
        }
    }
    val col = IntArray(n) { localRank[it] + offset[groupOf[it]] }
    val firstCol = col.minOrNull() ?: 0
    for (i in 0 until n) col[i] -= firstCol

    // --- Items: the titles, plus an invisible "passing" item in every column a pipe skips, so the columns make room
    // for it between their cards.
    val itemCol = ArrayList<Int>()
    for (i in 0 until n) itemCol.add(col[i])
    val flowEdges = ArrayList<FlowEdge>()
    for (e in usable) {
        val a = index.getValue(e.from)
        val b = index.getValue(e.to)
        val u = if (col[a] <= col[b]) a else b
        val v = if (col[a] <= col[b]) b else a
        val chain = ArrayList<Int>()
        for (c in col[u] + 1 until col[v]) { chain.add(itemCol.size); itemCol.add(c) }
        flowEdges.add(FlowEdge(e, u, v, chain))
    }
    val total = itemCol.size
    var maxCol = 0
    for (c in itemCol) if (c > maxCol) maxCol = c
    fun hOf(item: Int): Float = if (item < n) CardH else 0f
    val leftN = Array(total) { ArrayList<Int>() }
    val rightN = Array(total) { ArrayList<Int>() }
    for (fe in flowEdges) {
        if (col[fe.v] == col[fe.u]) continue
        val path = listOf(fe.u) + fe.chain + fe.v
        for (i in 0 until path.size - 1) { rightN[path[i]].add(path[i + 1]); leftN[path[i + 1]].add(path[i]) }
    }

    // --- Order inside the columns: sweep left and right, putting every item at the average place of its neighbours.
    val colItems = Array(maxCol + 1) { ArrayList<Int>() }
    for (item in 0 until total) colItems[itemCol[item]].add(item)
    val pos = IntArray(total)
    fun refreshPos(c: Int) { colItems[c].forEachIndexed { i, item -> pos[item] = i } }
    for (c in 0..maxCol) refreshPos(c)
    fun reorder(c: Int, nbrs: Array<ArrayList<Int>>) {
        colItems[c].sortWith(
            compareBy<Int>({ item -> if (nbrs[item].isEmpty()) pos[item].toFloat() else nbrs[item].map { pos[it] }.average().toFloat() }, { item -> pos[item] }),
        )
        refreshPos(c)
    }
    repeat(10) { sweep ->
        if (sweep % 2 == 0) {
            for (c in 1..maxCol) reorder(c, leftN)
        } else {
            for (c in maxCol - 1 downTo 0) reorder(c, rightN)
        }
    }

    // --- Heights: stack every column, then pull each item toward the middle of its neighbours without overlaps.
    val yc = FloatArray(total)
    for (c in 0..maxCol) {
        var cur = 0f
        for (item in colItems[c]) { yc[item] = cur + hOf(item) / 2f; cur += hOf(item) + ItemGap }
    }
    fun align(c: Int, nbrs: Array<ArrayList<Int>>) {
        val list = colItems[c]
        if (list.isEmpty()) return
        val offs = FloatArray(list.size)
        val vals = FloatArray(list.size)
        for (k in list.indices) {
            if (k > 0) offs[k] = offs[k - 1] + hOf(list[k - 1]) / 2f + ItemGap + hOf(list[k]) / 2f
            val item = list[k]
            val desired = if (nbrs[item].isEmpty()) yc[item] else nbrs[item].map { yc[it] }.average().toFloat()
            vals[k] = desired - offs[k]
        }
        val fit = isotonic(vals)
        for (k in list.indices) yc[list[k]] = fit[k] + offs[k]
    }
    repeat(4) {
        for (c in 1..maxCol) align(c, leftN)
        for (c in maxCol - 1 downTo 0) align(c, rightN)
    }
    var minTop = Float.MAX_VALUE
    var maxBottom = -Float.MAX_VALUE
    for (item in 0 until total) { minTop = minOf(minTop, yc[item] - hOf(item) / 2f); maxBottom = maxOf(maxBottom, yc[item] + hOf(item) / 2f) }
    val shiftY = WorldMargin - minTop
    for (item in 0 until total) yc[item] += shiftY

    // --- Where along a card's side each pipe attaches: every pipe its own spot, in the order its partner sits.
    val rightList = HashMap<Int, MutableList<Pair<Int, Int>>>() // item -> (flow edge, the item the pipe goes to)
    val leftList = HashMap<Int, MutableList<Pair<Int, Int>>>()
    flowEdges.forEachIndexed { fi, fe ->
        if (col[fe.v] == col[fe.u]) {
            rightList.getOrPut(fe.u) { mutableListOf() }.add(fi to fe.v)
            rightList.getOrPut(fe.v) { mutableListOf() }.add(fi to fe.u)
        } else {
            rightList.getOrPut(fe.u) { mutableListOf() }.add(fi to (fe.chain.firstOrNull() ?: fe.v))
            leftList.getOrPut(fe.v) { mutableListOf() }.add(fi to (fe.chain.lastOrNull() ?: fe.u))
        }
    }
    val portY = HashMap<String, Float>()
    fun assignPorts(lists: Map<Int, MutableList<Pair<Int, Int>>>, side: String) {
        for ((item, list) in lists) {
            list.sortBy { yc[it.second] }
            val cnt = list.size
            val spacing = if (cnt <= 1) 0f else minOf(18f, (CardH - 56f) / (cnt - 1))
            list.forEachIndexed { j, p -> portY["${p.first}|$item|$side"] = yc[item] + (j - (cnt - 1) / 2f) * spacing }
        }
    }
    assignPorts(rightList, "R")
    assignPorts(leftList, "L")
    fun yRight(fi: Int, item: Int): Float = if (item < n) portY.getValue("$fi|$item|R") else yc[item]
    fun yLeft(fi: Int, item: Int): Float = if (item < n) portY.getValue("$fi|$item|L") else yc[item]

    // --- Gutters: the stretches of every pipe, a lane each (shared where they don't overlap).
    val hops = ArrayList<Hop>()
    val hopsOf = Array(flowEdges.size) { ArrayList<Int>() }
    flowEdges.forEachIndexed { fi, fe ->
        if (col[fe.v] == col[fe.u]) {
            hopsOf[fi].add(hops.size)
            hops.add(Hop(fi, col[fe.u], yRight(fi, fe.u), yRight(fi, fe.v), true))
        } else {
            val path = listOf(fe.u) + fe.chain + fe.v
            for (i in 0 until path.size - 1) {
                hopsOf[fi].add(hops.size)
                hops.add(Hop(fi, itemCol[path[i]], yRight(fi, path[i]), yLeft(fi, path[i + 1]), false))
            }
        }
    }
    val laneOf = IntArray(hops.size) { -1 }
    val laneCount = IntArray(maxCol + 1)
    hops.withIndex()
        .filter { it.value.same || abs(it.value.y1 - it.value.y2) >= 1f }
        .groupBy { it.value.g }
        .forEach { (g, group) ->
            val lo = FloatArray(group.size) { minOf(group[it].value.y1, group[it].value.y2) }
            val hi = FloatArray(group.size) { maxOf(group[it].value.y1, group[it].value.y2) }
            val firstEnds = group.map { iv -> if (iv.value.same) floatArrayOf(iv.value.y1, iv.value.y2) else floatArrayOf(iv.value.y1) }
            val secondEnds = group.map { iv -> if (iv.value.same) floatArrayOf() else floatArrayOf(iv.value.y2) }
            val (lanes, count) = assignLanes(lo, hi, firstEnds, secondEnds)
            group.forEachIndexed { j, iv -> laneOf[iv.index] = lanes[j] }
            laneCount[g] = count
        }
    val gw = FloatArray(maxCol + 1) { g -> if (g < maxCol || laneCount[g] > 0) maxOf(GutterMin, GutterEdge * 2 + LaneStep * (laneCount[g] + 1)) else 0f }
    val colLeft = FloatArray(maxCol + 2)
    colLeft[0] = WorldMargin
    for (c in 0..maxCol) colLeft[c + 1] = colLeft[c] + CardW + gw[c]
    fun laneX(g: Int, lane: Int): Float = colLeft[g] + CardW + GutterEdge + (gw[g] - GutterEdge * 2) * (lane + 1) / (laneCount[g] + 1)

    // --- The pipes.
    val pipes = ArrayList<Pipe>()
    flowEdges.forEachIndexed { fi, fe ->
        val pts = ArrayList<Offset>()
        for (hi in hopsOf[fi]) {
            val h = hops[hi]
            val xr = colLeft[h.g] + CardW
            if (h.same) {
                val lx = laneX(h.g, laneOf[hi])
                pts.add(Offset(xr, h.y1)); pts.add(Offset(lx, h.y1)); pts.add(Offset(lx, h.y2)); pts.add(Offset(xr, h.y2))
            } else {
                val xl = colLeft[h.g + 1]
                if (laneOf[hi] < 0) {
                    pts.add(Offset(xr, h.y1)); pts.add(Offset(xl, h.y1))
                } else {
                    val lx = laneX(h.g, laneOf[hi])
                    pts.add(Offset(xr, h.y1)); pts.add(Offset(lx, h.y1)); pts.add(Offset(lx, h.y2)); pts.add(Offset(xl, h.y2))
                }
            }
        }
        // The path runs from the "from" title to the "to" title so the arrow head lands on the right end.
        val ordered = if (fe.e.from == keys[fe.u]) pts else pts.reversed()
        val clean = ArrayList<Offset>()
        for (p in ordered) if (clean.isEmpty() || (p - clean.last()).getDistance() > 0.5f) clean.add(p)
        if (clean.size >= 2) pipes.add(Pipe(clean, kindOf(fe.e), fe.e.from, fe.e.to))
    }

    val cardPos = HashMap<String, Offset>()
    for (i in 0 until n) cardPos[keys[i]] = Offset(colLeft[col[i]], yc[i] - CardH / 2f)
    val width = colLeft[maxCol] + CardW + gw[maxCol] + WorldMargin
    val height = maxBottom + shiftY + WorldMargin
    val rootCenter = if (rootIdx != null) Offset(colLeft[col[rootIdx]] + CardW / 2f, yc[rootIdx]) else Offset(width / 2f, height / 2f)
    return Flow(cardPos, pipes, width, height, rootCenter)
}

// Polyline with every inner corner rounded off by [radius] (px), so the pipes bend like pipework.
private fun roundedPath(pts: List<Offset>, radius: Float): Path {
    val path = Path()
    path.moveTo(pts[0].x, pts[0].y)
    for (i in 1 until pts.size - 1) {
        val prev = pts[i - 1]
        val cur = pts[i]
        val next = pts[i + 1]
        val d1 = (cur - prev).getDistance()
        val d2 = (next - cur).getDistance()
        val r = minOf(radius, d1 / 2f, d2 / 2f)
        if (r < 0.5f) { path.lineTo(cur.x, cur.y); continue }
        val a = cur - (cur - prev) / d1 * r
        val b = cur + (next - cur) / d2 * r
        path.lineTo(a.x, a.y)
        path.quadraticBezierTo(cur.x, cur.y, b.x, b.y)
    }
    path.lineTo(pts.last().x, pts.last().y)
    return path
}

@Composable fun RelationPipelineScreen(
    vm: LibraryViewModel,
    item: MediaItem,
    onBack: () -> Unit,
    onOpenNode: (RelatedEntry) -> Unit,
) {
    val c = LocalKikoColors.current
    val context = LocalContext.current
    BackHandler(onBack = onBack)
    val kindKey = "${item.type}:${item.id}"
    var reload by remember(item.id, item.type) { mutableIntStateOf(0) }
    var data by remember(item.id, item.type) { mutableStateOf(vm.cachedRelationGraph(item)) }
    var found by remember(item.id, item.type) { mutableIntStateOf(1) }
    var showManga by rememberSaveable(item.id, item.type) { mutableStateOf(savedGraphKind[kindKey] ?: (item.type == MediaType.Manga)) }
    var menuOpen by remember { mutableStateOf(false) }
    var legendOpen by remember { mutableStateOf(false) }
    LaunchedEffect(item.id, item.type, reload) {
        if (data != null) return@LaunchedEffect
        found = 1
        data = vm.buildRelationGraph(context, item) { found = it }
    }
    val rootKey = "${if (item.type == MediaType.Anime) "anime" else "manga"}:${item.id}"
    val graph = data
    val visible = remember(graph, showManga) { graph?.nodes?.filter { (it.malType == "manga") == showManga }.orEmpty() }
    val flow = remember(graph, showManga) { buildFlow(visible, graph?.edges.orEmpty(), rootKey) }
    val kindsPresent = remember(flow) { flow.pipes.map { it.kind }.distinct().sortedBy { it.ordinal } }
    val statusOf: (RelatedEntry) -> WatchStatus? = { n ->
        vm.trackedStatus(n.malId.toString(), if (n.malType == "manga") MediaType.Manga else MediaType.Anime)
    }
    val kindLabel = if (showManga) "manga" else "anime"
    val animeCount = graph?.nodes?.count { it.malType != "manga" } ?: 0
    val mangaCount = graph?.nodes?.count { it.malType == "manga" } ?: 0

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp).padding(top = KikoHeader.BackTop, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.headerEdgeStart()) { Icon(Icons.Rounded.ArrowBack, "Back", tint = c.ink, modifier = Modifier.size(24.dp)) }
            Text(
                "Relation pipelines", style = MaterialTheme.typography.titleLarge, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).headerTitleStart(),
            )
            Row(Modifier.headerEdgeEnd(), verticalAlignment = Alignment.CenterVertically) {
                Box {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Rounded.MoreVert, "More options", tint = c.ink, modifier = Modifier.size(24.dp)) }
                    KikoDropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Refresh") },
                            leadingIcon = { Icon(Icons.Rounded.Refresh, null) },
                            onClick = {
                                menuOpen = false
                                savedGraphView.remove("$kindKey:true"); savedGraphView.remove("$kindKey:false")
                                vm.clearRelationGraph(item); data = null; reload++
                            },
                        )
                    }
                }
            }
        }
        // Anime / Manga half of the franchise, only when the franchise has both.
        if (graph != null && animeCount > 0 && mangaCount > 0) {
            Row(Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !showManga, onClick = { showManga = false; savedGraphKind[kindKey] = false }, label = { Text("Anime · $animeCount") }, colors = kikoFilterChipColors())
                FilterChip(selected = showManga, onClick = { showManga = true; savedGraphKind[kindKey] = true }, label = { Text("Manga · $mangaCount") }, colors = kikoFilterChipColors())
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                graph == null -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    KikoLoadingIndicator(color = c.primary, size = 36.dp)
                    Text("Found $found titles…", color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 14.dp))
                }
                graph.nodes.size <= 1 -> Text("No related titles found.", color = c.muted, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 60.dp))
                visible.isEmpty() -> Text("No related $kindLabel.", color = c.muted, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 60.dp))
                else -> RelationFlowCanvas(
                    viewKey = "$kindKey:$showManga",
                    flow = flow, nodes = visible, rootKey = rootKey,
                    statusOf = statusOf, loadingId = vm.relatedLoadingId, onOpenNode = onOpenNode, onShowLegend = { legendOpen = true },
                )
            }
        }
    }
    if (legendOpen) {
        AlertDialog(
            onDismissRequest = { legendOpen = false },
            confirmButton = { TextButton(onClick = { legendOpen = false }) { Text("Got it") } },
            title = { Text("Reading the map") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Read it from left to right: a pipe leaves the right side of one title and arrives at the left side of the other, and its arrow points forward in the story. The colour is the kind of relation.", color = c.muted, fontSize = 13.sp)
                    for (k in kindsPresent) LegendLine(k.color, k.label)
                    Text("Tap a title to light up its own pipes and the sequels that follow it, all the way to the end of the story; tap an empty spot to go back.", color = c.muted, fontSize = 13.sp)
                    Text("The badge on a cover is your list status:", color = c.muted, fontSize = 13.sp)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LegendBadge(WatchStatus.Watching, "Watching / Reading")
                        LegendBadge(WatchStatus.Completed, "Completed")
                        LegendBadge(WatchStatus.OnHold, "On hold")
                        LegendBadge(WatchStatus.Dropped, "Dropped")
                        LegendBadge(WatchStatus.Plan, "Plan to watch / read")
                    }
                }
            },
        )
    }
}

@Composable private fun LegendLine(color: Color, label: String) {
    val c = LocalKikoColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 30.dp, height = 4.dp).clip(RoundedCornerShape(50)).background(color))
        Text(label, color = c.ink, fontSize = 13.sp, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable private fun LegendBadge(status: WatchStatus, label: String) {
    val c = LocalKikoColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        CoverStatusMark(status)
        Text(label, color = c.ink, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable private fun RelationFlowCanvas(
    viewKey: String,
    flow: Flow,
    nodes: List<RelatedEntry>,
    rootKey: String,
    statusOf: (RelatedEntry) -> WatchStatus?,
    loadingId: Int?,
    onOpenNode: (RelatedEntry) -> Unit,
    onShowLegend: () -> Unit,
) {
    val c = LocalKikoColors.current
    val d = LocalDensity.current.density
    val byKey = remember(nodes) { nodes.associateBy { it.graphKey() } }
    var selected by remember(viewKey) { mutableStateOf<String?>(null) }
    // What lights up for the selected title: all of its own pipes, plus its sequels followed on to the end of the
    // story (the sequel of a sequel of a sequel, ...). Those pipes and titles stay bright, the rest dims.
    val lit = remember(flow, selected) {
        val sel = selected
        val litPipes = HashSet<Pipe>()
        val litKeys = HashSet<String>()
        if (sel != null) {
            litKeys.add(sel)
            for (p in flow.pipes) if (p.fromKey == sel || p.toKey == sel) { litPipes.add(p); litKeys.add(p.fromKey); litKeys.add(p.toKey) }
            val queue = arrayListOf(sel)
            val seen = hashSetOf(sel)
            var qh = 0
            while (qh < queue.size) {
                val cur = queue[qh++]
                for (p in flow.pipes) {
                    if (p.kind != RelKind.Sequel || p.fromKey != cur) continue
                    litPipes.add(p)
                    litKeys.add(p.toKey)
                    if (seen.add(p.toKey)) queue.add(p.toKey)
                }
            }
        }
        litPipes to litKeys
    }
    val litPipes = lit.first
    val litKeys = lit.second
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val saved = savedGraphView[viewKey]
    var scale by remember(viewKey) { mutableFloatStateOf(saved?.get(0) ?: InitialScale) }
    var offset by remember(viewKey) { mutableStateOf(if (saved != null) Offset(saved[1], saved[2]) else Offset.Zero) }
    fun persist() { savedGraphView[viewKey] = floatArrayOf(scale, offset.x, offset.y) }
    // Fits as much of the map as stays readable, centred on the title you came from.
    fun centerOnRoot() {
        val fit = minOf(viewport.width / (flow.width * d), viewport.height / (flow.height * d))
        scale = fit.coerceIn(0.4f, InitialScale)
        offset = Offset(viewport.width / 2f - flow.rootCenter.x * d * scale, viewport.height / 2f - flow.rootCenter.y * d * scale)
        persist()
    }
    // Only the first time: coming back to the map keeps where you had panned / zoomed to.
    LaunchedEffect(flow, viewport, viewKey) { if (viewport != IntSize.Zero && savedGraphView[viewKey] == null) centerOnRoot() }
    val bgColor = c.background
    val ringColor = c.primary

    Box(
        Modifier.fillMaxSize().clipToBounds()
            .onSizeChanged { viewport = it }
            .pointerInput(viewKey) { detectTapGestures(onTap = { selected = null }) }
            .pointerInput(viewKey) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(0.25f, 2.5f)
                    val factor = newScale / scale
                    // Zoom around the fingers: the point under the centroid stays under it.
                    offset = centroid - (centroid - offset) * factor + pan
                    scale = newScale
                    persist()
                }
            },
    ) {
        Box(
            Modifier.layout { measurable, constraints ->
                val placeable = measurable.measure(Constraints.fixed((flow.width * d).roundToInt(), (flow.height * d).roundToInt()))
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeable.placeWithLayer(0, 0) {
                        transformOrigin = TransformOrigin(0f, 0f)
                        scaleX = scale; scaleY = scale
                        translationX = offset.x; translationY = offset.y
                    }
                }
            },
        ) {
            // The pipes, under the cards. Each is first drawn wider in the background colour, so where two cross the
            // one drawn later is clearly on top. With a title selected its pipes are drawn last, bright and thick,
            // and all the others fade.
            Canvas(Modifier.fillMaxSize()) {
                val sel = selected
                val ordered = if (sel == null) flow.pipes else flow.pipes.sortedBy { if (it in litPipes) 1 else 0 }
                for (p in ordered) {
                    val on = sel == null || p in litPipes
                    val alpha = if (on) 1f else 0.13f
                    val strokeW = (if (sel != null && on) 5.5f else 4f) * density
                    val px = p.pts.map { it * density }
                    val tip = px.last()
                    val toTip = tip - px[px.size - 2]
                    val len = toTip.getDistance()
                    if (len < 1f) continue
                    val dir = toTip / len
                    val normal = Offset(-dir.y, dir.x)
                    val path = roundedPath(px, 16f * density)
                    val back = tip - dir * (10f * density)
                    val spread = 8f * density
                    val chevron = Path().apply {
                        moveTo(back.x + normal.x * spread, back.y + normal.y * spread)
                        lineTo(tip.x, tip.y)
                        lineTo(back.x - normal.x * spread, back.y - normal.y * spread)
                    }
                    val casing = Stroke(width = strokeW + 5f * density, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    val line = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    drawPath(path, bgColor, style = casing)
                    drawPath(path, p.kind.color.copy(alpha = alpha), style = line)
                    drawPath(chevron, bgColor, style = casing)
                    drawPath(chevron, p.kind.color.copy(alpha = alpha), style = line)
                }
            }
            for (n in nodes) {
                val k = n.graphKey()
                val pos = flow.cardPos[k] ?: continue
                key(k) {
                    RelationGraphCard(
                        entry = n, isRoot = k == rootKey, isSelected = k == selected, dim = selected != null && k !in litKeys,
                        status = statusOf(n), ringColor = ringColor,
                        loading = n.malId > 0 && loadingId == n.malId,
                        onClick = { selected = if (selected == k) null else k },
                        modifier = Modifier.offset { IntOffset((pos.x * d).roundToInt(), (pos.y * d).roundToInt()) },
                    )
                }
            }
        }
        val sel = selected
        val selEntry = if (sel != null) byKey[sel] else null
        if (selEntry != null) {
            SelectedBar(
                entry = selEntry, canOpen = sel != rootKey, onOpen = { onOpenNode(selEntry) },
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 16.dp, end = 72.dp, bottom = 24.dp),
            )
        }
        // Two small buttons stacked at the bottom right: centre on top, info under it.
        Column(
            Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MapButton(Icons.Rounded.MyLocation, "Center on this title") { centerOnRoot() }
            MapButton(Icons.Rounded.Info, "How to read the map", onShowLegend)
        }
    }
}

// A small round-square button for the map's corner.
@Composable private fun MapButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    val c = LocalKikoColors.current
    Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(kikoCorner(14.dp))).background(c.primaryContainer).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, tint = c.onPrimaryContainer, modifier = Modifier.size(22.dp)) }
}

// Shown while a title is selected: its name, and a small icon button to open it.
@Composable private fun SelectedBar(entry: RelatedEntry, canOpen: Boolean, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalKikoColors.current
    Row(
        modifier.height(36.dp).clip(RoundedCornerShape(kikoCorner(18.dp))).background(c.primaryContainer).padding(start = 14.dp, end = if (canOpen) 3.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            entry.displayTitle(), color = c.onPrimaryContainer, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (canOpen) {
            Box(
                Modifier.padding(start = 8.dp).size(30.dp).clip(RoundedCornerShape(50)).background(c.onPrimaryContainer.copy(alpha = 0.12f)).clickable(onClick = onOpen),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.OpenInNew, "Open title", tint = c.onPrimaryContainer, modifier = Modifier.size(16.dp)) }
        }
    }
}

// Same card as Detail's Related row; only the selected title gets a primary outline (the title you came from is
// marked by its "This title" label alone, so it doesn't look as if it were tapped).
@Composable private fun RelationGraphCard(
    entry: RelatedEntry, isRoot: Boolean, isSelected: Boolean, dim: Boolean, status: WatchStatus?, ringColor: Color,
    loading: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier,
) {
    val title = entry.displayTitle()
    val ringRadius = with(LocalDensity.current) { kikoCorner(14.dp).toPx() }
    val ring = isSelected
    Box(
        modifier
            .graphicsLayer { alpha = if (dim) 0.35f else 1f }
            .drawBehind {
                if (ring) {
                    val out = 4.dp.toPx()
                    drawRoundRect(
                        color = ringColor, topLeft = Offset(-out, -out), size = Size(size.width + out * 2, size.height + out * 2),
                        cornerRadius = CornerRadius(ringRadius + out), style = Stroke(width = 3.dp.toPx()),
                    )
                }
            },
    ) {
        DetailRowCard(
            imageUrl = entry.cover, fallbackLetter = title.take(1), title = title,
            label = if (isRoot) "This title" else entry.relation.ifBlank { entry.malType.replaceFirstChar { it.uppercase() } },
            loading = loading, onClick = onClick, myStatus = status,
            cardWidth = CardW.dp, textBlockHeight = TextH.dp, coverBottomCorner = 14.dp,
        )
    }
}