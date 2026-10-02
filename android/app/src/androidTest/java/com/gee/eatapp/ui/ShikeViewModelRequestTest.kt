package com.gee.eatapp.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import androidx.lifecycle.viewModelScope
import androidx.test.platform.app.InstrumentationRegistry
import com.gee.eatapp.update.AppRelease
import com.gee.eatapp.image.CapturedPhotoStore
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

class ShikeViewModelRequestTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val application get() = instrumentation.targetContext.applicationContext as Application
    private fun onMain(action: () -> Unit) = instrumentation.runOnMainSync(action)
    private fun idle() = instrumentation.waitForIdleSync()

    @Test fun switchingProviderReleasesLoadingAndIgnoresOldCompletion() {
        val oldResponse = CompletableDeferred<List<String>>()
        lateinit var model: ShikeViewModel
        onMain {
            model = ShikeViewModel(application, modelDiscovery = {
                _, _ -> withContext(NonCancellable) { oldResponse.await() }
            })
            model.openSettings()
            model.selectProvider("openai")
            model.discoverModels(false)
            assertTrue(model.uiState.settingsDraft!!.isLoading)
            model.selectProvider("claude")
            assertFalse(model.uiState.settingsDraft!!.isLoading)
        }
        oldResponse.complete(listOf("old-model"))
        idle()
        onMain {
            assertEquals("claude", model.uiState.settingsDraft!!.providerId)
            assertFalse(model.uiState.settingsDraft!!.isLoading)
            assertTrue(model.uiState.settingsDraft!!.discoveredModels.isEmpty())
            model.viewModelScope.cancel()
        }
    }

    @Test fun oldSessionDoesNotOverwriteNewSessionForSameProvider() {
        val oldResponse = CompletableDeferred<List<String>>()
        val newResponse = CompletableDeferred<List<String>>()
        var requests = 0
        lateinit var model: ShikeViewModel
        onMain {
            model = ShikeViewModel(application, modelDiscovery = { _, _ ->
                val response = if (requests++ == 0) oldResponse else newResponse
                withContext(NonCancellable) { response.await() }
            })
            model.openSettings()
            model.discoverModels(false)
            model.dismissSettings()
            model.openSettings()
            model.discoverModels(false)
        }
        oldResponse.complete(listOf("old-model"))
        idle()
        onMain { assertTrue(model.uiState.settingsDraft!!.isLoading) }
        newResponse.complete(listOf("new-model"))
        idle()
        onMain {
            assertFalse(model.uiState.settingsDraft!!.isLoading)
            assertEquals(listOf("new-model"), model.availableModels(model.uiState.settingsDraft!!).map { it.id })
            model.viewModelScope.cancel()
        }
    }

    @Test fun changingCredentialsCancelsLoadingAndClearsConnectionStatus() {
        val response = CompletableDeferred<List<String>>()
        lateinit var model: ShikeViewModel
        onMain {
            model = ShikeViewModel(application, modelDiscovery = { _, _ -> response.await() })
            model.openSettings()
            model.discoverModels(true)
            model.updateApiKey("replacement-test-key")
            assertFalse(model.uiState.settingsDraft!!.isLoading)
            assertEquals(ConnectionStatusKind.IDLE, model.uiState.settingsDraft!!.statusKind)
            assertEquals("replacement-test-key", model.uiState.settingsDraft!!.apiKey)
            model.viewModelScope.cancel()
        }
    }

    @Test fun oldDownloadCompletionCannotClearOrReplaceRetryState() {
        val oldResult = CompletableDeferred<File>()
        val newResult = CompletableDeferred<File>()
        val oldFinished = CompletableDeferred<Unit>()
        var requests = 0
        lateinit var model: ShikeViewModel
        val release = AppRelease("9.0.0", "https://github.com/McGeeLee/shike/releases/tag/v9.0.0", "", "shike-v9.0.0.apk", "", "")
        val oldFile = File.createTempFile("old-request-test-", ".apk", application.cacheDir)
        val newFile = File.createTempFile("new-request-test-", ".apk", application.cacheDir)
        try {
            onMain {
                model = ShikeViewModel(application, releaseDownloader = { _, _ ->
                    val first = requests++ == 0
                    try { withContext(NonCancellable) { (if (first) oldResult else newResult).await() } }
                    finally { if (first) oldFinished.complete(Unit) }
                })
                // Seed a discovered release without contacting GitHub or changing release build flags.
                val setter = ShikeViewModel::class.java.getDeclaredMethod("setUiState", ShikeUiState::class.java)
                setter.isAccessible = true
                setter.invoke(model, model.uiState.copy(availableUpdate = release))
                model.downloadUpdate()
                model.cancelUpdateDownload()
                model.downloadUpdate()
            }
            oldResult.complete(oldFile)
            runBlocking { oldFinished.await() }
            idle()
            onMain {
                assertTrue(model.uiState.isDownloadingUpdate)
                assertFalse(oldFile.exists())
                assertTrue(newFile.exists())
            }
            newResult.complete(newFile)
            idle()
            onMain {
                assertFalse(model.uiState.isDownloadingUpdate)
                assertEquals(newFile.absolutePath, model.uiState.downloadedUpdatePath)
                assertTrue(newFile.exists())
                assertTrue(model.uiState.updateStatusMessage.contains("下载完成"))
                model.viewModelScope.cancel()
            }
        } finally {
            oldFile.delete()
            newFile.delete()
        }
    }

    @Test fun cameraSourceIsRemovedAfterSuccessfulAndFailedPreparation() {
        val store = CapturedPhotoStore(application.cacheDir)
        val valid = store.create()
        Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).let { bitmap ->
            try { valid.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) } }
            finally { bitmap.recycle() }
        }
        val invalid = store.create().apply { writeText("invalid image") }
        lateinit var model: ShikeViewModel
        try {
            onMain {
                model = ShikeViewModel(application)
                model.prepareImage(FileProvider.getUriForFile(application, "${application.packageName}.fileprovider", valid), true)
            }
            awaitSourceCleanup(valid)
            onMain {
                assertTrue(model.uiState.mealPanel is MealPanel.Preview)
                model.prepareImage(FileProvider.getUriForFile(application, "${application.packageName}.fileprovider", invalid), true)
            }
            awaitSourceCleanup(invalid)
            onMain {
                assertTrue(model.uiState.mealPanel is MealPanel.Error)
                model.viewModelScope.cancel()
            }
        } finally {
            valid.delete()
            invalid.delete()
        }
    }

    private fun awaitSourceCleanup(file: File) {
        val deadline = System.nanoTime() + 10_000_000_000L
        while (file.exists() && System.nanoTime() < deadline) Thread.sleep(25)
        idle()
        assertFalse("Camera source must be removed after preparation", file.exists())
    }
}
