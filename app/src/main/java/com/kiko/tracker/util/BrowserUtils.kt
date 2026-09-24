package com.kiko.tracker.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent

/**
 * The one place every "Open in browser" action goes through.
 *
 * Why not just `uriHandler.openUri(url)` / an unpinned `CustomTabsIntent.launchUrl`: both fire an
 * *implicit* ACTION_VIEW, and Kiko's own manifest claims myanimelist.net/anime and /manga links
 * (so title links open in-app). Android will therefore happily route an "Open in browser" tap for
 * exactly those pages (anime/manga detail, company pages under /anime/producer/...) straight back
 * into Kiko. Pinning the intent to a browser's package name makes it explicit, so it can only be
 * delivered to that browser and never to Kiko's intent filter.
 *
 * Order: Custom Tab pinned to the default browser (or best Custom Tabs provider) -> plain VIEW
 * pinned to any installed browser -> toast. Returns whether something was launched.
 */
fun Context.openInBrowser(url: String): Boolean {
    val uri = Uri.parse(url.trim())
    val browser = findBrowserPackage()

    val tab = CustomTabsIntent.Builder().build()
    if (browser != null) tab.intent.setPackage(browser)
    // Compose's LocalContext is often a ContextWrapper around the Activity; only a context with no
    // Activity underneath needs NEW_TASK (adding it otherwise would start the tab in its own task).
    if (findActivity() == null) tab.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val tabResult = runCatching { tab.launchUrl(this, uri) }
    if (tabResult.isSuccess) return true
    Log.w("OpenInBrowser", "custom tab failed for $url (browser=$browser)", tabResult.exceptionOrNull())

    if (browser != null) {
        val view = Intent(Intent.ACTION_VIEW, uri).setPackage(browser)
        if (findActivity() == null) view.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val viewResult = runCatching { startActivity(view) }
        if (viewResult.isSuccess) return true
        Log.w("OpenInBrowser", "plain VIEW failed for $url (browser=$browser)", viewResult.exceptionOrNull())
    }

    Toast.makeText(this, "Couldn't open link in a browser", Toast.LENGTH_SHORT).show()
    return false
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

// Default browser if it supports Custom Tabs, otherwise the best installed Custom Tabs provider;
// failing that, any browser that isn't Kiko itself. Needs the <queries> entries in the manifest.
@Suppress("DEPRECATION")
private fun Context.findBrowserPackage(): String? {
    runCatching { CustomTabsClient.getPackageName(this, null) }.getOrNull()?.let { return it }
    val probe = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")).addCategory(Intent.CATEGORY_BROWSABLE)
    return runCatching {
        packageManager.queryIntentActivities(probe, PackageManager.MATCH_ALL)
            .map { it.activityInfo.packageName }
            .firstOrNull { it != packageName }
    }.getOrNull()
}
