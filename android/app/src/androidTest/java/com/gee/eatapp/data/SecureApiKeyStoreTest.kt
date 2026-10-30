package com.gee.eatapp.data

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class SecureApiKeyStoreTest {
    @Test fun stagedVersionsStayEncryptedAndIndependentAfterReopeningStore() = withStore { context, preferences ->
        val store = SecureApiKeyStore(context)
        store.put("custom", "legacy-secret")
        val first = store.stage("  first-secret  ")
        val second = store.stage("second-secret")

        assertNotEquals(first, second)
        assertEquals("first-secret", store.getVersion(first))
        assertEquals("second-secret", store.getVersion(second))
        assertEquals("legacy-secret", store.get("custom"))
        val reopened = SecureApiKeyStore(context)
        assertEquals("first-secret", reopened.getVersion(first))
        assertEquals("second-secret", reopened.getVersion(second))
        preferences.all.values.filterIsInstance<String>().forEach { encrypted ->
            assertFalse(encrypted.contains("first-secret"))
            assertFalse(encrypted.contains("second-secret"))
            assertFalse(encrypted.contains("legacy-secret"))
        }
    }

    @Test fun emptyVersionIsPersistedWithoutRevivingOrRemovingOlderKeys() = withStore { context, preferences ->
        val store = SecureApiKeyStore(context)
        store.put("custom", "legacy-secret")
        val first = store.stage("first-secret")
        val empty = store.stage("   ")

        assertNotEquals(first, empty)
        assertNotNull(preferences.getString("api_key_version_$empty", null))
        val reopened = SecureApiKeyStore(context)
        assertEquals("", reopened.getVersion(empty))
        assertEquals("first-secret", reopened.getVersion(first))
        assertEquals("legacy-secret", reopened.get("custom"))
    }

    @Test fun missingOrUnreadableVersionsDoNotFallBackToLegacyKeys() = withStore { context, preferences ->
        val store = SecureApiKeyStore(context)
        store.put("custom", "legacy-secret")
        val version = store.stage("version-secret")
        preferences.edit().putString("api_key_version_$version", "corrupted ciphertext").commit()

        assertEquals("", store.getVersion(version))
        assertEquals("", store.getVersion("custom"))
        assertEquals("", store.getVersion(UUID.randomUUID().toString()))
        assertEquals("legacy-secret", store.get("custom"))
    }

    @Test fun recoveryKeysAreEncryptedAndDoNotReactivateOrReplaceActiveKeys() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("secure_keys_test_${UUID.randomUUID()}", Context.MODE_PRIVATE)
        val isolatedContext = object : ContextWrapper(context) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = preferences
        }
        val store = SecureApiKeyStore(isolatedContext)
        val oldKeys = "{\"openai\":\"old-secret\",\"custom\":\"deleted-secret\"}"
        try {
            store.put("openai", "new-secret")
            store.retainLegacyKeys(oldKeys)
            val recovery = preferences.getString("legacy_api_keys_recovery_v1", null)
            assertNotNull(recovery)
            assertFalse(recovery!!.contains("old-secret"))
            assertNotEquals(oldKeys, recovery)
            assertEquals("new-secret", store.get("openai"))
            assertEquals("", store.get("custom"))
            store.retainLegacyKeys(oldKeys)
            assertEquals(recovery, preferences.getString("legacy_api_keys_recovery_v1", null))
            assertEquals("new-secret", SecureApiKeyStore(isolatedContext).get("openai"))
        } finally { preferences.edit().clear().commit() }
    }

    private fun withStore(test: (Context, SharedPreferences) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("secure_versions_test_${UUID.randomUUID()}", Context.MODE_PRIVATE)
        val isolatedContext = object : ContextWrapper(context) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = preferences
        }
        try {
            test(isolatedContext, preferences)
        } finally {
            preferences.edit().clear().commit()
        }
    }
}
