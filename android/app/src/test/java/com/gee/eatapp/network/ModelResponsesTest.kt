package com.gee.eatapp.network

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelResponsesTest {
    @Test fun claudeJoinsFinalTextAndSkipsThinking() {
        val payload = JSONObject("""{
            "stop_reason":"end_turn",
            "content":[
                {"type":"thinking","thinking":"private reasoning","text":"ignore"},
                {"type":"text","text":"{\"is_food\":"},
                {"type":"redacted_thinking","data":"ignore"},
                {"type":"text","text":"true}"}
            ]
        }""")
        assertEquals(FOOD_JSON, extractClaudeText(payload))
    }

    @Test fun claudeRejectsTruncatedAndUnfinishedResponsesEvenWhenJsonLooksComplete() {
        listOf("max_tokens", "model_context_window_exceeded", "pause_turn", "tool_use").forEach { reason ->
            assertThrows(IllegalStateException::class.java) {
                extractClaudeText(claudeResponse().put("stop_reason", reason))
            }
        }
    }

    @Test fun claudeRefusalNeverReturnsPartialTextOrProviderExplanation() {
        val payload = claudeResponse().put("stop_reason", "refusal")
            .put("stop_details", JSONObject().put("type", "refusal").put("explanation", "private detail"))
        val error = assertThrows(IllegalStateException::class.java) { extractClaudeText(payload) }
        assertTrue(error.message.orEmpty().contains("拒绝"))
        assertFalse(error.message.orEmpty().contains("private detail"))
    }

    @Test fun responsesReadsOnlyAssistantFinalText() {
        val payload = JSONObject("""{
            "status":"completed",
            "output":[
                {"type":"reasoning","content":[{"type":"text","text":"ignore"}]},
                {"type":"function_call","content":[{"type":"output_text","text":"ignore"}]},
                {"type":"message","role":"user","content":[{"type":"output_text","text":"ignore"}]},
                {"type":"message","role":"assistant","status":"completed","content":[
                    {"type":"reasoning_text","text":"ignore"},
                    {"type":"output_text","text":"{\"is_food\":"},
                    {"type":"output_text","text":"true}"}
                ]}
            ]
        }""")
        assertEquals(FOOD_JSON, extractResponsesText(payload))
    }

    @Test fun responsesRejectsIncompleteFailedAndPendingResultsBeforeShortcutText() {
        listOf("incomplete", "failed", "cancelled", "queued", "in_progress").forEach { status ->
            val payload = JSONObject().put("status", status).put("output_text", FOOD_JSON)
            assertThrows(IllegalStateException::class.java) { extractResponsesText(payload) }
        }
    }

    @Test fun responsesRejectsIncompleteMessageInsideCompletedResponse() {
        val payload = responsesResponse().put("status", "completed")
        payload.getJSONArray("output").getJSONObject(0).put("status", "incomplete")
        assertThrows(IllegalStateException::class.java) { extractResponsesText(payload) }
    }

    @Test fun responsesRejectsRefusalEvenWhenShortcutContainsJson() {
        val payload = responsesResponse().put("output_text", FOOD_JSON)
        payload.getJSONArray("output").getJSONObject(0).put("content", JSONArray()
            .put(JSONObject().put("type", "refusal").put("refusal", "private detail")))
        val error = assertThrows(IllegalStateException::class.java) { extractResponsesText(payload) }
        assertTrue(error.message.orEmpty().contains("拒绝"))
        assertFalse(error.message.orEmpty().contains("private detail"))
    }

    @Test fun responsesAcceptsConvenienceTextAndNullOptionalFields() {
        val payload = JSONObject().put("output_text", FOOD_JSON)
            .put("error", JSONObject.NULL).put("incomplete_details", JSONObject.NULL)
        assertEquals(FOOD_JSON, extractResponsesText(payload))
        assertEquals("", extractResponsesText(JSONObject().put("output_text", JSONObject.NULL)))
    }

    @Test fun geminiSkipsThoughtsAndJoinsFinalText() {
        val payload = JSONObject("""{
            "candidates":[{"finishReason":"STOP","content":{"parts":[
                {"thought":true,"text":"private reasoning with {braces}"},
                {"text":"{\"is_food\":"},
                {"thoughtSignature":"opaque"},
                {"thought":false,"text":"true}"}
            ]}}]
        }""")
        assertEquals(FOOD_JSON, extractGeminiText(payload))
    }

    @Test fun geminiRejectsTruncationAndNonSuccessfulFinishesEvenWithValidJson() {
        listOf("MAX_TOKENS", "SAFETY", "RECITATION", "PROHIBITED_CONTENT", "OTHER", "MALFORMED_FUNCTION_CALL").forEach { reason ->
            val payload = geminiResponse()
            payload.getJSONArray("candidates").getJSONObject(0).put("finishReason", reason)
            assertThrows(IllegalStateException::class.java) { extractGeminiText(payload) }
        }
    }

    @Test fun geminiReportsBlockedPromptWithoutCandidates() {
        val payload = JSONObject().put("promptFeedback", JSONObject().put("blockReason", "SAFETY"))
        val error = assertThrows(IllegalStateException::class.java) { extractGeminiText(payload) }
        assertTrue(error.message.orEmpty().contains("拦截"))
    }

    @Test fun legacyResponsesWithoutStatusRemainReadable() {
        assertEquals(FOOD_JSON, extractClaudeText(claudeResponse()))
        assertEquals(FOOD_JSON, extractResponsesText(responsesResponse()))
        assertEquals(FOOD_JSON, extractGeminiText(geminiResponse()))
    }

    @Test fun protocolErrorsNeverReturnJsonFromAnErrorResponse() {
        val parsers = listOf(::extractClaudeText, ::extractResponsesText, ::extractGeminiText)
        val payloads = listOf(claudeResponse(), responsesResponse(), geminiResponse())
        parsers.zip(payloads).forEach { (parse, payload) ->
            payload.put("error", JSONObject().put("message", "private detail"))
            val error = assertThrows(IllegalStateException::class.java) { parse(payload) }
            assertFalse(error.message.orEmpty().contains("private detail"))
        }
    }

    @Test fun reasoningOnlyAndAbsentResponsesRemainEmpty() {
        assertEquals("", extractClaudeText(JSONObject("""{"content":[{"type":"thinking","text":"ignore"}]}""")))
        assertEquals("", extractResponsesText(JSONObject("""{"output":[{"type":"reasoning","content":[{"type":"text","text":"ignore"}]}]}""")))
        assertEquals("", extractGeminiText(JSONObject("""{"candidates":[{"content":{"parts":[{"thought":true,"text":"ignore"}]}}]}""")))
        listOf(::extractClaudeText, ::extractResponsesText, ::extractGeminiText).forEach { parse ->
            assertEquals("", parse(JSONObject()))
        }
    }

    private fun claudeResponse() = JSONObject().put("content", JSONArray()
        .put(JSONObject().put("type", "text").put("text", FOOD_JSON)))

    private fun responsesResponse() = JSONObject().put("output", JSONArray()
        .put(JSONObject().put("type", "message").put("content", JSONArray()
            .put(JSONObject().put("type", "output_text").put("text", FOOD_JSON)))))

    private fun geminiResponse() = JSONObject().put("candidates", JSONArray()
        .put(JSONObject().put("content", JSONObject().put("parts", JSONArray()
            .put(JSONObject().put("text", FOOD_JSON))))))

    private companion object {
        const val FOOD_JSON = "{\"is_food\":true}"
    }
}
