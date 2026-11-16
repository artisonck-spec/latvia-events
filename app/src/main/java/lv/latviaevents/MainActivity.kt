package lv.latviaevents

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat

/** A small Android shell around the event page in assets/index.html. */
class MainActivity : Activity() {

    private lateinit var web: WebView
    private lateinit var assets: WebViewAssetLoader
    private var pageReady = false
    private var openEvent: String? = null   // event to show when the app was opened from a reminder

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WebView.setWebContentsDebuggingEnabled(
            (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        )

        assets = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        web = WebView(this)
        web.setBackgroundColor(0xFFF2F2F4.toInt())
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.setSupportMultipleWindows(false)
        web.addJavascriptInterface(Bridge(), "Android")
        web.webViewClient = object : WebViewClientCompat() {
            override fun onPageFinished(view: WebView, url: String) {
                pageReady = true
                deliverOpenEvent()
            }

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                assets.shouldInterceptRequest(request.url)

            // Our own page stays inside the app; every other link (tickets, maps, calendar) opens in the phone's apps
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url
                if (url.host == "appassets.androidplatform.net") return false
                openOutside(url)
                return true
            }
        }

        // Keep the page clear of the status bar and the gesture bar (Android 15+ draws apps edge to edge)
        web.setOnApplyWindowInsetsListener { v, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val b = insets.getInsets(android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.ime())
                v.setPadding(b.left, b.top, b.right, b.bottom)
            } else {
                @Suppress("DEPRECATION")
                v.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            }
            insets
        }

        setContentView(web)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
            (if (Build.VERSION.SDK_INT >= 26) View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0)

        openEvent = intent?.getStringExtra("event")
        Reminders.schedule(this)
        if (savedInstanceState != null) web.restoreState(savedInstanceState)
        else web.loadUrl("https://appassets.androidplatform.net/assets/index.html")
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra("event")?.let { openEvent = it; if (pageReady) deliverOpenEvent() }
    }

    private fun deliverOpenEvent() {
        val id = openEvent ?: return
        openEvent = null
        val safe = org.json.JSONObject.quote(id)
        js("window.openFromNotice && window.openFromNotice($safe)")
    }

    fun askNotify() = runOnUiThread {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 41)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    private fun openOutside(url: Uri) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    /** Back closes the open event / sheet first, then leaves the app. */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        web.evaluateJavascript("window.onBack ? window.onBack() : false") { r ->
            if (r != "true") {
                @Suppress("DEPRECATION")
                super.onBackPressed()
            }
        }
    }

    private fun js(code: String) = runOnUiThread { runCatching { web.evaluateJavascript(code, null) } }

    private fun buildNumber(): Long = runCatching {
        val info = packageManager.getPackageInfo(packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
    }.getOrDefault(0L)

    inner class Bridge {
        @JavascriptInterface fun build(): Long = buildNumber()
        @JavascriptInterface fun downloadUpdate(url: String) = this@MainActivity.downloadUpdate(url)
        @JavascriptInterface fun open(url: String) = openOutside(Uri.parse(url))
        @JavascriptInterface fun setReminders(json: String) = Reminders.save(this@MainActivity, json)
        @JavascriptInterface fun askNotify() = this@MainActivity.askNotify()
        @JavascriptInterface fun testReminder() {
            askNotify()
            Reminders.notify(this@MainActivity, "", "Atgādinājumi strādā", "Par saglabātajiem pasākumiem: dienu iepriekš 18:00 un pasākuma dienā 8:00.", 7)
        }
    }

    // ---- Updating the app: the browser downloads the new APK; tapping it in Downloads installs it over this one ----
    fun downloadUpdate(url: String) {
        if (!url.startsWith("https://")) return
        runOnUiThread { openOutside(Uri.parse(url)) }
    }
}
