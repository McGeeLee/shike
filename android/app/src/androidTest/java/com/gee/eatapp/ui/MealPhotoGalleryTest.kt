package com.gee.eatapp.ui

import android.graphics.Bitmap
import android.util.Base64
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.gee.eatapp.data.MealEntry
import com.gee.eatapp.data.ShikeRepository
import com.gee.eatapp.image.MealPhotoArchive
import com.gee.eatapp.ui.theme.ShikeTheme
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MealPhotoGalleryTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun savedPhotoSurvivesRepositoryReloadAndCanBePreviewed() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val archive = MealPhotoArchive(context.filesDir)
        val date = LocalDate.of(2000, 1, 2)
        val repository = ShikeRepository(context)
        val previous = repository.entries(date)
        val bytes = jpeg()
        val name = archive.save(UUID.randomUUID().toString(), bytes)
        try {
            val entry = meal(name, bytes)
            repository.saveEntries(date, listOf(entry))
            val restored = ShikeRepository(context).entries(date).single()
            assertEquals(entry, restored)
            assertArrayEquals(bytes, archive.bytes(restored))
            composeRule.setContent { ShikeTheme { MealPhotoGallery(date, listOf(restored)) } }
            composeRule.onNodeWithText("下载当日图片").assertIsEnabled()
            composeRule.onNodeWithContentDescription("查看苹果的照片").performClick()
            composeRule.waitUntil(10_000) {
                composeRule.onAllNodesWithContentDescription("苹果").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithContentDescription("苹果").assertIsDisplayed()
            composeRule.onNodeWithText("下载照片").assertIsEnabled()
            composeRule.onNodeWithText("根据可见份量估算").assertIsDisplayed()
            composeRule.onNodeWithText("关闭").performClick()
        } finally {
            repository.saveEntries(date, previous)
            archive.delete(name)
        }
    }

    @Test fun legacyRecordShowsThumbnailDownloadLimitation() {
        composeRule.setContent { ShikeTheme { MealPhotoGallery(LocalDate.now(), listOf(meal("", jpeg()))) } }
        composeRule.onNodeWithContentDescription("查看苹果的照片").performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithContentDescription("苹果").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("旧记录仅保留缩略图，下载清晰度有限。").assertIsDisplayed()
        composeRule.onNodeWithText("下载照片").assertIsEnabled()
    }

    private fun meal(file: String, bytes: ByteArray) = MealEntry(
        id = "gallery-test", name = "苹果", calories = 95,
        proteinGrams = 0.5, carbsGrams = 25.0, fatGrams = 0.3,
        time = "12:30", note = "一个苹果", thumbnailBase64 = Base64.encodeToString(bytes, Base64.NO_WRAP),
        photoFile = file, analysisNotes = "根据可见份量估算", modelLabel = "视觉模型",
    )

    private fun jpeg(): ByteArray {
        val bitmap = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.RED)
        return ByteArrayOutputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it))
            bitmap.recycle()
            it.toByteArray()
        }
    }
}
