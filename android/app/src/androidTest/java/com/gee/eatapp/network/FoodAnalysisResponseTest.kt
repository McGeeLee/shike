package com.gee.eatapp.network

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import com.gee.eatapp.data.MAX_MEAL_CALORIES
import com.gee.eatapp.data.MAX_NUTRITION_GRAMS

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

    @Test fun normalFoodResultKeepsAllItemsAndUsesTheirCompleteTotal() {
        val result = FoodAnalysisClient().normalizeResult(foodResult(2))
        assertTrue(result.isFood)
        assertEquals(2, result.foods.size)
        assertEquals(200, result.totalCalories)
        assertEquals(10.0, result.foods.first().proteinGrams, 0.0)
    }

    @Test fun thirtyFoodItemsAreAcceptedWithoutTruncation() {
        val result = FoodAnalysisClient().normalizeResult(foodResult(30))
        assertEquals(30, result.foods.size)
        assertEquals(3000, result.totalCalories)
    }

    @Test fun thirtyOneFoodItemsAreRejectedInsteadOfLosingCalories() {
        val error = assertThrows(IllegalStateException::class.java) {
            FoodAnalysisClient().normalizeResult(foodResult(31))
        }
        assertTrue(error.message.orEmpty().contains("30"))
    }

    @Test fun invalidAndOversizedNutritionValuesCannotBecomeSavedMeals() {
        listOf("1e308", "NaN", "Infinity", -1, "not a number").forEach { value ->
            val source = foodResult(1)
            source.getJSONArray("foods").getJSONObject(0).put("protein_g", value)
            assertThrows(IllegalStateException::class.java) { FoodAnalysisClient().normalizeResult(source) }
        }
        val source = foodResult(1)
        source.getJSONArray("foods").getJSONObject(0).put("calories", Int.MAX_VALUE)
        assertThrows(IllegalStateException::class.java) { FoodAnalysisClient().normalizeResult(source) }
    }

    @Test fun aggregateMealValuesHaveTheSameBoundsAsStoredMeals() {
        val excessiveCalories = foodResult(2)
        for (index in 0..1) {
            excessiveCalories.getJSONArray("foods").getJSONObject(index).put("calories", MAX_MEAL_CALORIES)
        }
        assertThrows(IllegalStateException::class.java) { FoodAnalysisClient().normalizeResult(excessiveCalories) }

        val excessiveProtein = foodResult(2)
        for (index in 0..1) {
            excessiveProtein.getJSONArray("foods").getJSONObject(index).put("protein_g", MAX_NUTRITION_GRAMS)
        }
        assertThrows(IllegalStateException::class.java) { FoodAnalysisClient().normalizeResult(excessiveProtein) }
    }

    @Test fun zeroCalorieFoodsDoNotUseAnInconsistentProviderTotal() {
        val source = foodResult(1).put("total_calories", 500)
        source.getJSONArray("foods").getJSONObject(0).put("calories", 0)
        assertEquals(0, FoodAnalysisClient().normalizeResult(source).totalCalories)
    }

    @Test fun schemasKeepTheFoodLimitWithoutUnsupportedAnthropicKeywords() {
        val generalFoods = FoodAnalysisClient.foodAnalysisSchema().getJSONObject("properties").getJSONObject("foods")
        assertEquals(30, generalFoods.getInt("maxItems"))
        val anthropicFoods = FoodAnalysisClient.foodAnalysisSchema(true).getJSONObject("properties").getJSONObject("foods")
        assertFalse(anthropicFoods.has("maxItems"))
        assertTrue(anthropicFoods.getString("description").contains("30"))
    }

    private fun foodResult(count: Int): JSONObject {
        val foods = JSONArray()
        repeat(count) { index ->
            foods.put(JSONObject().put("name", "食物$index").put("portion", "100克")
                .put("calories", 100).put("protein_g", 10).put("carbs_g", 20).put("fat_g", 2))
        }
        return JSONObject().put("is_food", true).put("foods", foods).put("total_calories", count * 100)
            .put("confidence", "high").put("notes", "估算")
    }

    private fun response(message: JSONObject, finish: String = "stop") = JSONObject()
        .put("choices", JSONArray().put(JSONObject().put("finish_reason", finish).put("message", message)))
}
