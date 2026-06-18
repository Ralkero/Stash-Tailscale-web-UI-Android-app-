package com.localstash.wrapper

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import org.json.JSONObject
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("stash_wrapper", Context.MODE_PRIVATE) }
    private lateinit var root: FrameLayout
    private lateinit var chrome: LinearLayout
    private lateinit var bottomNav: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var progress: ProgressBar
    private lateinit var webView: WebView
    private var fullScreenView: View? = null
    private var fullScreenCallback: WebChromeClient.CustomViewCallback? = null
    private var toolbarVisible = false
    private var gestureStartY = 0f
    private var gestureStartedNearTop = false
    private var focusSearchOnNextPage = false
    private var activeNavItem: LinearLayout? = null

    private val defaultUrl: String
        get() = getString(R.string.default_stash_url)

    private val serverUrl: String
        get() = prefs.getString(KEY_SERVER_URL, defaultUrl) ?: defaultUrl

    private val lastUrl: String?
        get() = prefs.getString(KEY_LAST_URL, null)

    private val mobilePlaybackEnabled: Boolean
        get() = prefs.getBoolean(KEY_MOBILE_PLAYBACK_ENABLED, false)

    private val mobilePlaybackResolution: String
        get() = prefs.getString(KEY_MOBILE_PLAYBACK_RESOLUTION, DEFAULT_MOBILE_RESOLUTION)
            ?: DEFAULT_MOBILE_RESOLUTION

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureSystemBars()
        WebView.setWebContentsDebuggingEnabled(false)
        buildLayout()
        configureWebView()
        loadServer()
    }

    override fun onBackPressed() {
        when {
            fullScreenView != null -> exitFullScreen()
            webView.canGoBack() -> webView.goBack()
            else -> super.onBackPressed()
        }
    }

    override fun onPause() {
        webView.onPause()
        CookieManager.getInstance().flush()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }

    private fun buildLayout() {
        root = FrameLayout(this)
        val vertical = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        chrome = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            setBackgroundColor(Color.rgb(15, 23, 42))
            visibility = View.GONE
        }

        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        statusText = TextView(this).apply {
            text = "Stash"
            setTextColor(Color.WHITE)
            textSize = 14f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.MIDDLE
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleSmall).apply {
            visibility = View.GONE
        }

        titleRow.addView(statusText)
        titleRow.addView(progress)
        chrome.addView(titleRow)

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, 0)
        }

        addButton(buttonRow, "Refresh") { webView.reload() }
        addButton(buttonRow, "Status") { checkStatus() }
        addButton(buttonRow, "Settings") { showSettings() }
        addButton(buttonRow, "Logout") { logout() }
        chrome.addView(buttonRow)

        webView = WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        vertical.addView(chrome)
        vertical.addView(webView)
        bottomNav = buildBottomNav()
        vertical.addView(bottomNav)
        root.addView(vertical)
        root.setOnTouchListener { _, event -> handleChromeGesture(event) }
        setContentView(root)
        applySystemBarInsets()
    }

    private fun configureSystemBars() {
        window.statusBarColor = Color.rgb(15, 23, 42)
        window.navigationBarColor = Color.rgb(226, 232, 240)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
    }

    private fun applySystemBarInsets() {
        root.setOnApplyWindowInsetsListener { _, insets ->
            val left: Int
            val top: Int
            val right: Int
            val bottom: Int

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val systemBars = insets.getInsets(WindowInsets.Type.systemBars())
                left = systemBars.left
                top = systemBars.top
                right = systemBars.right
                bottom = systemBars.bottom
            } else {
                @Suppress("DEPRECATION")
                left = insets.systemWindowInsetLeft
                @Suppress("DEPRECATION")
                top = insets.systemWindowInsetTop
                @Suppress("DEPRECATION")
                right = insets.systemWindowInsetRight
                @Suppress("DEPRECATION")
                bottom = insets.systemWindowInsetBottom
            }

            root.setPadding(left, top, right, bottom)
            insets
        }
        root.requestApplyInsets()
    }

    private fun configureWebView() {
        webView.setOnTouchListener { _, event ->
            handleChromeGesture(event)
        }

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadsImagesAutomatically = true
            mediaPlaybackRequiresUserGesture = false
            useWideViewPort = true
            loadWithOverviewMode = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            allowFileAccess = false
            allowContentAccess = false
            setSupportMultipleWindows(false)
            cacheMode = WebSettings.LOAD_DEFAULT
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                if (isAllowedInWebView(uri)) return false
                openExternal(uri)
                return true
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                progress.visibility = View.VISIBLE
                statusText.text = "Loading Stash..."
            }

            override fun onPageFinished(view: WebView, url: String?) {
                progress.visibility = View.GONE
                statusText.text = url ?: serverUrl
                rememberLastUrl(url)
                installStashNavigationChrome()
                installSceneDisplayModeSync()
                installMobilePlaybackSourceSync()
                if (focusSearchOnNextPage) {
                    focusSearchOnNextPage = false
                    focusStashSearchBox()
                }
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                if (request.isForMainFrame) {
                    progress.visibility = View.GONE
                    statusText.text = "Cannot reach Stash. Check Tailscale and the PC."
                    Toast.makeText(
                        this@MainActivity,
                        "Cannot reach Stash. Connect Tailscale, start Stash, or wake the PC.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            override fun onReceivedHttpError(
                view: WebView,
                request: WebResourceRequest,
                errorResponse: WebResourceResponse
            ) {
                if (request.isForMainFrame && errorResponse.statusCode >= 400) {
                    statusText.text = "Stash returned HTTP ${errorResponse.statusCode}"
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                if (fullScreenView != null) {
                    callback.onCustomViewHidden()
                    return
                }
                fullScreenView = view
                fullScreenCallback = callback
                chrome.visibility = View.GONE
                bottomNav.visibility = View.GONE
                webView.visibility = View.GONE
                root.addView(
                    view,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                )
            }

            override fun onHideCustomView() {
                exitFullScreen()
            }
        }
    }

    private fun loadServer() {
        val url = normalizeUrl(serverUrl)
        prefs.edit().putString(KEY_SERVER_URL, url).apply()
        val resumeUrl = lastUrl?.takeIf { isAllowedServerUrl(it) && isAllowedInWebView(Uri.parse(it)) }
        webView.loadUrl(resumeUrl ?: url)
    }

    private fun checkStatus() {
        showToolbar()
        val url = normalizeUrl(serverUrl)
        statusText.text = "Checking Stash..."
        progress.visibility = View.VISIBLE
        thread {
            val result = try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.requestMethod = "HEAD"
                val code = connection.responseCode
                connection.disconnect()
                "Reachable: HTTP $code"
            } catch (headError: Exception) {
                try {
                    val connection = URL(url).openConnection() as HttpURLConnection
                    connection.connectTimeout = 5000
                    connection.readTimeout = 5000
                    connection.requestMethod = "GET"
                    val code = connection.responseCode
                    connection.disconnect()
                    "Reachable: HTTP $code"
                } catch (getError: Exception) {
                    "Cannot reach Stash. Connect Tailscale, start Stash, or wake the PC."
                }
            }
            runOnUiThread {
                progress.visibility = View.GONE
                statusText.text = result
                Toast.makeText(this, result, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showSettings() {
        showToolbar()
        val settingsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(4), dp(4), 0)
        }

        val input = EditText(this).apply {
            setSingleLine(true)
            setText(serverUrl)
            setSelection(text.length)
        }
        settingsLayout.addView(input)

        val mobileCheck = CheckBox(this).apply {
            text = "Prefer mobile playback transcodes"
            isChecked = mobilePlaybackEnabled
            setPadding(0, dp(10), 0, 0)
        }
        settingsLayout.addView(mobileCheck)

        val resolutionLabels = MOBILE_PLAYBACK_OPTIONS.map { it.label }
        val resolutionSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                resolutionLabels
            )
            val selected = MOBILE_PLAYBACK_OPTIONS.indexOfFirst {
                it.resolution == mobilePlaybackResolution
            }.takeIf { it >= 0 } ?: 0
            setSelection(selected)
        }
        settingsLayout.addView(resolutionSpinner)

        AlertDialog.Builder(this)
            .setTitle("Stash server")
            .setMessage("HTTP is allowed only for 100.102.126.109. Use HTTPS for any other host.")
            .setView(settingsLayout)
            .setPositiveButton("Save") { _, _ ->
                val value = normalizeUrl(input.text.toString())
                if (!isAllowedServerUrl(value)) {
                    Toast.makeText(
                        this,
                        "Use http://100.102.126.109:9999/ or an HTTPS URL.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@setPositiveButton
                }
                prefs.edit()
                    .putString(KEY_SERVER_URL, value)
                    .putBoolean(KEY_MOBILE_PLAYBACK_ENABLED, mobileCheck.isChecked)
                    .putString(
                        KEY_MOBILE_PLAYBACK_RESOLUTION,
                        MOBILE_PLAYBACK_OPTIONS[resolutionSpinner.selectedItemPosition].resolution
                    )
                    .remove(KEY_LAST_URL)
                    .apply()
                webView.loadUrl(value)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun logout() {
        showToolbar()
        CookieManager.getInstance().removeAllCookies {
            CookieManager.getInstance().flush()
            webView.clearCache(false)
            prefs.edit().remove(KEY_LAST_URL).apply()
            loadServer()
            Toast.makeText(this, "Stash cookies cleared.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isAllowedInWebView(uri: Uri): Boolean {
        if (uri.scheme != "http" && uri.scheme != "https") return false
        val server = URI(normalizeUrl(serverUrl))
        return uri.host.equals(server.host, ignoreCase = true) &&
            effectivePort(uri.scheme, uri.port) == effectivePort(server.scheme, server.port)
    }

    private fun isAllowedServerUrl(value: String): Boolean {
        val uri = URI(value)
        return when (uri.scheme?.lowercase()) {
            "https" -> true
            "http" -> uri.host == "100.102.126.109"
            else -> false
        }
    }

    private fun rememberLastUrl(value: String?) {
        if (value.isNullOrBlank()) return
        val uri = Uri.parse(value)
        if (!isAllowedInWebView(uri)) return
        prefs.edit().putString(KEY_LAST_URL, value).apply()
    }

    private fun normalizeUrl(value: String): String {
        val trimmed = value.trim()
        val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "http://$trimmed"
        }
        return if (withScheme.endsWith("/")) withScheme else "$withScheme/"
    }

    private fun openExternal(uri: Uri) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: Exception) {
            Toast.makeText(this, "Blocked unsupported link: $uri", Toast.LENGTH_LONG).show()
        }
    }

    private fun exitFullScreen() {
        val view = fullScreenView ?: return
        root.removeView(view)
        fullScreenView = null
        fullScreenCallback?.onCustomViewHidden()
        fullScreenCallback = null
        chrome.visibility = if (toolbarVisible) View.VISIBLE else View.GONE
        bottomNav.visibility = View.VISIBLE
        webView.visibility = View.VISIBLE
    }

    private fun buildBottomNav(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(4), dp(4), dp(4))
            setBackgroundColor(Color.rgb(15, 23, 42))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(64)
            )
            addView(navItem("Scenes", R.drawable.ic_nav_scenes) { navigateToPath("/scenes") })
            addView(navItem("Groups", R.drawable.ic_nav_groups) { navigateToPath("/groups") })
            addView(navItem("Studios", R.drawable.ic_nav_studios) { navigateToPath("/studios") })
            addView(navItem("Tags", R.drawable.ic_nav_tags) { navigateToPath("/tags") })
            addView(navItem("Search", R.drawable.ic_nav_search) { openSearch() })
        }
    }

    private fun navItem(label: String, iconRes: Int, onClick: () -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            background = navItemBackground()
            setPadding(dp(2), dp(3), dp(2), dp(3))
            setOnClickListener {
                markNavItemActive(this)
                onClick()
            }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)

            addView(ImageView(context).apply {
                setImageResource(iconRes)
                setColorFilter(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(dp(24), dp(24))
            })
            addView(TextView(context).apply {
                text = label
                setTextColor(Color.WHITE)
                textSize = 11f
                maxLines = 1
                gravity = Gravity.CENTER
                includeFontPadding = false
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(3)
                }
            })
        }
    }

    private fun navigateToPath(path: String) {
        focusSearchOnNextPage = false
        val fullTarget = stashUrlWithCurrentSceneDisplay(path)
        val targetUri = Uri.parse(fullTarget)
        val targetPath = buildString {
            append(targetUri.encodedPath ?: "/")
            targetUri.encodedQuery?.let { append('?').append(it) }
            targetUri.encodedFragment?.let { append('#').append(it) }
        }
        val currentUri = webView.url?.let(Uri::parse)
        if (currentUri == null || !isAllowedInWebView(currentUri)) {
            webView.loadUrl(fullTarget)
            return
        }

        val quotedTarget = JSONObject.quote(targetPath)
        val navigateScript = """
            (function() {
                try {
                    const target = $quotedTarget;
                    if (location.pathname + location.search + location.hash === target) return true;
                    history.pushState({}, '', target);
                    window.dispatchEvent(new PopStateEvent('popstate', { state: history.state }));
                    return true;
                } catch (_) {
                    return false;
                }
            })();
        """.trimIndent()

        webView.evaluateJavascript(navigateScript) { result ->
            if (result != "true") {
                webView.loadUrl(fullTarget)
                return@evaluateJavascript
            }
            webView.postDelayed({
                val verifyScript =
                    "location.pathname + location.search + location.hash === $quotedTarget"
                webView.evaluateJavascript(verifyScript) { matches ->
                    if (matches != "true") webView.loadUrl(fullTarget)
                }
            }, 350)
        }
    }

    private fun openSearch() {
        val currentPath = currentStashPath()
        val searchablePath = when {
            currentPath.startsWith("/scenes") -> "/scenes"
            currentPath.startsWith("/groups") -> "/groups"
            currentPath.startsWith("/studios") -> "/studios"
            currentPath.startsWith("/tags") -> "/tags"
            else -> "/scenes"
        }

        if (currentPath.startsWith(searchablePath)) {
            focusSearchOnNextPage = false
            focusStashSearchBox()
        } else {
            focusSearchOnNextPage = true
            webView.loadUrl(stashUrlWithCurrentSceneDisplay(searchablePath))
        }
    }

    private fun focusStashSearchBox() {
        webView.requestFocus()
        val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val delays = longArrayOf(200, 600)
        for (delay in delays) {
            webView.postDelayed({
                webView.evaluateJavascript(
                    SEARCH_FOCUS_SCRIPT,
                    null
                )
                inputMethodManager.showSoftInput(webView, InputMethodManager.SHOW_IMPLICIT)
            }, delay)
        }
    }

    private fun installSceneDisplayModeSync() {
        webView.postDelayed({
            webView.evaluateJavascript(SCENE_DISPLAY_MODE_SYNC_SCRIPT, null)
        }, 300)
    }

    private fun installStashNavigationChrome() {
        webView.postDelayed({
            webView.evaluateJavascript(STASH_NAVIGATION_CHROME_SCRIPT, null)
        }, 100)
    }

    private fun installMobilePlaybackSourceSync() {
        if (!mobilePlaybackEnabled) return
        val resolution = mobilePlaybackResolution.uppercase()
        if (resolution == "ORIGINAL" || resolution !in SUPPORTED_MOBILE_RESOLUTIONS) return
        val script = MOBILE_PLAYBACK_SOURCE_SYNC_SCRIPT.replace(
            "__TARGET_RESOLUTION__",
            JSONObject.quote(resolution)
        )
        webView.postDelayed({
            webView.evaluateJavascript(script, null)
        }, 300)
    }

    private fun navItemBackground(): StateListDrawable {
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), ColorDrawable(Color.rgb(51, 65, 85)))
            addState(intArrayOf(android.R.attr.state_focused), ColorDrawable(Color.rgb(51, 65, 85)))
            addState(intArrayOf(android.R.attr.state_selected), ColorDrawable(Color.rgb(30, 41, 59)))
            addState(intArrayOf(), ColorDrawable(Color.TRANSPARENT))
        }
    }

    private fun markNavItemActive(item: LinearLayout) {
        activeNavItem?.isSelected = false
        activeNavItem = item
        item.isSelected = true
    }

    private fun stashUrl(path: String): String {
        val base = URI(normalizeUrl(serverUrl))
        val port = if (base.port != -1) ":${base.port}" else ""
        val cleanPath = if (path.startsWith("/")) path else "/$path"
        return "${base.scheme}://${base.host}$port$cleanPath"
    }

    private fun stashUrlWithCurrentSceneDisplay(path: String): String {
        if (path != "/scenes") return stashUrl(path)
        val displayMode = currentSceneDisplayModeParam() ?: return stashUrl(path)
        return "${stashUrl(path)}?disp=$displayMode"
    }

    private fun currentSceneDisplayModeParam(): String? {
        return try {
            val current = Uri.parse(webView.url ?: return null)
            val value = current.getQueryParameter("disp") ?: return null
            if (value in setOf("0", "1", "2", "3")) value else null
        } catch (_: Exception) {
            null
        }
    }

    private fun currentStashPath(): String {
        return try {
            URI(webView.url ?: serverUrl).path ?: "/"
        } catch (_: Exception) {
            "/"
        }
    }

    private fun handleChromeGesture(event: MotionEvent): Boolean {
        if (fullScreenView != null) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                gestureStartY = event.rawY
                gestureStartedNearTop = event.rawY <= dp(72)
            }
            MotionEvent.ACTION_UP -> {
                val deltaY = event.rawY - gestureStartY
                when {
                    gestureStartedNearTop && deltaY >= dp(48) -> {
                        showToolbar()
                        return true
                    }
                    toolbarVisible && deltaY <= -dp(32) -> {
                        hideToolbar()
                        return true
                    }
                    toolbarVisible && event.rawY > chrome.bottom + dp(16) && kotlin.math.abs(deltaY) < dp(12) -> {
                        hideToolbar()
                        return false
                    }
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                gestureStartedNearTop = false
            }
        }
        return false
    }

    private fun showToolbar() {
        if (toolbarVisible) return
        toolbarVisible = true
        chrome.visibility = View.VISIBLE
        chrome.alpha = 0f
        chrome.translationY = -dp(16).toFloat()
        chrome.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(160)
            .start()
    }

    private fun hideToolbar() {
        if (!toolbarVisible) return
        toolbarVisible = false
        chrome.animate()
            .alpha(0f)
            .translationY(-dp(16).toFloat())
            .setDuration(140)
            .withEndAction {
                if (!toolbarVisible) {
                    chrome.visibility = View.GONE
                    chrome.alpha = 1f
                    chrome.translationY = 0f
                }
            }
            .start()
    }

    private fun addButton(row: LinearLayout, label: String, onClick: () -> Unit) {
        row.addView(Button(this).apply {
            text = label
            textSize = 12f
            minHeight = dp(36)
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(6)
            }
        })
    }

    private fun effectivePort(scheme: String?, port: Int): Int {
        if (port != -1) return port
        return if (scheme == "https") 443 else 80
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_LAST_URL = "last_url"
        private const val KEY_MOBILE_PLAYBACK_ENABLED = "mobile_playback_enabled"
        private const val KEY_MOBILE_PLAYBACK_RESOLUTION = "mobile_playback_resolution"
        private const val DEFAULT_MOBILE_RESOLUTION = "STANDARD_HD"
        private val SUPPORTED_MOBILE_RESOLUTIONS = setOf("STANDARD", "STANDARD_HD", "FULL_HD")
        private val MOBILE_PLAYBACK_OPTIONS = listOf(
            PlaybackOption("720p mobile", "STANDARD_HD"),
            PlaybackOption("480p low bandwidth", "STANDARD"),
            PlaybackOption("1080p higher quality", "FULL_HD"),
            PlaybackOption("Original", "ORIGINAL")
        )

        private data class PlaybackOption(
            val label: String,
            val resolution: String
        )

        private val STASH_NAVIGATION_CHROME_SCRIPT = """
            (function() {
                const STYLE_ID = 'stash-wrapper-navigation-style';
                const COVERED_LABELS = new Set(['scenes', 'groups', 'studios', 'tags']);

                if (!document.getElementById(STYLE_ID)) {
                    const style = document.createElement('style');
                    style.id = STYLE_ID;
                    style.textContent = `
                        body {
                            padding-bottom: 0.5rem !important;
                        }

                        .top-nav {
                            background: transparent !important;
                            bottom: auto !important;
                            height: 0 !important;
                            min-height: 0 !important;
                            padding: 0 !important;
                            pointer-events: none !important;
                            top: 0 !important;
                        }

                        .top-nav > .navbar-brand,
                        .top-nav > .navbar-buttons > :not(.nav-menu-toggle),
                        .top-nav .stash-wrapper-covered-link {
                            display: none !important;
                        }

                        .top-nav > .navbar-buttons {
                            margin: 0 !important;
                            pointer-events: auto !important;
                            position: fixed !important;
                            right: 0.65rem !important;
                            top: 0.65rem !important;
                            z-index: 1042 !important;
                        }

                        .top-nav .nav-menu-toggle {
                            align-items: center !important;
                            background: rgba(15, 23, 42, 0.94) !important;
                            border: 1px solid rgba(148, 163, 184, 0.45) !important;
                            border-radius: 6px !important;
                            color: #f8fafc !important;
                            display: flex !important;
                            height: 3rem !important;
                            justify-content: center !important;
                            margin: 0 !important;
                            padding: 0 !important;
                            width: 3rem !important;
                        }

                        .top-nav .navbar-collapse {
                            background: #0f172a !important;
                            border: 1px solid rgba(148, 163, 184, 0.35) !important;
                            border-radius: 6px !important;
                            box-shadow: 0 8px 24px rgba(0, 0, 0, 0.45) !important;
                            left: auto !important;
                            margin: 0 !important;
                            max-height: calc(100vh - 5rem) !important;
                            overflow-y: auto !important;
                            padding: 0.5rem !important;
                            pointer-events: auto !important;
                            position: fixed !important;
                            right: 0.65rem !important;
                            top: 4.1rem !important;
                            width: min(20rem, calc(100vw - 1.3rem)) !important;
                            z-index: 1041 !important;
                        }

                        .top-nav .navbar-collapse:not(.show) {
                            display: none !important;
                        }

                        .top-nav .navbar-collapse .navbar-nav:first-child {
                            align-items: stretch !important;
                            display: flex !important;
                            flex-direction: column !important;
                            flex-wrap: nowrap !important;
                            gap: 0.2rem !important;
                            justify-content: stretch !important;
                            padding: 0 !important;
                            width: 100% !important;
                        }

                        .top-nav .navbar-collapse .navbar-nav + .navbar-nav {
                            border-top: 1px solid rgba(148, 163, 184, 0.25) !important;
                            display: flex !important;
                            flex-direction: row !important;
                            flex-wrap: wrap !important;
                            justify-content: center !important;
                            margin-top: 0.75rem !important;
                            padding-top: 0.75rem !important;
                        }

                        .top-nav .navbar-collapse .navbar-nav:first-child > .nav-link {
                            flex: 0 0 auto !important;
                            max-width: none !important;
                            width: 100% !important;
                        }

                        .top-nav .navbar-collapse .navbar-nav:first-child > .nav-link > .btn {
                            align-items: center !important;
                            display: flex !important;
                            flex-direction: row !important;
                            font-size: 1rem !important;
                            justify-content: flex-start !important;
                            min-height: 3.25rem !important;
                            padding: 0.6rem 0.75rem !important;
                            text-align: left !important;
                            width: 100% !important;
                        }

                        .top-nav .navbar-collapse .navbar-nav:first-child .nav-menu-icon {
                            flex: 0 0 auto !important;
                            height: 1.5rem !important;
                            margin: 0 0.85rem 0 0 !important;
                            max-height: none !important;
                            width: 1.5rem !important;
                        }
                    `;
                    document.head.appendChild(style);
                }

                function filterCoveredDestinations() {
                    const primaryMenu = document.querySelector(
                        '.top-nav .navbar-collapse .navbar-nav:first-child'
                    );
                    if (!primaryMenu) return;

                    primaryMenu.querySelectorAll(':scope > .nav-link').forEach(function(item) {
                        const label = (item.textContent || '').trim().toLowerCase();
                        item.classList.toggle('stash-wrapper-covered-link', COVERED_LABELS.has(label));
                    });
                }

                if (!window.__stashWrapperNavigationObserver) {
                    const observer = new MutationObserver(filterCoveredDestinations);
                    observer.observe(document.documentElement, { childList: true, subtree: true });
                    window.__stashWrapperNavigationObserver = observer;
                }

                filterCoveredDestinations();
                return true;
            })();
        """.trimIndent()

        private val MOBILE_PLAYBACK_SOURCE_SYNC_SCRIPT = """
            (function() {
                const targetResolution = __TARGET_RESOLUTION__;
                const existing = window.__stashWrapperMobilePlaybackSourceSync;
                if (existing) {
                    existing.targetResolution = targetResolution;
                    existing.schedule(0);
                    return true;
                }

                const state = {
                    targetResolution: targetResolution,
                    timer: 0,
                    applying: false,
                    schedule: function(delay) {
                        clearTimeout(state.timer);
                        state.timer = setTimeout(applyPreferredSource, delay || 0);
                    }
                };

                function preferredPatterns() {
                    if (state.targetResolution === 'FULL_HD') {
                        return [
                            [/mp4.*(?:1080p|full\s*hd)/i],
                            [/mp4.*(?:720p|\bhd\b)/i]
                        ];
                    }
                    if (state.targetResolution === 'STANDARD_HD') {
                        return [[/mp4.*(?:720p|\bhd\b)/i]];
                    }
                    if (state.targetResolution === 'STANDARD') {
                        return [[/mp4.*(?:480p|standard)/i]];
                    }
                    return [];
                }

                function findPreferredItem(items) {
                    const patternGroups = preferredPatterns();
                    for (const patterns of patternGroups) {
                        const match = items.find(function(item) {
                            const label = (item.textContent || '').trim();
                            return patterns.every(function(pattern) { return pattern.test(label); });
                        });
                        if (match) return match;
                    }
                    return null;
                }

                function isSelected(item) {
                    return item.classList.contains('vjs-selected') ||
                        item.getAttribute('aria-checked') === 'true' ||
                        item.getAttribute('aria-selected') === 'true';
                }

                function applyPreferredSource() {
                    if (state.applying) return;
                    const items = Array.from(document.querySelectorAll('.vjs-source-menu-item'));
                    if (!items.length) return;
                    const preferred = findPreferredItem(items);
                    if (!preferred || isSelected(preferred)) return;

                    state.applying = true;
                    preferred.click();
                    setTimeout(function() {
                        state.applying = false;
                    }, 500);
                }

                const observer = new MutationObserver(function() {
                    state.schedule(150);
                });
                observer.observe(document.documentElement, {
                    childList: true,
                    subtree: true,
                    attributes: true,
                    attributeFilter: ['class', 'aria-checked', 'aria-selected']
                });

                window.__stashWrapperMobilePlaybackSourceSync = state;
                state.schedule(0);
                setTimeout(function() { state.schedule(0); }, 500);
                setTimeout(function() { state.schedule(0); }, 1200);
                return true;
            })();
        """.trimIndent()

        private val SCENE_DISPLAY_MODE_SYNC_SCRIPT = """
            (function() {
                if (window.__stashWrapperSceneDisplaySyncInstalled) {
                    window.__stashWrapperSceneDisplaySyncApply &&
                        window.__stashWrapperSceneDisplaySyncApply();
                    return true;
                }

                window.__stashWrapperSceneDisplaySyncInstalled = true;

                const STORAGE_KEY = 'stashWrapper.sceneDisplayMode';
                const MODES = {
                    grid: '0',
                    list: '1',
                    wall: '2',
                    tagger: '3'
                };
                const MODE_BY_VALUE = {
                    '0': 'grid',
                    '1': 'list',
                    '2': 'wall',
                    '3': 'tagger'
                };
                let applying = false;
                let userModeChangedAt = 0;
                let lastUrl = location.href;

                function sceneListRoot() {
                    return document.querySelector('.item-list-container.scene-list');
                }

                function hasSceneListDom() {
                    return !!sceneListRoot();
                }

                function normalizeMode(value) {
                    if (value === undefined || value === null) return null;
                    const text = String(value).trim().toLowerCase();
                    if (Object.prototype.hasOwnProperty.call(MODE_BY_VALUE, text)) {
                        return MODE_BY_VALUE[text];
                    }
                    if (Object.prototype.hasOwnProperty.call(MODES, text)) {
                        return text;
                    }
                    return null;
                }

                function getModeFromUrl() {
                    try {
                        return normalizeMode(new URL(location.href).searchParams.get('disp'));
                    } catch (_) {
                        return null;
                    }
                }

                function saveMode(mode) {
                    const normalized = normalizeMode(mode);
                    if (!normalized) return null;
                    try {
                        localStorage.setItem(STORAGE_KEY, normalized);
                    } catch (_) {
                    }
                    return normalized;
                }

                function loadMode() {
                    try {
                        return normalizeMode(localStorage.getItem(STORAGE_KEY));
                    } catch (_) {
                        return null;
                    }
                }

                function describeElement(element) {
                    if (!element) return '';
                    const parts = [
                        element.textContent,
                        element.title,
                        element.getAttribute('aria-label'),
                        element.getAttribute('data-testid'),
                        element.getAttribute('data-test-id'),
                        element.className
                    ];
                    element.querySelectorAll('svg, use, path, span').forEach(function(child) {
                        parts.push(child.getAttribute('data-icon'));
                        parts.push(child.getAttribute('aria-label'));
                        parts.push(child.getAttribute('title'));
                        parts.push(child.className && String(child.className));
                    });
                    return parts.filter(Boolean).join(' ').toLowerCase();
                }

                function modeFromElement(element) {
                    const text = describeElement(element);
                    if (!text) return null;
                    if (/\btagger\b|\btagging\b/.test(text)) return 'tagger';
                    if (/\bwall\b|\bphoto gallery\b|\bmasonry\b/.test(text)) return 'wall';
                    if (/\blist\b|\btable\b/.test(text)) return 'list';
                    if (/\bgrid\b|\bth-large\b|\bgrip\b|\btable-cells\b|\bborder-all\b/.test(text)) return 'grid';
                    return null;
                }

                function activeButtonMode() {
                    const root = sceneListRoot();
                    if (!root) return null;
                    const activeSelectors = [
                        '.btn.active',
                        '.active',
                        '[aria-pressed="true"]',
                        '[aria-current="true"]'
                    ];
                    for (const selector of activeSelectors) {
                        const elements = Array.from(root.querySelectorAll(selector));
                        for (const element of elements) {
                            const mode = modeFromElement(element);
                            if (mode) return mode;
                        }
                    }
                    return null;
                }

                function applyModeToUrl(mode) {
                    const wanted = normalizeMode(mode);
                    if (!wanted || !hasSceneListDom()) return false;
                    const wantedValue = MODES[wanted];
                    const url = new URL(location.href);
                    if (url.searchParams.get('disp') === wantedValue) return false;
                    url.searchParams.set('disp', wantedValue);
                    applying = true;
                    history.replaceState(history.state, document.title, url.toString());
                    window.dispatchEvent(new PopStateEvent('popstate', { state: history.state }));
                    setTimeout(function() {
                        applying = false;
                    }, 100);
                    return true;
                }

                function sync() {
                    const urlMode = getModeFromUrl();
                    if (urlMode) {
                        saveMode(urlMode);
                    }

                    if (applying || !hasSceneListDom()) return;
                    if (Date.now() - userModeChangedAt < 1200) return;

                    const storedMode = loadMode();
                    if (!storedMode) return;

                    const currentMode = getModeFromUrl();
                    if (currentMode === storedMode) return;

                    const activeMode = activeButtonMode();
                    if (activeMode === storedMode) return;

                    applyModeToUrl(storedMode);
                }

                function scheduleSync(delay) {
                    setTimeout(sync, delay || 0);
                }

                function wrapHistory(name) {
                    const original = history[name];
                    history[name] = function() {
                        const result = original.apply(this, arguments);
                        scheduleSync(60);
                        scheduleSync(250);
                        return result;
                    };
                }

                wrapHistory('pushState');
                wrapHistory('replaceState');

                window.addEventListener('popstate', function() {
                    scheduleSync(60);
                    scheduleSync(250);
                });

                document.addEventListener('click', function(event) {
                    if (!hasSceneListDom()) return;
                    const target = event.target && event.target.closest &&
                        event.target.closest('button, .btn');
                    const clickedMode = modeFromElement(target);
                    if (!clickedMode) return;
                    userModeChangedAt = Date.now();
                    saveMode(clickedMode);
                    setTimeout(function() {
                        saveMode(getModeFromUrl() || activeButtonMode() || clickedMode);
                        sync();
                    }, 120);
                }, true);

                const observer = new MutationObserver(function() {
                    if (location.href !== lastUrl) {
                        lastUrl = location.href;
                        scheduleSync(60);
                    }
                    scheduleSync(300);
                });
                observer.observe(document.documentElement, { childList: true, subtree: true });

                window.__stashWrapperSceneDisplaySyncApply = sync;
                scheduleSync(100);
                scheduleSync(500);
                return true;
            })();
        """.trimIndent()
        private val SEARCH_FOCUS_SCRIPT = """
            (function() {
                const selectors = [
                    'input[type="search"]',
                    'input[placeholder="Search..."]',
                    'input[placeholder*="Search"]',
                    'input[aria-label*="Search"]',
                    '[role="searchbox"]',
                    'input.form-control',
                    '.search-input input',
                    '.search-box input'
                ];
                function visible(el) {
                    const rect = el.getBoundingClientRect();
                    const style = window.getComputedStyle(el);
                    return rect.width > 0 && rect.height > 0 &&
                        style.visibility !== 'hidden' &&
                        style.display !== 'none' &&
                        !el.disabled;
                }
                for (const selector of selectors) {
                    const elements = Array.from(document.querySelectorAll(selector));
                    for (const el of elements) {
                        if (!visible(el)) continue;
                        el.scrollIntoView({ block: 'center', inline: 'nearest' });
                        el.click();
                        el.focus({ preventScroll: false });
                        if (typeof el.select === 'function') el.select();
                        el.dispatchEvent(new Event('focus', { bubbles: true }));
                        return true;
                    }
                }
                return false;
            })();
        """.trimIndent()
    }
}
