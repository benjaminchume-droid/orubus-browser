package app.orubus.browser

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.io.ByteArrayInputStream
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicInteger

/**
 * Initial browser shell. WebView uses the device's Chromium-derived WebView package;
 * this is not yet a custom, bundled Chromium build.
 *
 * The request filter below performs real host-based blocking for the included starter
 * rules. It is not yet a complete EasyList/uBlock-compatible filtering engine.
 */
class MainActivity : Activity() {
    private val cosmic = Color.rgb(35, 33, 44)
    private val vanilla = Color.rgb(241, 254, 203)
    private val ink = Color.rgb(31, 31, 37)
    private val blockedRequests = AtomicInteger(0)
    private val blockedHosts = setOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com",
        "adnxs.com", "adsrvr.org", "adservice.google.com",
        "connect.facebook.net", "scorecardresearch.com", "hotjar.com",
        "segment.io", "mixpanel.com"
    )

    private data class BrowserTab(val id: Int, val webView: WebView, var title: String = "New tab")
    private val tabs = mutableListOf<BrowserTab>()
    private var nextTabId = 1
    private lateinit var root: LinearLayout
    private lateinit var webContainer: FrameLayout
    private lateinit var address: EditText
    private lateinit var status: TextView
    private lateinit var blockCount: TextView
    private lateinit var tabButton: Button
    private lateinit var activeTabLabel: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = vanilla
        window.navigationBarColor = vanilla
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
            View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        buildUi()
        createTab("https://www.google.com")
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
            singleLine = true
            setTextColor(ink)
            setHintTextColor(Color.GRAY)
            setBackgroundColor(Color.TRANSPARENT)
            imeOptions = EditorInfo.IME_ACTION_GO
            setSelectAllOnFocus(false)
            setOnEditorActionListener { _, actionId, event ->
                if (actionId == EditorInfo.IME_ACTION_GO ||
                    event?.keyCode == android.view.KeyEvent.KEYCODE_ENTER) {
                    navigate(address.text.toString()); true
                } else false
            }
        }
        val go = button("Go") { navigate(address.text.toString()) }
        addressRow.addView(address, LinearLayout.LayoutParams(0, dp(48), 1f))
        addressRow.addView(go, LinearLayout.LayoutParams(dp(52), dp(38)))
        val addressParams = LinearLayout.LayoutParams(-1, dp(52))
        addressParams.topMargin = dp(8)
        root.addView(addressRow, addressParams)

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        tabButton = button("Tabs · 0") { showTabs() }
        val newTab = button("+") { createTab("about:blank") }
        val privacy = button("Privacy") { showPrivacy() }
        val profile = button("Profiles") { showProfileStatus() }
        actions.addView(tabButton)
        actions.addView(newTab)
        actions.addView(privacy)
        actions.addView(profile)
        val actionParams = LinearLayout.LayoutParams(-1, dp(46))
        actionParams.topMargin = dp(5)
        root.addView(actions, actionParams)

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

    private fun createTab(initialUrl: String) {
        val web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.javaScriptCanOpenWindowsAutomatically = false
        web.settings.setSupportMultipleWindows(false)
        web.webChromeClient = object : WebChromeClient() {
            override fun onReceivedTitle(view: WebView?, title: String?) {
                tabs.firstOrNull { it.webView === view }?.let { it.title = title ?: "Untitled" }
                updateChrome()
            }
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return false
                if (uri.scheme !in setOf("http", "https", "about")) {
                    Toast.makeText(this@MainActivity, "This link type is not opened by the browser.", Toast.LENGTH_SHORT).show()
                    return true
                }
                return false
            }

            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val host = request?.url?.host?.lowercase() ?: return null
                if (blockedHosts.any { host == it || host.endsWith(".$it") }) {
                    blockedRequests.incrementAndGet()
                    runOnUiThread { blockCount.text = "Blocked: ${blockedRequests.get()}" }
                    return WebResourceResponse(
                        "text/plain", "utf-8", 204, "Blocked by Orubus privacy filter",
                        mapOf("Cache-Control" to "no-store"),
                        ByteArrayInputStream(ByteArray(0))
                    )
                }
                return null
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                address.setText(url ?: "")
                activeTabLabel.text = "Loading…"
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                address.setText(url ?: "")
                updateChrome()
            }
        }
        val tab = BrowserTab(nextTabId++, web)
        tabs.add(tab)
        webContainer.addView(web, FrameLayout.LayoutParams(-1, -1))
        activate(tab)
        navigate(initialUrl)
        updateChrome()
    }

    private fun activate(tab: BrowserTab) {
        tabs.forEach { it.webView.visibility = if (it.id == tab.id) View.VISIBLE else View.GONE }
        tab.webView.bringToFront()
        address.setText(tab.webView.url ?: "")
        updateChrome()
    }

    private fun activeTab(): BrowserTab? = tabs.firstOrNull { it.webView.visibility == View.VISIBLE }

    private fun navigate(raw: String) {
        val value = raw.trim()
        if (value.isEmpty()) return
        val target = when {
            value.startsWith("about:") -> value
            value.matches(Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:.*")) -> value
            value.contains(".") && !value.contains(" ") -> "https://$value"
            else -> "https://www.google.com/search?q=" + URLEncoder.encode(value, "UTF-8")
        }
        if (!target.startsWith("https://") && !target.startsWith("http://") && !target.startsWith("about:")) {
            Toast.makeText(this, "Only web and about: URLs are supported.", Toast.LENGTH_SHORT).show()
            return
        }
        activeTab()?.webView?.loadUrl(target)
    }

    private fun showTabs() {
        if (tabs.isEmpty()) return
        val labels = tabs.mapIndexed { index, tab -> "${index + 1}. ${tab.title}" }.toMutableList()
        labels.add("＋ New tab")
        AlertDialog.Builder(this)
            .setTitle("Tabs (${tabs.size})")
            .setItems(labels.toTypedArray()) { _, which ->
                if (which == tabs.size) createTab("about:blank")
                else activate(tabs[which])
            }
            .setNeutralButton("Close active tab") { _, _ ->
                if (tabs.size > 1) {
                    val current = activeTab()
                    current?.let {
                        tabs.remove(it)
                        webContainer.removeView(it.webView)
                        it.webView.destroy()
                    }
                    activate(tabs.last())
                    updateChrome()
                } else {
                    activeTab()?.webView?.loadUrl("about:blank")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showPrivacy() {
        AlertDialog.Builder(this)
            .setTitle("Privacy status")
            .setMessage(
                "Network route: Direct\n" +
                "Blocked requests: ${blockedRequests.get()}\n\n" +
                "The starter filter blocks requests to a small built-in list of known ad/tracker hosts. " +
                "This is not yet a comprehensive filter-list engine. VPN, Tor, DNS leak protection, " +
                "and verified route enforcement are not implemented in this build."
            )
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showProfileStatus() {
        AlertDialog.Builder(this)
            .setTitle("Browser profiles")
            .setMessage(
                "Profile isolation is planned but not enabled in this initial build. Tabs currently share " +
                "the app's WebView storage context. The profile selector will not claim isolation until " +
                "separate browser storage contexts and isolation tests are implemented."
            )
            .setPositiveButton("Understood", null)
            .show()
    }

    private fun updateChrome() {
        tabButton.text = "Tabs · ${tabs.size}"
        blockCount.text = "Blocked: ${blockedRequests.get()}"
        activeTabLabel.text = activeTab()?.title ?: "No active tab"
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onBackPressed() {
        val web = activeTab()?.webView
        if (web?.canGoBack() == true) web.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        tabs.forEach { it.webView.stopLoading(); it.webView.destroy() }
        tabs.clear()
        super.onDestroy()
    }
}
