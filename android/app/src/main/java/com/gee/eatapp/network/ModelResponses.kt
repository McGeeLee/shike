package com.gee.eatapp.network

import org.json.JSONObject

private const val INCOMPLETE_RESULT = "模型未完成识别，结果可能不完整；请减少照片中的食物数量或更换模型后重试"
private const val REFUSED_RESULT = "模型拒绝或拦截了这次识别，请更换照片后重试"
private const val FAILED_RESULT = "模型未能完成这次识别，请稍后重试"

internal fun extractClaudeText(payload: JSONObject): String {
    payload.requireNoResponseError()
    if (payload.optJSONObject("stop_details")?.stringValue("type") == "refusal") {
        throw IllegalStateException(REFUSED_RESULT)
    }
    when (payload.stringValue("stop_reason")) {
        "", "end_turn", "stop_sequence" -> Unit
        "refusal" -> throw IllegalStateException(REFUSED_RESULT)
        "max_tokens", "model_context_window_exceeded", "pause_turn", "tool_use" ->
            throw IllegalStateException(INCOMPLETE_RESULT)
        else -> throw IllegalStateException(FAILED_RESULT)
    }
    val content = payload.optJSONArray("content") ?: return ""
    return buildString {
        for (index in 0 until content.length()) {
            val part = content.optJSONObject(index) ?: continue
            when (part.stringValue("type")) {
                "text" -> append(part.stringValue("text"))
                "refusal" -> throw IllegalStateException(REFUSED_RESULT)
            }
        }
    }
}

internal fun extractResponsesText(payload: JSONObject): String {
    payload.requireNoResponseError()
    payload.requireCompletedResponse()
    val output = payload.optJSONArray("output")
    val text = buildString {
        for (index in 0 until (output?.length() ?: 0)) {
            val item = output?.optJSONObject(index) ?: continue
            // Reasoning summaries and tool output are not the final answer.
            if (item.stringValue("type") !in listOf("", "message")) continue
            if (item.stringValue("role") !in listOf("", "assistant")) continue
            item.requireCompletedResponse()
            val content = item.optJSONArray("content") ?: continue
            for (partIndex in 0 until content.length()) {
                val part = content.optJSONObject(partIndex) ?: continue
                when (part.stringValue("type")) {
                    "output_text", "text" -> append(part.stringValue("text"))
                    "refusal" -> throw IllegalStateException(REFUSED_RESULT)
                }
            }
        }
    }
    // Some compatible providers return only the convenience output_text field.
    return text.ifBlank { payload.stringValue("output_text") }
}

internal fun extractGeminiText(payload: JSONObject): String {
    payload.requireNoResponseError()
    val blockReason = payload.optJSONObject("promptFeedback")?.stringValue("blockReason").orEmpty()
    if (blockReason.isNotEmpty() && blockReason != "BLOCK_REASON_UNSPECIFIED") {
        throw IllegalStateException(REFUSED_RESULT)
    }
    val candidate = payload.optJSONArray("candidates")?.optJSONObject(0) ?: return ""
    when (candidate.stringValue("finishReason")) {
        "", "STOP" -> Unit
        "MAX_TOKENS" -> throw IllegalStateException(INCOMPLETE_RESULT)
        "SAFETY", "RECITATION", "BLOCKLIST", "PROHIBITED_CONTENT", "SPII", "IMAGE_SAFETY" ->
            throw IllegalStateException(REFUSED_RESULT)
        else -> throw IllegalStateException(FAILED_RESULT)
    }
    val parts = candidate.optJSONObject("content")?.optJSONArray("parts") ?: return ""
    return buildString {
        for (index in 0 until parts.length()) {
            val part = parts.optJSONObject(index) ?: continue
            if (!part.optBoolean("thought", false)) append(part.stringValue("text"))
        }
    }
}

private fun JSONObject.stringValue(key: String): String = opt(key) as? String ?: ""

private fun JSONObject.requireNoResponseError() {
    if (has("error") && !isNull("error")) throw IllegalStateException(FAILED_RESULT)
}

private fun JSONObject.requireCompletedResponse() {
    when (stringValue("status")) {
        "", "completed" -> Unit
        "incomplete" -> {
            if (optJSONObject("incomplete_details")?.stringValue("reason") == "content_filter") {
                throw IllegalStateException(REFUSED_RESULT)
            }
            throw IllegalStateException(INCOMPLETE_RESULT)
        }
        "in_progress", "queued" -> throw IllegalStateException(INCOMPLETE_RESULT)
        else -> throw IllegalStateException(FAILED_RESULT)
    }
    if (has("incomplete_details") && !isNull("incomplete_details")) {
        throw IllegalStateException(INCOMPLETE_RESULT)
    }
}
