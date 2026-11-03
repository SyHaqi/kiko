@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.kiko.tracker.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiko.tracker.data.api.MAL_REVIEW_MIN_CHARS
import com.kiko.tracker.data.api.MAL_REVIEW_MIN_WORDS
import com.kiko.tracker.data.api.MalReviewApi
import com.kiko.tracker.data.api.MalReviewFeeling
import com.kiko.tracker.data.api.MalReviewForm
import com.kiko.tracker.data.api.MalSessionCookie
import com.kiko.tracker.data.api.MalSessionExpired
import com.kiko.tracker.data.api.malReviewWordCount
import com.kiko.tracker.data.model.MediaItem
import com.kiko.tracker.data.model.displayTitle
import com.kiko.tracker.data.model.onSentiment
import com.kiko.tracker.data.model.reviewScoreColor
import com.kiko.tracker.data.model.sentimentGood
import com.kiko.tracker.data.model.verdictColor
import com.kiko.tracker.data.model.verdictIcon
import com.kiko.tracker.ui.components.KikoHeader
import com.kiko.tracker.ui.components.KikoLoadingIndicator
import com.kiko.tracker.ui.components.MalLoginWebView
import com.kiko.tracker.ui.components.headerEdgeStart
import com.kiko.tracker.ui.components.HeaderTitleSubtitle
import com.kiko.tracker.ui.theme.LocalKikoColors
import com.kiko.tracker.ui.theme.kikoClickable
import com.kiko.tracker.ui.theme.kikoCorner
import com.kiko.tracker.ui.theme.kikoPillShape
import kotlinx.coroutines.launch

// MAL's score captions (the labels its own form shows under the 1-10 picker).
private val ReviewScoreLabels = mapOf(
    10 to "Masterpiece", 9 to "Great", 8 to "Very Good", 7 to "Good", 6 to "Fine",
    5 to "Average", 4 to "Bad", 3 to "Very Bad", 2 to "Horrible", 1 to "Appalling",
)

// Entry row for Detail's Reviews section — shown whether or not the title has any reviews yet,
// since the Reviews row itself is hidden for a title with none and "See more" only appears past
// three, so neither of those can be the only way in.
@Composable fun WriteReviewRow(hasReviews: Boolean, onClick: () -> Unit) {
    val c = LocalKikoColors.current
    Row(
        Modifier
            .padding(top = if (hasReviews) 12.dp else 28.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(kikoCorner(18.dp)))
            .background(c.surfaceLow)
            .kikoClickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Edit, null, tint = c.primary, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text("Write a review", color = c.ink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text("Published to your MAL profile", color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

// Full-page review editor, posting through MalReviewApi (needs the website session cookie, same
// connect-then-retry shape as the forum reply bar). The draft lives in rememberSaveable so it
// survives rotation/process death; backing out with text in it asks before discarding.
@Composable fun WriteReviewScreen(item: MediaItem, onBack: () -> Unit, onPosted: () -> Unit) {
    val c = LocalKikoColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val malId = item.id.toIntOrNull()

    val session = remember { MalSessionCookie(context) }
    var connected by remember { mutableStateOf(session.has()) }
    var showLogin by remember { mutableStateOf(false) }
    var verifyingLogin by remember { mutableStateOf(false) }

    var form by remember(item.id, item.type) { mutableStateOf<MalReviewForm?>(null) }
    var loading by remember(item.id, item.type) { mutableStateOf(true) }
    var loadError by remember(item.id, item.type) { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }

    var text by rememberSaveable(item.id, item.type) { mutableStateOf("") }
    var score by rememberSaveable(item.id, item.type) { mutableIntStateOf(0) }
    // MAL's form leaves both unselected and makes the user pick; here they start on the neutral answers
    // (Mixed Feelings = 2, no spoilers = 0) so the buttons always have a selection. An existing review's own
    // answers replace these when the form loads (see the prefill below).
    var feeling by rememberSaveable(item.id, item.type) { mutableIntStateOf(MalReviewFeeling.MixedFeelings) }
    var spoiler by rememberSaveable(item.id, item.type) { mutableIntStateOf(0) }
    var prefilled by rememberSaveable(item.id, item.type) { mutableStateOf(false) }

    var posting by remember { mutableStateOf(false) }
    var postError by remember { mutableStateOf<String?>(null) }
    var showDiscard by remember { mutableStateOf(false) }

    LaunchedEffect(item.id, item.type, connected, reloadKey) {
        if (!connected) { loading = false; return@LaunchedEffect }
        if (malId == null) { loading = false; loadError = "This title isn't a MAL entry, so it can't be reviewed."; return@LaunchedEffect }
        loading = true
        loadError = null
        runCatching { MalReviewApi(context).loadForm(malId, item.type) }
            .onSuccess { f ->
                form = f
                // Seed once from what MAL pre-filled (list score; the old review if one exists) —
                // never over text the user has already typed.
                if (!prefilled) {
                    if (text.isBlank()) text = f.existingText
                    if (score == 0) score = f.existingScore
                    // Only an already-published review carries these; otherwise keep the defaults.
                    if (f.existingFeeling in 1..3) feeling = f.existingFeeling
                    f.existingSpoiler?.let { spoiler = if (it) 1 else 0 }
                    prefilled = true
                }
            }
            .onFailure { e ->
                if (e is MalSessionExpired) { connected = false; session.clear() }
                else loadError = e.message ?: "Could not open MAL's review form"
            }
        loading = false
    }

    val dirty = text.isNotBlank()
    val tryBack = { if (dirty && !posting) showDiscard = true else onBack() }
    BackHandler(enabled = !showLogin, onBack = tryBack)
    BackHandler(enabled = showLogin) { showLogin = false }

    if (showLogin) {
        Box(Modifier.fillMaxSize()) {
            MalLoginWebView(
                session = session,
                onLoginSuccess = { showLogin = false; connected = true },
                onVerifyingChange = { verifyingLogin = it },
                modifier = Modifier.fillMaxSize(),
            )
            if (verifyingLogin) {
                Box(Modifier.fillMaxSize().background(c.surface.copy(alpha = 0.92f)), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        KikoLoadingIndicator(color = c.primary)
                        Text("Confirming your MAL login…", color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 14.dp))
                    }
                }
            }
        }
        return
    }

    val words = malReviewWordCount(text)
    val chars = text.trim().length
    val lengthOk = words >= MAL_REVIEW_MIN_WORDS && chars >= MAL_REVIEW_MIN_CHARS
    val f = form
    val canPublish = connected && f != null && f.blockedReason == null && lengthOk &&
            score in 1..10 && feeling in 1..3 && spoiler in 0..1 && !posting

    fun publish() {
        val target = form ?: return
        if (!canPublish) return
        posting = true
        postError = null
        scope.launch {
            runCatching { MalReviewApi(context).submit(target, text.trim(), score, feeling, spoiler == 1) }
                .onSuccess {
                    Toast.makeText(context, "Review published", Toast.LENGTH_SHORT).show()
                    onPosted()
                }
                .onFailure { e ->
                    if (e is MalSessionExpired) { connected = false; session.clear() }
                    else postError = e.message ?: "Could not publish the review"
                }
            posting = false
        }
    }

    if (showDiscard) {
        AlertDialog(
            onDismissRequest = { showDiscard = false },
            containerColor = c.surfaceContainerHigh,
            title = { Text("Discard this review?", color = c.ink) },
            text = { Text("Your draft hasn't been published and will be lost.", color = c.muted) },
            confirmButton = { TextButton(onClick = { showDiscard = false; onBack() }) { Text("Discard", color = c.danger) } },
            dismissButton = { TextButton(onClick = { showDiscard = false }) { Text("Keep editing") } },
        )
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp).padding(top = KikoHeader.BackTop, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = tryBack, modifier = Modifier.headerEdgeStart()) {
                Icon(Icons.Rounded.ArrowBack, "Back", tint = c.ink, modifier = Modifier.size(24.dp))
            }
            HeaderTitleSubtitle("Write a Review", item.displayTitle(), Modifier.weight(1f))
            Box(
                Modifier.padding(start = 8.dp).height(36.dp)
                    .clip(kikoPillShape())
                    .background(if (canPublish) c.primary else c.surfaceLow)
                    .clickable(enabled = canPublish, onClick = ::publish)
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (posting) KikoLoadingIndicator(color = c.onPrimary, size = 20.dp)
                else Text("Publish", color = if (canPublish) c.onPrimary else c.muted, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        Box(Modifier.weight(1f).consumeWindowInsets(WindowInsets.navigationBars).imePadding()) {
            when {
                !connected -> ReviewCenteredMessage(
                    "Connect your MAL account to write a review.",
                    action = "Connect", onAction = { showLogin = true },
                )
                loading && f == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    KikoLoadingIndicator(color = c.primary, size = 28.dp)
                }
                loadError != null && f == null -> ReviewCenteredMessage(loadError!!, action = "Try again", onAction = { reloadKey++ })
                f?.blockedReason != null -> ReviewCenteredMessage(f?.blockedReason.orEmpty())
                else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp).padding(bottom = 28.dp)) {
                    // ── Review text ──
                    ReviewFieldLabel("Review", "required")
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(kikoCorner(18.dp))).background(c.surfaceContainerHigh)
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                    ) {
                        BasicTextField(
                            value = text,
                            onValueChange = { text = it },
                            enabled = !posting,
                            minLines = 10,
                            textStyle = LocalTextStyle.current.copy(fontSize = 15.sp, lineHeight = 22.sp, color = c.ink),
                            cursorBrush = SolidColor(c.primary),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { inner ->
                                Box(Modifier.fillMaxWidth()) {
                                    if (text.isEmpty()) Text(
                                        "What stood out, what didn't, and who would enjoy it? Your first lines are what readers see before “read more”.",
                                        color = c.muted, fontSize = 15.sp, lineHeight = 22.sp,
                                    )
                                    inner()
                                }
                            },
                        )
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp, start = 4.dp)) {
                        ReviewCounter("$words / $MAL_REVIEW_MIN_WORDS words", words >= MAL_REVIEW_MIN_WORDS)
                        Spacer(Modifier.width(14.dp))
                        ReviewCounter("$chars / $MAL_REVIEW_MIN_CHARS characters", chars >= MAL_REVIEW_MIN_CHARS)
                    }
                    Text(
                        "MAL requires at least $MAL_REVIEW_MIN_WORDS words and $MAL_REVIEW_MIN_CHARS characters.",
                        color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp, start = 4.dp),
                    )

                    // ── Overall rating ──
                    ReviewFieldLabel("Overall rating", "required", Modifier.padding(top = 26.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        (10 downTo 1).forEach { n ->
                            val selected = score == n
                            val tint = reviewScoreColor(n, c)
                            Box(
                                Modifier.size(44.dp).clip(RoundedCornerShape(kikoCorner(14.dp)))
                                    .background(if (selected) tint else c.surfaceContainerHigh)
                                    .clickable(enabled = !posting) { score = n },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(n.toString(), color = if (selected) onSentiment(tint) else c.ink, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                        }
                    }
                    Text(
                        if (score in 1..10) "$score · ${ReviewScoreLabels[score]}" else "Pre-filled from your list score when you have one.",
                        color = if (score in 1..10) reviewScoreColor(score, c) else c.muted, fontWeight = if (score in 1..10) FontWeight.Bold else FontWeight.Normal, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                    )

                    // ── Verdict ──
                    ReviewFieldLabel("Would you recommend this?", "required", Modifier.padding(top = 26.dp))
                    ReviewChoiceRow(
                        options = listOf("Recommended", "Mixed Feelings", "Not Recommended"),
                        selected = feeling - 1, enabled = !posting,
                        onSelect = { feeling = it + 1 },
                        icons = listOf("Recommended", "Mixed Feelings", "Not Recommended").map { verdictIcon(it) },
                        tints = listOf("Recommended", "Mixed Feelings", "Not Recommended").map { verdictColor(it, c) },
                        iconOnly = true,
                    )
                    // Icon-only buttons: name the one that's picked.
                    val verdictNames = listOf("Recommended", "Mixed Feelings", "Not Recommended")
                    Text(
                        if (feeling in 1..3) verdictNames[feeling - 1] else "",
                        color = if (feeling in 1..3) verdictColor(verdictNames[feeling - 1], c) else c.muted,
                        fontWeight = if (feeling in 1..3) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                    )

                    // ── Spoilers ──
                    ReviewFieldLabel("Contains spoilers?", "required", Modifier.padding(top = 26.dp))
                    ReviewChoiceRow(
                        options = listOf("Yes", "No"),
                        selected = when (spoiler) { 1 -> 0; 0 -> 1; else -> -1 }, enabled = !posting,
                        onSelect = { spoiler = if (it == 0) 1 else 0 },
                        icons = listOf(Icons.Rounded.VisibilityOff, Icons.Rounded.Visibility),
                        tints = listOf(c.danger, sentimentGood(c)),
                    )
                    Text(
                        "Choose “Yes” if it reveals plot details that would ruin the surprise.",
                        color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                    )

                    postError?.let {
                        Text(it, color = c.danger, fontSize = 13.sp, modifier = Modifier.padding(top = 22.dp, start = 4.dp))
                    }
                }
            }
        }
    }
}

@Composable private fun ReviewFieldLabel(title: String, note: String = "", modifier: Modifier = Modifier) {
    val c = LocalKikoColors.current
    Row(modifier.padding(bottom = 10.dp, start = 4.dp), verticalAlignment = Alignment.Bottom) {
        Text(title, color = c.ink, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        if (note.isNotBlank()) Text(note, color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp, bottom = 1.dp))
    }
}

@Composable private fun ReviewCounter(label: String, met: Boolean) {
    val c = LocalKikoColors.current
    Text(label, color = if (met) c.primary else c.muted, fontWeight = if (met) FontWeight.Bold else FontWeight.Normal, fontSize = 12.sp)
}

// Material 3 Expressive connected button group (single-select), built like the Stats card's Anime/Manga switch:
// ToggleButtons spaced by ButtonGroupDefaults.ConnectedSpaceBetween with the stock leading/middle/trailing shapes.
// Flat colors: unchecked = surfaceContainer, checked = the option's own solid color (primary when it has none).
// selected = -1 means nothing picked (unused now that both groups default to a choice). iconOnly buttons show just
// the icon (the label stays as the icon's content description for screen readers).
@Composable private fun ReviewChoiceRow(
    options: List<String>, selected: Int, enabled: Boolean, onSelect: (Int) -> Unit,
    icons: List<ImageVector> = emptyList(), tints: List<Color> = emptyList(), iconOnly: Boolean = false,
) {
    val c = LocalKikoColors.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        options.forEachIndexed { i, label ->
            val tint = tints.getOrNull(i)
            val icon = icons.getOrNull(i)
            val button: @Composable (Modifier) -> Unit = { mod ->
                ToggleButton(
                    checked = i == selected,
                    onCheckedChange = { onSelect(i) },
                    enabled = enabled,
                    modifier = mod.height(52.dp).semantics { role = Role.RadioButton },
                    shapes = when (i) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                    colors = ToggleButtonDefaults.toggleButtonColors(
                        containerColor = c.surfaceContainer,
                        contentColor = c.onSurfaceVariant,
                        checkedContainerColor = tint ?: c.primary,
                        checkedContentColor = tint?.let { onSentiment(it) } ?: c.onPrimary,
                    ),
                ) {
                    if (icon != null) Icon(icon, if (iconOnly) label else null, modifier = Modifier.size(if (iconOnly) 24.dp else 18.dp))
                    if (!iconOnly) Text(label, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, modifier = Modifier.padding(start = if (icon != null) 8.dp else 0.dp))
                }
            }
            button(Modifier.weight(1f))
        }
    }
}

@Composable private fun ReviewCenteredMessage(message: String, action: String? = null, onAction: () -> Unit = {}) {
    val c = LocalKikoColors.current
    Column(Modifier.fillMaxSize().padding(horizontal = 32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(message, color = c.muted, fontSize = 14.sp, lineHeight = 20.sp, textAlign = TextAlign.Center)
        if (action != null) TextButton(onClick = onAction, modifier = Modifier.padding(top = 8.dp)) { Text(action) }
    }
}