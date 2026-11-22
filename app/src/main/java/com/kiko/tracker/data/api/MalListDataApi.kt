package com.kiko.tracker.data.api

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.zip.GZIPInputStream

/**
 * MAL's list export / import (website session needed), verified on a real account:
 *  - export : POST panel.php?go=export2 with type=1 (anime) / 2 (manga), subexport and csrf_token answers with a .xml.gz attachment.
 *  - import : POST import.php (multipart) with importtype=3 (MyAnimeList), mal = the XML file, csrf_token. MAL rejects the gzip itself
 *             ("must be an xml file"), so it is unzipped first. Entries are only updated when their update_on_import tag is 1; exports
 *             write 0, so "update existing" rewrites the tags before upload. New titles are added either way.
 *  The answer page lists "Updated X on your list." lines and "Total Entries Updated: N", or "The following errors occurred: ...".
 */
class MalListDataApi(context: Context) {
    private val client = NetworkClient.shared
    private val session = MalSessionCookie(context)
    private val base = "https://myanimelist.net"

    private fun cookie(): String = session.get() ?: throw MalSessionExpired()

    private fun csrfFrom(url: String): String {
        val req = Request.Builder().url(url).header("Cookie", cookie()).header("User-Agent", MAL_DESKTOP_USER_AGENT).build()
        return client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (resp.request.url.toString().contains("login.php")) throw MalSessionExpired()
            Jsoup.parse(body).selectFirst("meta[name=csrf_token]")?.attr("content")?.takeIf { it.isNotBlank() } ?: throw IOException("Couldn't read MAL's security token")
        }
    }

    private fun gunzipIfNeeded(bytes: ByteArray): ByteArray =
        if (bytes.size > 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()) GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() } else bytes

    /** The user's whole anime or manga list as plain XML. */
    suspend fun export(manga: Boolean): ByteArray = withContext(Dispatchers.IO) {
        val form = FormBody.Builder().add("type", if (manga) "2" else "1").add("subexport", "Export My List").add("csrf_token", csrfFrom("$base/panel.php?go=export")).build()
        val req = Request.Builder().url("$base/panel.php?go=export2").post(form).header("Cookie", cookie()).header("User-Agent", MAL_DESKTOP_USER_AGENT).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("MAL returned ${resp.code}")
            val bytes = resp.body?.bytes() ?: throw IOException("MAL sent an empty export")
            val xml = gunzipIfNeeded(bytes)
            if (!String(xml, 0, minOf(xml.size, 200), Charsets.UTF_8).contains("<")) throw IOException("MAL didn't send a list file. Try again in a moment.")
            xml
        }
    }

    /** Uploads [file] (XML, or a gzip of it) and returns MAL's own summary of what changed. */
    suspend fun import(file: ByteArray, updateExisting: Boolean): String = withContext(Dispatchers.IO) {
        var xml = String(gunzipIfNeeded(file), Charsets.UTF_8)
        if (!xml.contains("<myanimelist>")) throw IOException("This doesn't look like a MyAnimeList export file")
        if (updateExisting) xml = xml.replace("<update_on_import>0<", "<update_on_import>1<")
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("importtype", "3")
            .addFormDataPart("mal", "list.xml", xml.toByteArray(Charsets.UTF_8).toRequestBody("text/xml".toMediaType()))
            .addFormDataPart("subimport", "Import Data")
            .addFormDataPart("csrf_token", csrfFrom("$base/import.php"))
            .build()
        val req = Request.Builder().url("$base/import.php").post(body).header("Cookie", cookie()).header("User-Agent", MAL_DESKTOP_USER_AGENT).build()
        client.newCall(req).execute().use { resp ->
            val html = resp.body?.string().orEmpty()
            if (resp.request.url.toString().contains("login.php")) throw MalSessionExpired()
            if (!resp.isSuccessful) throw IOException("MAL returned ${resp.code}")
            val text = Jsoup.parse(html).body().text().substringAfter("Import to My List", "").substringBefore(" Home About").trim()
            when {
                text.isBlank() -> throw IOException("MAL's answer wasn't readable. Check your list on the website.")
                text.contains("errors occurred") -> throw IOException(text.substringAfter("errors occurred:").trim().removePrefix("-").trim().take(200))
                else -> {
                    val total = Regex("Total Entries Updated:\\s*(\\d+)").find(text)?.groupValues?.get(1)?.toIntOrNull()
                    when {
                        total != null && total > 0 -> "Updated $total ${if (total == 1) "entry" else "entries"} on your list."
                        text.contains("Nothing to update") -> "Nothing needed updating. Turn on \"Update existing entries\" to overwrite titles already on your list."
                        else -> text.take(240)
                    }
                }
            }
        }
    }
}
