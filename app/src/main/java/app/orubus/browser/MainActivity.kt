package app.orubus.browser

import android.app.Activity
import android.content.Intent
import android.os.Environment
import java.io.File
import app.orubus.browser.navigation.UrlResolver
import app.orubus.browser.storage.BrowserDataStore
import app.orubus.browser.network.TorTransportController
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.cefrium.CefriumBrowser
import java.net.URLEncoder

/**
 * Orubus browser shell backed by a bundled Chromium engine through Cefrium/CEF.
 * Cefrium is currently pre-1.0, so stable release requires additional device and
 * security verification plus an explicit upstream engine update process.
 */
class MainActivity : Activity() {
    private val cosmic = Color.rgb(35, 33, 44)
    private val vanilla = Color.rgb(241, 254, 203)
    private val ink = Color.rgb(31, 31, 37)
    private val dataStore by lazy { BrowserDataStore(applicationContext) }
    private val downloads = linkedMapOf<Int, CefriumBrowser.DownloadItem>()
    private lateinit var torController: TorTransportController
    private var torState = TorTransportController.State.OFF
    private var torRequested = false
    private val torPendingUrls = linkedMapOf<Int, String>()
    private val torProxy = "socks5://127.0.0.1:" + TorTransportController.SOCKS_PORT

    private data class BrowserTab(
        val id: Int,
        val browser: CefriumBrowser,
        val privateMode: Boolean,
        var title: String = "New tab"
    )

    private val tabs = mutableListOf<BrowserTab>()
    private var nextTabId = 1
    private lateinit var root: LinearLayout
    private lateinit var webContainer: FrameLayout
    private lateinit var address: EditText
    private lateinit var status: TextView
    private lateinit var blockCount: TextView
    private lateinit var tabButton: Button
    private lateinit var torButton: Button
    private lateinit var activeTabLabel: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = vanilla
        window.navigationBarColor = vanilla
        @Suppress("DEPRECATION")
        run {
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
        buildUi()
        torController = TorTransportController(this) { state -> runOnUiThread { handleTorState(state) } }
        val restored = dataStore.restoreSession()
        if (restored.isEmpty()) createTab("https://duckduckgo.com/", privateMode = false)
        else restored.forEach { createTab(it, privateMode = false) }
    }

    private fun buildUi() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(vanilla)
            setPadding(dp(12), dp(10), dp(12), dp(8))
        }
        val brandRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val brand = TextView(this).apply {
            text = "orubus"
            textSize = 25f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setTextColor(cosmic)
        }
        val spacer = View(this)
        status = TextView(this).apply {
            text = "DIRECT"
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(cosmic)
            background = rounded(vanilla, 24, cosmic)
            setPadding(dp(10), dp(7), dp(10), dp(7))
        }
        brandRow.addView(brand)
        brandRow.addView(spacer, LinearLayout.LayoutParams(0, 1, 1f))
        brandRow.addView(status)
        root.addView(brandRow, LinearLayout.LayoutParams(-1, dp(44)))

        val addressRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(Color.WHITE, 24, Color.rgb(221, 226, 200))
            setPadding(dp(10), 0, dp(5), 0)
        }
        address = EditText(this).apply {
            hint = "Search or enter address"
            textSize = 14f
            setSingleLine(true)
            setTextColor(ink)
            setHintTextColor(Color.GRAY)
            setBackgroundColor(Color.TRANSPARENT)
            imeOptions = EditorInfo.IME_ACTION_GO
            setOnEditorActionListener { _, actionId, event ->
                if (actionId == EditorInfo.IME_ACTION_GO ||
                    event?.keyCode == android.view.KeyEvent.KEYCODE_ENTER
                ) {
                    navigate(address.text.toString())
                    true
                } else false
            }
        }
        addressRow.addView(address, LinearLayout.LayoutParams(0, dp(48), 1f))
        addressRow.addView(button("Go") { navigate(address.text.toString()) },
            LinearLayout.LayoutParams(dp(52), dp(38)))
        root.addView(addressRow, LinearLayout.LayoutParams(-1, dp(52)).apply {
            topMargin = dp(8)
        })

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        actions.addView(button("Back") { activeTab()?.browser?.let { if (it.canGoBack()) it.goBack() } })
        actions.addView(button("Forward") { activeTab()?.browser?.goForward() })
        actions.addView(button("Reload") { activeTab()?.browser?.reload() })
        actions.addView(button("Home") { navigate("https://duckduckgo.com/") })
        tabButton = button("Tabs · 0") { showTabs() }
        actions.addView(tabButton)
        actions.addView(button("+") { createTab("https://duckduckgo.com/", privateMode = false) })
        actions.addView(button("Private") { createTab("https://duckduckgo.com/", privateMode = true) })
        actions.addView(button("Save") { toggleCurrentBookmark() })
        actions.addView(button("Marks") { showBookmarks() })
        actions.addView(button("History") { showHistory() })
        actions.addView(button("Files") { showDownloads() })
        actions.addView(button("Find") { showFind() })
        actions.addView(button("Share") { shareCurrentPage() })
        torButton = button("Tor: Off") { toggleTor() }
        actions.addView(torButton)
        actions.addView(button("Privacy") { showPrivacy() })
        val actionScroll = android.widget.HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(actions)
        }
        root.addView(actionScroll, LinearLayout.LayoutParams(-1, dp(46)).apply {
            topMargin = dp(5)
        })

        webContainer = FrameLayout(this).apply {
            background = rounded(Color.WHITE, 22, Color.TRANSPARENT)
            clipToOutline = true
        }
        root.addView(webContainer, LinearLayout.LayoutParams(-1, 0, 1f).apply {
            topMargin = dp(7)
        })

        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        activeTabLabel = TextView(this).apply {
            text = "Ready"
            textSize = 11f
            setTextColor(cosmic)
            maxLines = 1
        }
        blockCount = TextView(this).apply {
            text = "Blocked: 0"
            textSize = 11f
            setTextColor(cosmic)
            gravity = Gravity.END
        }
        footer.addView(activeTabLabel, LinearLayout.LayoutParams(0, dp(25), 1f))
        footer.addView(blockCount, LinearLayout.LayoutParams(0, dp(25), 1f))
        root.addView(footer)
        setContentView(root)
    }

    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        textSize = 11f
        isAllCaps = false
        setTextColor(cosmic)
        background = rounded(Color.argb(170, 255, 255, 255), 18, Color.rgb(221, 226, 200))
        setPadding(dp(5), 0, dp(5), 0)
        setOnClickListener { action() }
    }

    private fun rounded(fill: Int, radiusDp: Int, stroke: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(radiusDp).toFloat()
            if (stroke != Color.TRANSPARENT) setStroke(dp(1), stroke)
        }

    private fun createTab(initialUrl: String, privateMode: Boolean) {
        val browser = CefriumBrowser.createWithSurface(this, privateMode)
        if (torRequested && !browser.setProxy(torProxy)) torState = TorTransportController.State.FAILED
        browser.setPullToRefreshEnabled(true)
        val tab = BrowserTab(nextTabId++, browser, privateMode)
        tabs.add(tab)

        browser.setOnUrlChangedListener { url ->
            runOnUiThread {
                if (activeTab()?.id == tab.id) address.setText(url ?: "")
                if (!tab.privateMode && !torRequested && !url.isNullOrBlank()) dataStore.recordVisit(tab.title, url)
            }
        }
        browser.setOnTitleChangedListener { title ->
            runOnUiThread {
                tab.title = title?.takeIf { it.isNotBlank() } ?: "Untitled"
                if (!tab.privateMode && !torRequested) tab.browser.url?.let { dataStore.recordVisit(tab.title, it) }
                updateChrome()
            }
        }

        browser.setDownloadHandler(object : CefriumBrowser.CefriumDownloadHandler {
            override fun onBeforeDownload(
                item: CefriumBrowser.DownloadItem,
                callback: CefriumBrowser.BeforeDownloadCallback
            ) {
                val suggestedName = item.suggestedName?.takeIf { it.isNotBlank() } ?: "download"
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Download file?")
                    .setMessage("$suggestedName\n\nFrom: ${item.url}")
                    .setPositiveButton("Download") { _, _ ->
                        val directory = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: filesDir
                        if (!directory.exists()) directory.mkdirs()
                        val safeName = suggestedName.replace(Regex("[^A-Za-z0-9._ -]"), "_")
                            .trim().take(120).ifBlank { "download" }
                        var target = File(directory, safeName)
                        var suffix = 1
                        while (target.exists()) {
                            val dot = safeName.lastIndexOf('.')
                            val base = if (dot > 0) safeName.substring(0, dot) else safeName
                            val ext = if (dot > 0) safeName.substring(dot) else ""
                            target = File(directory, "$base ($suffix)$ext")
                            suffix++
                        }
                        callback.allow(target.absolutePath)
                    }
                    .setNegativeButton("Cancel") { _, _ -> callback.cancel() }
                    .setOnCancelListener { callback.cancel() }
                    .show()
            }

            override fun onDownloadUpdated(item: CefriumBrowser.DownloadItem) {
                runOnUiThread {
                    downloads[item.id] = item
                    if (item.isComplete) {
                        Toast.makeText(this@MainActivity, "Downloaded: ${item.suggestedName}", Toast.LENGTH_LONG).show()
                    }
                    updateChrome()
                }
            }
        })
        webContainer.addView(browser.surfaceContainer, FrameLayout.LayoutParams(-1, -1))
        activate(tab)
        browser.loadUrl(if (torRequested && torState != TorTransportController.State.CONNECTED) "about:blank" else initialUrl)
        updateChrome()
    }

    private fun activate(tab: BrowserTab) {
        tabs.forEach {
            it.browser.surfaceContainer.visibility =
                if (it.id == tab.id) View.VISIBLE else View.GONE
            it.browser.setPageVisible(it.id == tab.id)
        }
        tab.browser.surfaceContainer.bringToFront()
        address.setText(tab.browser.url ?: "")
        updateRoutingBadge()
        updateChrome()
    }

    private fun activeTab(): BrowserTab? =
        tabs.firstOrNull { it.browser.surfaceContainer.visibility == View.VISIBLE }

    private fun navigate(raw: String) {
        val target = UrlResolver.resolve(raw) ?: return
        if (torRequested && torState != TorTransportController.State.CONNECTED) {
            Toast.makeText(this, "Tor is not connected. Navigation is blocked.", Toast.LENGTH_LONG).show()
            return
        }
        activeTab()?.browser?.loadUrl(target)
        address.clearFocus()
    }

    private fun toggleTor() {
        when {
            !torRequested -> enableTor()
            torState == TorTransportController.State.CONNECTED -> disableTor()
            else -> retryTor()
        }
    }

    private fun enableTor() {
        torRequested = true
        tabs.forEach { tab ->
            tab.browser.url?.takeIf(::isWebUrl)?.let { torPendingUrls[tab.id] = it }
        }
        startTorTransport()
    }

    private fun retryTor() {
        torController.stop()
        startTorTransport()
    }

    private fun startTorTransport() {
        var proxyApplied = true
        tabs.forEach { tab ->
            tab.browser.loadUrl("about:blank")
            if (!tab.browser.setProxy(torProxy)) proxyApplied = false
            tab.browser.loadUrl("about:blank")
        }
        if (!proxyApplied) {
            torState = TorTransportController.State.FAILED
            updateRoutingBadge()
            Toast.makeText(this, "Could not apply Tor proxy to every tab. Navigation remains blocked.", Toast.LENGTH_LONG).show()
            return
        }
        torState = TorTransportController.State.STARTING
        updateRoutingBadge()
        torController.start()
    }

    private fun disableTor() {
        val resumeUrls = tabs.associate { tab ->
            tab.id to (torPendingUrls[tab.id] ?: tab.browser.url?.takeIf(::isWebUrl).orEmpty())
        }
        tabs.forEach { it.browser.loadUrl("about:blank") }
        val cleared = tabs.all { it.browser.clearProxy() }
        if (!cleared) {
            val restored = tabs.all { it.browser.setProxy(torProxy) }
            torState = if (restored) torController.state else TorTransportController.State.FAILED
            updateRoutingBadge()
            Toast.makeText(this, "Could not switch every tab to Direct. Pages remain closed.", Toast.LENGTH_LONG).show()
            return
        }
        torRequested = false
        torController.stop()
        torState = TorTransportController.State.OFF
        updateRoutingBadge()
        tabs.forEach { tab ->
            val url = resumeUrls[tab.id].orEmpty()
            torPendingUrls.remove(tab.id)
            if (url.isNotBlank()) tab.browser.loadUrl(url)
        }
    }

    private fun handleTorState(state: TorTransportController.State) {
        if (!torRequested && state != TorTransportController.State.OFF) {
            torState = state
            updateRoutingBadge()
            return
        }
        torState = state
        when (state) {
            TorTransportController.State.CONNECTED -> {
                val proxiesApplied = tabs.all { it.browser.setProxy(torProxy) }
                if (!proxiesApplied) {
                    torState = TorTransportController.State.FAILED
                    tabs.forEach { it.browser.loadUrl("about:blank") }
                    Toast.makeText(this, "Tor is up but a tab proxy could not be applied. Navigation remains blocked.", Toast.LENGTH_LONG).show()
                } else {
                    tabs.forEach { tab ->
                        val resumeUrl = torPendingUrls.remove(tab.id)
                        if (!resumeUrl.isNullOrBlank()) tab.browser.loadUrl(resumeUrl)
                    }
                }
            }
            TorTransportController.State.FAILED -> {
                tabs.forEach { tab ->
                    tab.browser.url?.takeIf(::isWebUrl)?.let { torPendingUrls.putIfAbsent(tab.id, it) }
                    tab.browser.loadUrl("about:blank")
                }
                Toast.makeText(this, "Tor failed or lost its circuit. Pages are paused; Direct was not selected.", Toast.LENGTH_LONG).show()
            }
            else -> Unit
        }
        updateRoutingBadge()
        updateChrome()
    }

    private fun updateRoutingBadge() {
        status.text = when {
            !torRequested -> "DIRECT"
            torState == TorTransportController.State.CONNECTED -> "TOR · CIRCUIT UP"
            torState == TorTransportController.State.STARTING -> "TOR · CONNECTING"
            torState == TorTransportController.State.STOPPING -> "TOR · STOPPING"
            else -> "TOR · FAILED"
        }
        torButton.text = when {
            !torRequested -> "Tor: Off"
            torState == TorTransportController.State.CONNECTED -> "Tor: On"
            else -> "Tor: Retry"
        }
    }

    private fun isWebUrl(url: String): Boolean =
        url.startsWith("https://", true) || url.startsWith("http://", true)

    private fun showTabs() {
        if (tabs.isEmpty()) return
        val labels = tabs.mapIndexed { index, tab ->
            val privacyLabel = if (tab.privateMode) " · Private" else ""
            "${index + 1}. ${tab.title}$privacyLabel"
        }.toMutableList()
        labels.add("＋ New tab")
        labels.add("＋ New private tab")
        AlertDialog.Builder(this)
            .setTitle("Tabs (${tabs.size})")
            .setItems(labels.toTypedArray()) { _, which ->
                when {
                    which < tabs.size -> activate(tabs[which])
                    which == tabs.size -> createTab("https://duckduckgo.com/", privateMode = false)
                    else -> createTab("https://duckduckgo.com/", privateMode = true)
                }
            }
            .setNeutralButton("Close active tab") { _, _ -> closeActiveTab() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun closeActiveTab() {
        val current = activeTab() ?: return
        tabs.remove(current)
        webContainer.removeView(current.browser.surfaceContainer)
        current.browser.close()
        if (tabs.isEmpty()) {
            createTab("about:blank", privateMode = false)
        } else {
            activate(tabs.last())
        }
        updateChrome()
    }

    private fun toggleCurrentBookmark() {
        val tab = activeTab() ?: return
        if (tab.privateMode) {
            Toast.makeText(this, "Bookmarks are disabled in private tabs to avoid persisting private URLs.", Toast.LENGTH_LONG).show()
            return
        }
        val url = tab.browser.url ?: return
        if (!url.startsWith("https://") && !url.startsWith("http://")) {
            Toast.makeText(this, "Only web pages can be bookmarked.", Toast.LENGTH_SHORT).show()
            return
        }
        val saved = dataStore.toggleBookmark(tab.title, url)
        Toast.makeText(this, if (saved) "Bookmark saved" else "Bookmark removed", Toast.LENGTH_SHORT).show()
    }

    private fun showBookmarks() {
        val entries = dataStore.bookmarks()
        if (entries.isEmpty()) {
            AlertDialog.Builder(this).setTitle("Bookmarks").setMessage("Your saved pages will appear here.")
                .setPositiveButton("OK", null).show()
            return
        }
        AlertDialog.Builder(this).setTitle("Bookmarks")
            .setItems(entries.map { "${it.title}\n${it.url}" }.toTypedArray()) { _, which ->
                navigate(entries[which].url)
            }
            .setPositiveButton("Done", null).show()
    }

    private fun showHistory() {
        val entries = dataStore.history()
        if (entries.isEmpty()) {
            AlertDialog.Builder(this).setTitle("History")
                .setMessage("No normal browsing history has been saved. Private tabs are excluded.")
                .setPositiveButton("OK", null).show()
            return
        }
        AlertDialog.Builder(this).setTitle("History")
            .setItems(entries.map { "${it.title}\n${it.url}" }.toTypedArray()) { _, which ->
                navigate(entries[which].url)
            }
            .setNeutralButton("Clear history") { _, _ ->
                AlertDialog.Builder(this).setTitle("Clear history?")
                    .setMessage("This clears the saved history list. It does not clear website cookies or browser storage.")
                    .setPositiveButton("Clear") { _, _ -> dataStore.clearHistory() }
                    .setNegativeButton("Cancel", null).show()
            }
            .setNegativeButton("Done", null).show()
    }

    private fun showFind() {
        val input = EditText(this).apply { hint = "Find text on this page"; setSingleLine(true) }
        AlertDialog.Builder(this).setTitle("Find in page").setView(input)
            .setPositiveButton("Find") { _, _ ->
                input.text.toString().takeIf { it.isNotBlank() }?.let { activeTab()?.browser?.find(it, true, false, false) }
            }
            .setNeutralButton("Next match") { _, _ ->
                input.text.toString().takeIf { it.isNotBlank() }?.let { activeTab()?.browser?.find(it, true, false, true) }
            }
            .setNegativeButton("Stop") { _, _ -> activeTab()?.browser?.stopFinding(true) }.show()
    }

    private fun shareCurrentPage() {
        val tab = activeTab() ?: return
        val url = tab.browser.url ?: return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, tab.title)
            putExtra(Intent.EXTRA_TEXT, url)
        }
        startActivity(Intent.createChooser(intent, "Share page"))
    }

    private fun showDownloads() {
        val items = downloads.values.toList().asReversed()
        if (items.isEmpty()) {
            AlertDialog.Builder(this).setTitle("Downloads")
                .setMessage("No downloads in this app session. Approved files are saved to the app-specific Downloads folder.")
                .setPositiveButton("OK", null).show()
            return
        }
        val labels = items.map { item ->
            val state = when {
                item.isComplete -> "Complete · ${item.fullPath}"
                item.isCanceled -> "Canceled"
                item.isPaused -> "Paused"
                else -> "${item.percentComplete.coerceAtLeast(0)}% · ${item.receivedBytes} bytes"
            }
            "${item.suggestedName}\n$state"
        }
        AlertDialog.Builder(this).setTitle("Downloads")
            .setItems(labels.toTypedArray()) { _, index ->
                val item = items[index]
                val options = when {
                    item.isComplete || item.isCanceled -> arrayOf("Remove from list")
                    item.isPaused -> arrayOf("Resume", "Cancel")
                    else -> arrayOf("Pause", "Cancel")
                }
                AlertDialog.Builder(this).setTitle(item.suggestedName).setItems(options) { _, selected ->
                    when {
                        item.isComplete || item.isCanceled -> downloads.remove(item.id)
                        item.isPaused && selected == 0 -> item.resume()
                        !item.isPaused && selected == 0 -> item.pause()
                        else -> item.cancel()
                    }
                }.setNegativeButton("Back", null).show()
            }.setPositiveButton("Done", null).show()
    }

    private fun showPrivacy() {
        val blocked = tabs.sumOf { it.browser.blockedCount }
        AlertDialog.Builder(this)
            .setTitle("Privacy status")
            .setMessage(
                "Browser engine: bundled Chromium via Cefrium\n" +
                    "Ad/tracker blocking: engine-provided EasyList blocker\n" +
                    "Blocked requests reported by open tabs: $blocked\n" +
                    "Network route: Direct\n\n" +
                    "Private tabs use the engine's off-the-record context and are intended to clear " +
                    "their in-memory browsing state when the last private tab closes. They do not hide " +
                    "traffic from your network provider or guarantee anonymity. Tor, WireGuard, " +
                    "automatic route enforcement and leak verification are not implemented yet."
            )
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showProfileStatus() {
        AlertDialog.Builder(this)
            .setTitle("Browser profiles")
            .setMessage(
                "Private browsing is backed by the engine's off-the-record mode. Named persistent " +
                    "profiles with independent cookies, storage, permissions, cache, and download state " +
                    "are not implemented yet. Normal tabs must not be treated as separate profiles."
            )
            .setPositiveButton("Understood", null)
            .show()
    }

    private fun updateChrome() {
        tabButton.text = "Tabs · ${tabs.size}"
        blockCount.text = "Blocked: ${tabs.sumOf { it.browser.blockedCount }}"
        updateRoutingBadge()
        val tab = activeTab()
        activeTabLabel.text = tab?.let {
            (if (it.privateMode) "Private · " else "") + it.title
        } ?: "No active tab"
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    @Deprecated("Deprecated in Android; retained for browser back navigation")
    override fun onBackPressed() {
        val browser = activeTab()?.browser
        if (browser?.canGoBack() == true) browser.goBack() else if (tabs.size > 1) closeActiveTab() else super.onBackPressed()
    }

    override fun onPause() {
        if (!torRequested) dataStore.saveSession(tabs.filterNot { it.privateMode }.mapNotNull { it.browser.url })
        tabs.forEach { it.browser.onPause() }
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        tabs.forEach { it.browser.onResume() }
    }

    @Deprecated("Deprecated in Android; required by browser engine activity integration")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        if (data != null) tabs.forEach {
            it.browser.onActivityResult(requestCode, resultCode, data)
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onDestroy() {
        if (::torController.isInitialized) torController.close()
        tabs.forEach { it.browser.close() }
        tabs.clear()
        super.onDestroy()
    }
}
