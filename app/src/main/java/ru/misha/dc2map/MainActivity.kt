package ru.misha.dc2map

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader

class MainActivity : Activity() {
    private lateinit var web: WebView
    @Volatile private var latest = """{"ok":false,"err":"starting"}"""
    @Volatile private var running = true
    private var poller: Thread? = null

    /** window.DC2.state() for map.html — returns the last state read by the poller, never blocks */
    inner class Bridge {
        @JavascriptInterface
        fun state(): String = latest
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val port = intent?.getIntExtra("pine_port", 0)?.takeIf { it > 0 }
            ?: getSharedPreferences("dc2map", MODE_PRIVATE).getInt("pine_port", 28011)

        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        web = WebView(this)
        web.setBackgroundColor(0xFF070B10.toInt())
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true          // options are saved in localStorage
        web.settings.mediaPlaybackRequiresUserGesture = true
        web.addJavascriptInterface(Bridge(), "DC2")
        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                loader.shouldInterceptRequest(request.url)
        }
        setContentView(web)
        hideBars()
        web.loadUrl("https://appassets.androidplatform.net/assets/map.html")

        poller = Thread({
            val reader = GameReader(port)
            while (running) {
                val t0 = System.nanoTime()
                latest = reader.state()
                val ms = (System.nanoTime() - t0) / 1_000_000
                // ~10 updates per second; back off when the emulator is not answering
                val pause = if (latest.startsWith("{\"ok\":false")) 1000L else maxOf(10L, 90L - ms)
                try { Thread.sleep(pause) } catch (_: InterruptedException) { break }
            }
        }, "pine-poller").apply { isDaemon = true; start() }
    }

    @Suppress("DEPRECATION")
    private fun hideBars() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideBars()
    }

    override fun onDestroy() {
        running = false
        poller?.interrupt()
        web.destroy()
        super.onDestroy()
    }
}
