package com.gee.eatapp.data

import java.net.URI
import java.time.LocalDate
import java.util.Locale

data class ModelOption(val id: String, val label: String)

data class ProviderDefinition(
    val id: String,
    val name: String,
    val baseUrl: String? = null,
    val models: List<ModelOption> = emptyList(),
)

object ProviderCatalog {
    val all = listOf(
        ProviderDefinition(
            id = "claude",
            name = "Claude (Anthropic)",
            models = listOf(
                ModelOption("claude-opus-4-8", "Opus 4.8（最准）"),
                ModelOption("claude-sonnet-4-6", "Sonnet 4.6（均衡）"),
                ModelOption("claude-haiku-4-5", "Haiku 4.5（最省）"),
            ),
        ),
        ProviderDefinition(
            id = "openai",
            name = "OpenAI",
            baseUrl = "https://api.openai.com/v1",
            models = listOf(
                ModelOption("gpt-5.1", "GPT-5.1"),
                ModelOption("gpt-4o", "GPT-4o"),
                ModelOption("gpt-4o-mini", "GPT-4o mini（最省）"),
            ),
        ),
        ProviderDefinition(
            id = "qwen",
            name = "通义千问（阿里云）",
            baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
            models = listOf(
                ModelOption("qwen-vl-max", "Qwen-VL-Max（最准）"),
                ModelOption("qwen-vl-plus", "Qwen-VL-Plus（更省）"),
            ),
        ),
        ProviderDefinition(
            id = "zhipu",
            name = "智谱 GLM",
            baseUrl = "https://open.bigmodel.cn/api/paas/v4",
            models = listOf(
                ModelOption("glm-4v-plus", "GLM-4V-Plus"),
                ModelOption("glm-4v", "GLM-4V"),
            ),
        ),
        ProviderDefinition(id = "custom", name = "自定义（OpenAI 兼容）"),
    )

    fun find(id: String?): ProviderDefinition? = all.find { it.id == id }
}

data class AppSettings(
    val providerId: String = ProviderCatalog.all.first().id,
    val model: String = "",
    val customBaseUrl: String = "",
    val customModel: String = "",
    val dynamicColorEnabled: Boolean = false,
)

fun AppSettings.effectiveModel(): String {
    if (providerId == "custom") return customModel.trim()
    val provider = ProviderCatalog.find(providerId) ?: return ""
    return model.trim().take(200).ifEmpty { provider.models.firstOrNull()?.id.orEmpty() }
}

fun normalizeBaseUrl(value: String): String {
    val uri = try {
        URI(value.trim())
    } catch (_: Exception) {
        throw IllegalArgumentException("接口地址不是有效 URL")
    }
    if (!uri.scheme.equals("https", ignoreCase = true) || uri.host.isNullOrBlank()) {
        throw IllegalArgumentException("接口地址必须使用 HTTPS")
    }
    if (uri.userInfo != null || uri.query != null || uri.fragment != null) {
        throw IllegalArgumentException("接口地址不能包含账号、查询参数或锚点")
    }
    return uri.toASCIIString().trimEnd('/')
}

fun normalizeModelIds(values: List<String>): List<String> = values
    .map { value -> value.filterNot { it.code in 0..31 || it.code == 127 }.trim().take(200) }
    .filter(String::isNotEmpty)
    .distinct()
    .sortedWith(String.CASE_INSENSITIVE_ORDER)
    .take(500)

data class FoodItem(
    val name: String,
    val portion: String,
    val calories: Int,
    val proteinGrams: Double,
    val carbsGrams: Double,
    val fatGrams: Double,
)

enum class Confidence { LOW, MEDIUM, HIGH }

data class AnalysisResult(
    val isFood: Boolean,
    val foods: List<FoodItem>,
    val totalCalories: Int,
    val confidence: Confidence,
    val notes: String,
)

data class MealEntry(
    val id: String,
    val name: String,
    val calories: Int,
    val proteinGrams: Double,
    val carbsGrams: Double,
    val fatGrams: Double,
    val time: String,
    val note: String,
    val thumbnailBase64: String,
)

data class DailySummary(
    val calories: Int,
    val proteinGrams: Double,
    val carbsGrams: Double,
    val fatGrams: Double,
) {
    companion object {
        fun from(entries: List<MealEntry>) = DailySummary(
            calories = entries.sumOf { it.calories.coerceAtLeast(0) },
            proteinGrams = entries.sumOf { it.proteinGrams.safeNutritionValue() },
            carbsGrams = entries.sumOf { it.carbsGrams.safeNutritionValue() },
            fatGrams = entries.sumOf { it.fatGrams.safeNutritionValue() },
        )
    }
}

data class DeletedMeal(
    val eventId: Long,
    val date: LocalDate,
    val entry: MealEntry,
    val index: Int,
)

internal fun Double.safeNutritionValue(): Double =
    if (isFinite()) coerceAtLeast(0.0) else 0.0

internal fun String.safeProviderId(): String =
    if (ProviderCatalog.find(this) != null) this else ProviderCatalog.all.first().id

internal val simplifiedChinese: Locale = Locale.SIMPLIFIED_CHINESE
