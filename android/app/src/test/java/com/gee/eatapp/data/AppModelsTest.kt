package com.gee.eatapp.data

import java.time.LocalDate
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
    fun modelSelectionUsesApiSelectionAndMigratesLegacyDeepSeekModels() {
        assertEquals("", AppSettings(providerId = "openai").effectiveModel())
        assertEquals("gpt-5.1", AppSettings(providerId = "openai", model = " gpt-5.1 ").effectiveModel())
        assertEquals("", AppSettings(providerId = "deepseek").effectiveModel())
        assertEquals(
            DEEPSEEK_VISION_MODEL,
            AppSettings(providerId = "deepseek", model = "deepseek-v4-flash").effectiveModel(),
        )
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
    fun nutritionStatisticsIncludesEmptyDaysInDailyAverage() {
        val today = LocalDate.of(2026, 8, 15)
        val points = listOf(
            DailyNutritionPoint(
                date = today.minusDays(1),
                mealCount = 0,
                summary = DailySummary(0, 0.0, 0.0, 0.0),
            ),
            DailyNutritionPoint(
                date = today,
                mealCount = 2,
                summary = DailySummary(1400, 60.0, 130.0, 30.0),
            ),
        )

        val statistics = NutritionStatistics.from(points)

        assertEquals(2, statistics.dayCount)
        assertEquals(1, statistics.recordedDays)
        assertEquals(2, statistics.mealCount)
        assertEquals(DailySummary(1400, 60.0, 130.0, 30.0), statistics.total)
        assertEquals(DailySummary(700, 30.0, 65.0, 15.0), statistics.dailyAverage)
    }

    @Test
    fun macroEnergyDistributionUsesStandardCalorieFactors() {
        val distribution = MacroEnergyDistribution.from(
            DailySummary(260, proteinGrams = 10.0, carbsGrams = 20.0, fatGrams = 10.0),
        )

        assertEquals(40.0, distribution.proteinCalories, 0.001)
        assertEquals(80.0, distribution.carbsCalories, 0.001)
        assertEquals(90.0, distribution.fatCalories, 0.001)
        assertEquals(40f / 210f, distribution.shareOf(distribution.proteinCalories), 0.001f)
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
        val deepSeek = ProviderCatalog.find("deepseek")
        assertEquals(ImageInputSupport.SUPPORTED, deepSeek?.imageInputSupport)
        assertEquals("models", deepSeek?.modelListPath)
        assertTrue(ProviderCatalog.find("openrouter")?.modelListPath?.contains("input_modalities=image") == true)
    }
}
