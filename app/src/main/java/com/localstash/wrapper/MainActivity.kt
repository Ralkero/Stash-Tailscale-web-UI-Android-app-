package com.localstash.wrapper

import android.app.Activity
import android.app.AlertDialog
import android.content.res.ColorStateList
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.RippleDrawable
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
    private lateinit var pageProgress: ProgressBar
    private lateinit var webView: WebView
    private var fullScreenView: View? = null
    private var fullScreenCallback: WebChromeClient.CustomViewCallback? = null
    private var toolbarVisible = false
    private var gestureStartY = 0f
    private var gestureStartedNearTop = false
    private var activeNavItem: LinearLayout? = null
    private val navItemsByPath = mutableMapOf<String, LinearLayout>()
    private var navigationGeneration = 0
    private var pendingNavigationAction: Pair<Int, () -> Unit>? = null
    private var mainFrameLoading = false

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
            setBackgroundColor(STASH_BACKGROUND_COLOR)
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
            setBackgroundColor(STASH_BACKGROUND_COLOR)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        pageProgress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            progressTintList = ColorStateList.valueOf(Color.rgb(14, 165, 233))
            progressBackgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(2)
            )
        }

        vertical.addView(chrome)
        vertical.addView(pageProgress)
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
                mainFrameLoading = true
                pageProgress.progress = 5
                pageProgress.visibility = View.VISIBLE
                progress.visibility = View.VISIBLE
                statusText.text = "Loading Stash..."
            }

            override fun onPageFinished(view: WebView, url: String?) {
                mainFrameLoading = false
                pageProgress.visibility = View.GONE
                progress.visibility = View.GONE
                statusText.text = url ?: serverUrl
                rememberLastUrl(url)
                syncNavSelection(url)
                installStashEnhancements()
                pendingNavigationAction?.takeIf { it.first == navigationGeneration }?.second?.invoke()
                pendingNavigationAction = null
            }

            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                super.doUpdateVisitedHistory(view, url, isReload)
                syncNavSelection(url)
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                if (request.isForMainFrame) {
                    mainFrameLoading = false
                    pageProgress.visibility = View.GONE
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
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                if (mainFrameLoading) {
                    pageProgress.progress = newProgress.coerceAtLeast(5)
                    pageProgress.visibility = if (newProgress >= 100) View.GONE else View.VISIBLE
                }
            }

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
            addDestinationNavItem(this, "Scenes", R.drawable.ic_nav_scenes, "/scenes")
            addDestinationNavItem(this, "Groups", R.drawable.ic_nav_groups, "/groups")
            addDestinationNavItem(this, "Studios", R.drawable.ic_nav_studios, "/studios")
            addDestinationNavItem(this, "Tags", R.drawable.ic_nav_tags, "/tags")
            addView(navItem("Search", R.drawable.ic_nav_search, selectOnClick = false) { openSearch() })
        }
    }

    private fun addDestinationNavItem(
        parent: LinearLayout,
        label: String,
        iconRes: Int,
        path: String
    ) {
        val item = navItem(label, iconRes) { navigateToPath(path) }
        navItemsByPath[path] = item
        parent.addView(item)
    }

    private fun navItem(
        label: String,
        iconRes: Int,
        selectOnClick: Boolean = true,
        onClick: () -> Unit
    ): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            background = navItemBackground()
            setPadding(dp(2), dp(3), dp(2), dp(3))
            setOnClickListener {
                if (selectOnClick) markNavItemActive(this)
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

    private fun navigateToPath(path: String, onComplete: (() -> Unit)? = null) {
        val requestId = ++navigationGeneration
        pendingNavigationAction = null
        val fullTarget = stashUrlWithCurrentSceneDisplay(path)
        val targetUri = Uri.parse(fullTarget)
        val targetPath = buildString {
            append(targetUri.encodedPath ?: "/")
            targetUri.encodedQuery?.let { append('?').append(it) }
            targetUri.encodedFragment?.let { append('#').append(it) }
        }
        val currentUri = webView.url?.let(Uri::parse)
        if (currentUri == null || !isAllowedInWebView(currentUri)) {
            if (onComplete != null) pendingNavigationAction = requestId to onComplete
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
            if (requestId != navigationGeneration) return@evaluateJavascript
            if (result != "true") {
                if (onComplete != null) pendingNavigationAction = requestId to onComplete
                webView.loadUrl(fullTarget)
                return@evaluateJavascript
            }
            syncNavSelection(fullTarget)
            onComplete?.invoke()
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
            focusStashSearchBox()
        } else {
            navigateToPath(searchablePath) { focusStashSearchBox() }
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

    private fun installStashEnhancements() {
        val resolution = mobilePlaybackResolution.uppercase()
        val config = JSONObject()
            .put(
                "mobilePlaybackEnabled",
                mobilePlaybackEnabled &&
                    resolution != "ORIGINAL" &&
                    resolution in SUPPORTED_MOBILE_RESOLUTIONS
            )
            .put("mobilePlaybackResolution", resolution)
        val script = buildString {
            append(STASH_WRAPPER_BOOTSTRAP_SCRIPT.replace("__CONFIG__", config.toString()))
            append('\n')
            append(STASH_NAVIGATION_CHROME_SCRIPT)
            append('\n')
            append(MOBILE_PLAYBACK_SOURCE_SYNC_SCRIPT.replace("__TARGET_RESOLUTION__", JSONObject.quote(resolution)))
            append('\n')
            append(SCENE_DISPLAY_MODE_SYNC_SCRIPT)
        }
        webView.postDelayed({
            webView.evaluateJavascript(script, null)
        }, 100)
    }

    private fun navItemBackground(): Drawable {
        val content = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), ColorDrawable(Color.rgb(51, 65, 85)))
            addState(intArrayOf(android.R.attr.state_focused), ColorDrawable(Color.rgb(51, 65, 85)))
            addState(intArrayOf(android.R.attr.state_selected), ColorDrawable(Color.rgb(30, 41, 59)))
            addState(intArrayOf(), ColorDrawable(Color.TRANSPARENT))
        }
        return RippleDrawable(
            ColorStateList.valueOf(Color.argb(110, 148, 163, 184)),
            content,
            null
        )
    }

    private fun markNavItemActive(item: LinearLayout) {
        activeNavItem?.isSelected = false
        activeNavItem = item
        item.isSelected = true
    }

    private fun syncNavSelection(value: String?) {
        val path = try {
            Uri.parse(value ?: return).path ?: return
        } catch (_: Exception) {
            return
        }
        val destination = when {
            path.startsWith("/scenes") -> "/scenes"
            path.startsWith("/groups") -> "/groups"
            path.startsWith("/studios") -> "/studios"
            path.startsWith("/tags") -> "/tags"
            else -> null
        }
        activeNavItem?.isSelected = false
        activeNavItem = destination?.let(navItemsByPath::get)
        activeNavItem?.isSelected = true
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
        private val STASH_BACKGROUND_COLOR = Color.rgb(30, 42, 50)
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

        private val STASH_WRAPPER_BOOTSTRAP_SCRIPT = """
            (function(config) {
                const existing = window.__stashWrapper;
                if (existing) {
                    existing.config = config;
                    existing.schedule({ all: true });
                    return true;
                }

                const state = {
                    config: config,
                    frame: 0,
                    flags: { nav: false, playback: false, scene: false },
                    navRoot: null,
                    navObserver: null,
                    playbackRoot: null,
                    playbackObserver: null,
                    lastUrl: location.href
                };

                function mergeFlags(next) {
                    if (!next) return;
                    if (next.all) {
                        state.flags.nav = true;
                        state.flags.playback = true;
                        state.flags.scene = true;
                        return;
                    }
                    if (next.nav) state.flags.nav = true;
                    if (next.playback) state.flags.playback = true;
                    if (next.scene) state.flags.scene = true;
                }

                function connectNavObserver() {
                    const root = document.querySelector('.top-nav');
                    if (root === state.navRoot) return;
                    if (state.navObserver) state.navObserver.disconnect();
                    state.navRoot = root;
                    state.navObserver = null;
                    if (!root) return;
                    state.navObserver = new MutationObserver(function() {
                        state.schedule({ nav: true });
                    });
                    state.navObserver.observe(root, { childList: true, subtree: true });
                }

                function connectPlaybackObserver() {
                    if (!state.config.mobilePlaybackEnabled) {
                        if (state.playbackObserver) state.playbackObserver.disconnect();
                        state.playbackObserver = null;
                        state.playbackRoot = null;
                        return;
                    }
                    const root = document.querySelector('.VideoPlayer');
                    if (root === state.playbackRoot) return;
                    if (state.playbackObserver) state.playbackObserver.disconnect();
                    state.playbackRoot = root;
                    state.playbackObserver = null;
                    if (!root) return;
                    state.playbackObserver = new MutationObserver(function() {
                        state.schedule({ playback: true });
                    });
                    state.playbackObserver.observe(root, {
                        childList: true,
                        subtree: true,
                        attributes: true,
                        attributeFilter: ['class', 'aria-checked', 'aria-selected']
                    });
                }

                function flush() {
                    state.frame = 0;
                    const flags = state.flags;
                    state.flags = { nav: false, playback: false, scene: false };

                    if (flags.nav) {
                        connectNavObserver();
                        state.navigation && state.navigation.apply();
                    }
                    if (flags.playback) {
                        connectPlaybackObserver();
                        state.playback && state.playback.apply();
                    }
                    if (flags.scene) {
                        state.scene && state.scene.schedule(80);
                    }
                }

                state.schedule = function(flags) {
                    mergeFlags(flags);
                    if (state.frame) return;
                    state.frame = requestAnimationFrame(flush);
                };

                function containsMatch(node, selector) {
                    if (!node || node.nodeType !== Node.ELEMENT_NODE) return false;
                    return node.matches(selector) || !!node.querySelector(selector);
                }

                state.documentObserver = new MutationObserver(function(records) {
                    let nav = false;
                    let playback = false;
                    let scene = false;

                    for (const record of records) {
                        for (const node of record.addedNodes) {
                            if (!nav && containsMatch(node, '.top-nav')) nav = true;
                            if (!playback && containsMatch(node, '.VideoPlayer')) playback = true;
                            if (!scene && containsMatch(node, '.item-list-container.scene-list')) scene = true;
                        }
                    }

                    if (state.navRoot && !state.navRoot.isConnected) nav = true;
                    if (state.playbackRoot && !state.playbackRoot.isConnected) playback = true;
                    if (location.href !== state.lastUrl) {
                        state.lastUrl = location.href;
                        nav = true;
                        playback = true;
                        scene = true;
                    }
                    if (nav || playback || scene) state.schedule({ nav, playback, scene });
                });
                state.documentObserver.observe(document.documentElement, {
                    childList: true,
                    subtree: true
                });

                function wrapHistory(name) {
                    const original = history[name];
                    history[name] = function() {
                        const result = original.apply(this, arguments);
                        state.lastUrl = location.href;
                        state.schedule({ nav: true, playback: true, scene: true });
                        return result;
                    };
                }
                wrapHistory('pushState');
                wrapHistory('replaceState');
                window.addEventListener('popstate', function() {
                    state.lastUrl = location.href;
                    state.schedule({ nav: true, playback: true, scene: true });
                });

                window.__stashWrapper = state;
                state.schedule({ all: true });
                return true;
            })(__CONFIG__);
        """.trimIndent()

        private val STASH_NAVIGATION_CHROME_SCRIPT = """
            (function() {
                const wrapper = window.__stashWrapper;
                if (!wrapper) return false;
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
                            width: min(15rem, calc(100vw - 1.3rem)) !important;
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
                            border-top: 0 !important;
                            display: flex !important;
                            flex-direction: row !important;
                            flex-wrap: wrap !important;
                            gap: 0.2rem !important;
                            justify-content: space-evenly !important;
                            margin-top: 0.2rem !important;
                            padding-top: 0 !important;
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

                        .top-nav .navbar-collapse .navbar-nav + .navbar-nav >
                            .nav-utility[href$="/settings"] {
                            border-bottom: 1px solid rgba(148, 163, 184, 0.25) !important;
                            flex: 0 0 100% !important;
                            margin: 0 0 0.4rem !important;
                            order: -1 !important;
                            padding: 0 0 0.4rem !important;
                            width: 100% !important;
                        }

                        .top-nav .navbar-collapse .navbar-nav + .navbar-nav >
                            .nav-utility[href$="/settings"] > .btn {
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

                        .top-nav .navbar-collapse .navbar-nav + .navbar-nav >
                            .nav-utility[href$="/settings"] > .btn::after {
                            content: 'Settings';
                        }

                        .top-nav .navbar-collapse .navbar-nav + .navbar-nav >
                            .nav-utility[href$="/settings"] .fa-icon,
                        .top-nav .navbar-collapse .navbar-nav + .navbar-nav >
                            .nav-utility[href$="/settings"] svg {
                            flex: 0 0 auto !important;
                            height: 1.5rem !important;
                            margin: 0 0.85rem 0 0 !important;
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

                wrapper.navigation = { apply: filterCoveredDestinations };
                wrapper.schedule({ nav: true });
                return true;
            })();
        """.trimIndent()

        private val MOBILE_PLAYBACK_SOURCE_SYNC_SCRIPT = """
            (function() {
                const wrapper = window.__stashWrapper;
                if (!wrapper) return false;
                const targetResolution = __TARGET_RESOLUTION__;
                const existing = wrapper.playback;
                if (existing) {
                    existing.targetResolution = targetResolution;
                    wrapper.schedule({ playback: true });
                    return true;
                }

                const state = {
                    targetResolution: targetResolution,
                    applying: false,
                    manualResolution: null
                };

                const QUALITY_OPTIONS = [
                    { value: 'SOURCE', label: 'Source', resolution: null },
                    { value: 'FOUR_K', label: '4K', resolution: 'FOUR_K' },
                    { value: 'FULL_HD', label: '1080P', resolution: 'FULL_HD' },
                    { value: 'STANDARD_HD', label: '720P', resolution: 'STANDARD_HD' },
                    { value: 'STANDARD', label: '480P', resolution: 'STANDARD' }
                ];

                function getPlayer(root) {
                    const video = root && root.querySelector('#VideoJsPlayer, video.video-js');
                    return video && (video.player || video.player_);
                }

                function getSources(player) {
                    if (!player || typeof player.sourceSelector !== 'function') return [];
                    const selector = player.sourceSelector();
                    return selector && Array.isArray(selector.sources) ? selector.sources : [];
                }

                function sourceResolution(source) {
                    try {
                        return new URL(source.src, window.location.href).searchParams.get('resolution');
                    } catch (_) {
                        return null;
                    }
                }

                function findSource(sources, option) {
                    if (option.value === 'SOURCE') {
                        return sources.find(function(source) {
                            const label = (source.label || '').toLowerCase();
                            return !sourceResolution(source) && /direct|source/.test(label);
                        }) || sources.find(function(source) {
                            return !sourceResolution(source);
                        }) || sources.find(function(source) {
                            return sourceResolution(source) === 'ORIGINAL';
                        }) || null;
                    }

                    return sources.find(function(source) {
                        return sourceResolution(source) === option.resolution &&
                            /video\/mp4/i.test(source.type || 'video/mp4');
                    }) || null;
                }

                function makeTranscodeSource(sources, option) {
                    const seed = sources.find(function(source) {
                        try {
                            return /\/scene\/\d+\/stream(?:\.[^/]*)?$/.test(
                                new URL(source.src, window.location.href).pathname
                            );
                        } catch (_) {
                            return false;
                        }
                    });
                    if (!seed || !option.resolution) return null;

                    const url = new URL(seed.src, window.location.href);
                    url.pathname = url.pathname.replace(
                        /(\/scene\/\d+\/stream)(?:\.[^/]*)?$/,
                        '$1.mp4'
                    );
                    url.searchParams.set('resolution', option.resolution);
                    return {
                        src: url.href,
                        type: 'video/mp4',
                        label: 'MP4 ' + option.label
                    };
                }

                function markSelected(root, value) {
                    if (!root) return;
                    root.querySelectorAll('.vjs-wrapper-quality-item').forEach(function(item) {
                        const selected = item.dataset.quality === value;
                        item.classList.toggle('vjs-selected', selected);
                        item.setAttribute('aria-checked', selected ? 'true' : 'false');
                    });
                    const button = root.querySelector('.vjs-wrapper-quality-button');
                    const option = QUALITY_OPTIONS.find(function(candidate) {
                        return candidate.value === value;
                    });
                    if (button && option) {
                        button.title = 'Quality: ' + option.label;
                        button.setAttribute('aria-label', 'Quality: ' + option.label);
                    }
                }

                function closeMenu(root) {
                    const control = root && root.querySelector('.vjs-wrapper-quality');
                    if (!control) return;
                    control.classList.remove('vjs-wrapper-quality-open');
                    const button = control.querySelector('.vjs-wrapper-quality-button');
                    if (button) button.setAttribute('aria-expanded', 'false');
                }

                function switchQuality(root, option, remember) {
                    const player = getPlayer(root);
                    if (!player) return false;
                    const sources = getSources(player);
                    const source = findSource(sources, option) || makeTranscodeSource(sources, option);
                    if (!source) return false;

                    if (remember) {
                        state.manualResolution = option.value;
                    }
                    markSelected(root, option.value);
                    closeMenu(root);

                    const current = typeof player.currentSource === 'function'
                        ? player.currentSource()
                        : null;
                    if (current && current.src === source.src) return true;

                    const currentTime = typeof player.currentTime === 'function'
                        ? player.currentTime()
                        : 0;
                    const wasPaused = typeof player.paused === 'function' ? player.paused() : true;
                    state.applying = true;
                    player.src(source);

                    function restorePlaybackState() {
                        if (Number.isFinite(currentTime) && currentTime > 0) {
                            const restoredTime = player.currentTime();
                            if (!Number.isFinite(restoredTime) || Math.abs(restoredTime - currentTime) > 1) {
                                try { player.currentTime(currentTime); } catch (_) {}
                            }
                        }
                        if (wasPaused) {
                            player.pause();
                        } else {
                            const playResult = player.play();
                            if (playResult && typeof playResult.catch === 'function') {
                                playResult.catch(function() {});
                            }
                        }
                    }

                    player.one('loadedmetadata', restorePlaybackState);
                    player.one('canplay', function() {
                        restorePlaybackState();
                        [250, 750, 1500].forEach(function(delay) {
                            setTimeout(restorePlaybackState, delay);
                        });
                        state.applying = false;
                    });
                    setTimeout(function() { state.applying = false; }, 3000);
                    return true;
                }

                function ensureQualityMenu(root) {
                    const controlBar = root && root.querySelector('.vjs-control-bar');
                    if (!controlBar) return;

                    root.querySelectorAll('.vjs-source-selector').forEach(function(button) {
                        button.classList.add('stash-wrapper-hidden-source-selector');
                    });

                    let control = controlBar.querySelector('.vjs-wrapper-quality');
                    if (!control) {
                        control = document.createElement('div');
                        control.className = 'vjs-wrapper-quality vjs-menu-button vjs-control vjs-button';
                        control.innerHTML = `
                            <button class="vjs-wrapper-quality-button" type="button"
                                aria-haspopup="true" aria-expanded="false" aria-label="Quality">
                                <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false">
                                    <path d="M19.4 13a7.8 7.8 0 0 0 .1-1 7.8 7.8 0 0 0-.1-1l2.1-1.6-2-3.4-2.5 1a7.7 7.7 0 0 0-1.7-1L15 3.3h-4L10.7 6A7.7 7.7 0 0 0 9 7L6.5 6l-2 3.4L6.6 11a7.8 7.8 0 0 0-.1 1 7.8 7.8 0 0 0 .1 1l-2.1 1.6 2 3.4L9 17a7.7 7.7 0 0 0 1.7 1l.3 2.7h4l.3-2.7a7.7 7.7 0 0 0 1.7-1l2.5 1 2-3.4L19.4 13ZM13 15.5a3.5 3.5 0 1 1 0-7 3.5 3.5 0 0 1 0 7Z"/>
                                </svg>
                            </button>
                            <div class="vjs-wrapper-quality-menu vjs-menu">
                                <ul class="vjs-menu-content" role="menu"></ul>
                            </div>
                        `;
                        const menu = control.querySelector('.vjs-menu-content');
                        QUALITY_OPTIONS.forEach(function(option) {
                            const item = document.createElement('li');
                            item.className = 'vjs-menu-item vjs-wrapper-quality-item';
                            item.dataset.quality = option.value;
                            item.setAttribute('role', 'menuitemradio');
                            item.setAttribute('aria-checked', 'false');
                            item.textContent = option.label;
                            item.addEventListener('click', function(event) {
                                event.stopPropagation();
                                switchQuality(root, option, true);
                            });
                            menu.appendChild(item);
                        });

                        const button = control.querySelector('.vjs-wrapper-quality-button');
                        ['touchstart', 'pointerdown', 'mousedown'].forEach(function(eventName) {
                            control.addEventListener(eventName, function(event) {
                                event.stopPropagation();
                            });
                        });
                        control.addEventListener('click', function(event) {
                            event.stopPropagation();
                            if (event.target.closest('.vjs-wrapper-quality-item')) return;
                            const open = control.classList.toggle('vjs-wrapper-quality-open');
                            button.setAttribute('aria-expanded', open ? 'true' : 'false');
                        });

                        const playbackRate = controlBar.querySelector('.vjs-playback-rate');
                        if (playbackRate) {
                            controlBar.insertBefore(control, playbackRate);
                        } else {
                            const fullscreen = controlBar.querySelector('.vjs-fullscreen-control');
                            controlBar.insertBefore(control, fullscreen || null);
                        }
                    }

                    if (!document.getElementById('stash-wrapper-quality-style')) {
                        const style = document.createElement('style');
                        style.id = 'stash-wrapper-quality-style';
                        style.textContent = `
                            .stash-wrapper-hidden-source-selector { display: none !important; }
                            .vjs-wrapper-quality { position: relative !important; }
                            .vjs-wrapper-quality-button {
                                background: transparent; border: 0; color: inherit; cursor: pointer;
                                height: 100%; margin: 0; padding: 0; width: 100%;
                            }
                            .vjs-wrapper-quality-button svg {
                                fill: currentColor; height: 1.75em; vertical-align: middle; width: 1.75em;
                            }
                            .vjs-wrapper-quality-menu {
                                bottom: 3em !important; display: none !important; left: 50% !important;
                                opacity: 0 !important; position: absolute !important;
                                transform: translateX(-50%) !important; visibility: hidden !important;
                                width: 8em !important;
                            }
                            .vjs-wrapper-quality-open .vjs-wrapper-quality-menu {
                                display: block !important; opacity: 1 !important; visibility: visible !important;
                            }
                            .vjs-wrapper-quality-menu .vjs-menu-content {
                                display: block !important; max-height: none !important; position: static !important;
                            }
                            .vjs-wrapper-quality-menu .vjs-wrapper-quality-item {
                                cursor: pointer; text-transform: none !important;
                            }
                        `;
                        document.head.appendChild(style);
                    }

                    if (state.manualResolution) {
                        markSelected(root, state.manualResolution);
                    } else {
                        const player = getPlayer(root);
                        const current = player && typeof player.currentSource === 'function'
                            ? player.currentSource()
                            : null;
                        const resolution = current && sourceResolution(current);
                        markSelected(root, resolution || 'SOURCE');
                    }
                }

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
                    const root = wrapper.playbackRoot || document.querySelector('.VideoPlayer');
                    if (!root) return;
                    ensureQualityMenu(root);

                    if (state.manualResolution) {
                        const player = getPlayer(root);
                        const current = player && typeof player.currentSource === 'function'
                            ? player.currentSource()
                            : null;
                        const currentResolution = current && sourceResolution(current);
                        const alreadySelected = state.manualResolution === 'SOURCE'
                            ? !currentResolution || currentResolution === 'ORIGINAL'
                            : currentResolution === state.manualResolution;
                        if (!alreadySelected) {
                            const option = QUALITY_OPTIONS.find(function(candidate) {
                                return candidate.value === state.manualResolution;
                            });
                            if (option) switchQuality(root, option, false);
                        }
                        return;
                    }

                    if (!wrapper.config.mobilePlaybackEnabled) return;
                    const items = Array.from(root.querySelectorAll('.vjs-source-menu-item'));
                    if (!items.length) return;
                    const preferred = findPreferredItem(items);
                    if (!preferred || isSelected(preferred)) return;

                    state.applying = true;
                    preferred.click();
                    setTimeout(function() {
                        state.applying = false;
                    }, 500);
                }

                state.apply = applyPreferredSource;
                wrapper.playback = state;
                wrapper.schedule({ playback: true });
                return true;
            })();
        """.trimIndent()

        private val SCENE_DISPLAY_MODE_SYNC_SCRIPT = """
            (function() {
                const wrapper = window.__stashWrapper;
                if (!wrapper) return false;
                if (wrapper.scene) {
                    wrapper.scene.schedule(0);
                    return true;
                }

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
                let syncTimer = 0;

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
                    clearTimeout(syncTimer);
                    syncTimer = setTimeout(sync, delay || 0);
                }

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

                wrapper.scene = { apply: sync, schedule: scheduleSync };
                wrapper.schedule({ scene: true });
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
