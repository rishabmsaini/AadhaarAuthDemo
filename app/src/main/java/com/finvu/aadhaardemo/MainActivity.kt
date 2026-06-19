package com.finvu.aadhaardemo

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject

/**
 * MainActivity = the FIU "parent app".
 *
 * It hosts an embedded WebView that renders Finvu's enrolment journey (assets/enrolment.html).
 * The WebView is used ONLY as a launcher for the app-to-app redirection — the camera capture,
 * liveness and on-device face match happen inside the Aadhaar App's own native process, never
 * inside this WebView. That is the whole point of the redirect: a WebView cannot do face capture,
 * but it can navigate to a deep link, and the OS hands off to the Aadhaar App.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    companion object {
        // ⚠️ PLACEHOLDERS — confirm the real values against https://docs.uidai.gov.in
        // The new Aadhaar App's package name and OpenID4VP deep-link scheme are NOT publicly
        // confirmed at the time of writing. These candidates are attempted in order.
        val AADHAAR_PACKAGE_CANDIDATES = listOf(
            "in.gov.uidai.aadhaar",       // hypothesised new Aadhaar App (2026)
            "in.gov.uidai.mAadhaarPlus"   // known mAadhaar package (being phased out)
        )

        // App Link (https) form is preferred in production — the OS routes it to the Aadhaar App
        // if the app has registered an assetlinks.json for this host. Placeholder host below.
        const val AADHAAR_APP_LINK = "https://tathya.uidai.gov.in/openid4vp"

        // Custom-scheme fallback some wallets accept.
        const val AADHAAR_CUSTOM_SCHEME = "aadhaar"

        // Our own return deep link, caught by the intent-filter in the manifest.
        const val CALLBACK_PREFIX = "finvu://callback"
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true       // required to drive the mock flow
            settings.domStorageEnabled = true       // the page keeps a little state
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                // Intercept deep links navigated from inside the page (an alternative trigger path
                // to the JS bridge). Returning true means "the app handled this, don't load it".
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    if (url == null) return false
                    return when {
                        url.startsWith(CALLBACK_PREFIX) -> { handleCallback(Uri.parse(url)); true }
                        url.startsWith("$AADHAAR_CUSTOM_SCHEME://") ||
                            url.startsWith(AADHAAR_APP_LINK) -> { launchAadhaar(url); true }
                        else -> false
                    }
                }
            }
            addJavascriptInterface(Bridge(), "AndroidBridge")
        }

        setContentView(webView)
        webView.loadUrl("file:///android_asset/enrolment.html")

        // If the activity was (re)started by the return deep link, process it now.
        intent?.data?.let { if (it.toString().startsWith(CALLBACK_PREFIX)) handleCallback(it) }
    }

    // singleTask launchMode → returning via the deep link delivers here instead of a new instance.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.let { if (it.toString().startsWith(CALLBACK_PREFIX)) handleCallback(it) }
    }

    /** JavaScript bridge exposed to the page as `window.AndroidBridge`. */
    inner class Bridge {
        /** Called by the page when the user taps "Verify with Aadhaar App". */
        @JavascriptInterface
        fun launchAadhaarApp(requestUri: String) {
            runOnUiThread { launchAadhaar(buildDeepLink(requestUri)) }
        }

        @JavascriptInterface
        fun log(message: String) {
            android.util.Log.d("FinvuDemo", message)
        }
    }

    /** Wrap the (mock) OpenID4VP request_uri into the Aadhaar App deep link. */
    private fun buildDeepLink(requestUri: String): String {
        val encoded = Uri.encode(requestUri)
        return "$AADHAAR_APP_LINK?request_uri=$encoded&client_id=finvu-aa&response_mode=direct_post"
    }

    /**
     * THE REDIRECTION. Attempts to open the real Aadhaar App, and only if it is not installed
     * falls back to the in-app simulator so the demo can complete end-to-end.
     */
    private fun launchAadhaar(deepLink: String) {
        val uri = Uri.parse(deepLink)

        // 1) Try package-targeted intents for the real Aadhaar App.
        for (pkg in AADHAAR_PACKAGE_CANDIDATES) {
            val intent = Intent(Intent.ACTION_VIEW, uri).setPackage(pkg)
            if (intent.resolveActivity(packageManager) != null) {
                Toast.makeText(this, "Opening Aadhaar App ($pkg)…", Toast.LENGTH_SHORT).show()
                startActivity(intent)
                return
            }
        }

        // 2) Try an untargeted VIEW intent (lets the OS route a verified App Link to its handler),
        //    but ignore a match that is just ourselves / a browser.
        val generic = Intent(Intent.ACTION_VIEW, uri)
        val resolved = generic.resolveActivity(packageManager)
        if (resolved != null && resolved.packageName != packageName) {
            Toast.makeText(this, "Opening Aadhaar App…", Toast.LENGTH_SHORT).show()
            startActivity(generic)
            return
        }

        // 3) Fallback: launch the simulator so the flow still demonstrates the round trip.
        Toast.makeText(this, "Aadhaar App not found — launching simulator", Toast.LENGTH_LONG).show()
        startActivity(Intent(this, MockAadhaarActivity::class.java).putExtra("deepLink", deepLink))
    }

    /**
     * Receives the (mock) Verifiable Presentation from the Aadhaar App / simulator and feeds it
     * back into the WebView. In production the heavy validation (UIDAI signature, nonce, expiry,
     * holder-binding, match result) MUST happen on Finvu's SERVER, not here — this client-side
     * hand-off is only to advance the demo UI.
     */
    private fun handleCallback(uri: Uri) {
        val status = uri.getQueryParameter("status") ?: "FAILURE"
        val vc = uri.getQueryParameter("vc") ?: ""
        val payload = JSONObject().put("status", status).put("vc", vc).toString()
        webView.evaluateJavascript(
            "window.onAadhaarResult(${JSONObject.quote(payload)});",
            null
        )
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}
