package com.gee.eatapp.data

import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class LegacyDataMigratorTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val origin = "https://migration-test-${UUID.randomUUID()}.example/"
    private val oldKeys = "{\"openai\":\"old-secret\"}"

    @After fun cleanUp() { evaluate("localStorage.clear(); true;") }

    @Test fun removesPlaintextOnlyAfterImporterSucceeds() {
        seedKeys()
        var importedKey: String? = null
        val failure = migrate { raw ->
            importedKey = JSONObject(raw).getJSONObject("keys").getString("openai")
        }
        assertNull(failure)
        assertEquals("old-secret", importedKey)
        assertEquals("null", evaluate("localStorage.getItem('eat-keys')"))
    }

    @Test fun importerFailurePreservesPlaintextForRetry() {
        seedKeys()
        val failure = migrate { throw IllegalStateException("simulated persistence failure") }
        assertNotNull(failure)
        assertEquals(oldKeys, storedKeys())
        assertNull(migrate { })
        assertEquals("null", evaluate("localStorage.getItem('eat-keys')"))
    }

    @Test fun malformedSourceDoesNotImportOrClearKeys() {
        evaluate("localStorage.setItem('eat-keys', 'not-json'); true;")
        var importerCalled = false
        assertNotNull(migrate { importerCalled = true })
        assertFalse(importerCalled)
        assertEquals("not-json", storedKeys())
    }

    private fun seedKeys() {
        evaluate("localStorage.setItem('eat-keys', ${JSONObject.quote(oldKeys)}); true;")
    }

    private fun storedKeys() = JSONArray("[${evaluate("localStorage.getItem('eat-keys')")}]").getString(0)

    private fun migrate(importer: (String) -> Unit): Throwable? {
        val done = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        instrumentation.runOnMainSync {
            LegacyDataMigrator.readFromOrigin(context, origin, importer,
                onKeysCleared = { done.countDown() },
                onError = { failure.set(it); done.countDown() })
        }
        assertTrue("Migration timed out", done.await(20, TimeUnit.SECONDS))
        return failure.get()
    }

    private fun evaluate(script: String): String {
        val done = CountDownLatch(1)
        val result = AtomicReference<String>()
        instrumentation.runOnMainSync {
            val view = WebView(context)
            view.settings.javaScriptEnabled = true
            view.settings.domStorageEnabled = true
            view.webViewClient = object : WebViewClient() {
                override fun onPageFinished(webView: WebView, url: String) {
                    webView.evaluateJavascript(script) {
                        result.set(it)
                        webView.stopLoading()
                        webView.destroy()
                        done.countDown()
                    }
                }
            }
            view.loadDataWithBaseURL(origin, "<html></html>", "text/html", "UTF-8", origin)
        }
        assertTrue("WebView script timed out", done.await(10, TimeUnit.SECONDS))
        return result.get()
    }
}
