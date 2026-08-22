package com.gee.eatapp.ui

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.gee.eatapp.data.DailyNutritionPoint
import com.gee.eatapp.data.DailySummary
import com.gee.eatapp.update.AppRelease
import com.gee.eatapp.ui.theme.ShikeLightColorScheme
import com.gee.eatapp.ui.theme.ShikeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class ShikeHomeScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun dashboardKeepsCoreHierarchyAndCaptureAction() {
        var captureClicked = false
        composeRule.setContent {
            ShikeTheme {
                ShikeHomeScreen(
                    state = ShikeUiState(),
                    snackbarHostState = remember { SnackbarHostState() },
                    onPreviousDay = {},
                    onNextDay = {},
                    onToday = {},
                    onOpenSettings = {},
                    onAddMeal = { captureClicked = true },
                    onDeleteMeal = {},
                )
            }
        }

        composeRule.onNodeWithText("食刻").assertIsDisplayed()
        composeRule.onNodeWithTag("summaryCard").assertIsDisplayed()
        composeRule.onNodeWithTag("dailyNutritionPanel").assertIsDisplayed()
        composeRule.onNodeWithText("营养面板").assertIsDisplayed()
        composeRule.onNodeWithText("今日记录").assertIsDisplayed()
        composeRule.onNodeWithTag("captureButton").performClick()
        composeRule.runOnIdle { assertTrue(captureClicked) }
    }

    @Test
    fun statisticsViewShowsTrendAndAverageNutrition() {
        val today = LocalDate.now()
        val history = (6 downTo 0).map { offset ->
            DailyNutritionPoint(
                date = today.minusDays(offset.toLong()),
                mealCount = if (offset < 3) 2 else 0,
                summary = if (offset < 3) {
                    DailySummary(1200 + offset * 100, 60.0, 140.0, 35.0)
                } else {
                    DailySummary(0, 0.0, 0.0, 0.0)
                },
            )
        }
        composeRule.setContent {
            ShikeTheme {
                ShikeHomeScreen(
                    state = ShikeUiState(nutritionHistory = history),
                    snackbarHostState = remember { SnackbarHostState() },
                    onPreviousDay = {},
                    onNextDay = {},
                    onToday = {},
                    onOpenSettings = {},
                    onAddMeal = {},
                    onDeleteMeal = {},
                )
            }
        }

        composeRule.onNodeWithTag("statisticsTab").performClick()
        composeRule.onNodeWithTag("statisticsView").assertIsDisplayed()
        composeRule.onNodeWithTag("statisticsOverviewCard").assertIsDisplayed()
        composeRule.onNodeWithTag("calorieTrendCard").assertIsDisplayed()
        composeRule.onNodeWithTag("statisticsNutritionPanel").assertIsDisplayed()
        composeRule.onNodeWithText("摄入统计").assertIsDisplayed()
        composeRule.onNodeWithText("3/7").assertIsDisplayed()
        composeRule.onNodeWithText("6").assertIsDisplayed()
        composeRule.onNodeWithTag("thirtyDayPeriod").performClick()
        composeRule.onNodeWithText("近 30 天每日平均").assertIsDisplayed()
    }

    @Test
    fun dynamicColorSettingIsOptIn() {
        var enabled = false
        composeRule.setContent {
            ShikeTheme {
                DynamicColorSetting(
                    enabled = false,
                    available = true,
                    onChanged = { enabled = it },
                )
            }
        }

        composeRule.onNodeWithText("使用 Material 动态色").assertIsDisplayed()
        composeRule.onNodeWithTag("dynamicColorSetting").performClick()
        composeRule.runOnIdle { assertTrue(enabled) }
    }

    @Test
    fun themeSwitchesFromShikePaletteToSystemDynamicScheme() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        var dynamicColor by mutableStateOf(false)
        var activePrimary = Color.Unspecified
        var systemPrimary = Color.Unspecified

        composeRule.setContent {
            systemPrimary = dynamicLightColorScheme(LocalContext.current).primary
            ShikeTheme(dynamicColor = dynamicColor) {
                activePrimary = MaterialTheme.colorScheme.primary
            }
        }

        composeRule.runOnIdle {
            assertEquals(ShikeLightColorScheme.primary, activePrimary)
            dynamicColor = true
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertEquals(systemPrimary, activePrimary) }
    }

    @Test
    fun updateCheckSettingExposesManualCheck() {
        var checkClicked = false
        composeRule.setContent {
            ShikeTheme {
                UpdateCheckSetting(
                    currentVersionName = "2.1.0",
                    isChecking = false,
                    statusMessage = "",
                    onCheck = { checkClicked = true },
                )
            }
        }

        composeRule.onNodeWithText("当前版本 v2.1.0").assertIsDisplayed()
        composeRule.onNodeWithTag("checkForUpdateButton").performClick()
        composeRule.runOnIdle { assertTrue(checkClicked) }
    }

    @Test
    fun debugUpdateSettingExplainsWhyUpdatesAreDisabled() {
        composeRule.setContent {
            ShikeTheme {
                UpdateCheckSetting(
                    currentVersionName = "2.2.0-debug",
                    isChecking = false,
                    statusMessage = "",
                    updatesSupported = false,
                    onCheck = {},
                )
            }
        }

        composeRule.onNodeWithText(
            "Debug 构建使用独立包名，应用内更新仅在正式版启用",
        ).assertIsDisplayed()
        composeRule.onNodeWithTag("checkForUpdateButton").assertIsNotEnabled()
        composeRule.onNodeWithText("仅正式版").assertIsDisplayed()
    }

    @Test
    fun availableUpdateDialogShowsNotesAndReleaseAction() {
        var downloadClicked = false
        var releaseClicked = false
        composeRule.setContent {
            ShikeTheme {
                UpdateAvailableDialog(
                    release = AppRelease(
                        versionName = "2.2.0",
                        releaseUrl = "https://github.com/McGeeLee/shike/releases/tag/v2.2.0",
                        releaseNotes = "新增版本更新检测。",
                        apkName = "shike-v2.2.0.apk",
                        apkUrl = "https://github.com/McGeeLee/shike/releases/download/v2.2.0/shike-v2.2.0.apk",
                        checksumUrl = "https://github.com/McGeeLee/shike/releases/download/v2.2.0/shike-v2.2.0.apk.sha256",
                    ),
                    currentVersionName = "2.1.0",
                    isDownloading = false,
                    downloaded = false,
                    statusMessage = "下载失败：更新包的应用标识或版本不匹配",
                    onDismiss = {},
                    onCancelDownload = {},
                    onDownload = { downloadClicked = true },
                    onInstall = {},
                    onOpenRelease = { releaseClicked = true },
                )
            }
        }

        composeRule.onNodeWithText("发现新版本 v2.2.0").assertIsDisplayed()
        composeRule.onNodeWithText("更新内容").assertIsDisplayed()
        composeRule.onNodeWithText("新增版本更新检测。").assertIsDisplayed()
        composeRule.onNodeWithText("下载失败：更新包的应用标识或版本不匹配").assertIsDisplayed()
        val statusTop = composeRule.onNodeWithTag("updateStatusMessage").fetchSemanticsNode().boundsInRoot.top
        val notesTop = composeRule.onNodeWithText("更新内容").fetchSemanticsNode().boundsInRoot.top
        assertTrue(statusTop < notesTop)
        composeRule.onNodeWithTag("updateActionButton").performClick()
        composeRule.onNodeWithTag("openUpdateReleaseButton").performClick()
        composeRule.runOnIdle {
            assertTrue(downloadClicked)
            assertTrue(releaseClicked)
        }
    }

    @Test
    fun settingsPutsConnectionControlsBeforeModelSelection() {
        var fetchClicked = false
        var testClicked = false
        composeRule.setContent {
            ShikeTheme {
                SettingsSheet(
                    draft = settingsDraft(
                        providerId = "openai",
                        selectedModel = "gpt-5.1",
                        statusMessage = "获取成功，发现 1 个可选模型。",
                        statusKind = ConnectionStatusKind.SUCCESS,
                    ),
                    modelChoices = listOf(ModelChoice("gpt-5.1", "GPT-5.1")),
                    onDismiss = {},
                    onProviderSelected = {},
                    onModelSelected = {},
                    onBaseUrlChanged = {},
                    onApiKeyChanged = {},
                    onGoalChanged = {},
                    onDynamicColorChanged = {},
                    currentVersionName = "2.2.0",
                    isCheckingForUpdate = false,
                    updateStatusMessage = "",
                    onCheckForUpdate = {},
                    onFetchModels = { fetchClicked = true },
                    onTestConnection = { testClicked = true },
                    onSave = {},
                )
            }
        }

        assertVerticalOrder(
            "apiKeyField",
            "modelConnectionActions",
            "modelConnectionStatus",
            "modelField",
        )
        composeRule.onNodeWithText("自动获取模型").performClick()
        composeRule.onNodeWithText("测试连接").performClick()
        composeRule.onNodeWithContentDescription("显示 API Key").performClick()
        composeRule.onNodeWithContentDescription("隐藏 API Key").assertIsDisplayed()
        composeRule.onNodeWithText("获取成功，发现 1 个可选模型。").assertIsDisplayed()
        composeRule.runOnIdle {
            assertTrue(fetchClicked)
            assertTrue(testClicked)
        }
    }

    @Test
    fun customSettingsPutsBaseUrlBeforeCredentialsAndModelSelection() {
        composeRule.setContent {
            ShikeTheme {
                SettingsSheet(
                    draft = settingsDraft(
                        providerId = "custom",
                        selectedModel = "vision-model",
                        customBaseUrl = "https://api.example.com/v1",
                        statusMessage = "无法获取模型，请检查接口设置",
                        statusKind = ConnectionStatusKind.ERROR,
                    ),
                    modelChoices = listOf(ModelChoice("vision-model", "vision-model")),
                    onDismiss = {},
                    onProviderSelected = {},
                    onModelSelected = {},
                    onBaseUrlChanged = {},
                    onApiKeyChanged = {},
                    onGoalChanged = {},
                    onDynamicColorChanged = {},
                    currentVersionName = "2.2.0",
                    isCheckingForUpdate = false,
                    updateStatusMessage = "",
                    onCheckForUpdate = {},
                    onFetchModels = {},
                    onTestConnection = {},
                    onSave = {},
                )
            }
        }

        assertVerticalOrder(
            "customBaseUrlField",
            "apiKeyField",
            "modelConnectionActions",
            "modelConnectionStatus",
            "modelField",
        )
        composeRule.onNodeWithText("无法获取模型，请检查接口设置").assertIsDisplayed()
    }

    @Test
    fun settingsDisablesConnectionActionsWhileLoading() {
        composeRule.setContent {
            ShikeTheme {
                SettingsSheet(
                    draft = settingsDraft(
                        providerId = "gemini",
                        selectedModel = "gemini-3-pro-preview",
                        statusMessage = "正在读取可用模型…",
                        statusKind = ConnectionStatusKind.LOADING,
                        isLoading = true,
                    ),
                    modelChoices = listOf(ModelChoice("gemini-3-pro-preview", "Gemini 3 Pro")),
                    onDismiss = {},
                    onProviderSelected = {},
                    onModelSelected = {},
                    onBaseUrlChanged = {},
                    onApiKeyChanged = {},
                    onGoalChanged = {},
                    onDynamicColorChanged = {},
                    currentVersionName = "2.2.0",
                    isCheckingForUpdate = false,
                    updateStatusMessage = "",
                    onCheckForUpdate = {},
                    onFetchModels = {},
                    onTestConnection = {},
                    onSave = {},
                )
            }
        }

        composeRule.onNodeWithText("正在读取可用模型…").assertIsDisplayed()
        composeRule.onNodeWithText("自动获取模型").assertIsNotEnabled()
        composeRule.onNodeWithText("测试连接").assertIsNotEnabled()
    }

    private fun settingsDraft(
        providerId: String,
        selectedModel: String,
        customBaseUrl: String = "",
        statusMessage: String = "",
        statusKind: ConnectionStatusKind = ConnectionStatusKind.IDLE,
        isLoading: Boolean = false,
    ) = SettingsDraft(
        providerId = providerId,
        selectedModel = selectedModel,
        customBaseUrl = customBaseUrl,
        apiKey = "test-key",
        goalInput = "2000",
        dynamicColorEnabled = false,
        selections = mapOf(providerId to selectedModel),
        statusMessage = statusMessage,
        statusKind = statusKind,
        isLoading = isLoading,
    )

    private fun assertVerticalOrder(vararg tags: String) {
        composeRule.waitForIdle()
        val tops = tags.map { tag ->
            composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.top
        }
        tops.zipWithNext().forEachIndexed { index, (upper, lower) ->
            assertTrue("${tags[index]} should be above ${tags[index + 1]}", upper < lower)
        }
    }
}
