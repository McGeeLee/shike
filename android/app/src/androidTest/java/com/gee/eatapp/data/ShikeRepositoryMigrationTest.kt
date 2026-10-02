package com.gee.eatapp.data

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.util.UUID

class ShikeRepositoryMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferences = context.getSharedPreferences("migration_test_${UUID.randomUUID()}", Context.MODE_PRIVATE)
    private val keyStore = FakeKeys()
    private val date = LocalDate.of(2026, 10, 1)

    @After fun cleanUp() { preferences.edit().clear().commit() }

    @Test fun malformedExportDoesNotMarkMigrationComplete() {
        val repository = ShikeRepository(preferences, keyStore)
        assertThrows(Exception::class.java) { repository.importLegacyData("{invalid") }
        assertThrows(Exception::class.java) { repository.importLegacyData("{}") }
        assertFalse(repository.legacyMigrationComplete())
        assertTrue(repository.legacyDataNeedsAttention())
        assertTrue(repository.entries(date).isEmpty())
    }

    @Test fun keyFailureLeavesMigrationRetryable() {
        val repository = ShikeRepository(preferences, keyStore)
        keyStore.failWrites = true
        assertThrows(IllegalStateException::class.java) { repository.importLegacyData(payload()) }
        assertFalse(repository.legacyMigrationComplete())
        assertTrue(repository.entries(date).isEmpty())
        keyStore.failWrites = false
        assertEquals(1, repository.importLegacyData(payload()))
        assertEquals("old-key", keyStore.get("openai"))
        assertEquals(1, repository.entries(date).size)
        assertTrue(repository.legacyMigrationComplete())
        assertEquals(0, repository.importLegacyData(payload()))
        assertEquals(1, repository.entries(date).size)
    }

    @Test fun failedCommitRestoresMemoryAndDoesNotAuthorizeCleanup() {
        val repository = ShikeRepository(FailOncePreferences(preferences), keyStore)
        assertThrows(IllegalStateException::class.java) { repository.importLegacyData(payload()) }
        assertFalse(repository.legacyMigrationComplete())
        assertTrue(repository.entries(date).isEmpty())
        assertEquals(1, repository.importLegacyData(payload()))
        assertTrue(repository.legacyDataNeedsAttention())
        repository.markLegacyKeysCleared()
        assertFalse(repository.legacyDataNeedsAttention())
    }

    @Test fun completedMigrationArchivesOldKeysWithoutOverwritingOrReactivatingCurrentKeys() {
        preferences.edit().putBoolean("legacy_webview_migrated_v1", true).commit()
        keyStore.active["openai"] = "new-key"
        val repository = ShikeRepository(preferences, keyStore)
        assertTrue(repository.legacyDataNeedsAttention())
        assertEquals(0, repository.importLegacyData(payload()))
        assertEquals("new-key", keyStore.get("openai"))
        assertEquals("", keyStore.get("custom"))
        val recovery = JSONObject(keyStore.recovery!!)
        assertEquals("old-key", recovery.getString("openai"))
        assertEquals("deleted-key", recovery.getString("custom"))
        assertTrue(repository.entries(date).isEmpty())
        assertEquals(1, keyStore.recoveryWrites)
        repository.importLegacyData(payload())
        assertEquals(1, keyStore.recoveryWrites)
    }

    @Test fun recoveryFailureDoesNotPermitMarkingPlaintextAsCleaned() {
        preferences.edit().putBoolean("legacy_webview_migrated_v1", true).commit()
        keyStore.failRecovery = true
        val repository = ShikeRepository(preferences, keyStore)
        assertThrows(IllegalStateException::class.java) { repository.importLegacyData(payload()) }
        assertTrue(repository.legacyDataNeedsAttention())
        assertNull(keyStore.recovery)
    }

    private fun payload() = JSONObject()
        .put("version", 1)
        .put("settings", JSONObject().put("provider", "openai").put("model", "vision-model"))
        .put("keys", JSONObject().put("openai", "old-key").put("custom", "deleted-key"))
        .put("goal", "1800")
        .put("logs", JSONObject().put("eat-log-$date", JSONArray().put(JSONObject()
            .put("name", "米饭").put("calories", 300).put("protein", 5))))
        .toString()

    private class FakeKeys : ApiKeyStore {
        val active = mutableMapOf<String, String>()
        var failWrites = false
        var failRecovery = false
        var recovery: String? = null
        var recoveryWrites = 0
        override fun get(providerId: String) = active[providerId].orEmpty()
        override fun put(providerId: String, apiKey: String) {
            check(!failWrites) { "key write failed" }
            active[providerId] = apiKey
        }
        override fun retainLegacyKeys(keysJson: String) {
            check(!failRecovery) { "recovery write failed" }
            if (recovery == null) {
                recovery = keysJson
                recoveryWrites++
            }
        }
    }

    private class FailOncePreferences(private val delegate: SharedPreferences) : SharedPreferences by delegate {
        private var failNextCommit = true
        override fun edit(): SharedPreferences.Editor {
            val editor = delegate.edit()
            return object : SharedPreferences.Editor by editor {
                override fun commit(): Boolean {
                    val persisted = editor.commit()
                    if (failNextCommit) {
                        failNextCommit = false
                        return false
                    }
                    return persisted
                }
            }
        }
    }
}
