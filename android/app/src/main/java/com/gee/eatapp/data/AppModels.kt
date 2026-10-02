package com.gee.eatapp.data

import java.net.URI
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt

const val DEEPSEEK_VISION_MODEL = "deepseek-flash"

// Bounds apply to a single saved meal. Aggregate statistics use a separate limit.
internal const val MAX_MEAL_CALORIES = 1_000_000
internal const val MAX_NUTRITION_GRAMS = 100_000.0
private const val MAX_TOTAL_NUTRITION_GRAMS = 1_000_000_000.0

internal fun normalizeDeepSeekVisionModel(model: String): String = when (model.trim().lowercase(Locale.ROOT)) {
    "deepseek-v4-flash", "deepseek-v4-flash-vision-exp", DEEPSEEK_VISION_MODEL -> DEEPSEEK_VISION_MODEL
    else -> model.trim()
}

enum class ApiProtocol {
    ANTHROPIC_MESSAGES,
    OPENAI_RESPONSES,
    OPENAI_CHAT_COMPLETIONS,
    GEMINI_GENERATE_CONTENT,
}

enum class ImageInputSupport { SUPPORTED, MODEL_DEPENDENT, UNSUPPORTED }

data class ProviderDefinition(
    val id: String,
    val name: String,
    val protocol: ApiProtocol,
    val baseUrl: String? = null,
    val imageInputSupport: ImageInputSupport = ImageInputSupport.SUPPORTED,
    val guidance: String,
    val strictStructuredOutput: Boolean = false,
    val modelListPath: String = "models",
)

object ProviderCatalog {
    val all = listOf(
        ProviderDefinition(
            id = "claude",
            name = "Claude (Anthropic)",
            protocol = ApiProtocol.ANTHROPIC_MESSAGES,
            baseUrl = "https://api.anthropic.com/v1",
            guidance = "使用 Anthropic 原生 Messages API 与结构化输出。",
            strictStructuredOutput = true,
        ),
        ProviderDefinition(
            id = "openai",
            name = "OpenAI",
            protocol = ApiProtocol.OPENAI_RESPONSES,
            baseUrl = "https://api.openai.com/v1",
            imageInputSupport = ImageInputSupport.MODEL_DEPENDENT,
            guidance = "使用 OpenAI 原生 Responses API 与 JSON Schema；请选择支持图片输入的模型。",
            strictStructuredOutput = true,
        ),
        ProviderDefinition(
            id = "gemini",
            name = "Gemini (Google)",
            protocol = ApiProtocol.GEMINI_GENERATE_CONTENT,
            baseUrl = "https://generativelanguage.googleapis.com/v1beta",
            imageInputSupport = ImageInputSupport.MODEL_DEPENDENT,
            guidance = "使用 Gemini 原生 generateContent、inlineData 与结构化输出；模型列表会过滤 generateContent 能力。",
            strictStructuredOutput = true,
        ),
        ProviderDefinition(
            id = "kimi",
            name = "Kimi (Moonshot AI)",
            protocol = ApiProtocol.OPENAI_CHAT_COMPLETIONS,
            baseUrl = "https://api.moonshot.ai/v1",
            guidance = "使用 Kimi 官方 Chat Completions；模型发现只保留 supports_image_in=true 的型号。",
        ),
        ProviderDefinition(
            id = "xai",
            name = "Grok (xAI)",
            protocol = ApiProtocol.OPENAI_RESPONSES,
            baseUrl = "https://api.x.ai/v1",
            guidance = "使用 xAI 原生 Responses API；模型发现只保留支持 image 输入的语言模型。",
            strictStructuredOutput = true,
            modelListPath = "language-models",
        ),
        ProviderDefinition(
            id = "mistral",
            name = "Mistral AI",
            protocol = ApiProtocol.OPENAI_CHAT_COMPLETIONS,
            baseUrl = "https://api.mistral.ai/v1",
            guidance = "使用 Mistral 原生 Chat Completions；模型发现只保留 capabilities.vision=true 的型号。",
        ),
        ProviderDefinition(
            id = "qwen",
            name = "通义千问（阿里云）",
            protocol = ApiProtocol.OPENAI_CHAT_COMPLETIONS,
            baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
            imageInputSupport = ImageInputSupport.MODEL_DEPENDENT,
            guidance = "使用百炼官方 OpenAI 兼容入口；请选择 VL 或其他支持图片输入的模型。",
        ),
        ProviderDefinition(
            id = "zhipu",
            name = "智谱 GLM",
            protocol = ApiProtocol.OPENAI_CHAT_COMPLETIONS,
            baseUrl = "https://open.bigmodel.cn/api/paas/v4",
            imageInputSupport = ImageInputSupport.MODEL_DEPENDENT,
            guidance = "使用智谱官方 OpenAI 兼容入口；请选择 GLM-4V 等视觉模型。",
        ),
        ProviderDefinition(
            id = "volcengine",
            name = "火山引擎方舟",
            protocol = ApiProtocol.OPENAI_RESPONSES,
            baseUrl = "https://ark.cn-beijing.volces.com/api/v3",
            imageInputSupport = ImageInputSupport.MODEL_DEPENDENT,
            guidance = "使用方舟原生 Responses API；需选择已开通图片理解能力的模型或推理接入点。",
        ),
        ProviderDefinition(
            id = "mimo",
            name = "Xiaomi MiMo",
            protocol = ApiProtocol.OPENAI_CHAT_COMPLETIONS,
            baseUrl = "https://api.xiaomimimo.com/v1",
            guidance = "使用 MiMo 官方 OpenAI API；当前仅 MiMo-V2.5 接受图片输入。",
        ),
        ProviderDefinition(
            id = "deepseek",
            name = "DeepSeek",
            protocol = ApiProtocol.OPENAI_CHAT_COMPLETIONS,
            baseUrl = "https://api.deepseek.com",
            guidance = "模型列表通过 DeepSeek 官方 /models 接口获取，并只保留支持 image_url 的视觉模型。",
        ),
        ProviderDefinition(
            id = "openrouter",
            name = "OpenRouter（聚合）",
            protocol = ApiProtocol.OPENAI_CHAT_COMPLETIONS,
            baseUrl = "https://openrouter.ai/api/v1",
            imageInputSupport = ImageInputSupport.MODEL_DEPENDENT,
            guidance = "聚合平台兼容入口；模型发现会请求并只显示支持 image 输入、text 输出的模型。",
            modelListPath = "models?input_modalities=image&output_modalities=text",
        ),
        ProviderDefinition(
            id = "siliconflow",
            name = "硅基流动（聚合）",
            protocol = ApiProtocol.OPENAI_CHAT_COMPLETIONS,
            baseUrl = "https://api.siliconflow.cn/v1",
            imageInputSupport = ImageInputSupport.MODEL_DEPENDENT,
            guidance = "聚合平台兼容入口；请选择模型广场中标有视觉能力的 VLM。",
        ),
        ProviderDefinition(
            id = "custom",
            name = "自定义（OpenAI 兼容）",
            protocol = ApiProtocol.OPENAI_CHAT_COMPLETIONS,
            imageInputSupport = ImageInputSupport.MODEL_DEPENDENT,
            guidance = "使用 OpenAI Chat Completions；接口和所选模型必须支持 image_url 图片输入。",
        ),
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
    if (ProviderCatalog.find(providerId) == null) return ""
    val selected = model.trim().take(200)
    return if (
        providerId == "deepseek" &&
        (selected == "deepseek-v4-pro")
    ) {
        DEEPSEEK_VISION_MODEL
    } else {
        if (providerId == "deepseek") normalizeDeepSeekVisionModel(selected) else selected
    }
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
    val photoFile: String = "",
    val analysisNotes: String = "",
    val modelLabel: String = "",
)

data class DailySummary(
    val calories: Int,
    val proteinGrams: Double,
    val carbsGrams: Double,
    val fatGrams: Double,
) {
    companion object {
        fun from(entries: List<MealEntry>) = DailySummary(
            calories = entries.saturatedIntSum { it.calories.safeCalorieValue() },
            proteinGrams = entries.saturatedNutritionSum { it.proteinGrams.safeNutritionValue() },
            carbsGrams = entries.saturatedNutritionSum { it.carbsGrams.safeNutritionValue() },
            fatGrams = entries.saturatedNutritionSum { it.fatGrams.safeNutritionValue() },
        )
    }
}

data class DailyNutritionPoint(
    val date: LocalDate,
    val mealCount: Int,
    val summary: DailySummary,
)

data class NutritionStatistics(
    val dayCount: Int,
    val recordedDays: Int,
    val mealCount: Int,
    val total: DailySummary,
    val dailyAverage: DailySummary,
) {
    companion object {
        fun from(points: List<DailyNutritionPoint>): NutritionStatistics {
            val total = DailySummary(
                calories = points.saturatedIntSum { it.summary.calories },
                proteinGrams = points.saturatedNutritionSum { it.summary.proteinGrams },
                carbsGrams = points.saturatedNutritionSum { it.summary.carbsGrams },
                fatGrams = points.saturatedNutritionSum { it.summary.fatGrams },
            )
            val dayCount = points.size
            return NutritionStatistics(
                dayCount = dayCount,
                recordedDays = points.count { it.mealCount > 0 },
                mealCount = points.saturatedIntSum { it.mealCount },
                total = total,
                dailyAverage = if (dayCount == 0) {
                    DailySummary(0, 0.0, 0.0, 0.0)
                } else {
                    DailySummary(
                        calories = (total.calories.toDouble() / dayCount).roundToInt(),
                        proteinGrams = total.proteinGrams / dayCount,
                        carbsGrams = total.carbsGrams / dayCount,
                        fatGrams = total.fatGrams / dayCount,
                    )
                },
            )
        }
    }
}

data class MacroEnergyDistribution(
    val proteinCalories: Double,
    val carbsCalories: Double,
    val fatCalories: Double,
) {
    val totalCalories: Double get() = proteinCalories + carbsCalories + fatCalories

    fun shareOf(value: Double): Float {
        val total = totalCalories
        if (!value.isFinite() || !total.isFinite() || total <= 0.0) return 0f
        return (value / total).toFloat().coerceIn(0f, 1f)
    }

    companion object {
        fun from(summary: DailySummary) = MacroEnergyDistribution(
            proteinCalories = summary.proteinGrams.safeNutritionTotal() * 4.0,
            carbsCalories = summary.carbsGrams.safeNutritionTotal() * 4.0,
            fatCalories = summary.fatGrams.safeNutritionTotal() * 9.0,
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
    if (isFinite()) coerceIn(0.0, MAX_NUTRITION_GRAMS) else 0.0

internal fun Int.safeCalorieValue(): Int = coerceIn(0, MAX_MEAL_CALORIES)

internal fun Double.safeCalorieValue(): Int =
    if (isFinite()) coerceIn(0.0, MAX_MEAL_CALORIES.toDouble()).toInt() else 0

private fun Double.safeNutritionTotal(): Double =
    if (isFinite()) coerceIn(0.0, MAX_TOTAL_NUTRITION_GRAMS) else 0.0

private inline fun <T> Iterable<T>.saturatedIntSum(value: (T) -> Int): Int =
    fold(0L) { total, item ->
        (total + value(item).coerceAtLeast(0).toLong()).coerceAtMost(Int.MAX_VALUE.toLong())
    }.toInt()

private inline fun <T> Iterable<T>.saturatedNutritionSum(value: (T) -> Double): Double =
    fold(0.0) { total, item ->
        (total + value(item).safeNutritionTotal()).coerceAtMost(MAX_TOTAL_NUTRITION_GRAMS)
    }

internal fun String.safeProviderId(): String =
    if (ProviderCatalog.find(this) != null) this else ProviderCatalog.all.first().id

internal val simplifiedChinese: Locale = Locale.SIMPLIFIED_CHINESE
