package com.gee.eatapp.data

import android.annotation.SuppressLint
import android.app.Activity
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONArray

/** Reads the previous Capacitor origin once so an app update does not strand WebView-local data. */
object LegacyDataMigrator {
    @SuppressLint("SetJavaScriptEnabled")
    fun read(activity: Activity, onResult: (String) -> Unit) {
        val webView = WebView(activity)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                view.evaluateJavascript(EXPORT_SCRIPT) { encodedResult ->
                    val decoded = runCatching {
                        JSONArray("[$encodedResult]").optString(0, "{}")
                    }.getOrDefault("{}")
                    onResult(decoded)
                    view.stopLoading()
                    view.destroy()
                }
            }
        }
        webView.loadDataWithBaseURL(
            "https://localhost/",
            "<!doctype html><html><head><meta charset=\"utf-8\"></head><body></body></html>",
            "text/html",
            "UTF-8",
            "https://localhost/",
        )
    }

    private val EXPORT_SCRIPT = """
        (function () {
          const read = (key, fallback) => {
            try { return JSON.parse(localStorage.getItem(key)) ?? fallback; }
            catch (_) { return fallback; }
          };
          const logs = {};
          for (let index = 0; index < localStorage.length; index += 1) {
            const key = localStorage.key(index);
            if (key && key.startsWith('eat-log-')) logs[key] = read(key, []);
          }
          return JSON.stringify({
            settings: read('eat-settings', null),
            keys: read('eat-keys', {}),
            goal: localStorage.getItem('eat-goal'),
            logs
          });
        })()
    """.trimIndent()
}
