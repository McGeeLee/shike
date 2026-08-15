package com.gee.eatapp.data

import java.net.URI
import java.time.LocalDate
import java.util.Locale

data class ModelOption(val id: String, val label: String)

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
    val models: List<ModelOption> = emptyList(),
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
            models = listOf(
                ModelOption("claude-opus-4-8", "Opus 4.8（最准）"),
                ModelOption("claude-sonnet-4-6", "Sonnet 4.6（均衡）"),
                ModelOption("claude-haiku-4-5", "Haiku 4.5（最省）"),
            ),
            guidance = "使用 Anthropic 原生 Messages API 与结构化输出。",
            strictStructuredOutput = true,
        ),
        ProviderDefinition(
            id = "openai",
            name = "OpenAI",
            protocol = ApiProtocol.OPENAI_RESPONSES,
            baseUrl = "https://api.openai.com/v1",
            models = listOf(
                ModelOption("gpt-5.1", "GPT-5.1"),
                ModelOption("gpt-4o", "GPT-4o"),
                ModelOption("gpt-4o-mini", "GPT-4o mini（最省）"),
            ),
            imageInputSupport = ImageInputSupport.MODEL_DEPENDENT,
            guidance = "使用 OpenAI 原生 Responses API 与 JSON Schema；请选择支持图片输入的模型。",
            strictStructuredOutput = true,
        ),
        ProviderDefinition(
            id = "gemini",
            name = "Gemini (Google)",
            protocol = ApiProtocol.GEMINI_GENERATE_CONTENT,
            baseUrl = "https://generativelanguage.googleapis.com/v1beta",
            models = listOf(
                ModelOption("gemini-3.6-flash", "Gemini 3.6 Flash（推荐）"),
                ModelOption("gemini-3.5-flash", "Gemini 3.5 Flash"),
            ),
            imageInputSupport = ImageInputSupport.MODEL_DEPENDENT,
            guidance = "使用 Gemini 原生 generateContent、inlineData 与结构化输出；模型列表会过滤 generateContent 能力。",
            strictStructuredOutput = true,
        ),
        ProviderDefinition(
            id = "kimi",
            name = "Kimi (Moonshot AI)",
            protocol = ApiProtocol.OPENAI_CHAT_COMPLETIONS,
            baseUrl = "https://api.moonshot.ai/v1",
            models = listOf(
                ModelOption("kimi-k2.6", "Kimi K2.6（推荐）"),
                ModelOption("kimi-k2.5", "Kimi K2.5"),
            ),
            guidance = "使用 Kimi 官方 Chat Completions；模型发现只保留 supports_image_in=true 的型号。",
        ),
        ProviderDefinition(
            id = "xai",
            name = "Grok (xAI)",
            protocol = ApiProtocol.OPENAI_RESPONSES,
            baseUrl = "https://api.x.ai/v1",
            models = listOf(
                ModelOption("grok-4.5", "Grok 4.5"),
            ),
            guidance = "使用 xAI 原生 Responses API；模型发现只保留支持 image 输入的语言模型。",
            strictStructuredOutput = true,
            modelListPath = "language-models",
        ),
        ProviderDefinition(
            id = "mistral",
            name = "Mistral AI",
            protocol = ApiProtocol.OPENAI_CHAT_COMPLETIONS,
            baseUrl = "https://api.mistral.ai/v1",
            models = listOf(
                ModelOption("mistral-large-2512", "Mistral Large 3"),
                ModelOption("mistral-small-2506", "Mistral Small 3.2（更省）"),
            ),
            guidance = "使用 Mistral 原生 Chat Completions；模型发现只保留 capabilities.vision=true 的型号。",
        ),
        ProviderDefinition(
            id = "qwen",
            name = "通义千问（阿里云）",
            protocol = ApiProtocol.OPENAI_CHAT_COMPLETIONS,
            baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
            models = listOf(
                ModelOption("qwen-vl-max", "Qwen-VL-Max（最准）"),
                ModelOption("qwen-vl-plus", "Qwen-VL-Plus（更省）"),
            ),
            imageInputSupport = ImageInputSupport.MODEL_DEPENDENT,
            guidance = "使用百炼官方 OpenAI 兼容入口；请选择 VL 或其他支持图片输入的模型。",
        ),
        ProviderDefinition(
            id = "zhipu",
            name = "智谱 GLM",
            protocol = ApiProtocol.OPENAI_CHAT_COMPLETIONS,
            baseUrl = "https://open.bigmodel.cn/api/paas/v4",
            models = listOf(
                ModelOption("glm-4v-plus", "GLM-4V-Plus"),
                ModelOption("glm-4v", "GLM-4V"),
            ),
            imageInputSupport = ImageInputSupport.MODEL_DEPENDENT,
            guidance = "使用智谱官方 OpenAI 兼容入口；请选择 GLM-4V 等视觉模型。",
        ),
        ProviderDefinition(
            id = "volcengine",
            name = "火山引擎方舟",
            protocol = ApiProtocol.OPENAI_RESPONSES,
            baseUrl = "https://ark.cn-beijing.volces.com/api/v3",
            models = listOf(
                ModelOption("doubao-seed-2-0-pro-260215", "Doubao Seed 2.0 Pro"),
                ModelOption("doubao-seed-2-0-lite-260215", "Doubao Seed 2.0 Lite（更省）"),
            ),
            imageInputSupport = ImageInputSupport.MODEL_DEPENDENT,
            guidance = "使用方舟原生 Responses API；需选择已开通图片理解能力的模型或推理接入点。",
        ),
        ProviderDefinition(
            id = "mimo",
            name = "Xiaomi MiMo",
            protocol = ApiProtocol.OPENAI_CHAT_COMPLETIONS,
            baseUrl = "https://api.xiaomimimo.com/v1",
            models = listOf(
                ModelOption("mimo-v2.5", "MiMo-V2.5（多模态）"),
            ),
            guidance = "使用 MiMo 官方 OpenAI API；当前仅 MiMo-V2.5 接受图片输入。",
        ),
        ProviderDefinition(
            id = "deepseek",
            name = "DeepSeek",
            protocol = ApiProtocol.OPENAI_CHAT_COMPLETIONS,
            baseUrl = "https://api.deepseek.com",
            models = listOf(
                ModelOption("deepseek-v4-flash", "DeepSeek V4 Flash（纯文本）"),
                ModelOption("deepseek-v4-pro", "DeepSeek V4 Pro（纯文本）"),
            ),
            imageInputSupport = ImageInputSupport.UNSUPPORTED,
            guidance = "DeepSeek V4 官方 API 当前仅支持文本；可测试密钥和读取模型，但不能直接识别食物照片。",
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
            models = listOf(
                ModelOption("Qwen/Qwen2.5-VL-72B-Instruct", "Qwen2.5-VL 72B"),
            ),
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
