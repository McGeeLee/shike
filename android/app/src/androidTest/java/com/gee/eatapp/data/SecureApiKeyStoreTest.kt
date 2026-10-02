package com.gee.eatapp.data

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class SecureApiKeyStoreTest {
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
}
