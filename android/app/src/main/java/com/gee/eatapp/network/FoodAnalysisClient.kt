package com.gee.eatapp.network

import com.gee.eatapp.data.AnalysisResult
import com.gee.eatapp.data.AppSettings
import com.gee.eatapp.data.Confidence
import com.gee.eatapp.data.FoodItem
import com.gee.eatapp.data.ProviderCatalog
import com.gee.eatapp.data.effectiveModel
import com.gee.eatapp.data.normalizeBaseUrl
import com.gee.eatapp.data.normalizeModelIds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import kotlin.math.roundToInt

class FoodAnalysisClient {
    suspend fun listAvailableModels(settings: AppSettings, apiKey: String): List<String> =
        withContext(Dispatchers.IO) {
            val provider = ProviderCatalog.find(settings.providerId)
                ?: throw IllegalArgumentException("请选择有效的模型服务商")
            requireApiKey(apiKey)
            val isClaude = provider.id == "claude"
            val baseUrl = if (isClaude) {
                CLAUDE_BASE_URL
            } else {
                normalizeBaseUrl(if (provider.id == "custom") settings.customBaseUrl else provider.baseUrl.orEmpty())
            }
            val headers = if (isClaude) {
                mapOf("x-api-key" to apiKey.trim(), "anthropic-version" to ANTHROPIC_VERSION)
            } else {
                mapOf("Authorization" to "Bearer ${apiKey.trim()}")
            }
            val response = request("$baseUrl/models", "GET", headers, null, MODEL_TIMEOUT_MS)
            ensureSuccessful(response)
            val payload = runCatching { JSONObject(response.body) }
                .getOrElse { throw IllegalStateException("模型列表返回了无效数据") }
            val source = payload.optJSONArray("data") ?: payload.optJSONArray("models") ?: JSONArray()
            val values = buildList {
                for (index in 0 until source.length()) {
                    when (val item = source.opt(index)) {
                        is String -> add(item)
                        is JSONObject -> add(item.optString("id").ifBlank { item.optString("name") })
                    }
                }
            }
            normalizeModelIds(values).ifEmpty {
                throw IllegalStateException("连接成功，但该 API Key 没有返回可用模型")
            }
        }

    suspend fun analyze(
        settings: AppSettings,
        apiKey: String,
        imageBase64: String,
        note: String,
    ): AnalysisResult = withContext(Dispatchers.IO) {
        val provider = ProviderCatalog.find(settings.providerId)
            ?: throw IllegalArgumentException("请选择有效的模型服务商")
        requireApiKey(apiKey, analyzing = true)
        val model = settings.effectiveModel()
        if (model.isBlank()) throw IllegalArgumentException("请先在设置中选择模型")
        validateImage(imageBase64)
        val safeNote = note.trim().take(500)
        val userText = buildString {
            append("请识别这张照片中的食物并估算热量。")
            if (safeNote.isNotEmpty()) append("\n用户补充说明：$safeNote")
        }

        val response = if (provider.id == "claude") {
            requestClaude(model, apiKey.trim(), imageBase64, userText)
        } else {
            val baseUrl = normalizeBaseUrl(
                if (provider.id == "custom") settings.customBaseUrl else provider.baseUrl.orEmpty(),
            )
            requestOpenAiCompatible(baseUrl, model, apiKey.trim(), imageBase64, userText)
        }
        ensureSuccessful(response)
        val payload = runCatching { JSONObject(response.body) }
            .getOrElse { throw IllegalStateException("模型返回了无效数据，请重试") }
        val text = if (provider.id == "claude") {
            val content = payload.optJSONArray("content") ?: JSONArray()
            (0 until content.length())
                .mapNotNull { content.optJSONObject(it) }
                .firstOrNull { it.optString("type") == "text" }
                ?.optString("text")
                .orEmpty()
        } else {
            extractMessageText(
                payload.optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.opt("content"),
            )
        }
        if (text.isBlank()) throw IllegalStateException("模型没有返回识别结果，请重试")
        normalizeResult(parseJsonLoose(text))
    }

    private fun requestClaude(model: String, apiKey: String, imageBase64: String, userText: String): HttpResponse {
        val body = JSONObject()
            .put("model", model)
            .put("max_tokens", 2048)
            .put(
                "output_config",
                JSONObject().put(
                    "format",
                    JSONObject()
                        .put("type", "json_schema")
                        .put("schema", JSONObject(FOOD_SCHEMA_JSON)),
                ),
            )
            .put("system", SYSTEM_PROMPT)
            .put(
                "messages",
                JSONArray().put(
                    JSONObject()
                        .put("role", "user")
                        .put(
                            "content",
                            JSONArray()
                                .put(
                                    JSONObject()
                                        .put("type", "image")
                                        .put(
                                            "source",
                                            JSONObject()
                                                .put("type", "base64")
                                                .put("media_type", "image/jpeg")
                                                .put("data", imageBase64),
                                        ),
                                )
                                .put(JSONObject().put("type", "text").put("text", userText)),
                        ),
                ),
            )
        return request(
            "$CLAUDE_BASE_URL/messages",
            "POST",
            mapOf(
                "x-api-key" to apiKey,
                "anthropic-version" to ANTHROPIC_VERSION,
                "Content-Type" to "application/json",
            ),
            body.toString(),
            ANALYSIS_TIMEOUT_MS,
        )
    }

    private fun requestOpenAiCompatible(
        baseUrl: String,
        model: String,
        apiKey: String,
        imageBase64: String,
        userText: String,
    ): HttpResponse {
        val body = JSONObject()
            .put("model", model)
            .put(
                "messages",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("role", "system")
                            .put(
                                "content",
                                "$SYSTEM_PROMPT\n你必须只输出一个 JSON 对象，不要输出解释或 Markdown。结构示例：$JSON_SHAPE_HINT",
                            ),
                    )
                    .put(
                        JSONObject()
                            .put("role", "user")
                            .put(
                                "content",
                                JSONArray()
                                    .put(
                                        JSONObject()
                                            .put("type", "image_url")
                                            .put(
                                                "image_url",
                                                JSONObject().put(
                                                    "url",
                                                    "data:image/jpeg;base64,$imageBase64",
                                                ),
                                            ),
                                    )
                                    .put(
                                        JSONObject()
                                            .put("type", "text")
                                            .put("text", "$userText\n只输出 JSON。"),
                                    ),
                            ),
                    ),
            )
        return request(
            "$baseUrl/chat/completions",
            "POST",
            mapOf("Authorization" to "Bearer $apiKey", "Content-Type" to "application/json"),
            body.toString(),
            ANALYSIS_TIMEOUT_MS,
        )
    }

    private fun request(
        url: String,
        method: String,
        headers: Map<String, String>,
        body: String?,
        timeoutMs: Int,
    ): HttpResponse {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            instanceFollowRedirects = false
            useCaches = false
            headers.forEach { (name, value) -> setRequestProperty(name, value) }
        }
        return try {
            if (body != null) {
                val bytes = body.toByteArray(Charsets.UTF_8)
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(bytes.size)
                connection.outputStream.use { it.write(bytes) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            HttpResponse(status, stream?.use(::readLimitedText).orEmpty())
        } catch (_: SocketTimeoutException) {
            throw IllegalStateException("模型响应超时，请稍后重试")
        } catch (_: IOException) {
            throw IllegalStateException("无法连接模型服务，请检查网络和接口设置")
        } finally {
            connection.disconnect()
        }
    }

    private fun readLimitedText(input: InputStream): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > MAX_RESPONSE_BYTES) throw IOException("Response is too large")
            output.write(buffer, 0, count)
        }
        return output.toString(Charsets.UTF_8.name())
    }

    private fun ensureSuccessful(response: HttpResponse) {
        if (response.status in 200..299) return
        val detail = runCatching {
            val payload = JSONObject(response.body)
            val error = payload.opt("error")
            when (error) {
                is JSONObject -> error.optString("message")
                is String -> error
                else -> payload.optString("message")
            }
        }.getOrDefault("")
            .filterNot { it.code in 0..31 || it.code == 127 }
            .trim()
            .take(240)
        val message = when (response.status) {
            401, 403 -> "API Key 无效或没有模型权限"
            404 -> "模型不存在或接口地址错误"
            429 -> "请求过于频繁，请稍后再试"
            in 500..599 -> "模型服务暂时不可用，请稍后再试"
            else -> "模型服务返回错误（${response.status}）"
        }
        throw IllegalStateException(if (detail.isEmpty()) message else "$message：$detail")
    }

    private fun parseJsonLoose(text: String): JSONObject {
        val cleaned = text.replace(Regex("```(?:json)?", RegexOption.IGNORE_CASE), "").trim()
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        if (start < 0 || end < start) throw IllegalStateException("模型没有返回有效 JSON")
        return runCatching { JSONObject(cleaned.substring(start, end + 1)) }
            .getOrElse { throw IllegalStateException("模型返回的 JSON 格式不正确，请重试") }
    }

    private fun normalizeResult(source: JSONObject): AnalysisResult {
        val sourceFoods = source.optJSONArray("foods") ?: JSONArray()
        val foods = buildList {
            for (index in 0 until minOf(sourceFoods.length(), 30)) {
                val food = sourceFoods.optJSONObject(index) ?: continue
                add(
                    FoodItem(
                        name = food.optString("name", "未知食物").take(100),
                        portion = food.optString("portion").take(100),
                        calories = safeNumber(food.opt("calories")).roundToInt(),
                        proteinGrams = safeNumber(food.opt("protein_g")),
                        carbsGrams = safeNumber(food.opt("carbs_g")),
                        fatGrams = safeNumber(food.opt("fat_g")),
                    ),
                )
            }
        }
        val total = safeNumber(source.opt("total_calories")).roundToInt()
            .takeIf { it > 0 } ?: foods.sumOf { it.calories }
        return AnalysisResult(
            isFood = source.optBoolean("is_food") && foods.isNotEmpty(),
            foods = foods,
            totalCalories = total,
            confidence = when (source.optString("confidence")) {
                "low" -> Confidence.LOW
                "high" -> Confidence.HIGH
                else -> Confidence.MEDIUM
            },
            notes = source.optString("notes").take(1000),
        )
    }

    private fun extractMessageText(content: Any?): String = when (content) {
        is String -> content
        is JSONArray -> buildString {
            for (index in 0 until content.length()) {
                when (val part = content.opt(index)) {
                    is String -> append(part)
                    is JSONObject -> append(part.optString("text"))
                }
            }
        }
        else -> ""
    }

    private fun safeNumber(value: Any?): Double {
        val number = when (value) {
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull()
            else -> null
        } ?: 0.0
        return if (number.isFinite()) number.coerceAtLeast(0.0) else 0.0
    }

    private fun validateImage(base64: String) {
        if (base64.isEmpty() || !BASE64_PATTERN.matches(base64)) {
            throw IllegalArgumentException("图片数据无效，请重新选择照片")
        }
        val padding = when {
            base64.endsWith("==") -> 2
            base64.endsWith("=") -> 1
            else -> 0
        }
        val bytes = base64.length * 3L / 4L - padding
        if (bytes > MAX_IMAGE_BYTES) throw IllegalArgumentException("压缩后的图片不能超过 8 MB")
    }

    private fun requireApiKey(apiKey: String, analyzing: Boolean = false) {
        if (apiKey.isBlank()) {
            throw IllegalArgumentException(if (analyzing) "请先在设置中填写 API Key" else "请先填写 API Key")
        }
    }

    private data class HttpResponse(val status: Int, val body: String)

    private companion object {
        const val CLAUDE_BASE_URL = "https://api.anthropic.com/v1"
        const val ANTHROPIC_VERSION = "2023-06-01"
        const val MODEL_TIMEOUT_MS = 20_000
        const val ANALYSIS_TIMEOUT_MS = 120_000
        const val MAX_IMAGE_BYTES = 8 * 1024 * 1024L
        const val MAX_RESPONSE_BYTES = 4 * 1024 * 1024
        val BASE64_PATTERN = Regex("^[A-Za-z0-9+/]+={0,2}$")

        const val SYSTEM_PROMPT =
            "你是一位专业营养师。根据食物照片识别其中的食物种类，估算每种食物的份量、热量（千卡）和三大营养素。" +
                "估算时参考常见餐具尺寸判断份量。如果照片里没有食物，将 is_food 设为 false 并将 foods 留空。" +
                "如果用户提供了份量、价格、单价、容器大小或品牌等补充说明，请优先据此推算份量并在 notes 里说明。" +
                "宁可给出合理区间的中间值，也不要拒绝估算。所有文字使用简体中文。"

        const val JSON_SHAPE_HINT =
            "{\"is_food\":true,\"foods\":[{\"name\":\"食物名\",\"portion\":\"约150克\",\"calories\":200," +
                "\"protein_g\":10,\"carbs_g\":20,\"fat_g\":8}],\"total_calories\":200,\"confidence\":\"medium\"," +
                "\"notes\":\"估算说明\"}"

        const val FOOD_SCHEMA_JSON = """
            {
              "type":"object",
              "properties":{
                "is_food":{"type":"boolean"},
                "foods":{"type":"array","items":{"type":"object","properties":{
                  "name":{"type":"string"},"portion":{"type":"string"},"calories":{"type":"integer"},
                  "protein_g":{"type":"number"},"carbs_g":{"type":"number"},"fat_g":{"type":"number"}
                },"required":["name","portion","calories","protein_g","carbs_g","fat_g"],"additionalProperties":false}},
                "total_calories":{"type":"integer"},
                "confidence":{"type":"string","enum":["low","medium","high"]},
                "notes":{"type":"string"}
              },
              "required":["is_food","foods","total_calories","confidence","notes"],
              "additionalProperties":false
            }
        """
    }
}
