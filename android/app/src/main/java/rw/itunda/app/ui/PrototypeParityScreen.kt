package rw.itunda.app.ui

import android.graphics.Color as AndroidColor
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/** Renders the canonical v434 HTML prototype after the native session/auth gate. */
@Composable
fun PrototypeParityScreen(modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(AndroidColor.WHITE)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = true
                settings.allowContentAccess = false
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                settings.cacheMode = WebSettings.LOAD_NO_CACHE
                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                        Log.e("ItundaPrototype", "${message.message()} (line ${message.lineNumber()})")
                        return true
                    }
                }
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        super.onPageFinished(view, url)
                        view.evaluateJavascript(
                            """
                            (function() {
                              try {
                                if (typeof S !== 'undefined' && typeof render === 'function') {
                                  S.auth = true;
                                  S.stack = [];
                                  S.tab = 'home';
                                  render('fade-none');
                                }
                                const root = document.body;
                                if (!root || !root.innerHTML.trim()) throw new Error('v434 document rendered no content');
                                return JSON.stringify({status:'rendered', title:document.title, viewport:innerWidth+'x'+innerHeight});
                              } catch (e) {
                                console.error('Itunda v434 startup failed', e && e.stack || e);
                                return 'startup failed: ' + String(e);
                              }
                            })();
                            """.trimIndent(),
                        ) { result -> Log.i("ItundaPrototype", "v434 startup verification: $result") }
                    }

                    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                        super.onReceivedError(view, request, error)
                        if (request.isForMainFrame) {
                            Log.e("ItundaPrototype", "Main-frame load failed: ${error.errorCode} ${error.description}")
                            view.loadDataWithBaseURL(
                                null,
                                "<!doctype html><meta name='viewport' content='width=device-width,initial-scale=1'><body style='font:16px system-ui;padding:24px'><h2>Itunda screen failed to load</h2><p>Please retry with the latest build.</p></body>",
                                "text/html", "UTF-8", null,
                            )
                        }
                    }
                }
                loadUrl("file:///android_asset/itunda__clickable_prototype_v434.html")
            }
        },
        update = {},
    )
}
