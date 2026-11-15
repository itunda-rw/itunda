package rw.itunda.app.ui

import android.graphics.Color as AndroidColor
import android.util.Log
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/** Renders the canonical v1038 HTML prototype after the native session/auth gate. */
@Composable
fun PrototypeParityScreen(modifier: Modifier = Modifier) {
    val webViewRef = remember { mutableStateOf<WebView?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef.value?.let { webView ->
                // Detach before destroy: Android WebView requires that it is no longer
                // attached to a parent when its renderer and native resources are released.
                (webView.parent as? ViewGroup)?.removeView(webView)
                webView.stopLoading()
                webView.webChromeClient = null
                webView.webViewClient = WebViewClient()
                webView.destroy()
            }
            webViewRef.value = null
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                webViewRef.value = this
                setBackgroundColor(AndroidColor.TRANSPARENT)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = true
                settings.allowContentAccess = false
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                // Keep the local prototype instant while allowing remote font assets
                // to use WebView's normal HTTP cache between app launches.
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                        val detail = "${message.message()} (line ${message.lineNumber()})"
                        when (message.messageLevel()) {
                            ConsoleMessage.MessageLevel.ERROR -> Log.e("ItundaPrototype", detail)
                            ConsoleMessage.MessageLevel.WARNING -> Log.w("ItundaPrototype", detail)
                            ConsoleMessage.MessageLevel.LOG -> Log.i("ItundaPrototype", detail)
                            ConsoleMessage.MessageLevel.TIP,
                            ConsoleMessage.MessageLevel.DEBUG -> Log.d("ItundaPrototype", detail)
                        }
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
                                const root = document.querySelector('#phone #view');
                                if (!root || !root.innerHTML.trim()) throw new Error('v1038 consumer app did not render #phone #view');
                                return JSON.stringify({status:'rendered', title:document.title, viewport:innerWidth+'x'+innerHeight});
                              } catch (e) {
                                console.error('Itunda v1038 startup failed', e && e.stack || e);
                                return 'startup failed: ' + String(e);
                              }
                            })();
                            """.trimIndent(),
                        ) { result -> Log.i("ItundaPrototype", "v1038 startup verification: $result") }
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
                loadUrl("file:///android_asset/itunda_clickable_prototype_v1038.html")
            }
        },
        update = {},
    )
}
