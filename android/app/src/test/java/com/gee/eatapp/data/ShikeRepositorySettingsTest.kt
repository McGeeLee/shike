package com.gee.eatapp.data

import com.gee.eatapp.data.SettingsTestPreferences.CommitOutcome.MEMORY_ONLY_FAILURE
import com.gee.eatapp.data.SettingsTestPreferences.CommitOutcome.PERSISTED_FAILURE
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37])
class ShikeRepositorySettingsTest {
    private val preferences = SettingsTestPreferences()
    private val keys = SettingsTestKeys()
    private val repository = ShikeRepository(preferences, keys)
    private val settingsA = AppSettings(
        providerId = "custom", customBaseUrl = "https://host-a.example/v1", customModel = "vision-a",
    )
    private val settingsB = settingsA.copy(
        customBaseUrl = "https://host-b.example/v1", customModel = "vision-b", dynamicColorEnabled = true,
    )
    private val date = LocalDate.of(2026, 10, 1)

    @Test fun secondStageFailureKeepsOldEndpointCredentialAndGoalInMemoryAndAfterRestart() {
        repository.saveSettings(settingsA, 1800, "key-a")
        val stagesBeforeFailure = keys.stageCalls
        preferences.commitOutcomes.add(MEMORY_ONLY_FAILURE)

        assertThrows(IllegalStateException::class.java) {
            repository.saveSettings(settingsB, 2200, "key-b")
        }

        assertEquals(stagesBeforeFailure + 1, keys.stageCalls)
        assertTrue("The new credential reached durable storage", keys.versions.containsValue("key-b"))
        assertConfiguration(repository, settingsA, "key-a", 1800)
        assertConfiguration(restart(), settingsA, "key-a", 1800)

        repository.saveSettings(settingsB, 2200, "key-b")
        assertConfiguration(restart(), settingsB, "key-b", 2200)
    }

    @Test fun rollbackDiskFailureCannotMixTheDurableSettingsAndCredentialReference() {
        repository.saveSettings(settingsA, 1800, "key-a")
        // The reported failure may leave a complete new file. A failed rollback restores only memory.
        preferences.commitOutcomes.add(PERSISTED_FAILURE)
        preferences.commitOutcomes.add(MEMORY_ONLY_FAILURE)

        assertThrows(IllegalStateException::class.java) {
            repository.saveSettings(settingsB, 2200, "key-b")
        }

        assertConfiguration(repository, settingsA, "key-a", 1800)
        assertConfiguration(restart(), settingsB, "key-b", 2200)
    }

    @Test fun failedPublishAndFailedRollbackLeaveTheDurableOldPairUsable() {
        repository.saveSettings(settingsA, 1800, "key-a")
        preferences.commitOutcomes.add(MEMORY_ONLY_FAILURE)
        preferences.commitOutcomes.add(MEMORY_ONLY_FAILURE)

        assertThrows(IllegalStateException::class.java) {
            repository.saveSettings(settingsB, 2200, "key-b")
        }

        assertConfiguration(repository, settingsA, "key-a", 1800)
        assertConfiguration(restart(), settingsA, "key-a", 1800)
    }

    @Test fun normalSavePublishesNewPairAndPreservesOtherProviderKeysAfterRestart() {
        repository.saveSettings(AppSettings(providerId = "openai", model = "vision"), 2000, "openai-key")
        repository.saveSettings(settingsA, 1800, "key-a")
        val snapshot = repository.apiConfiguration()

        repository.saveSettings(settingsB, 2200, "key-b")

        assertConfiguration(repository, settingsB, "key-b", 2200)
        assertConfiguration(restart(), settingsB, "key-b", 2200)
        assertEquals("openai-key", restart().apiKey("openai"))
        assertEquals(settingsA, snapshot.settings)
        assertEquals("key-a", snapshot.apiKey)
        assertFalse(preferences.all.values.any { it.toString().contains("key-b") })
    }

    @Test fun credentialStageFailureDoesNotAttemptToPublishSettings() {
        repository.saveSettings(settingsA, 1800, "key-a")
        val commitsBeforeFailure = preferences.commitCount
        keys.failStage = true

        assertThrows(IllegalStateException::class.java) {
            repository.saveSettings(settingsB, 2200, "key-b")
        }

        assertEquals(commitsBeforeFailure, preferences.commitCount)
        assertConfiguration(repository, settingsA, "key-a", 1800)
        assertConfiguration(restart(), settingsA, "key-a", 1800)
    }

    @Test fun processDeathAfterCredentialStageLeavesAnUnreferencedVersion() {
        repository.saveSettings(settingsA, 1800, "key-a")
        keys.afterStage = { throw SimulatedProcessDeath() }

        assertThrows(SimulatedProcessDeath::class.java) {
            repository.saveSettings(settingsB, 2200, "key-b")
        }

        assertTrue(keys.versions.containsValue("key-b"))
        assertConfiguration(restart(), settingsA, "key-a", 1800)
    }

    @Test fun clearingCredentialDoesNotReactivateTheLegacyProviderKeyAfterRestart() {
        seedNativeLegacyConfiguration()
        repository.saveSettings(settingsB, 2200, "")

        assertEquals("key-a", keys.get("custom"))
        assertConfiguration(repository, settingsB, "", 2200)
        assertConfiguration(restart(), settingsB, "", 2200)
    }

    @Test fun missingReferencedVersionFailsClosedInsteadOfFallingBackToLegacyKey() {
        seedNativeLegacyConfiguration()
        repository.saveSettings(settingsB, 2200, "key-b")
        keys.versions.clear()

        assertConfiguration(repository, settingsB, "", 2200)
        assertConfiguration(restart(), settingsB, "", 2200)
    }

    @Test fun existingNativeConfigurationSurvivesFirstVersionedSaveFailure() {
        seedNativeLegacyConfiguration()
        assertConfiguration(restart(), settingsA, "key-a", 1800)
        preferences.commitOutcomes.add(MEMORY_ONLY_FAILURE)

        assertThrows(IllegalStateException::class.java) {
            repository.saveSettings(settingsB, 2200, "key-b")
        }

        assertEquals("key-a", keys.get("custom"))
        assertConfiguration(repository, settingsA, "key-a", 1800)
        assertConfiguration(restart(), settingsA, "key-a", 1800)
        repository.saveSettings(settingsB, 2200, "key-b")
        assertConfiguration(restart(), settingsB, "key-b", 2200)
    }

    @Test fun failedLegacyImportDoesNotReplaceActiveCredentialsAndRemainsRetryable() {
        repository.saveSettings(settingsA, 1800, "key-a")
        preferences.commitOutcomes.add(MEMORY_ONLY_FAILURE)

        assertThrows(IllegalStateException::class.java) { repository.importLegacyData(legacyPayload()) }

        assertConfiguration(repository, settingsA, "key-a", 1800)
        assertConfiguration(restart(), settingsA, "key-a", 1800)
        assertFalse(restart().legacyMigrationComplete())
        assertTrue(restart().entries(date).isEmpty())
        assertEquals(1, repository.importLegacyData(legacyPayload()))
        val restarted = restart()
        assertConfiguration(restarted, settingsB.copy(dynamicColorEnabled = false), "key-b", 2200)
        assertEquals("openai-legacy-key", restarted.apiKey("openai"))
        assertEquals("米饭", restarted.entries(date).single().name)
        assertTrue(restarted.legacyMigrationComplete())
        assertEquals(0, restarted.importLegacyData(legacyPayload()))
        assertEquals(1, restarted.entries(date).size)
    }

    @Test fun legacyCustomKeyWithoutAnEndpointIsArchivedWithoutActivation() {
        repository.saveSettings(settingsA, 1800, "key-a")
        val payload = JSONObject(legacyPayload()).put("settings", JSONObject.NULL).toString()

        assertEquals(1, repository.importLegacyData(payload))

        assertConfiguration(restart(), settingsA, "key-a", 2200)
        assertEquals("key-b", JSONObject(keys.recovery!!).getString("custom"))
        assertEquals("openai-legacy-key", restart().apiKey("openai"))
    }

    @Test fun aCustomCredentialIsBoundToItsFullBaseUrlIncludingPathAndPort() {
        repository.saveSettings(settingsA, 1800, "key-a")

        assertEquals("key-a", repository.apiKey("custom", settingsA.customBaseUrl + "/"))
        assertEquals("", repository.apiKey("custom", "https://host-a.example/another-api"))
        assertEquals("", repository.apiKey("custom", "https://host-a.example:8443/v1"))
        assertEquals("", repository.apiKey("custom", settingsB.customBaseUrl))
        assertEquals("", repository.apiKey("custom", ""))
        assertEquals("", repository.apiKey("custom", "not a URL"))
    }

    @Test fun savingAnotherProviderCannotBindItsRetainedCustomKeyToANewEndpoint() {
        seedNativeLegacyConfiguration()
        val otherProvider = settingsB.copy(providerId = "openai", model = "vision")

        repository.saveSettings(otherProvider, 2200, "openai-key")

        assertConfiguration(restart(), otherProvider, "openai-key", 2200)
        assertEquals("", restart().apiKey("custom"))
        assertEquals("", restart().apiKey("custom", settingsB.customBaseUrl))
        assertEquals("key-a", restart().apiKey("custom", settingsA.customBaseUrl))
    }

    @Test fun importingANewEndpointWithoutACustomKeyCannotReuseTheOldNativeKey() {
        seedNativeLegacyConfiguration()
        val payload = JSONObject(legacyPayload()).put("keys", JSONObject().put("openai", "openai-legacy-key"))

        repository.importLegacyData(payload.toString())

        assertConfiguration(restart(), settingsB.copy(dynamicColorEnabled = false), "", 2200)
        assertEquals("key-a", restart().apiKey("custom", settingsA.customBaseUrl))
        assertEquals("", restart().apiKey("custom", settingsB.customBaseUrl))
    }

    @Test fun importingANewEndpointWithABlankCustomKeyCannotReuseTheOldVersionedKey() {
        repository.saveSettings(settingsA, 1800, "key-a")
        val payload = JSONObject(legacyPayload()).put("keys", JSONObject().put("custom", ""))

        repository.importLegacyData(payload.toString())

        assertConfiguration(restart(), settingsB.copy(dynamicColorEnabled = false), "", 2200)
        assertEquals("key-a", restart().apiKey("custom", settingsA.customBaseUrl))
        assertEquals("", restart().apiKey("custom", settingsB.customBaseUrl))
    }

    @Test fun invalidCustomEndpointsCannotAuthorizeACredentialEvenWhenTheyMatch() {
        val invalidSettings = settingsA.copy(customBaseUrl = "not a URL")
        repository.saveSettings(invalidSettings, 1800, "key-a")

        assertConfiguration(restart(), invalidSettings, "", 1800)
        assertEquals("", restart().apiKey("custom", "not a URL"))
    }

    @Test fun completedMigrationArchivesOldKeysWithoutReplacingOrReactivatingCurrentKey() {
        repository.saveSettings(settingsA, 1800, "")
        preferences.edit().putBoolean("legacy_webview_migrated_v1", true).commit()

        assertEquals(0, repository.importLegacyData(legacyPayload()))

        assertConfiguration(restart(), settingsA, "", 1800)
        assertEquals("key-b", JSONObject(keys.recovery!!).getString("custom"))
        assertTrue(restart().entries(date).isEmpty())
    }

    private fun restart() = ShikeRepository(preferences.restart(), keys.restart())

    private fun assertConfiguration(repository: ShikeRepository, settings: AppSettings, key: String, goal: Int) {
        val configuration = repository.apiConfiguration()
        assertEquals(settings, configuration.settings)
        assertEquals(key, configuration.apiKey)
        assertEquals(key, repository.apiKey(settings.providerId))
        assertEquals(goal, repository.goal())
    }

    private fun seedNativeLegacyConfiguration() {
        keys.legacyKeys["custom"] = "key-a"
        preferences.edit().putString("settings", settingsJson(settingsA).toString()).putInt("goal", 1800).commit()
    }

    private fun settingsJson(settings: AppSettings) = JSONObject()
        .put("provider", settings.providerId)
        .put("model", settings.model)
        .put("customBaseUrl", settings.customBaseUrl)
        .put("customModel", settings.customModel)
        .put("dynamicColorEnabled", settings.dynamicColorEnabled)

    private fun legacyPayload() = JSONObject()
        .put("version", 1)
        .put("settings", settingsJson(settingsB))
        .put("goal", "2200")
        .put("keys", JSONObject().put("custom", "key-b").put("openai", "openai-legacy-key"))
        .put("logs", JSONObject().put("eat-log-$date", JSONArray().put(JSONObject()
            .put("name", "米饭").put("calories", 300).put("protein", 5))))
        .toString()

    private class SimulatedProcessDeath : Error("Process stopped before configuration publication")
}
