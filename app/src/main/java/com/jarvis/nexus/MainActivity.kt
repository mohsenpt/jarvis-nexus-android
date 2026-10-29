package com.jarvis.nexus

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private val liveUrl = "https://mohsenpt.github.io/jarvis-nexus/"

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        webView = WebView(this)
        setContentView(webView)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.mediaPlaybackRequiresUserGesture = true
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun onReceivedError(
                view: WebView, request: android.webkit.WebResourceRequest,
                error: android.webkit.WebResourceError
            ) {
                if (request.isForMainFrame) {
                    Toast.makeText(this@MainActivity, "اتصال برقرار نشد؛ نسخه ذخیره‌شده باز می‌شود.", Toast.LENGTH_LONG).show()
                    view.loadUrl("file:///android_asset/index.html")
                }
            }
        }
        if (savedInstanceState == null) webView.loadUrl(liveUrl)
        else webView.restoreState(savedInstanceState)
    }

    // Native actions are intentionally user-triggered and do not place calls or send SMS silently.
    @android.webkit.JavascriptInterface
    fun openDialer(number: String) {
        runOnUiThread {
            try {
                startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number))))
            } catch (_: Exception) {
                Toast.makeText(this, "برنامه تماس در دسترس نیست.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @android.webkit.JavascriptInterface
    fun composeSms(number: String, message: String) {
        runOnUiThread {
            try {
                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(number)))
                intent.putExtra("sms_body", message)
                startActivity(intent)
            } catch (_: Exception) {
                Toast.makeText(this, "برنامه پیامک در دسترس نیست.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}
