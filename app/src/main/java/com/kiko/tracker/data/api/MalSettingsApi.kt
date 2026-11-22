package com.kiko.tracker.data.api

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.io.IOException

/** The settings tabs of myanimelist.net/editprofile.php (plus notification/setting) that the app edits itself. */
enum class MalSettingsPage(val title: String, val subtitle: String, val path: String, val formKey: String) {
    Profile("Profile", "Appearance, time zone, privacy, birthday, about me", "/editprofile.php", "bubbles"),
    Notifications("Notifications", "What reaches you on the web and by email", "/notification/setting", "notification_setting[get_all_notifications]"),
    ForumSignature("Forum signature", "The text under your forum posts", "/editprofile.php?go=forumoptions", "signature"),
}

data class SettingsOption(val value: String, val label: String, val enabled: Boolean = true)

/** One editable control. Values are strings the way a browser form would submit them. */
sealed class SettingsControl {
    abstract val name: String
    abstract val caption: String
    abstract val enabled: Boolean
    /** Key under which an edit is stored (checkboxes can share a name, so they include their value). */
    open val key: String get() = name
    abstract val initial: String

    class Choice(override val name: String, override val caption: String, override val enabled: Boolean, val options: List<SettingsOption>, val value: String) : SettingsControl() {
        override val initial get() = value
    }
    class Radios(override val name: String, override val caption: String, override val enabled: Boolean, val options: List<SettingsOption>, val value: String) : SettingsControl() {
        override val initial get() = value
    }
    class Toggle(override val name: String, override val caption: String, override val enabled: Boolean, val onValue: String, val checked: Boolean) : SettingsControl() {
        override val key get() = "$name#$onValue"
        override val initial get() = if (checked) onValue else ""
    }
    class Field(override val name: String, override val caption: String, override val enabled: Boolean, val value: String, val multiline: Boolean, val numeric: Boolean) : SettingsControl() {
        override val initial get() = value
    }
}

data class SettingsRow(val label: String, val hint: String, val controls: List<SettingsControl>)
data class SettingsSection(val title: String, val rows: List<SettingsRow>)

/**
 * Reads and writes MAL's own account-settings forms through the logged-in website session (MalSessionCookie), since
 * the official API has no profile settings.
 *
 * Rather than hard-coding every field, the page's <form> is parsed into rows of controls (label | control(s) tables,
 * optional column headers, section headers) so the app follows whatever the site offers. Saving re-fetches the page,
 * rebuilds the complete form submission the way a browser would (every enabled control, hidden fields included, with
 * the user's edits applied on top, so anything the app doesn't show is sent back unchanged) and posts it to the same
 * URL with the form's submit button and a fresh csrf_token (<meta name="csrf_token">). MAL answers 200 with a
 * ".goodresult" or ".badresult" box. Checked against a real account for all six pages above.
 */
class MalSettingsApi(context: Context) {
    private val client = NetworkClient.shared
    private val session = MalSessionCookie(context)

    private suspend fun fetch(page: MalSettingsPage): Pair<Document, Element> = withContext(Dispatchers.IO) {
        val cookie = session.get() ?: throw MalSessionExpired()
        val url = "https://myanimelist.net${page.path}"
        val req = Request.Builder().url(url).header("Cookie", cookie).header("User-Agent", MAL_DESKTOP_USER_AGENT).build()
        client.newCall(req).execute().use { resp ->
            val finalUrl = resp.request.url.toString()
            val body = resp.body?.string().orEmpty()
            if (finalUrl.contains("login.php") || body.contains("id=\"loginForm\"")) throw MalSessionExpired()
            if (!resp.isSuccessful) throw IOException("MAL returned ${resp.code} for ${page.title}")
            val doc = Jsoup.parse(body, url)
            val form = doc.select("form").firstOrNull { it.selectFirst("[name=\"${page.formKey}\"]") != null }
                ?: throw IOException("Couldn't find the ${page.title} settings on MAL's page. The layout may have changed.")
            doc to form
        }
    }

    suspend fun load(page: MalSettingsPage): List<SettingsSection> {
        val (_, form) = fetch(page)
        return parseForm(form)
    }

    /** Posts [edits] (control key -> value) for [page]. Returns MAL's own confirmation text. */
    suspend fun save(page: MalSettingsPage, edits: Map<String, String>): String {
        val (doc, form) = fetch(page)
        val cookie = session.get() ?: throw MalSessionExpired()
        val csrf = doc.selectFirst("meta[name=csrf_token]")?.attr("content")?.takeIf { it.isNotBlank() }
            ?: throw IOException("Couldn't read MAL's security token")
        val body = FormBody.Builder()
        var sentCsrf = false
        for (el in form.select("input, select, textarea")) {
            val name = el.attr("name")
            if (name.isBlank() || el.hasAttr("disabled")) continue
            when (el.tagName()) {
                "select" -> body.add(name, edits[name] ?: selectedValue(el))
                "textarea" -> body.add(name, edits[name] ?: el.wholeText())
                else -> when (el.attr("type").lowercase()) {
                    "checkbox" -> {
                        val v = el.attr("value").ifEmpty { "on" }
                        val on = edits["$name#$v"]?.let { it.isNotEmpty() } ?: el.hasAttr("checked")
                        if (on) body.add(name, v)
                    }
                    "radio" -> {
                        val chosen = edits[name]
                        val on = if (chosen != null) chosen == el.attr("value") else el.hasAttr("checked")
                        if (on) body.add(name, el.attr("value"))
                    }
                    "submit", "button", "reset", "image", "file" -> {}
                    else -> {
                        if (name == "csrf_token") { body.add(name, csrf); sentCsrf = true }
                        else body.add(name, edits[name] ?: el.attr("value"))
                    }
                }
            }
        }
        form.select("input[type=submit][name]").firstOrNull { !it.attr("name").contains("preview", ignoreCase = true) }
            ?.let { body.add(it.attr("name"), it.attr("value")) }
        if (!sentCsrf) body.add("csrf_token", csrf)
        val action = form.attr("action")
        val target = if (action.isBlank()) "https://myanimelist.net${page.path}" else form.absUrl("action").ifBlank { "https://myanimelist.net${page.path}" }
        return withContext(Dispatchers.IO) {
            val req = Request.Builder().url(target).post(body.build()).header("Cookie", cookie).header("User-Agent", MAL_DESKTOP_USER_AGENT)
                .header("Referer", "https://myanimelist.net${page.path}").build()
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (resp.request.url.toString().contains("login.php")) throw MalSessionExpired()
                val d = Jsoup.parse(text)
                val bad = d.selectFirst(".badresult")?.text()?.trim()
                if (bad != null) throw IOException(bad.ifBlank { "MAL rejected the change" })
                if (!resp.isSuccessful) throw IOException("MAL rejected the change (${resp.code})")
                d.selectFirst(".goodresult")?.text()?.trim()?.takeIf { it.isNotBlank() } ?: "Saved"
            }
        }
    }

    // ---- profile picture ---------------------------------------------------------------------------------------------

    /** Uploads [bytes] (jpg / png / gif, at most 2048 x 2048 px) as the profile picture. Returns MAL's own message. */
    suspend fun uploadPicture(bytes: ByteArray, mime: String, fileName: String): String = pictureRequest { mb ->
        mb.addFormDataPart("file", fileName, bytes.toRequestBody(mime.toMediaType()))
        mb.addFormDataPart("submit", "Submit")
    }

    suspend fun removePicture(): String = pictureRequest { mb -> mb.addFormDataPart("remove_pic", "Remove") }

    // The picture form is multipart and posts to its own action (editprofile.php?go=picture) with a fresh csrf_token.
    private suspend fun pictureRequest(fill: (MultipartBody.Builder) -> Unit): String = withContext(Dispatchers.IO) {
        val cookie = session.get() ?: throw MalSessionExpired()
        val pageUrl = "https://myanimelist.net/editprofile.php?go=picture"
        val (csrf, action) = client.newCall(Request.Builder().url(pageUrl).header("Cookie", cookie).header("User-Agent", MAL_DESKTOP_USER_AGENT).build()).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (resp.request.url.toString().contains("login.php") || body.contains("id=\"loginForm\"")) throw MalSessionExpired()
            if (!resp.isSuccessful) throw IOException("MAL returned ${resp.code}")
            val doc = Jsoup.parse(body, pageUrl)
            val form = doc.select("form").firstOrNull { it.selectFirst("input[type=file]") != null }
                ?: throw IOException("Couldn't find the picture form on MAL's page. The layout may have changed.")
            val token = doc.selectFirst("meta[name=csrf_token]")?.attr("content")?.takeIf { it.isNotBlank() }
                ?: throw IOException("Couldn't read MAL's security token")
            token to form.absUrl("action").ifBlank { pageUrl }
        }
        val mb = MultipartBody.Builder().setType(MultipartBody.FORM)
        fill(mb)
        mb.addFormDataPart("csrf_token", csrf)
        val req = Request.Builder().url(action).post(mb.build()).header("Cookie", cookie).header("User-Agent", MAL_DESKTOP_USER_AGENT).header("Referer", pageUrl).build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (resp.request.url.toString().contains("login.php")) throw MalSessionExpired()
            val d = Jsoup.parse(text)
            val bad = d.selectFirst(".badresult")?.text()?.trim()
            if (bad != null) throw IOException(bad.ifBlank { "MAL rejected the picture" })
            if (!resp.isSuccessful) throw IOException("MAL rejected the picture (${resp.code})")
            d.selectFirst(".goodresult")?.text()?.trim()?.takeIf { it.isNotBlank() } ?: "Done"
        }
    }

    // ---- parsing -----------------------------------------------------------------------------------------------

    private fun selectedValue(select: Element): String {
        val options = select.select("option")
        return (options.firstOrNull { it.hasAttr("selected") } ?: options.firstOrNull())?.let { it.attr("value").ifEmpty { it.text() } }.orEmpty()
    }

    private fun parseForm(form: Element): List<SettingsSection> {
        val sections = mutableListOf<SettingsSection>()
        var title = ""
        var rows = mutableListOf<SettingsRow>()
        var headers = emptyList<String>()
        val seen = HashSet<String>()
        // A wrapper row like "Show Columns | <nested table of checkboxes>" is skipped as a row, but its label is kept for
        // the first nested row that has controls and no label of its own.
        var pendingLabel = ""
        fun flush() {
            if (rows.isNotEmpty()) sections += SettingsSection(title, rows.toList())
            rows = mutableListOf()
        }
        for (tr in form.select("tr")) {
            // Only leaf rows: an outer layout row that wraps nested rows would repeat every control.
            if (tr.select("tr").size != 1) {
                val kids = tr.children().filter { it.tagName() == "td" || it.tagName() == "th" }
                val first = kids.firstOrNull()
                if (kids.size >= 2 && first != null && first.select("table, select, textarea, input").isEmpty()) {
                    cleanText(first.text()).takeIf { it.isNotBlank() && it.length <= 40 }?.let { pendingLabel = it }
                }
                continue
            }
            // Rows with their own buttons (SNS post templates, connect buttons) can't be edited from here.
            if (tr.select("button, input[type=button]").isNotEmpty()) continue
            val cells = tr.children().filter { it.tagName() == "td" || it.tagName() == "th" }
            if (cells.isEmpty()) continue
            val hasControls = tr.select("select, textarea, input").any { isEditable(it) }
            if (!hasControls) {
                val text = cleanText(tr.text())
                when {
                    cells.size == 1 && text.isNotBlank() && text.length <= 70 -> { flush(); title = text; headers = emptyList() }
                    cells.size == 1 && text.length > 70 -> rows.add(SettingsRow("", text, emptyList()))
                    cells.size >= 3 && cells.all { cleanText(it.text()).isNotBlank() } -> headers = cells.drop(1).map { cleanText(it.text()) }
                }
                continue
            }
            val labelCell = cells.first().takeIf { cells.size >= 2 && it.select("select, textarea, input").none { e -> isEditable(e) } }
            val lines = labelCell?.let { cellLines(it) }.orEmpty()
            var label = lines.firstOrNull().orEmpty().trimEnd(':').trim()
            if (label.isBlank() && labelCell == null && pendingLabel.isNotBlank()) { label = pendingLabel; pendingLabel = "" }
            val labelHint = lines.drop(1).joinToString(" ")
            val controls = mutableListOf<SettingsControl>()
            val hints = mutableListOf<String>()
            if (labelHint.isNotBlank()) hints += labelHint
            val dataCells = if (labelCell != null) cells.drop(1) else cells
            dataCells.forEachIndexed { i, cell ->
                val header = if (labelCell != null) headers.getOrNull(i).orEmpty() else ""
                val (found, hint) = buildControls(cell, header, seen)
                controls += found
                if (hint.isNotBlank()) hints += hint
            }
            if (controls.isEmpty()) continue
            rows.add(SettingsRow(label, hints.joinToString(" "), controls))
        }
        flush()
        return sections
    }

    private fun isEditable(e: Element): Boolean {
        if (e.attr("name").isBlank()) return false
        if (e.tagName() != "input") return true
        return e.attr("type").lowercase() in setOf("", "text", "number", "radio", "checkbox", "search", "email", "url")
    }

    private fun cleanText(s: String) = s.replace(' ', ' ').replace(Regex("\\s+"), " ").trim()

    /** A label cell's text as lines (<br> splits). */
    private fun cellLines(cell: Element): List<String> {
        val copy = cell.clone()
        copy.select("br").after("\\n")
        copy.select("script, style").remove()
        return copy.wholeText().replace("\\n", "\n").split('\n').map { cleanText(it) }.filter { it.isNotBlank() }
    }

    private fun buildControls(cell: Element, columnHeader: String, seen: MutableSet<String>): Pair<List<SettingsControl>, String> {
        val out = mutableListOf<SettingsControl>()
        val caption = StringBuilder()
        val consumed = HashSet<Node>()
        val doneRadios = HashSet<String>()
        fun takeCaption(): String = cleanText(caption.toString()).trim(':', ' ').also { caption.setLength(0) }

        fun labelFor(input: Element): String {
            val id = input.id()
            if (id.isNotBlank()) {
                cell.ownerDocument()?.selectFirst("label[for=\"$id\"]")?.let { consumed += it; if (it.text().isNotBlank()) return cleanText(it.text()) }
            }
            input.parent()?.takeIf { it.tagName() == "label" }?.let { p ->
                consumed += p
                return cleanText(p.ownText().ifBlank { p.text() })
            }
            val sb = StringBuilder()
            var sib: Node? = input.nextSibling()
            while (sib != null) {
                if (sib is Element && (sib.tagName() in setOf("input", "select", "textarea", "br") || sib.select("input, select, textarea").isNotEmpty())) break
                if (sib is TextNode || sib is Element) { sb.append(' ').append(if (sib is TextNode) sib.text() else (sib as Element).text()); consumed += sib }
                sib = sib.nextSibling()
            }
            return cleanText(sb.toString())
        }

        fun walk(node: Node) {
            if (node in consumed) return
            when (node) {
                is TextNode -> caption.append(' ').append(node.text())
                is Element -> when (node.tagName()) {
                    "script", "style", "option" -> {}
                    "br" -> caption.append(' ')
                    "label" -> if (node.select("input, select, textarea").isEmpty()) caption.append(' ').append(node.text()) else node.childNodes().forEach { walk(it) }
                    "select" -> if (isEditable(node) && seen.add(node.attr("name"))) {
                        val opts = node.select("option").map { SettingsOption(it.attr("value").ifEmpty { it.text() }, cleanText(it.text())) }
                        out += SettingsControl.Choice(node.attr("name"), takeCaption().ifBlank { columnHeader }, !node.hasAttr("disabled"), opts, selectedValue(node))
                    }
                    "textarea" -> if (isEditable(node) && seen.add(node.attr("name"))) {
                        out += SettingsControl.Field(node.attr("name"), takeCaption().ifBlank { columnHeader }, !node.hasAttr("disabled"), node.wholeText(), multiline = true, numeric = false)
                    }
                    "input" -> if (isEditable(node)) {
                        val name = node.attr("name")
                        when (node.attr("type").lowercase()) {
                            "radio" -> if (doneRadios.add(name) && seen.add(name)) {
                                val radios = cell.select("input[type=radio]").filter { it.attr("name") == name }
                                val opts = radios.map { SettingsOption(it.attr("value"), labelFor(it).ifBlank { it.attr("value") }, !it.hasAttr("disabled")) }
                                out += SettingsControl.Radios(name, takeCaption().ifBlank { columnHeader }, opts.any { it.enabled }, opts, radios.firstOrNull { it.hasAttr("checked") }?.attr("value").orEmpty())
                            }
                            "checkbox" -> {
                                val v = node.attr("value").ifEmpty { "on" }
                                if (seen.add("$name#$v")) {
                                    var cap = labelFor(node).ifBlank { takeCaption() }
                                    if (cap.isBlank()) cap = columnHeader
                                    if (cap.isBlank()) cap = when {
                                        name.endsWith("_email]") -> "Email"
                                        name.endsWith("_notification]") -> "Web"
                                        else -> ""
                                    }
                                    caption.setLength(0)
                                    out += SettingsControl.Toggle(name, cap, !node.hasAttr("disabled"), v, node.hasAttr("checked"))
                                }
                            }
                            else -> if (seen.add(name)) {
                                val type = node.attr("type").lowercase()
                                out += SettingsControl.Field(name, takeCaption().ifBlank { columnHeader }, !node.hasAttr("disabled"), node.attr("value"), multiline = false, numeric = type == "number")
                            }
                        }
                    }
                    else -> {
                        val before = out.size
                        node.childNodes().forEach { walk(it) }
                        // "Show [n] item(s)" / "located [..]": text trailing a control inside its own span belongs to
                        // that span, not to the next control's caption.
                        if (node.tagName() == "span" && out.size > before) caption.setLength(0)
                    }
                }
            }
        }
        cell.childNodes().forEach { walk(it) }
        return out to takeCaption()
    }
}