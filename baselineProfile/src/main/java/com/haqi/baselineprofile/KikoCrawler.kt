package com.haqi.baselineprofile

import android.os.SystemClock
import android.util.Log
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import java.util.regex.Pattern

/*
 * Baseline-profile crawler: from each bottom-nav tab it taps every clickable thing it can see (cards,
 * chips, dropdown triggers and their menu items, avatar / drawer entries, header buttons, ...), explores
 * whatever opens (pages, sheets, dialogs) a few levels deep, then goes back and carries on. Everything
 * it touches gets compiled into the profile.
 *
 * It must NEVER change account data, so anything whose label looks like an action is skipped (see
 * [DENY]): add / remove / delete / friend-request accept+deny / save / post / reply / rate / favorite /
 * mark read / sign in-out / share / browser links ... plus status names (Watching, Completed, ...),
 * +/- steppers, text fields, and (on the Notifications page) every row, because opening a notification
 * marks it read. Skipped labels are logged as "SKIP" so the list can be reviewed in logcat:
 *     adb logcat -s KikoCrawler
 * Even so, run it on a throw-away / test MAL account if you can: a label this list doesn't know could
 * still be an action.
 *
 * Needs the app to be logged in, same as [kikoJourney].
 */

private const val TAG = "KikoCrawler"

// Each profile-generation iteration stops after this long / this many taps, whichever comes first.
private const val ITERATION_BUDGET_MS = 7 * 60_000L
private const val MAX_TAPS = 220
private const val MAX_DEPTH = 3          // tab -> page -> page-in-page -> one more
private const val MAX_TAPS_PER_LEVEL = 14
private const val MAX_SCROLLS_PER_LEVEL = 2

// Title typed into Discover's search page by the crawler (only adds a local "recent search" entry).
private const val SEARCH_QUERY = "Naruto"

private val NAV_LABELS = listOf("Home", "My list", "Discover", "Seasonal", "Hub")

// Words that mean "this changes something" (or leaves the app). Matched anywhere in the label,
// case-insensitive, on word boundaries.
private val DENY = Regex(
    "\\b(add|remove|delete|unfriend|accept|deny|decline|reject|sign ?out|log ?out|sign ?in|log ?in|" +
            "disconnect|connect|clear|reset|mark|post|send|reply|quote|submit|save|publish|vote|like|unlike|" +
            "rat(e|ing)|install|update|download|upload|report|block|follow|unfollow|subscribe|create|" +
            "import|export|restack|join|leave|share|browser|open in|older on mal|view on mal|" +
            "plus|minus|increase|decrease|increment|decrement|play|youtube|trailers?)\\b",
    RegexOption.IGNORE_CASE,
)
// Whole-label matches: a lone +/- (episode / chapter / score steppers), bare favorite toggles, and the
// list-status names (the My List status tabs are already covered by kikoJourney(); on a detail page the
// same words would change the title's status).
private val DENY_EXACT = Regex(
    "^([+\\-\\u2212\\u2013]\\s?\\d*|(un)?favou?rite(d)?|watching|reading|plan to (watch|read)|completed|" +
            "on[- ]?hold|dropped|re-?watching|re-?reading|back|navigate up|close|dismiss)$",
    RegexOption.IGNORE_CASE,
)
// On the Notifications page only the category tabs are tapped, never a row (opening one marks it read).
private val NOTIFICATION_TABS = Regex(
    "^(All|Friend requests|Forum quotes|User mentions|Watched topics|Club messages|New Related anime|Now airing)(\\s+\\d+\\+?)?$",
    RegexOption.IGNORE_CASE,
)

private class CrawlReset : RuntimeException()

private data class Target(val label: String, val x: Int, val y: Int, val key: String)

private class Crawler(private val s: MacrobenchmarkScope) {
    private val device get() = s.device
    private val pkg get() = s.packageName
    private val visited = HashSet<String>()
    private val loggedSkips = HashSet<String>()
    private var taps = 0
    private val deadline = SystemClock.elapsedRealtime() + ITERATION_BUDGET_MS

    private fun exhausted() = taps >= MAX_TAPS || SystemClock.elapsedRealtime() > deadline

    fun run() {
        // Search first so it always gets its turn before the tap / time budget runs out.
        if (!exhausted()) {
            try {
                searchTitle(SEARCH_QUERY)
            } catch (e: Exception) {
                Log.w(TAG, "search failed: $e - relaunching")
                relaunch()
            }
        }
        for (label in NAV_LABELS) {
            if (exhausted()) break
            try {
                s.openNav(label)
                level(depth = 0, root = label)
            } catch (_: CrawlReset) {
                Log.w(TAG, "left the app - relaunching")
                relaunch()
            } catch (e: Exception) {
                Log.w(TAG, "crawl of '$label' failed: $e - relaunching")
                relaunch()
            }
        }
        Log.w(TAG, "crawl done: $taps taps")
    }

    private fun killExternal(other: String?) {
        if (other.isNullOrBlank() || other == pkg || other == "com.android.systemui" ||
            other == device.launcherPackageName) return
        Log.w(TAG, "force-stop $other")
        runCatching { device.executeShellCommand("am force-stop $other") }
        Thread.sleep(500)
    }

    // Types a title into Discover's search page, submits it and pokes at the results, so the search page,
    // the suggestions list and the results list all end up in the profile. Read-only.
    private fun searchTitle(query: String) {
        Log.w(TAG, "SEARCH $query")
        s.openNav("Discover")
        val bar = device.wait(Until.findObject(By.text("Search in MAL")), 5_000)
        if (bar == null) { Log.w(TAG, "search bar not found"); return }
        bar.click(); taps++
        device.waitForIdle(1000)
        val field = device.wait(Until.findObject(By.clazz("android.widget.EditText").pkg(pkg)), 5_000)
        if (field == null) { Log.w(TAG, "search field not found"); return }
        field.text = query
        device.waitForIdle(1000); Thread.sleep(2500) // let the suggestions load and render

        device.pressEnter() // IME "search" action
        device.waitForIdle(1000); Thread.sleep(2500)
        if (device.hasObject(By.clazz("android.widget.EditText").pkg(pkg))) {
            // Enter didn't submit: tap the first suggestion instead.
            Log.w(TAG, "Enter did not submit - tapping a suggestion")
            device.findObjects(By.pkg(pkg).textContains(query))
                .firstOrNull { it.className != "android.widget.EditText" }?.click()
            device.waitForIdle(1000); Thread.sleep(2500)
        }
        if (device.currentPackageName != pkg) throw CrawlReset()
        if (!device.hasObject(By.clazz("android.widget.EditText").pkg(pkg))) {
            level(depth = 2, root = "Discover") // results page: scroll it, open a result or two
        }
        // Back out to the Discover landing page so the normal crawl of that tab starts clean.
        repeat(4) {
            if (device.hasObject(By.text("Search in MAL"))) return
            device.pressBack(); device.waitForIdle(1000); Thread.sleep(500)
            if (device.currentPackageName != pkg) throw CrawlReset()
        }
    }

    private fun relaunch() {
        s.startActivityAndWait()
        device.wait(Until.hasObject(By.text("My list")), 15_000)
        Thread.sleep(1500)
    }

    private fun level(depth: Int, root: String) {
        var tapsHere = 0
        var scrolls = 0
        while (!exhausted() && tapsHere < MAX_TAPS_PER_LEVEL) {
            val target = targets().firstOrNull { it.key !in visited }
            if (target == null) {
                // Nothing new in view: reveal a bit more of the page once or twice, then move on.
                if (scrolls++ >= MAX_SCROLLS_PER_LEVEL) break
                swipeUp()
                continue
            }
            visited += target.key
            tapsHere++; taps++
            val before = texts()
            val navBefore = navVisible()
            Log.w(TAG, "TAP[d$depth] ${target.label}")
            device.click(target.x, target.y)
            device.waitForIdle(1000)
            Thread.sleep(800)

            if (device.currentPackageName != pkg) { // share sheet / browser / YouTube: get back in
                val other: String? = device.currentPackageName
                device.pressBack(); Thread.sleep(500)
                killExternal(other) // YouTube etc. can linger as a miniplayer / PiP window over Kiko
                if (device.currentPackageName != pkg) throw CrawlReset()
                continue
            }
            // A page / sheet / dialog opened when most of what was on screen is gone. Menus, chip and
            // tab changes keep the page underneath, so those are handled by the next loop pass instead.
            val after = texts()
            val opened = similarity(before, after) < 0.5 || (navBefore && !navVisible())
            if (!opened) continue

            if (depth < MAX_DEPTH) level(depth + 1, root)
            device.pressBack(); device.waitForIdle(1000); Thread.sleep(500)
            if (device.currentPackageName != pkg) throw CrawlReset()
            // Back from a page opened on a tab lands on the tab (or on Home); put the tab back.
            if (depth == 0 && similarity(before, texts()) < 0.8) s.openNav(root)
        }
    }

    private fun swipeUp() {
        val x = device.displayWidth / 2
        device.swipe(x, (device.displayHeight * 0.75).toInt(), x, (device.displayHeight * 0.35).toInt(), 25)
        device.waitForIdle(1000)
    }

    private fun navVisible() = device.hasObject(By.text("My list")) && device.hasObject(By.text("Hub"))

    private fun texts(): Set<String> =
        device.findObjects(By.pkg(pkg).text(Pattern.compile(".+")))
            .mapNotNull { runCatching { it.text }.getOrNull() }.toSet()

    private fun similarity(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() && b.isEmpty()) return 1.0
        return a.intersect(b).size.toDouble() / a.union(b).size
    }

    private fun targets(): List<Target> {
        val h = device.displayHeight
        val onNotifications = device.hasObject(By.desc("Mark all as read"))
        return device.findObjects(By.clickable(true).pkg(pkg)).mapNotNull { o ->
            runCatching {
                val b = o.visibleBounds
                if (b.width() < 8 || b.height() < 8) return@runCatching null
                if (o.className == "android.widget.EditText") return@runCatching null
                val label = labelOf(o).trim()
                if (label.isBlank()) return@runCatching null // can't tell what an unlabeled icon does
                // Bottom-nav items are driven explicitly by run().
                if (label in NAV_LABELS && b.centerY() > h * 0.80) return@runCatching null
                val denied = DENY.containsMatchIn(label) || DENY_EXACT.matches(label) ||
                        (onNotifications && !NOTIFICATION_TABS.matches(label))
                if (denied) {
                    if (loggedSkips.add(label)) Log.w(TAG, "SKIP $label")
                    return@runCatching null
                }
                Target(label, b.centerX(), b.centerY(), "$label@${b.centerX() / 60},${b.centerY() / 60}")
            }.getOrNull()
        }
    }

    // Compose merges a clickable's children into one node, so text / description are usually on the node
    // itself; fall back to its children for the cases where they aren't.
    private fun labelOf(o: androidx.test.uiautomator.UiObject2): String =
        o.text ?: o.contentDescription ?: o.children.joinToString(" ") { labelOf(it) }
}

internal fun MacrobenchmarkScope.kikoCrawl() {
    Log.w(TAG, "crawl start")
    check(device.wait(Until.hasObject(By.text("My list")), 15_000) == true) { "Kiko main screen never appeared" }
    Thread.sleep(2000)
    Crawler(this).run()
}