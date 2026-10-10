package app.orubus.browser.navigation

import java.net.URLEncoder

/**
 * Resolves address-bar input without accepting executable or privileged schemes.
 * Web URLs are passed through; host-like input gets HTTPS; everything else is searched.
 */
object UrlResolver {
    private const val SEARCH_BASE = "https://duckduckgo.com/?q="

    fun resolve(input: String): String? {
        val value = input.trim()
        if (value.isEmpty()) return null

        if (value.equals("about:blank", ignoreCase = true)) return "about:blank"

        val lower = value.lowercase()
        if (lower.startsWith("https://") || lower.startsWith("http://")) {
            return value
        }

        // Never forward javascript:, intent:, file:, data:, content:, or arbitrary schemes.
        if (value.contains("://") || value.matches(Regex("^[A-Za-z][A-Za-z0-9+.-]*:.*"))) {
            return search(value)
        }

        val hostLike = value.matches(
            Regex("^(localhost|(?:[A-Za-z0-9-]+\\.)+[A-Za-z]{2,}|(?:\\d{1,3}\\.){3}\\d{1,3})(:\\d{1,5})?([/?#].*)?$")
        )
        return if (hostLike && !value.contains(" ")) "https://$value" else search(value)
    }

    private fun search(query: String): String =
        SEARCH_BASE + URLEncoder.encode(query, Charsets.UTF_8.name())
}
