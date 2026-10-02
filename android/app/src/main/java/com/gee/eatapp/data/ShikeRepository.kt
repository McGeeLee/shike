package com.gee.eatapp.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

class ShikeRepository internal constructor(
    private val preferences: SharedPreferences,
    private val keyStore: ApiKeyStore,
) {
    constructor(context: Context) : this(
        context.getSharedPreferences("shike_native", Context.MODE_PRIVATE),
        SecureApiKeyStore(context),
    )

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
        keyStore.put(settings.providerId, apiKey)
        preferences.commitOrThrow("设置保存失败，请检查设备存储空间") {
            putString(KEY_SETTINGS, json.toString())
            putInt(KEY_GOAL, goal.coerceIn(1, MAX_GOAL))
        }
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
        preferences.commitOrThrow("记录保存失败，请检查设备存储空间") {
            putString(entriesKey(date), array.toString())
        }
    }

    fun nutritionHistory(endDate: LocalDate, days: Int): List<DailyNutritionPoint> {
        val dayCount = days.coerceIn(1, MAX_HISTORY_DAYS)
        return (dayCount - 1 downTo 0).map { offset ->
            val date = endDate.minusDays(offset.toLong())
            val entries = entries(date)
            DailyNutritionPoint(
                date = date,
                mealCount = entries.size,
                summary = DailySummary.from(entries),
            )
        }
    }

    fun legacyMigrationComplete(): Boolean = preferences.getBoolean(KEY_LEGACY_MIGRATED, false)

    fun legacyDataNeedsAttention(): Boolean = !legacyMigrationComplete() ||
        !preferences.getBoolean(KEY_LEGACY_KEYS_CLEARED, false)

    fun markLegacyKeysCleared() {
        preferences.commitOrThrow("旧数据清理状态保存失败，请重试") {
            putBoolean(KEY_LEGACY_KEYS_CLEARED, true)
        }
    }

    fun importLegacyData(raw: String): Int = synchronized(preferences) {
        val root = JSONObject(raw)
        require(root.optInt("version") == 1 && root.has("settings") && root.has("goal")) {
            "旧数据读取不完整，请重试"
        }
        val legacyKeys = root.getJSONObject("keys")
        val logs = root.getJSONObject("logs")
        if (legacyMigrationComplete()) {
            if (legacyKeys.length() > 0) keyStore.retainLegacyKeys(legacyKeys.toString())
            return@synchronized 0
        }
        var importedEntries = 0
        val migratedLogs = linkedMapOf<LocalDate, List<MealEntry>>()
        val logKeys = logs.keys()
        while (logKeys.hasNext()) {
            val rawKey = logKeys.next()
            val date = LocalDate.parse(rawKey.removePrefix("eat-log-"))
            val array = logs.getJSONArray(rawKey)
            if (entries(date).isNotEmpty()) continue
            val imported = buildList {
                for (index in 0 until array.length()) {
                    array.getJSONObject(index).toMealEntry()?.let(::add)
                }
            }
            if (imported.isNotEmpty()) {
                migratedLogs[date] = imported
                importedEntries += imported.size
            }
        }
        val importedSettings = if (root.isNull("settings")) null else {
            val settingsJson = root.getJSONObject("settings")
            AppSettings(
                providerId = settingsJson.optString("provider", AppSettings().providerId).safeProviderId(),
                model = settingsJson.optString("model").take(200),
                customBaseUrl = settingsJson.optString("customBaseUrl").take(2048),
                customModel = settingsJson.optString("customModel").take(200),
                dynamicColorEnabled = false,
            )
        }
        ProviderCatalog.all.forEach { provider ->
            legacyKeys.optString(provider.id).takeIf(String::isNotBlank)?.let { keyStore.put(provider.id, it) }
        }
        if (legacyKeys.length() > 0) keyStore.retainLegacyKeys(legacyKeys.toString())
        preferences.commitOrThrow("旧数据迁移保存失败，请检查设备存储空间") {
            importedSettings?.let { settings ->
                putString(KEY_SETTINGS, JSONObject()
                    .put("provider", settings.providerId)
                    .put("model", settings.model)
                    .put("customBaseUrl", settings.customBaseUrl)
                    .put("customModel", settings.customModel)
                    .put("dynamicColorEnabled", settings.dynamicColorEnabled)
                    .toString())
            }
            root.optString("goal").toIntOrNull()?.let { putInt(KEY_GOAL, it.coerceIn(1, MAX_GOAL)) }
            migratedLogs.forEach { (date, entries) ->
                putString(entriesKey(date), JSONArray().apply { entries.forEach { put(it.toJson()) } }.toString())
            }
            putBoolean(KEY_LEGACY_MIGRATED, true)
        }
        importedEntries
    }

    private fun entriesKey(date: LocalDate) = "entries_$date"

    private fun MealEntry.toJson() = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("calories", calories.safeCalorieValue())
        .put("protein", proteinGrams.safeNutritionValue())
        .put("carbs", carbsGrams.safeNutritionValue())
        .put("fat", fatGrams.safeNutritionValue())
        .put("time", time)
        .put("note", note)
        .put("thumb", thumbnailBase64)
        .put("photoFile", photoFile)
        .put("analysisNotes", analysisNotes)
        .put("modelLabel", modelLabel)

    private fun JSONObject.toMealEntry(): MealEntry? {
        val name = optString("name", "未知食物").trim().take(300)
        if (name.isEmpty()) return null
        return MealEntry(
            id = optString("id").takeIf(String::isNotBlank) ?: UUID.randomUUID().toString(),
            name = name,
            calories = optDouble("calories", 0.0).safeCalorieValue(),
            proteinGrams = optDouble("protein", 0.0).safeNutritionValue(),
            carbsGrams = optDouble("carbs", 0.0).safeNutritionValue(),
            fatGrams = optDouble("fat", 0.0).safeNutritionValue(),
            time = optString("time").take(30),
            note = optString("note").take(500),
            photoFile = optString("photoFile").take(100),
            analysisNotes = optString("analysisNotes").take(1000),
            modelLabel = optString("modelLabel").take(300),
            thumbnailBase64 = optString("thumb")
                .removePrefix("data:image/jpeg;base64,")
                .take(MAX_THUMBNAIL_CHARS),
        )
    }

    companion object {
        const val DEFAULT_GOAL = 2000
        const val MAX_GOAL = 100_000
        const val MAX_HISTORY_DAYS = 30
        private const val MAX_THUMBNAIL_CHARS = 256_000
        private const val KEY_SETTINGS = "settings"
        private const val KEY_GOAL = "goal"
        private const val KEY_LEGACY_MIGRATED = "legacy_webview_migrated_v1"
        private const val KEY_LEGACY_KEYS_CLEARED = "legacy_webview_keys_cleared_v1"
    }
}

/** A failed commit still changes SharedPreferences memory; restore it before allowing a retry. */
internal fun SharedPreferences.commitOrThrow(
    message: String,
    changes: SharedPreferences.Editor.() -> Unit,
) = synchronized(this) {
    val previous = all.toMap()
    if (!edit().apply(changes).commit()) {
        edit().clear().apply {
            previous.forEach { (key, value) ->
                when (value) {
                    is String -> putString(key, value)
                    is Int -> putInt(key, value)
                    is Long -> putLong(key, value)
                    is Float -> putFloat(key, value)
                    is Boolean -> putBoolean(key, value)
                    is Set<*> -> putStringSet(key, value.filterIsInstance<String>().toSet())
                }
            }
        }.commit()
        throw IllegalStateException(message)
    }
}
