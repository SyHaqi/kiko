@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.kiko.tracker.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import com.kiko.tracker.ui.theme.LocalKikoColors
import androidx.compose.runtime.Composable
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
 * Pull-to-refresh whose indicator is the expressive morphing LoadingIndicator, so a refresh
 * shows the same loader as everything else. Pages that use this must NOT also draw their own
 * KikoLoadingRow for the same loading flag, or two loaders show at once.
 */
@Composable
fun KikoPullToRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val c = LocalKikoColors.current
    val state = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
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
