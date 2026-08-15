package com.gee.eatapp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AppModelsTest {
    @Test
    fun dynamicColorIsOptInByDefault() {
        assertFalse(AppSettings().dynamicColorEnabled)
    }

    @Test
    fun customBaseUrlRequiresCleanHttpsUrl() {
        assertEquals("https://api.example.com/v1", normalizeBaseUrl("https://api.example.com/v1/"))
        assertThrows(IllegalArgumentException::class.java) { normalizeBaseUrl("http://api.example.com/v1") }
        assertThrows(IllegalArgumentException::class.java) {
            normalizeBaseUrl("https://user:secret@example.com/v1")
        }
        assertThrows(IllegalArgumentException::class.java) {
            normalizeBaseUrl("https://api.example.com/v1?token=secret")
        }
    }

    @Test
    fun modelSelectionUsesProviderDefault() {
        assertEquals("gpt-5.1", AppSettings(providerId = "openai").effectiveModel())
        assertEquals(
            "vision-model",
            AppSettings(providerId = "custom", customModel = " vision-model ").effectiveModel(),
        )
    }

    @Test
    fun dailySummaryClampsInvalidNutritionValues() {
        val entries = listOf(
            MealEntry("1", "米饭", 180, 4.0, 40.0, 0.5, "12:00", "", ""),
            MealEntry("2", "异常", -20, Double.NaN, -4.0, 2.0, "13:00", "", ""),
        )
        assertEquals(DailySummary(180, 4.0, 40.0, 2.5), DailySummary.from(entries))
    }

    @Test
    fun discoveredModelsAreSanitizedAndDeduplicated() {
        assertEquals(
            listOf("gpt-4o", "gpt-5.1"),
            normalizeModelIds(listOf(" gpt-5.1 ", "gpt-4o", "gpt-5.1", "\u0000")),
        )
    }

    @Test
    fun providerCatalogCoversNativeAndCompatibleProtocols() {
        val expected = setOf(
            "claude", "openai", "gemini", "kimi", "xai", "mistral", "qwen", "zhipu",
            "volcengine", "mimo", "deepseek", "openrouter", "siliconflow", "custom",
        )
        assertEquals(expected, ProviderCatalog.all.map { it.id }.toSet())
        assertEquals(ApiProtocol.ANTHROPIC_MESSAGES, ProviderCatalog.find("claude")?.protocol)
        assertEquals(ApiProtocol.GEMINI_GENERATE_CONTENT, ProviderCatalog.find("gemini")?.protocol)
        assertEquals(ApiProtocol.OPENAI_RESPONSES, ProviderCatalog.find("xai")?.protocol)
        assertEquals("language-models", ProviderCatalog.find("xai")?.modelListPath)
        assertEquals(ImageInputSupport.UNSUPPORTED, ProviderCatalog.find("deepseek")?.imageInputSupport)
        assertTrue(ProviderCatalog.find("openrouter")?.modelListPath?.contains("input_modalities=image") == true)
    }
}
