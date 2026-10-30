package com.jarvis.nexus

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private var pendingWebPermission: PermissionRequest? = null
    private var pageReady = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(8, 11, 20)
        window.navigationBarColor = Color.rgb(8, 11, 20)
        createWebView()
        if (savedInstanceState == null) {
            loadApp()
        } else {
            val restored = webView.restoreState(savedInstanceState)
            if (restored == null) loadApp()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView() {
        webView = WebView(this)
        webView.setBackgroundColor(Color.rgb(8, 11, 20))
        webView.isVerticalScrollBarEnabled = false
        webView.isHorizontalScrollBarEnabled = false
        webView.overScrollMode = WebView.OVER_SCROLL_NEVER
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = true
            allowFileAccess = true
            allowContentAccess = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            builtInZoomControls = false
            displayZoomControls = false
            loadsImagesAutomatically = true
            cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
            // The app is bundled locally; do not permit file:// pages to read other files.
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }
        webView.addJavascriptInterface(JarvisBridge(), "JarvisNative")
        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                runOnUiThread {
                    val wantsAudio = request.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)
                    if (!wantsAudio || request.resources.size != 1) {
                        request.deny()
                    } else if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        request.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
                    } else {
                        pendingWebPermission?.deny()
                        pendingWebPermission = request
                        requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), REQUEST_AUDIO)
                    }
                }
            }
        }
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                pageReady = true
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) {
                    pageReady = false
                    Toast.makeText(this@MainActivity, "بارگذاری جارویس ناموفق بود. برنامه را یک‌بار دوباره باز کن.", Toast.LENGTH_LONG).show()
                }
            }
        }
        setContentView(webView)
    }

    private fun loadApp() {
        pageReady = false
        webView.loadUrl(APP_URL)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_AUDIO) {
            val request = pendingWebPermission
            pendingWebPermission = null
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                request?.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
            } else {
                request?.deny()
                Toast.makeText(this, "برای استفاده از میکروفون باید اجازه بدهی.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    inner class JarvisBridge {
        @JavascriptInterface
        fun openDialer(number: String) {
            runOnUiThread {
                val clean = number.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
                if (clean.length < 3) {
                    Toast.makeText(this@MainActivity, "شماره واردشده معتبر نیست.", Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }
                try {
                    startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(clean, "+*#")}")))
                } catch (_: Exception) {
                    Toast.makeText(this@MainActivity, "برنامه تماس در دسترس نیست.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        @JavascriptInterface
        fun composeSms(number: String, message: String) {
            runOnUiThread {
                val clean = number.filter { it.isDigit() || it == '+' }
                if (clean.length < 3) {
                    Toast.makeText(this@MainActivity, "شماره واردشده معتبر نیست.", Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }
                try {
                    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(clean, "+")}"))
                    intent.putExtra("sms_body", message.take(2000))
                    startActivity(intent)
                } catch (_: Exception) {
                    Toast.makeText(this@MainActivity, "برنامه پیامک در دسترس نیست.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        @JavascriptInterface
        fun batteryPercent(): Int {
            return try {
                val manager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(-1, 100)
            } catch (_: Exception) {
                -1
            }
        }
    }

    override fun onPause() {
        if (::webView.isInitialized) {
            webView.evaluateJavascript("if(window.JarvisAppLifecycle){window.JarvisAppLifecycle.pause();}", null)
            webView.onPause()
        }
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (::webView.isInitialized) webView.onResume()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (::webView.isInitialized) webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        pendingWebPermission?.deny()
        pendingWebPermission = null
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.evaluateJavascript("if(window.JarvisAppLifecycle){window.JarvisAppLifecycle.destroy();}", null)
            webView.removeJavascriptInterface("JarvisNative")
            webView.webChromeClient = null
            webView.webViewClient = WebViewClient()
            webView.removeAllViews()
            webView.destroy()
        }
        super.onDestroy()
    }

    companion object {
        private const val REQUEST_AUDIO = 701
        private const val APP_URL = "file:///android_asset/index.html"
    }
}
