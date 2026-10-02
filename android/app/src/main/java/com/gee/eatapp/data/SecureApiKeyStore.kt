package com.gee.eatapp.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal interface ApiKeyStore {
    fun get(providerId: String): String
    fun put(providerId: String, apiKey: String)
    fun retainLegacyKeys(keysJson: String)
}

internal class SecureApiKeyStore(context: Context) : ApiKeyStore {
    private val preferences = context.getSharedPreferences("shike_secure_keys", Context.MODE_PRIVATE)

    override fun get(providerId: String): String {
        val encoded = preferences.getString(keyName(providerId), null) ?: return ""
        return runCatching { decrypt(encoded) }.getOrElse {
            preferences.edit { remove(keyName(providerId)) }
            ""
        }
    }

    override fun put(providerId: String, apiKey: String) {
        preferences.commitOrThrow("API Key 保存失败，请检查设备存储空间") {
            if (apiKey.isBlank()) remove(keyName(providerId))
            else putString(keyName(providerId), encrypt(apiKey.trim()))
        }
    }

    /** Keeps old keys encrypted without replacing or reactivating the user's current keys. */
    override fun retainLegacyKeys(keysJson: String) {
        synchronized(preferences) {
            val existing = preferences.getString(LEGACY_RECOVERY_KEY, null)
            if (existing != null) {
                decrypt(existing) // An unreadable recovery copy must not authorize plaintext deletion.
            } else {
                preferences.commitOrThrow("旧 API Key 安全保存失败，请检查设备存储空间") {
                    putString(LEGACY_RECOVERY_KEY, encrypt(keysJson))
                }
            }
        }
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val payload = cipher.iv + encrypted
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val payload = Base64.decode(encoded, Base64.NO_WRAP)
        require(payload.size > IV_SIZE)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(TAG_LENGTH_BITS, payload.copyOfRange(0, IV_SIZE)),
        )
        return cipher.doFinal(payload.copyOfRange(IV_SIZE, payload.size)).toString(Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generateKey()
        }
    }

    private fun keyName(providerId: String) = "api_key_${providerId.safeProviderId()}"

    private companion object {
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val KEY_ALIAS = "shike_api_keys_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_LENGTH_BITS = 128
        const val IV_SIZE = 12
        const val LEGACY_RECOVERY_KEY = "legacy_api_keys_recovery_v1"
    }
}
