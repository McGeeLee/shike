package com.gee.eatapp.data

import java.time.LocalDate
import kotlin.math.roundToInt
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
    fun deepSeekFlashAliasesUseCurrentVisionModel() {
        listOf("deepseek-flash", "deepseek-v4-flash", "deepseek-v4-flash-vision-exp").forEach { alias ->
            assertEquals("deepseek-flash", AppSettings(providerId = "deepseek", model = alias).effectiveModel())
            assertEquals("deepseek-flash", normalizeDeepSeekVisionModel(alias))
        }
        assertEquals("deepseek-v4-pro", normalizeDeepSeekVisionModel("deepseek-v4-pro"))
        assertEquals("deepseek-future", AppSettings(providerId = "deepseek", model = "deepseek-future").effectiveModel())
        assertEquals("deepseek-v4-flash-vision-exp", AppSettings(providerId = "custom", customModel = "deepseek-v4-flash-vision-exp").effectiveModel())
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
    fun oversizedStoredMealCannotProduceNaNPercentages() {
        val entry = MealEntry("1", "异常", Int.MAX_VALUE, 1e308, 20.0, 8.0, "12:00", "", "")
        val summary = DailySummary.from(listOf(entry))
        val distribution = MacroEnergyDistribution.from(summary)
        val share = distribution.shareOf(distribution.proteinCalories)

        assertEquals(MAX_MEAL_CALORIES, summary.calories)
        assertEquals(MAX_NUTRITION_GRAMS, summary.proteinGrams, 0.0)
        assertTrue(distribution.totalCalories.isFinite())
        assertTrue(share.isFinite())
        assertTrue((share * 100).roundToInt() in 0..100)
    }

    @Test
    fun dailyAndHistoryTotalsDoNotOverflowIntegers() {
        val entry = MealEntry("1", "餐", MAX_MEAL_CALORIES, 1.0, 2.0, 3.0, "12:00", "", "")
        val summary = DailySummary.from(List(3000) { entry })
        assertEquals(Int.MAX_VALUE, summary.calories)
        assertEquals(3000.0, summary.proteinGrams, 0.0)

        val points = List(2) { offset ->
            DailyNutritionPoint(LocalDate.of(2026, 10, 2).minusDays(offset.toLong()), Int.MAX_VALUE, summary)
        }
        val statistics = NutritionStatistics.from(points)
        assertEquals(Int.MAX_VALUE, statistics.total.calories)
        assertEquals(Int.MAX_VALUE, statistics.mealCount)
        assertEquals(6000.0, statistics.total.proteinGrams, 0.0)
    }

    @Test
    fun macroSharesRejectNonFiniteInputsAndAggregatesRemainFinite() {
        val distribution = MacroEnergyDistribution.from(DailySummary(0, 1e308, 1e308, 1e308))
        assertTrue(distribution.totalCalories.isFinite())
        assertTrue(distribution.shareOf(distribution.proteinCalories).isFinite())
        assertEquals(0f, distribution.shareOf(Double.NaN), 0f)
        assertEquals(0f, MacroEnergyDistribution(Double.POSITIVE_INFINITY, 0.0, 0.0)
            .shareOf(Double.POSITIVE_INFINITY), 0f)

        val statistics = NutritionStatistics.from(List(2) {
            DailyNutritionPoint(LocalDate.of(2026, 10, 2), 1, DailySummary(0, 1e308, Double.NaN, -1.0))
        })
        assertTrue(statistics.total.proteinGrams.isFinite())
        assertEquals(0.0, statistics.total.carbsGrams, 0.0)
        assertEquals(0.0, statistics.total.fatGrams, 0.0)
    }

    @Test
    fun storedCalorieNormalizationHasMealBounds() {
        assertEquals(MAX_MEAL_CALORIES, 1e308.safeCalorieValue())
        assertEquals(0, Double.NaN.safeCalorieValue())
        assertEquals(0, (-1.0).safeCalorieValue())
        assertEquals(180, 180.9.safeCalorieValue())
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
