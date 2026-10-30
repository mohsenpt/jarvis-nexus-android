package com.jarvis.nexus

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.Context
import android.os.BatteryManager
import android.webkit.JavascriptInterface
import android.net.Uri
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.PermissionRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private var pendingWebPermission: PermissionRequest? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        webView = WebView(this)
        setContentView(webView)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.mediaPlaybackRequiresUserGesture = true
        webView.addJavascriptInterface(JarvisBridge(), "JarvisNative")
        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                runOnUiThread {
                    if (request.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) {
                        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            request.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
                        } else {
                            pendingWebPermission = request
                            requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), 701)
                        }
                    } else request.deny()
                }
            }
        }
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
        if (savedInstanceState == null) webView.loadUrl("file:///android_asset/index.html")
        else webView.restoreState(savedInstanceState)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 701) {
            val request = pendingWebPermission
            pendingWebPermission = null
            if (grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                request?.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
            } else request?.deny()
        }
    }

    // Native actions always open Android's own UI; no silent calls or SMS.
    inner class JarvisBridge {
        @JavascriptInterface
        fun openDialer(number: String) {
        runOnUiThread {
            try {
                startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number))))
            } catch (_: Exception) {
                Toast.makeText(this, "برنامه تماس در دسترس نیست.", Toast.LENGTH_SHORT).show()
            }
        }
    }

        @JavascriptInterface
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

        @JavascriptInterface
        fun batteryPercent(): Int {
            val manager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            return manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
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
