package com.gee.eatapp.network

import org.json.JSONObject

/** Use the current API schema rather than inferring a protocol from a model name. */
internal fun geminiGenerationConfig(): JSONObject = JSONObject()
    .put("maxOutputTokens", 8192)
    .put("responseFormat", JSONObject().put("text", JSONObject()
        .put("mimeType", "application/json")
        .put("schema", FoodAnalysisClient.foodAnalysisSchema())))

internal fun configureMimoVisionRequest(body: JSONObject): JSONObject = body
    .put("thinking", JSONObject().put("type", "disabled"))
    .put("max_completion_tokens", 8192)
    .put("response_format", JSONObject().put("type", "json_object"))
