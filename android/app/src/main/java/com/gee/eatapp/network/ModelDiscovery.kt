package com.gee.eatapp.network

import com.gee.eatapp.data.ApiProtocol
import com.gee.eatapp.data.ProviderDefinition
import com.gee.eatapp.data.normalizeModelIds
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import org.json.JSONArray
import org.json.JSONObject

/** Model IDs always come from the API. Missing capability metadata is unknown, not false. */
internal fun parseModelPage(payload: JSONObject, provider: ProviderDefinition): List<String> {
    val source = payload.optJSONArray("data") ?: payload.optJSONArray("models")
        ?: throw IllegalStateException("模型列表返回了无效数据")
    return buildList {
        for (index in 0 until source.length()) {
            val item = source.opt(index)
            val id = when (item) {
                is String -> item
                is JSONObject -> {
                    if (!item.acceptsFoodAnalysis(provider)) continue
                    if (provider.protocol == ApiProtocol.GEMINI_GENERATE_CONTENT) {
                        item.stringValue("name").removePrefix("models/")
                            .ifBlank { item.stringValue("id") }
                            .ifBlank { item.stringValue("baseModelId") }
                    } else {
                        item.stringValue("id").ifBlank { item.stringValue("name") }
                    }
                }
                else -> continue
            }
            add(id)
        }
    }
}

private fun JSONObject.acceptsFoodAnalysis(provider: ProviderDefinition): Boolean {
    if (stringValue("lifecycle") == "retired") return false
    val capabilities = optJSONObject("capabilities")
    val architecture = optJSONObject("architecture")
    val imageSignals = listOfNotNull(
        opt("supports_image_in") as? Boolean,
        capabilities?.opt("vision") as? Boolean,
        capabilities?.optJSONObject("image_input")?.opt("supported") as? Boolean,
        optJSONArray("input_modalities")?.containsString("image"),
        architecture?.optJSONArray("input_modalities")?.containsString("image"),
    )
    if (false in imageSignals) return false
    if (optJSONArray("output_modalities")?.containsString("text") == false ||
        architecture?.optJSONArray("output_modalities")?.containsString("text") == false
    ) return false
    if (provider.protocol == ApiProtocol.GEMINI_GENERATE_CONTENT) {
        val methods = optJSONArray("supportedGenerationMethods") ?: optJSONArray("supportedActions")
        if (methods?.containsString("generateContent") == false) return false
    }
    return true
}

internal fun modelListUrl(baseUrl: String, provider: ProviderDefinition, cursor: String? = null): String {
    val path = "$baseUrl/${provider.modelListPath}"
    val parameters = when (provider.protocol) {
        ApiProtocol.GEMINI_GENERATE_CONTENT -> "pageSize=1000" +
            (cursor?.let { "&pageToken=${encodeQueryValue(it)}" } ?: "")
        ApiProtocol.ANTHROPIC_MESSAGES -> "limit=1000" +
            (cursor?.let { "&after_id=${encodeQueryValue(it)}" } ?: "")
        else -> ""
    }
    return if (parameters.isEmpty()) path else "$path${if ('?' in path) '&' else '?'}$parameters"
}

internal suspend fun discoverModelIds(
    provider: ProviderDefinition,
    fetchPage: suspend (cursor: String?) -> JSONObject,
): List<String> {
    val values = mutableListOf<String>()
    val visitedCursors = mutableSetOf<String>()
    var cursor: String? = null
    repeat(10) {
        val payload = fetchPage(cursor)
        values += parseModelPage(payload, provider)
        cursor = when (provider.protocol) {
            ApiProtocol.GEMINI_GENERATE_CONTENT -> payload.stringValue("nextPageToken").takeIf(String::isNotBlank)
            ApiProtocol.ANTHROPIC_MESSAGES -> if (payload.optBoolean("has_more")) {
                payload.stringValue("last_id").takeIf(String::isNotBlank)
                    ?: throw IllegalStateException("模型列表缺少分页信息，请重试")
            } else null
            else -> null
        }
        if (cursor == null) {
            return normalizeModelIds(values).ifEmpty {
                throw IllegalStateException("连接成功，但未返回可用于图片识别的模型；请核对账号权限和模型能力")
            }
        }
        if (!visitedCursors.add(cursor)) throw IllegalStateException("模型列表分页重复，请重试")
    }
    throw IllegalStateException("模型列表分页过多，请缩小服务端模型范围后重试")
}

private fun encodeQueryValue(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8.name())

private fun JSONObject.stringValue(key: String): String = (opt(key) as? String).orEmpty()

private fun JSONArray.containsString(value: String): Boolean =
    (0 until length()).any { optString(it).equals(value, ignoreCase = true) }
