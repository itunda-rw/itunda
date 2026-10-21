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

/**
 * Pixel-parity staging screen backed by the exact repository prototype.
 *
 * Authentication and app-lock remain native. The prototype has its own demo
 * onboarding state, so explicitly move its in-page state to the signed-in Home
 * screen once the document has loaded. Real account data/API bindings follow later.
 */
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
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                        Log.e(
                            "ItundaPrototype",
                            "${message.message()} (line ${message.lineNumber()})",
                        )
                        return true
                    }
                }
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        super.onPageFinished(view, url)
                        // S and render are top-level lexical bindings in v395.html.
                        // Native auth has already succeeded; don't leave the prototype
                        // on its separate, demo-only onboarding screen.
                        view.evaluateJavascript(
                            """
                            (function() {
                              try {
                                if (typeof S === 'undefined' || typeof render !== 'function') {
                                  throw new Error('v395 app state did not initialize');
                                }
                                S.auth = true;
                                S.stack = [];
                                S.tab = 'home';
                                render('fade-none');
                                const view = document.getElementById('view');
                                const screen = view && view.querySelector('.screen');
                                if (!screen || !screen.innerHTML.trim()) {
                                  throw new Error('render() completed but #view has no screen content');
                                }
                                return JSON.stringify({
                                  status: 'rendered',
                                  title: document.title,
                                  screen: screen.id || screen.className,
                                  contentLength: screen.innerHTML.length,
                                  viewport: window.innerWidth + 'x' + window.innerHeight
                                });
                              } catch (e) {
                                console.error('Itunda v395 startup failed', e && e.stack || e);
                                document.body.innerHTML =
                                  '<main style="font:16px system-ui;padding:24px;color:#222">' +
                                  '<h2>Itunda could not load this screen</h2>' +
                                  '<p>The prototype did not initialize correctly. Please retry with the latest build.</p>' +
                                  '<pre style="white-space:pre-wrap;color:#b42318"></pre></main>';
                                document.querySelector('pre').textContent = String(e);
                                return 'Itunda v395 startup failed: ' + String(e);
                              }
                            })();
                            """.trimIndent(),
                            { result ->
                                Log.i("ItundaPrototype", "Startup verification: $result")
                                if (result == null || result == "null" || result.contains("startup failed")) {
                                    Log.e("ItundaPrototype", "Prototype startup did not return a successful render result")
                                }
                            },
                        )
                    }

                    override fun onReceivedError(
                        view: WebView,
                        request: WebResourceRequest,
                        error: WebResourceError,
                    ) {
                        super.onReceivedError(view, request, error)
                        if (request.isForMainFrame) {
                            Log.e(
                                "ItundaPrototype",
                                "Main-frame load failed: ${error.errorCode} ${error.description}",
                            )
                            view.loadDataWithBaseURL(
                                null,
                                "<!doctype html><meta name='viewport' content='width=device-width,initial-scale=1'>" +
                                    "<body style='font:16px system-ui;padding:24px;color:#222'>" +
                                    "<h2>Itunda screen failed to load</h2>" +
                                    "<p>${error.description}</p><p>Please retry with the latest build.</p></body>",
                                "text/html",
                                "UTF-8",
                                null,
                            )
                        }
                    }
                }
                loadUrl("file:///android_asset/v395.html")
            }
        },
        update = { },
    )
}
