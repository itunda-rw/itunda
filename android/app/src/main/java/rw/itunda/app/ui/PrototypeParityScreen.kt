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
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import rw.itunda.core.designsystem.components.LocalRealActivity

/** Renders the canonical v1038 HTML prototype after the native session/auth gate. */
@Composable
fun PrototypeParityScreen(modifier: Modifier = Modifier) {
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    val activity = LocalRealActivity.current
    val backPending = remember { mutableStateOf(false) }

    // Mirror the prototype's own navigation model for the Android system Back action:
    // dismiss an open sheet first, then pop its screen stack, then return to Home.
    // Only finish the Activity when the prototype is already at its root destination.
    BackHandler {
        if (backPending.value) return@BackHandler
        val webView = webViewRef.value
        if (webView == null) {
            activity.finish()
        } else {
            backPending.value = true
            webView.evaluateJavascript(
                """
                (function() {
                  try {
                    const layer = document.getElementById('layer');
                    if (layer && layer.innerHTML.trim()) { closeSheet(); return 'handled'; }
                    if (typeof S !== 'undefined' && Array.isArray(S.stack) && S.stack.length) { back(); return 'handled'; }
                    if (typeof S !== 'undefined' && S.tab && S.tab !== 'home') { tab('home'); return 'handled'; }
                    return 'exit';
                  } catch (error) {
                    console.error('Itunda system Back handling failed', error);
                    return 'exit';
                  }
                })();
                """.trimIndent(),
            ) { result ->
                backPending.value = false
                if (result?.contains("exit") == true) activity.finish()
            }
        }
    }

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
                                // The prototype keeps its app state in top-level lexical bindings.
                                // Enter through its own public shortcut so the call runs in the
                                // same document scope as S and render(), rather than trying to
                                // reach those lexical bindings from Android's JS evaluator.
                                if (typeof window.obSkip === 'function') {
                                  window.obSkip();
                                } else if (typeof window.loginOk === 'function') {
                                  window.loginOk();
                                } else {
                                  throw new Error('v1038 prototype startup hook is unavailable');
                                }
                                const root = document.querySelector('#phone #view');
                                if (!root || !root.innerHTML.trim()) throw new Error('v1038 consumer app did not render #phone #view');
                                return JSON.stringify({status:'rendered', title:document.title, viewport:innerWidth+'x'+innerHeight, screen:root.querySelector('.screen')?.id || null});
                              } catch (e) {
                                const detail = String(e && (e.message || e) || 'Unknown startup error');
                                console.error('Itunda v1038 startup failed', e && e.stack || e);
                                const root = document.querySelector('#phone #view');
                                const target = root || document.body;
                                target.innerHTML = '<div style="min-height:100vh;box-sizing:border-box;padding:32px 24px;background:#fff;color:#16181D;font:16px/1.5 system-ui,sans-serif"><div style="font-size:22px;font-weight:750;margin:12px 0">Itunda could not open</div><p style="color:#596273">The consumer screen did not finish starting. Reload this screen to try again.</p><p style="font-size:12px;color:#8A3440;overflow-wrap:anywhere"></p><button onclick="location.reload()" style="margin-top:16px;border:0;border-radius:14px;background:#8F89FF;color:#fff;padding:14px 20px;font:inherit;font-weight:700">Reload screen</button></div>';
                                const message = target.querySelector('p:last-of-type');
                                if (message) message.textContent = detail;
                                return 'startup failed: ' + detail;
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
