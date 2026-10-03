@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.kiko.tracker.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import com.kiko.tracker.ui.theme.LocalKikoColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Material 3 Expressive loading indicator (the morphing-shape one) for indeterminate waits.
 * Used everywhere a spinner / indeterminate bar used to be. Determinate progress bars
 * (download %, watch progress) are untouched.
 * https://m3.material.io/components/loading-indicator/overview
 */
@Composable
fun KikoLoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    size: Dp = 48.dp,
) {
    LoadingIndicator(modifier = modifier.size(size), color = color)
}

/**
 * Page / section loading: a centred loading indicator in place of the old indeterminate
 * linear bar. [modifier] carries the layout (fillMaxWidth + padding) the bar used to have.
 */
@Composable
fun KikoLoadingRow(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    size: Dp = 40.dp,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        LoadingIndicator(modifier = Modifier.size(size), color = color)
    }
}

/**
 * Stand-in for the old skeleton placeholders: one centred loading indicator with generous
 * vertical padding so a list / row / section that is still loading has some height to it.
 */
@Composable
fun KikoSectionLoading(modifier: Modifier = Modifier) {
    KikoLoadingRow(modifier.fillMaxWidth().padding(vertical = 32.dp))
}

/**
 * Full-page loading (detail pages whose data hasn't arrived yet): a back button at the top
 * and the loading indicator centred in the rest of the screen.
 */
@Composable
fun KikoLoadingScreen(onBack: () -> Unit) {
    val c = LocalKikoColors.current
    Box(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 13.dp, bottom = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.headerEdgeStart()) {
                Icon(Icons.Default.ArrowBack, "Back", tint = c.ink, modifier = Modifier.size(24.dp))
            }
        }
        KikoLoadingIndicator(modifier = Modifier.align(Alignment.Center), color = c.primary)
    }
}

/**
 * Pull-to-refresh whose indicator is the expressive morphing LoadingIndicator, so a refresh
 * shows the same loader as everything else. Pages that use this must NOT also draw their own
 * KikoLoadingRow for the same loading flag, or two loaders show at once.
 */
@Composable
fun KikoPullToRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    // true = the pull indicator only appears for a refresh the user started by pulling; loads the
    // screen starts itself (first open, section loaders) never show it.
    onlyWhenPulled: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val c = LocalKikoColors.current
    val state = rememberPullToRefreshState()
    val currentRefreshing by rememberUpdatedState(isRefreshing)
    var pulled by remember { mutableStateOf(false) }
    if (onlyWhenPulled) {
        LaunchedEffect(pulled) {
            if (pulled) {
                // Wait (briefly) for the load to actually start, then for it to finish.
                withTimeoutOrNull(1500) { snapshotFlow { currentRefreshing }.first { it } }
                snapshotFlow { currentRefreshing }.first { !it }
                pulled = false
            }
        }
    }
    PullToRefreshBox(
        isRefreshing = if (onlyWhenPulled) pulled else isRefreshing,
        onRefresh = {
            if (onlyWhenPulled) pulled = true
            onRefresh()
        },
        modifier = modifier,
        state = state,
        indicator = {
            PullToRefreshDefaults.LoadingIndicator(
                state = state,
                isRefreshing = isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter),
                containerColor = c.surfaceLow,
                color = c.primary,
            )
        },
        content = content,
    )
}