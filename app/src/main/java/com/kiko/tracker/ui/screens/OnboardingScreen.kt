package com.kiko.tracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiko.tracker.data.api.MalSessionCookie
import com.kiko.tracker.ui.components.MalLoginWebView
import com.kiko.tracker.ui.theme.LocalKikoColors

/**
 * First screen on a fresh install, shown once (see LibraryViewModel's
 * onboardingSeen/markOnboardingSeen — backed by "kiko_settings" like the
 * other one-off prefs). This is where the two separate MAL logins get
 * introduced up front instead of the user tripping over them piecemeal:
 * the OAuth sign-in (real REST API, drives the actual list) and the
 * embedded-cookie login (everything MAL never put in that API — friends,
 * favorites, forum replies; see MalLoginWebView/MalSessionCookie), which
 * previously only surfaced the first time someone opened Friends &
 * Favorites. FriendsFavoritesScreen still has its own "Connect" prompt as
 * a fallback for anyone who skips here or whose cookie session expires
 * later.
 *
 * Stays up until both logins are done (each button greys out to "Signed
 * in" once its side completes) or the user taps Skip — whichever comes
 * first.
 */
@Composable
fun OnboardingScreen(
    malSignedIn: Boolean,
    onSignIn: () -> Unit,
    onFinish: () -> Unit,
) {
    val c = LocalKikoColors.current
    val context = LocalContext.current
    val session = remember { MalSessionCookie(context) }
    var extraConnected by remember { mutableStateOf(session.has()) }
    var showLogin by remember { mutableStateOf(false) }
    var verifyingLogin by remember { mutableStateOf(false) }

    // Onboarding only ends once both logins are done — no separate tap
    // needed for that, it just falls through the moment both are true.
    LaunchedEffect(malSignedIn, extraConnected) {
        if (malSignedIn && extraConnected) onFinish()
    }

    if (showLogin) {
        Box(Modifier.fillMaxSize().background(c.background)) {
            MalLoginWebView(
                session = session,
                onLoginSuccess = { showLogin = false; extraConnected = true },
                onVerifyingChange = { verifyingLogin = it },
                modifier = Modifier.fillMaxSize(),
            )
            // Same cover-the-extra-hop treatment as FriendsFavoritesScreen's
            // login flow — MAL's "are you actually logged in" check page
            // loads in between, so mask it as progress instead of a stall.
            if (verifyingLogin) {
                Box(Modifier.fillMaxSize().background(c.surface.copy(alpha = 0.92f)), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = c.primary)
                }
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.background)
            .padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start,
    ) {
        Text("Welcome", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = c.ink, modifier = Modifier.padding(bottom = 40.dp))

        Text("Track your list", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = c.ink, modifier = Modifier.padding(bottom = 8.dp))
        Button(
            onClick = onSignIn,
            enabled = !malSignedIn,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = c.primary, contentColor = c.onPrimary,
                disabledContainerColor = c.surfaceContainerHigh, disabledContentColor = c.muted,
            ),
        ) { Text(if (malSignedIn) "Signed in" else "Sign in with MyAnimeList") }

        Spacer(Modifier.height(28.dp))

        Text("Unlock everything else", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = c.ink, modifier = Modifier.padding(bottom = 8.dp))
        OutlinedButton(
            onClick = { showLogin = true },
            enabled = !extraConnected,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = c.ink, disabledContentColor = c.muted),
        ) { Text(if (extraConnected) "Signed in" else "Connect extra features") }

        Spacer(Modifier.height(24.dp))
        TextButton(onClick = onFinish, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Skip for now", color = c.muted)
        }
    }
}