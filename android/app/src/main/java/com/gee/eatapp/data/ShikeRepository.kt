package com.gee.eatapp.data

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

class ShikeRepository(context: Context) {
    private val preferences = context.getSharedPreferences("shike_native", Context.MODE_PRIVATE)
    private val keyStore = SecureApiKeyStore(context)

    fun settings(): AppSettings {
        val raw = preferences.getString(KEY_SETTINGS, null) ?: return AppSettings()
        return runCatching {
            val json = JSONObject(raw)
            AppSettings(
                providerId = json.optString("provider", AppSettings().providerId).safeProviderId(),
                model = json.optString("model").take(200),
                customBaseUrl = json.optString("customBaseUrl").take(2048),
                customModel = json.optString("customModel").take(200),
                dynamicColorEnabled = json.optBoolean("dynamicColorEnabled", false),
            )
        }.getOrDefault(AppSettings())
    }

    fun goal(): Int = preferences.getInt(KEY_GOAL, DEFAULT_GOAL).coerceIn(1, MAX_GOAL)

    fun apiKey(providerId: String): String = keyStore.get(providerId)

    fun saveSettings(settings: AppSettings, goal: Int, apiKey: String) {
        val json = JSONObject()
            .put("provider", settings.providerId.safeProviderId())
            .put("model", settings.model.take(200))
            .put("customBaseUrl", settings.customBaseUrl.take(2048))
            .put("customModel", settings.customModel.take(200))
            .put("dynamicColorEnabled", settings.dynamicColorEnabled)
        preferences.edit {
            putString(KEY_SETTINGS, json.toString())
            putInt(KEY_GOAL, goal.coerceIn(1, MAX_GOAL))
        }
        keyStore.put(settings.providerId, apiKey)
    }

    fun entries(date: LocalDate): List<MealEntry> {
        val raw = preferences.getString(entriesKey(date), null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    array.optJSONObject(index)?.toMealEntry()?.let(::add)
                }
            }
        }.getOrDefault(emptyList())
    }

    fun saveEntries(date: LocalDate, entries: List<MealEntry>) {
        val array = JSONArray()
        entries.forEach { array.put(it.toJson()) }
        preferences.edit { putString(entriesKey(date), array.toString()) }
    }

    fun legacyMigrationComplete(): Boolean = preferences.getBoolean(KEY_LEGACY_MIGRATED, false)

    fun importLegacyData(raw: String): Int {
        if (legacyMigrationComplete()) return 0
        var importedEntries = 0
        runCatching {
            val root = JSONObject(raw)
            root.optJSONObject("settings")?.let { settingsJson ->
                val importedSettings = AppSettings(
                    providerId = settingsJson.optString("provider", AppSettings().providerId).safeProviderId(),
                    model = settingsJson.optString("model").take(200),
                    customBaseUrl = settingsJson.optString("customBaseUrl").take(2048),
                    customModel = settingsJson.optString("customModel").take(200),
                    dynamicColorEnabled = false,
                )
                val importedGoal = root.optString("goal").toIntOrNull()?.coerceIn(1, MAX_GOAL) ?: DEFAULT_GOAL
                val importedKey = root.optJSONObject("keys")?.optString(importedSettings.providerId).orEmpty()
                saveSettings(importedSettings, importedGoal, importedKey)
                root.optJSONObject("keys")?.let { keys ->
                    ProviderCatalog.all.forEach { provider ->
                        keys.optString(provider.id).takeIf(String::isNotBlank)?.let { keyStore.put(provider.id, it) }
                    }
                }
            }
            val logs = root.optJSONObject("logs")
            if (logs != null) {
                val keys = logs.keys()
                while (keys.hasNext()) {
                    val rawKey = keys.next()
                    val date = runCatching { LocalDate.parse(rawKey.removePrefix("eat-log-")) }.getOrNull() ?: continue
                    if (entries(date).isNotEmpty()) continue
                    val array = logs.optJSONArray(rawKey) ?: continue
                    val imported = buildList {
                        for (index in 0 until array.length()) {
                            array.optJSONObject(index)?.toMealEntry()?.let(::add)
                        }
                    }
                    if (imported.isNotEmpty()) {
                        saveEntries(date, imported)
                        importedEntries += imported.size
                    }
                }
            }
        }
        preferences.edit { putBoolean(KEY_LEGACY_MIGRATED, true) }
        return importedEntries
    }

    private fun entriesKey(date: LocalDate) = "entries_$date"

    private fun MealEntry.toJson() = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("calories", calories)
        .put("protein", proteinGrams)
        .put("carbs", carbsGrams)
        .put("fat", fatGrams)
        .put("time", time)
        .put("note", note)
        .put("thumb", thumbnailBase64)

    private fun JSONObject.toMealEntry(): MealEntry? {
        val name = optString("name", "未知食物").trim().take(300)
        if (name.isEmpty()) return null
        return MealEntry(
            id = optString("id").takeIf(String::isNotBlank) ?: UUID.randomUUID().toString(),
            name = name,
            calories = optDouble("calories", 0.0).safeNutritionValue().toInt(),
            proteinGrams = optDouble("protein", 0.0).safeNutritionValue(),
            carbsGrams = optDouble("carbs", 0.0).safeNutritionValue(),
            fatGrams = optDouble("fat", 0.0).safeNutritionValue(),
            time = optString("time").take(30),
            note = optString("note").take(500),
            thumbnailBase64 = optString("thumb")
                .removePrefix("data:image/jpeg;base64,")
                .take(MAX_THUMBNAIL_CHARS),
        )
    }

    companion object {
        const val DEFAULT_GOAL = 2000
        const val MAX_GOAL = 100_000
        private const val MAX_THUMBNAIL_CHARS = 256_000
        private const val KEY_SETTINGS = "settings"
        private const val KEY_GOAL = "goal"
        private const val KEY_LEGACY_MIGRATED = "legacy_webview_migrated_v1"
    }
}
