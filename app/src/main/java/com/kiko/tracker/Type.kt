package com.kiko.tracker

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ---------------------------------------------------------------------------
// Material 3 Expressive type scale.
//
// The app previously only defined 5 of the 15 M3 text styles, so every other
// role (bodyLarge, labelSmall, headlineLarge, etc.) silently fell back to
// Compose's plain default Typography — meaning most on-screen text wasn't
// actually reading the app's own type system at all. This defines the full
// scale, and leans into Expressive's signature move: noticeably heavier,
// tighter-tracked display/headline/title weights against lighter, roomier
// body/label weights, for a much stronger size-and-weight contrast than
// classic M3's more uniform Regular/Medium split.
//
// There's no bundled Google Sans/Roboto Flex in this project, so AppFont
// (system sans-serif) carries the whole scale — the contrast comes from
// weight and tracking rather than a variable-width grade axis. Dropping in
// a real variable font later is a drop-in upgrade: only the fontFamily
// argument below would need to change.
// ---------------------------------------------------------------------------

val KikoTypography = Typography(
    displayLarge = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Bold, fontSize = 57.sp, lineHeight = 62.sp, letterSpacing = (-0.5).sp),
    displayMedium = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Bold, fontSize = 45.sp, lineHeight = 50.sp, letterSpacing = (-0.25).sp),
    displaySmall = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 42.sp),

    headlineLarge = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 38.sp),
    headlineMedium = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp),
    headlineSmall = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp),

    titleLarge = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.1.sp),
    titleSmall = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),

    bodyLarge = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp),
    bodyMedium = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.15.sp),
    bodySmall = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.2.sp),

    labelLarge = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.3.sp),
    labelSmall = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.3.sp),
)
