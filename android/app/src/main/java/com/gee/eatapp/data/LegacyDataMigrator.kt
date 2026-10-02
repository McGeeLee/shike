package com.gee.eatapp.data

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONArray

/** Imports the Capacitor origin, then clears plaintext keys only after confirmed safe storage. */
object LegacyDataMigrator {
    private var migrationInProgress = false

    fun read(
        activity: Activity,
        onResult: (String) -> Unit,
        onKeysCleared: () -> Unit = {},
        onError: (Throwable) -> Unit = {},
    ) {
        if (migrationInProgress) return
        migrationInProgress = true
        readFromOrigin(activity, "https://localhost/", onResult,
            onKeysCleared = {
                try { onKeysCleared() }
                finally { migrationInProgress = false }
            },
            onError = {
                migrationInProgress = false
                onError(it)
            })
    }

    @SuppressLint("SetJavaScriptEnabled")
    internal fun readFromOrigin(
        context: Context,
        origin: String,
        onResult: (String) -> Unit,
        onKeysCleared: () -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        val webView = runCatching { WebView(context) }.getOrElse {
            onError(it)
            return
        }
        val handler = Handler(Looper.getMainLooper())
        var finished = false
        var started = false
        var timeout: Runnable? = null
        fun finish(error: Throwable? = null) {
            if (finished) return
            finished = true
            timeout?.let(handler::removeCallbacks)
            webView.stopLoading()
            webView.destroy()
            if (error != null) onError(error)
        }
        timeout = Runnable { finish(IllegalStateException("旧数据读取超时")) }
        handler.postDelayed(timeout, 15_000L)
        runCatching {
            webView.settings.javaScriptEnabled = true
            webView.settings.domStorageEnabled = true
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    if (finished || started) return
                    started = true
                    view.evaluateJavascript(EXPORT_SCRIPT) { encodedResult ->
                        if (finished) return@evaluateJavascript
                        val imported = runCatching {
                            val decoded = JSONArray("[$encodedResult]").get(0) as? String
                                ?: throw IllegalStateException("旧数据读取失败，请重试")
                            onResult(decoded)
                        }
                        if (imported.isFailure) {
                            finish(imported.exceptionOrNull())
                        } else {
                            view.evaluateJavascript(CLEAR_KEYS_SCRIPT) cleared@{ cleared ->
                                if (finished) return@cleared
                                if (cleared != "true") {
                                    finish(IllegalStateException("旧 API Key 清理未完成"))
                                } else {
                                    val result = runCatching(onKeysCleared)
                                    finish(result.exceptionOrNull())
                                }
                            }
                        }
                    }
                }
            }
            webView.loadDataWithBaseURL(
                origin,
                "<!doctype html><html><head><meta charset=\"utf-8\"></head><body></body></html>",
                "text/html",
                "UTF-8",
                origin,
            )
        }.onFailure { finish(it) }
    }

    internal val EXPORT_SCRIPT = """
        (function () {
          const read = (key, fallback) => {
            const raw = localStorage.getItem(key);
            return raw === null ? fallback : JSON.parse(raw);
          };
          const logs = Object.create(null);
          for (let index = 0; index < localStorage.length; index += 1) {
            const key = localStorage.key(index);
            if (key && key.startsWith('eat-log-')) logs[key] = read(key, []);
          }
          window.__shikeLegacyKeys = localStorage.getItem('eat-keys');
          return JSON.stringify({
            version: 1,
            settings: read('eat-settings', null),
            keys: read('eat-keys', {}),
            goal: localStorage.getItem('eat-goal'),
            logs
          });
        })()
    """.trimIndent()

    internal val CLEAR_KEYS_SCRIPT = """
        (function () {
          if (localStorage.getItem('eat-keys') !== window.__shikeLegacyKeys) return false;
          localStorage.removeItem('eat-keys');
          return localStorage.getItem('eat-keys') === null;
        })()
    """.trimIndent()
}
