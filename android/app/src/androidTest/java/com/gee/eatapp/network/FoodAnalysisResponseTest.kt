package com.gee.eatapp.network

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class FoodAnalysisResponseTest {
    @Test fun deepSeekExplicitlyDisablesThinkingAndLeavesEnoughAnswerBudget() {
        val body = configureDeepSeekVisionRequest(JSONObject().put("model", "deepseek-flash"))
        assertEquals("disabled", body.getJSONObject("thinking").getString("type"))
        assertEquals(8192, body.getInt("max_tokens"))
        assertEquals("json_object", body.getJSONObject("response_format").getString("type"))
        assertEquals("deepseek-flash", body.getString("model"))
    }

    @Test fun emptyJsonFallbackKeepsThinkingDisabledAndRemovesJsonMode() {
        val body = configureDeepSeekVisionRequest(JSONObject().put("response_format", JSONObject()), false)
        assertFalse(body.has("response_format"))
        assertEquals("disabled", body.getJSONObject("thinking").getString("type"))
        assertEquals(8192, body.getInt("max_tokens"))
    }

    @Test fun readsFinalAnswerInsteadOfReasoning() {
        val result = response(JSONObject().put("content", "{\"is_food\":true}").put("reasoning_content", "private reasoning").put("refusal", JSONObject.NULL))
        assertEquals("{\"is_food\":true}", extractChatCompletionText(result))
    }

    @Test fun readsTextPartsAndIgnoresReasoningParts() {
        val content = JSONArray()
            .put(JSONObject().put("type", "reasoning").put("text", "not an answer"))
            .put(JSONObject().put("type", "text").put("text", "{\"is_food\":"))
            .put(JSONObject().put("type", "text").put("text", "true}"))
        assertEquals("{\"is_food\":true}", extractChatCompletionText(response(JSONObject().put("content", content))))
    }

    @Test fun truncatedJsonProducesLengthError() {
        val error = assertThrows(IllegalStateException::class.java) {
            extractChatCompletionText(response(JSONObject().put("content", "{\"foods\": ["), "length"))
        }
        assertTrue(error.message.orEmpty().contains("长度限制"))
    }

    @Test fun reasoningOnlyResponseDoesNotBecomeFoodData() {
        val error = assertThrows(IllegalStateException::class.java) {
            extractChatCompletionText(response(JSONObject().put("content", JSONObject.NULL).put("reasoning_content", "{\"is_food\":true}")))
        }
        assertTrue(error.message.orEmpty().contains("思考内容"))
    }

    @Test fun emptyJsonModeResponseRemainsEmpty() {
        assertEquals("", extractChatCompletionText(response(JSONObject().put("content", "").put("reasoning_content", JSONObject.NULL).put("refusal", JSONObject.NULL))))
        assertEquals("", extractChatCompletionText(JSONObject()))
    }

    @Test fun refusalIsReportedWithoutRevealingProviderText() {
        val error = assertThrows(IllegalStateException::class.java) {
            extractChatCompletionText(response(JSONObject().put("refusal", "provider refusal details")))
        }
        assertTrue(error.message.orEmpty().contains("拒绝"))
        assertFalse(error.message.orEmpty().contains("provider refusal details"))
    }

    @Test fun contentFilterDoesNotBecomeAnEmptyResult() {
        val error = assertThrows(IllegalStateException::class.java) {
            extractChatCompletionText(response(JSONObject(), "content_filter"))
        }
        assertTrue(error.message.orEmpty().contains("拦截"))
    }

    private fun response(message: JSONObject, finish: String = "stop") = JSONObject()
        .put("choices", JSONArray().put(JSONObject().put("finish_reason", finish).put("message", message)))
}
