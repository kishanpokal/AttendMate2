package com.kishan.attendmate.ui.settings

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun Interactive3DGlobe(modifier: Modifier = Modifier) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var engineReady by remember { mutableStateOf(false) }
    val queuedCommands = remember { mutableListOf<String>() }

    // NEW: We need a way to jump back to the Main UI Thread
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    val jsInterface = remember {
        object {
            @JavascriptInterface
            fun onEngineReady() {
                // BUG FIX: @JavascriptInterface runs on a background thread.
                // We MUST post evaluateJavascript to the Main UI thread!
                mainHandler.post {
                    engineReady = true
                    if (queuedCommands.isNotEmpty()) {
                        val combined = queuedCommands.joinToString(";")
                        webViewRef?.evaluateJavascript(combined, null)
                        queuedCommands.clear()
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        ScrapingEventBus.events.collect { event ->
            val script = when (event) {
                is ScrapingEvent.SpawnSubject -> "window.spawnSubject('${event.name.replace("'", "\\'")}');"
                is ScrapingEvent.StartExtraction -> "window.startExtraction('${event.name.replace("'", "\\'")}');"
                is ScrapingEvent.UpdateProgress -> "window.updateProgress(${event.percent}, '${event.text.replace("'", "\\'")}');"
                is ScrapingEvent.FinishSubject -> "window.finishSubject('${event.name.replace("'", "\\'")}');"
                is ScrapingEvent.RecordExtracted -> "window.recordExtracted(${event.count});"
                is ScrapingEvent.SetPhase -> ""
            }
            if (script.isNotEmpty()) {
                if (engineReady) {
                    webViewRef?.evaluateJavascript(script, null)
                } else {
                    queuedCommands.add(script)
                }
            }
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                setBackgroundColor(android.graphics.Color.TRANSPARENT)

                addJavascriptInterface(jsInterface, "AndroidSync")

                webChromeClient = WebChromeClient()
                webViewClient = WebViewClient()

                loadUrl("file:///android_asset/sync_scene.html")
                webViewRef = this
            }
        },
        onRelease = { webView ->
            webView.removeJavascriptInterface("AndroidSync")
            webView.destroy()
        }
    )
}