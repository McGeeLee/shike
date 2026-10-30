package com.gee.eatapp.data

import android.content.SharedPreferences
import java.util.ArrayDeque
import java.util.UUID

/** Models SharedPreferences' separate in-memory update and durable file replacement. */
internal class SettingsTestPreferences private constructor(
    private val durable: MutableMap<String, Any>,
) : SharedPreferences {
    constructor() : this(linkedMapOf())

    enum class CommitOutcome { SUCCESS, MEMORY_ONLY_FAILURE, PERSISTED_FAILURE }

    private val memory = durable.toMutableMap()
    val commitOutcomes = ArrayDeque<CommitOutcome>()
    var commitCount = 0
        private set

    fun restart() = SettingsTestPreferences(durable)

    override fun getAll(): MutableMap<String, *> = memory.toMutableMap()
    override fun contains(key: String?) = memory.containsKey(key)
    override fun getString(key: String?, defValue: String?): String? = memory[key] as? String ?: defValue
    override fun getInt(key: String?, defValue: Int) = memory[key] as? Int ?: defValue
    override fun getLong(key: String?, defValue: Long) = memory[key] as? Long ?: defValue
    override fun getFloat(key: String?, defValue: Float) = memory[key] as? Float ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean) = memory[key] as? Boolean ?: defValue
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        (memory[key] as? Set<*>)?.filterIsInstance<String>()?.toMutableSet() ?: defValues

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        private val changes = linkedMapOf<String, Any?>()
        private var clearFirst = false

        override fun putString(key: String?, value: String?) = apply { changes[requireNotNull(key)] = value }
        override fun putInt(key: String?, value: Int) = apply { changes[requireNotNull(key)] = value }
        override fun putLong(key: String?, value: Long) = apply { changes[requireNotNull(key)] = value }
        override fun putFloat(key: String?, value: Float) = apply { changes[requireNotNull(key)] = value }
        override fun putBoolean(key: String?, value: Boolean) = apply { changes[requireNotNull(key)] = value }
        override fun putStringSet(key: String?, values: MutableSet<String>?) = apply {
            changes[requireNotNull(key)] = values?.toSet()
        }
        override fun remove(key: String?) = apply { changes[requireNotNull(key)] = null }
        override fun clear() = apply { clearFirst = true }

        override fun commit(): Boolean {
            commitCount++
            if (clearFirst) memory.clear()
            changes.forEach { (key, value) ->
                if (value == null) memory.remove(key) else memory[key] = value
            }
            val outcome = commitOutcomes.pollFirst() ?: CommitOutcome.SUCCESS
            if (outcome != CommitOutcome.MEMORY_ONLY_FAILURE) {
                durable.clear()
                durable.putAll(memory)
            }
            return outcome == CommitOutcome.SUCCESS
        }

        override fun apply() { commit() }
    }
}

/** Credential versions survive recreation independently of the ordinary preferences. */
internal class SettingsTestKeys private constructor(
    val legacyKeys: MutableMap<String, String>,
    val versions: MutableMap<String, String>,
) : ApiKeyStore {
    constructor() : this(linkedMapOf(), linkedMapOf())

    var failStage = false
    var failRecovery = false
    var stageCalls = 0
        private set
    var afterStage: (() -> Unit)? = null
    var recovery: String? = null
        private set

    fun restart() = SettingsTestKeys(legacyKeys, versions).also { it.recovery = recovery }

    override fun get(providerId: String) = legacyKeys[providerId].orEmpty()
    override fun put(providerId: String, apiKey: String) { legacyKeys[providerId] = apiKey.trim() }
    override fun getVersion(version: String) = versions[version].orEmpty()
    override fun stage(apiKey: String): String {
        stageCalls++
        check(!failStage) { "Credential persistence failed" }
        val version = UUID.randomUUID().toString()
        versions[version] = apiKey.trim()
        afterStage?.also { afterStage = null }?.invoke()
        return version
    }
    override fun retainLegacyKeys(keysJson: String) {
        check(!failRecovery) { "Recovery persistence failed" }
        if (recovery == null) recovery = keysJson
    }
}
