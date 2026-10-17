package com.gee.eatapp.network

import com.gee.eatapp.data.ProviderCatalog
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ModelDiscoveryTest {
    @Test fun everyProviderAcceptsUnfamiliarIdsReturnedByItsApi() {
        ProviderCatalog.all.forEach { provider ->
            val payload = JSONObject("""{"data":[{"id":"brand-new-vision-model"},"another-new-model"]}""")
            assertEquals(listOf("brand-new-vision-model", "another-new-model"), parseModelPage(payload, provider))
        }
    }

    @Test fun deepSeekAndMimoDoNotRewriteOrWhitelistApiIds() {
        listOf("deepseek", "mimo").forEach { provider ->
            assertEquals(listOf("future-vision", "legacy-alias"), parse(provider, """
                {"data":[{"id":"future-vision","input_modalities":["text","image"]},
                {"id":"legacy-alias","input_modalities":["image"]},
                {"id":"text-only","input_modalities":["text"]}]}
            """))
        }
    }

    @Test fun missingCapabilitiesStaySelectableButExplicitFalseIsExcluded() {
        assertEquals(listOf("unknown", "vision"), parse("kimi", """
            {"data":[{"id":"unknown"},{"id":"vision","supports_image_in":true},
            {"id":"text-only","supports_image_in":false}]}
        """))
        assertEquals(listOf("unknown", "vision"), parse("mistral", """
            {"data":[{"id":"unknown","capabilities":null},
            {"id":"vision","capabilities":{"vision":true}},
            {"id":"text-only","capabilities":{"vision":false}}]}
        """))
    }

    @Test fun claudeUsesCapabilitiesAndExcludesRetiredModels() {
        assertEquals(listOf("current"), parse("claude", """
            {"data":[{"id":"current","capabilities":{"image_input":{"supported":true}}},
            {"id":"text-only","capabilities":{"image_input":{"supported":false}}},
            {"id":"retired","lifecycle":"retired"}]}
        """))
    }

    @Test fun imageInputWithoutTextOutputIsNotAFoodAnalysisModel() {
        assertEquals(listOf("vision"), parse("openrouter", """
            {"data":[{"id":"vision","architecture":{"input_modalities":["image"],"output_modalities":["text"]}},
            {"id":"generator","architecture":{"input_modalities":["image"],"output_modalities":["image"]}},
            {"id":"text-only","architecture":{"input_modalities":["text"]}}]}
        """))
        assertEquals(listOf("vision"), parse("xai", """
            {"models":[{"id":"vision","input_modalities":["image"],"output_modalities":["text"]},
            {"id":"video","input_modalities":["image"],"output_modalities":["video"]}]}
        """))
    }

    @Test fun contradictoryCapabilitySignalsCannotEnableImages() {
        assertTrue(parse("custom", """
            {"data":[{"id":"contradiction","input_modalities":["image"],"capabilities":{"vision":false}}]}
        """).isEmpty())
    }

    @Test fun geminiKeepsFullVersionedNameAndFiltersNonGeneratingModels() {
        assertEquals(listOf("future-preview-10-17", "unknown"), parse("gemini", """
            {"models":[{"name":"models/future-preview-10-17","baseModelId":"future","supportedGenerationMethods":["generateContent"]},
            {"name":"models/embed","supportedGenerationMethods":["embedContent"]},
            {"name":"models/unknown"}]}
        """))
    }

    @Test fun nullIdentifiersDoNotBecomeLiteralNull() {
        assertEquals(listOf("fallback"), runBlocking {
            discoverModelIds(provider("openai")) {
                JSONObject("""{"data":[{"id":null},{"id":null,"name":"fallback"}]}""")
            }
        })
    }

    @Test fun emptyOrInvalidApiResponsesNeverProduceLocalFallbackModels() {
        listOf("{}", "{\"data\":[]}", "{\"models\":null}").forEach { json ->
            assertThrows(IllegalStateException::class.java) {
                runBlocking { discoverModelIds(provider("mimo")) { JSONObject(json) } }
            }
        }
    }

    @Test fun geminiPaginationKeepsLaterModelsAndDeduplicatesResults() = runBlocking {
        val cursors = mutableListOf<String?>()
        val result = discoverModelIds(provider("gemini")) { cursor ->
            cursors.add(cursor)
            JSONObject(if (cursor == null) """{"models":[{"name":"models/one"}],"nextPageToken":"next/token?"}"""
                else """{"models":[{"name":"models/two"},{"name":"models/one"}]}""")
        }
        assertEquals(listOf(null, "next/token?"), cursors)
        assertEquals(listOf("one", "two"), result)
    }

    @Test fun claudePaginationCanStartWithAnEmptyFilteredPage() = runBlocking {
        val result = discoverModelIds(provider("claude")) { cursor ->
            JSONObject(if (cursor == null) """{"data":[{"id":"text-only","capabilities":{"image_input":{"supported":false}}}],"has_more":true,"last_id":"text-only"}"""
                else """{"data":[{"id":"vision"}],"has_more":false}""")
        }
        assertEquals(listOf("vision"), result)
    }

    @Test fun repeatedOrMissingPaginationCursorsFailInsteadOfReturningAPartialList() {
        listOf("""{"data":["one"],"has_more":true,"last_id":"same"}""",
            """{"data":["one"],"has_more":true}""").forEach { page ->
            assertThrows(IllegalStateException::class.java) {
                runBlocking { discoverModelIds(provider("claude")) { JSONObject(page) } }
            }
        }
    }

    @Test fun runawayPaginationIsBounded() {
        var pages = 0
        assertThrows(IllegalStateException::class.java) {
            runBlocking { discoverModelIds(provider("gemini")) {
                JSONObject().put("models", org.json.JSONArray().put("one")).put("nextPageToken", "page-${++pages}")
            } }
        }
        assertEquals(10, pages)
    }

    @Test fun paginationTokensStayOnTheConfiguredEndpoint() {
        assertEquals("https://example.com/v1/models?pageSize=1000&pageToken=https%3A%2F%2Fother.test%2F%3Fa%3Db%26key%3Dx",
            modelListUrl("https://example.com/v1", provider("gemini"), "https://other.test/?a=b&key=x"))
        assertEquals("https://example.com/v1/models?limit=1000&after_id=a%2Fb",
            modelListUrl("https://example.com/v1", provider("claude"), "a/b"))
        assertEquals("https://example.com/v1/models?input_modalities=image&output_modalities=text",
            modelListUrl("https://example.com/v1", provider("openrouter")))
    }

    private fun provider(id: String) = ProviderCatalog.find(id)!!
    private fun parse(id: String, json: String) = parseModelPage(JSONObject(json), provider(id))
}
