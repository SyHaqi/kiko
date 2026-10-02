@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.kiko.tracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.kiko.tracker.ui.theme.LocalKikoColors
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

// Swipeable tabs + pages, built the same way My List's status strip is: M3
// scrollable primary tabs whose indicator follows the pager's live position, with
// a HorizontalPager below. `selected` is the screen's own filter value; a tab tap
// calls onSelect right away and animates the pager, a finished swipe (settledPage,
// not currentPage, so it doesn't fight the gesture) calls onSelect for the page
// it landed on. Call it inside a Column — the pager takes the remaining height.
@Composable fun <T> ColumnScope.KikoTabPager(
    items: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    tab: @Composable (item: T, selected: Boolean) -> Unit,
    page: @Composable (item: T) -> Unit,
) {
    val currentItems by rememberUpdatedState(items)
    val currentSelected by rememberUpdatedState(selected)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val selectedIndex = items.indexOf(selected).coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = selectedIndex) { currentItems.size }
    val scope = rememberCoroutineScope()

    // Selection changed from outside the pager (a tab tap) -> scroll to it.
    LaunchedEffect(selectedIndex) {
        if (pagerState.currentPage != selectedIndex) pagerState.animateScrollToPage(selectedIndex)
    }
    // A swipe finished -> adopt that page as the selection.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.distinctUntilChanged().collect { p ->
            val item = currentItems.getOrNull(p) ?: return@collect
            if (item != currentSelected) currentOnSelect(item)
        }
    }

    KikoTabRow(
        count = items.size,
        pagerState = pagerState,
        modifier = modifier,
        onSelect = { i ->
            currentItems.getOrNull(i)?.let { currentOnSelect(it) }
            scope.launch { pagerState.animateScrollToPage(i) }
        },
    ) { index, isSelected -> items.getOrNull(index)?.let { tab(it, isSelected) } }

    HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth(), beyondViewportPageCount = 1) { p ->
        items.getOrNull(p)?.let { page(it) }
    }
}

// Same tab strip as StatusTabRow (HomeScreen.kt), with a content slot so a tab can
// hold more than text (e.g. the score stars).
@Composable fun KikoTabRow(
    count: Int,
    pagerState: PagerState,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit,
    content: @Composable (index: Int, selected: Boolean) -> Unit,
) {
    val c = LocalKikoColors.current
    val density = LocalDensity.current
    val textWidths = remember(count) { mutableStateMapOf<Int, Dp>() }
    val liveIndex by remember(count) {
        derivedStateOf { (pagerState.currentPage + pagerState.currentPageOffsetFraction).roundToInt().coerceIn(0, (count - 1).coerceAtLeast(0)) }
    }

    ScrollableTabRow(
        selectedTabIndex = liveIndex,
        modifier = modifier,
        containerColor = Color.Transparent,
        contentColor = c.primary,
        edgePadding = 14.dp,
        divider = { HorizontalDivider(color = c.outlineVariant) },
        indicator = { tabPositions ->
            if (tabPositions.isNotEmpty()) {
                val rawPage = (pagerState.currentPage + pagerState.currentPageOffsetFraction).coerceIn(0f, (tabPositions.size - 1).toFloat())
                val from = floor(rawPage).toInt().coerceIn(0, tabPositions.size - 1)
                val to = (from + 1).coerceAtMost(tabPositions.size - 1)
                val frac = rawPage - from
                val fromWidth = textWidths[from] ?: tabPositions[from].width
                val toWidth = textWidths[to] ?: tabPositions[to].width
                val fromOffset = tabPositions[from].left + (tabPositions[from].width - fromWidth) / 2
                val toOffset = tabPositions[to].left + (tabPositions[to].width - toWidth) / 2
                Box(
                    Modifier
                        .fillMaxWidth()
                        .wrapContentSize(Alignment.BottomStart)
                        .offset(x = lerp(fromOffset, toOffset, frac))
                        .width(lerp(fromWidth, toWidth, frac))
                        .height(3.dp)
                        .background(c.primary, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)),
                )
            }
        },
    ) {
        repeat(count) { index ->
            val isSelected = index == liveIndex
            Tab(
                selected = isSelected,
                onClick = { onSelect(index) },
                selectedContentColor = c.primary,
                unselectedContentColor = c.muted,
                text = {
                    Box(Modifier.onGloballyPositioned { coords ->
                        val width = with(density) { coords.size.width.toDp() }
                        if (textWidths[index] != width) textWidths[index] = width
                    }) { content(index, isSelected) }
                },
            )
        }
    }
}

@Composable fun KikoTabText(label: String, selected: Boolean) {
    Text(label, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
}