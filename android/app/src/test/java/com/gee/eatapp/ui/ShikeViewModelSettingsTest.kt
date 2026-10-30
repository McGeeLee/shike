package com.gee.eatapp.ui

import android.app.Application
import android.graphics.Bitmap
import android.os.Looper
import androidx.lifecycle.viewModelScope
import com.gee.eatapp.data.AnalysisResult
import com.gee.eatapp.data.AppSettings
import com.gee.eatapp.data.Confidence
import com.gee.eatapp.data.SettingsTestKeys
import com.gee.eatapp.data.SettingsTestPreferences
import com.gee.eatapp.data.SettingsTestPreferences.CommitOutcome.MEMORY_ONLY_FAILURE
import com.gee.eatapp.data.ShikeRepository
import com.gee.eatapp.image.PreparedImage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37])
@LooperMode(LooperMode.Mode.PAUSED)
class ShikeViewModelSettingsTest {
    private lateinit var application: Application
    private lateinit var bitmap: Bitmap
    private val preferences = SettingsTestPreferences()
    private val keys = SettingsTestKeys()
    private val repository = ShikeRepository(preferences, keys)
    private val models = mutableListOf<ShikeViewModel>()
    private val requests = mutableListOf<AnalysisInvocation>()
    private val settingsA = AppSettings(
        providerId = "custom", customBaseUrl = "https://host-a.example/v1", customModel = "vision-a",
    )
    private val settingsB = settingsA.copy(customBaseUrl = "https://host-b.example/v1", customModel = "vision-b")

    @Before fun setUp() {
        application = RuntimeEnvironment.getApplication()
        bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        repository.saveSettings(settingsA, 1800, "key-a")
    }

    @After fun tearDown() {
        models.forEach { it.viewModelScope.cancel() }
        idle()
        bitmap.recycle()
    }

    @Test fun failedSecondStageKeepsTheVisibleSettingsAndAnalysisUsesTheOldPair() {
        val model = createModel()
        editSettingsB(model)
        val stagesBeforeFailure = keys.stageCalls
        preferences.commitOutcomes.add(MEMORY_ONLY_FAILURE)

        model.saveSettings()

        assertEquals(stagesBeforeFailure + 1, keys.stageCalls)
        assertTrue(keys.versions.containsValue("key-b"))
        assertEquals(settingsA, model.uiState.settings)
        assertEquals(1800, model.uiState.goal)
        assertTrue(model.uiState.settingsDraft!!.errorMessage.isNotBlank())
        assertEquals("key-b", model.uiState.settingsDraft!!.apiKey)
        analyze(model)
        assertRequest(settingsA, "key-a")

        val restarted = createModel(ShikeRepository(preferences.restart(), keys.restart()))
        assertEquals(settingsA, restarted.uiState.settings)
        analyze(restarted)
        assertRequest(settingsA, "key-a")
    }

    @Test fun successfulSaveAndRestartUseTheNewEndpointModelAndCredential() {
        val model = createModel()
        editSettingsB(model)

        model.saveSettings()

        assertNull(model.uiState.settingsDraft)
        assertEquals(settingsB, model.uiState.settings)
        assertEquals(2200, model.uiState.goal)
        analyze(model)
        assertRequest(settingsB, "key-b")

        val restarted = createModel(ShikeRepository(preferences.restart(), keys.restart()))
        assertEquals(settingsB, restarted.uiState.settings)
        assertEquals(2200, restarted.uiState.goal)
        analyze(restarted)
        assertRequest(settingsB, "key-b")
    }

    @Test fun staleVisibleSettingsCannotCombineTheirEndpointWithANewerCredential() {
        val model = createModel()
        repository.saveSettings(settingsB, 2200, "key-b")
        assertEquals(settingsA, model.uiState.settings)

        analyze(model)

        assertRequest(settingsB, "key-b")
        model.openSettings()
        assertEquals(settingsB.customBaseUrl, model.uiState.settingsDraft!!.customBaseUrl)
        assertEquals(settingsB.customModel, model.uiState.settingsDraft!!.selectedModel)
        assertEquals("key-b", model.uiState.settingsDraft!!.apiKey)
    }

    @Test fun anInFlightAnalysisKeepsItsSnapshotWhenSettingsAreSavedAgain() {
        val continueAnalysis = CompletableDeferred<Unit>()
        var enteredAnalysis = false
        val model = createModel(analysis = { settings, apiKey, image, note ->
            enteredAnalysis = true
            continueAnalysis.await()
            requests += AnalysisInvocation(settings, apiKey, image, note)
            noFood()
        })
        analyze(model)
        assertTrue(enteredAnalysis)
        assertTrue(requests.isEmpty())

        repository.saveSettings(settingsB, 2200, "key-b")
        continueAnalysis.complete(Unit)
        idle()

        assertRequest(settingsA, "key-a")
        assertEquals(settingsB, repository.apiConfiguration().settings)
        assertEquals("key-b", repository.apiConfiguration().apiKey)
    }

    @Test fun retainedDraftCannotLoadAMigratedCredentialForAnotherHost() {
        assertRetainedDraftDoesNotLoadMigratedKey(settingsB.customBaseUrl)
    }

    @Test fun retainedDraftCannotLoadAMigratedCredentialForAnotherPathOnTheSameHost() {
        assertRetainedDraftDoesNotLoadMigratedKey("https://host-a.example/other/v1")
    }

    @Test fun retainedDraftCannotLoadAMigratedCredentialForAnotherPortOnTheSameHost() {
        assertRetainedDraftDoesNotLoadMigratedKey("https://host-a.example:8443/v1")
    }

    private fun assertRetainedDraftDoesNotLoadMigratedKey(endpoint: String) {
        val model = createModel()
        model.openSettings()
        model.selectProvider("openai")
        val payload = JSONObject()
            .put("version", 1)
            .put("settings", JSONObject()
                .put("provider", "custom")
                .put("customBaseUrl", endpoint)
                .put("customModel", "vision-b"))
            .put("goal", "2200")
            .put("keys", JSONObject().put("custom", "key-b"))
            .put("logs", JSONObject())

        model.importLegacyData(payload.toString())
        assertEquals(endpoint, model.uiState.settings.customBaseUrl)
        assertEquals(settingsA.customBaseUrl, model.uiState.settingsDraft!!.customBaseUrl)
        model.selectProvider("custom")

        assertEquals(settingsA.customBaseUrl, model.uiState.settingsDraft!!.customBaseUrl)
        assertEquals("", model.uiState.settingsDraft!!.apiKey)
        assertEquals("key-b", repository.apiConfiguration().apiKey)
    }

    private fun editSettingsB(model: ShikeViewModel) {
        model.openSettings()
        model.updateCustomBaseUrl(settingsB.customBaseUrl)
        model.updateApiKey("key-b")
        model.selectModel(settingsB.customModel)
        model.updateGoal("2200")
    }

    private fun createModel(
        storage: ShikeRepository = repository,
        analysis: suspend (AppSettings, String, String, String) -> AnalysisResult = { settings, key, image, note ->
            requests += AnalysisInvocation(settings, key, image, note)
            noFood()
        },
    ): ShikeViewModel = ShikeViewModel(application, repository = storage, mealAnalysis = analysis)
        .also(models::add)

    private fun analyze(model: ShikeViewModel) {
        val setter = ShikeViewModel::class.java.getDeclaredMethod("setUiState", ShikeUiState::class.java)
        setter.isAccessible = true
        setter.invoke(model, model.uiState.copy(mealPanel = MealPanel.Preview(
            PreparedImage(bitmap, "AQID", ""), "test note",
        )))
        model.analyzeMeal()
        idle()
    }

    private fun assertRequest(settings: AppSettings, apiKey: String) {
        assertEquals("Each analysis must make exactly one request", 1, requests.size)
        assertEquals(AnalysisInvocation(settings, apiKey, "AQID", "test note"), requests.single())
        requests.clear()
    }

    private fun noFood() = AnalysisResult(false, emptyList(), 0, Confidence.LOW, "")

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private data class AnalysisInvocation(
        val settings: AppSettings,
        val apiKey: String,
        val image: String,
        val note: String,
    )
}
