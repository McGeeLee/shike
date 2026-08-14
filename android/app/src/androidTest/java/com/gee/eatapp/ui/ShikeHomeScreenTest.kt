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
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.gee.eatapp.update.AppRelease
import com.gee.eatapp.ui.theme.ShikeLightColorScheme
import com.gee.eatapp.ui.theme.ShikeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

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
        composeRule.onNodeWithText("今日记录").assertIsDisplayed()
        composeRule.onNodeWithTag("captureButton").performClick()
        composeRule.runOnIdle { assertTrue(captureClicked) }
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
                    statusMessage = "发现新版本 v2.2.0",
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
        composeRule.onNodeWithTag("updateActionButton").performClick()
        composeRule.onNodeWithTag("openUpdateReleaseButton").performClick()
        composeRule.runOnIdle {
            assertTrue(downloadClicked)
            assertTrue(releaseClicked)
        }
    }
}
