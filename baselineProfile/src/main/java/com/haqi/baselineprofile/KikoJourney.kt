package com.haqi.baselineprofile

import android.os.SystemClock
import android.util.Log
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until

private fun step(msg: String) { Log.w("KikoJourney", msg) }

// Logs BEGIN before the call and END (with elapsed ms) after it.
// A BEGIN with no END means the call is stuck.
private inline fun <T> timed(label: String, block: () -> T): T {
    val t0 = SystemClock.elapsedRealtime()
    step("BEGIN $label")
    try {
        return block()
    } finally {
        step("END $label ${SystemClock.elapsedRealtime() - t0}ms")
    }
}

internal fun MacrobenchmarkScope.scrollScreen() {
    val x = device.displayWidth / 2
    val top = (device.displayHeight * 0.30).toInt()
    val bottom = (device.displayHeight * 0.75).toInt()
    repeat(3) {
        timed("swipe up") { device.swipe(x, bottom, x, top, 25) }
        timed("idle") { device.waitForIdle(1000) }
    }
    repeat(2) {
        timed("swipe down") { device.swipe(x, top, x, bottom, 25) }
        timed("idle") { device.waitForIdle(1000) }
    }
}

internal fun MacrobenchmarkScope.openNav(label: String) {
    step("open nav: $label")
    val node = timed("find nav $label") {
        device.findObjects(By.text(label)).maxByOrNull { it.visibleCenter.y }
    } ?: error("Bottom-nav item '$label' not found")
    timed("click nav $label") { node.click() }
    timed("idle after nav") { device.waitForIdle(1000) }
    Thread.sleep(1500)
}

internal fun MacrobenchmarkScope.kikoJourney() {
    step("journey start")
    check(device.wait(Until.hasObject(By.text("My list")), 15_000) == true) { "Kiko main screen never appeared" }
    Thread.sleep(2000)
    scrollScreen() // Home

    openNav("My list")
    val statuses = listOf("Watching", "Plan to Watch", "Completed", "On Hold", "Dropped", "All")
    val deadline = SystemClock.elapsedRealtime() + 10_000
    while (statuses.none { device.hasObject(By.text(it)) }) {
        check(SystemClock.elapsedRealtime() < deadline) { "My list tabs not found. Is the app logged in?" }
        Thread.sleep(250)
    }
    for (status in statuses) {
        step("tab: $status")
        val tab = timed("find tab $status") {
            device.findObjects(By.text(status)).minByOrNull { it.visibleCenter.y }
        }
        if (tab != null) timed("click tab $status") { tab.click() }
        timed("idle after tab") { device.waitForIdle(1000) }
        Thread.sleep(1000)
        scrollScreen()
    }
    for (label in listOf("Discover", "Seasonal", "Hub", "Home")) {
        openNav(label)
        scrollScreen()
    }
    step("journey end")
}