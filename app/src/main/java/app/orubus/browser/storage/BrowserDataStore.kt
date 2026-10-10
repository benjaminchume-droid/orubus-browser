package app.orubus.browser.storage

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Small, versioned local store for browser-owned metadata.
 *
 * Private tabs must never call recordVisit() or saveSession(). The Chromium engine
 * owns cookies, cache and site storage; this class only stores bookmarks, normal
 * browsing history, and normal-tab URLs for session restoration.
 */
class BrowserDataStore(context: Context) {
    data class Entry(
        val title: String,
        val url: String,
        val visitedAt: Long = 0L
    )

    private val preferences = context.getSharedPreferences("orubus_browser_data_v1", Context.MODE_PRIVATE)

    @Synchronized
    fun bookmarks(): List<Entry> = readEntries(KEY_BOOKMARKS)

    @Synchronized
    fun history(): List<Entry> = readEntries(KEY_HISTORY)

    @Synchronized
    fun isBookmarked(url: String): Boolean = bookmarks().any { it.url == url }

    @Synchronized
    fun toggleBookmark(title: String, url: String): Boolean {
        val current = bookmarks().toMutableList()
        val existing = current.indexOfFirst { it.url == url }
        if (existing >= 0) {
            current.removeAt(existing)
            writeEntries(KEY_BOOKMARKS, current)
            return false
        }
        current.add(0, Entry(title.ifBlank { url }, url, System.currentTimeMillis()))
        writeEntries(KEY_BOOKMARKS, current)
        return true
    }

    @Synchronized
    fun recordVisit(title: String, url: String) {
        if (!isWebUrl(url)) return
        val current = history().toMutableList()
        val existing = current.indexOfFirst { it.url == url }
        if (existing >= 0) current.removeAt(existing)
        current.add(0, Entry(title.ifBlank { url }, url, System.currentTimeMillis()))
        writeEntries(KEY_HISTORY, current.take(MAX_HISTORY))
    }

    @Synchronized
    fun clearHistory() {
        preferences.edit().remove(KEY_HISTORY).apply()
    }

    @Synchronized
    fun saveSession(urls: List<String>) {
        val array = JSONArray()
        urls.filter(::isRestorableUrl).distinct().take(MAX_TABS).forEach(array::put)
        preferences.edit().putString(KEY_SESSION, array.toString()).apply()
    }

    @Synchronized
    fun restoreSession(): List<String> {
        val raw = preferences.getString(KEY_SESSION, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { array.optString(it).takeIf(::isRestorableUrl) }
        }.getOrDefault(emptyList())
    }

    private fun readEntries(key: String): List<Entry> {
        val raw = preferences.getString(key, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val url = item.optString("url").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                Entry(
                    title = item.optString("title").ifBlank { url },
                    url = url,
                    visitedAt = item.optLong("visitedAt", 0L)
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun writeEntries(key: String, entries: List<Entry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(JSONObject()
                .put("title", entry.title)
                .put("url", entry.url)
                .put("visitedAt", entry.visitedAt))
        }
        preferences.edit().putString(key, array.toString()).apply()
    }

    private fun isWebUrl(url: String): Boolean =
        url.startsWith("https://", true) || url.startsWith("http://", true)

    private fun isRestorableUrl(url: String): Boolean =
        url == "about:blank" || isWebUrl(url)

    private companion object {
        const val KEY_BOOKMARKS = "bookmarks"
        const val KEY_HISTORY = "history"
        const val KEY_SESSION = "normal_tab_session"
        const val MAX_HISTORY = 500
        const val MAX_TABS = 30
    }
}
