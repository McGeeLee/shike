package com.gee.eatapp.ui

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.os.Looper
import androidx.lifecycle.viewModelScope
import com.gee.eatapp.data.ApiKeyStore
import com.gee.eatapp.data.AnalysisResult
import com.gee.eatapp.data.Confidence
import com.gee.eatapp.data.MealEntry
import com.gee.eatapp.data.ShikeRepository
import com.gee.eatapp.image.MealPhotoArchive
import com.gee.eatapp.image.PreparedImage
import java.io.File
import java.time.LocalDate
import java.util.ArrayDeque
import java.util.UUID
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37])
@LooperMode(LooperMode.Mode.PAUSED)
class ShikeViewModelPersistenceTest {
    private lateinit var application: Application
    private lateinit var preferences: FailingPreferences
    private lateinit var repository: ShikeRepository
    private lateinit var model: ShikeViewModel
    private val io = QueuedDispatcher()
    private val date = LocalDate.now()
    private lateinit var first: MealEntry
    private lateinit var second: MealEntry

    @Before fun setUp() {
        application = RuntimeEnvironment.getApplication()
        preferences = FailingPreferences(application.getSharedPreferences("meals-${UUID.randomUUID()}", Context.MODE_PRIVATE))
        repository = ShikeRepository(preferences, object : ApiKeyStore {
            override fun get(providerId: String) = ""
            override fun put(providerId: String, apiKey: String) = Unit
            override fun retainLegacyKeys(keysJson: String) = Unit
        })
        first = meal("first", 100)
        second = meal("second", 200)
        repository.saveEntries(date, listOf(first, second))
        model = ShikeViewModel(application, repository = repository, mealIoDispatcher = io)
    }

    @After fun tearDown() {
        model.viewModelScope.cancel()
        drain()
        preferences.edit().clear().commit()
        photo(first).delete()
        photo(second).delete()
    }

    @Test fun failedDeleteKeepsRecordsTotalsAndPhotoAndAllowsRetry() {
        val before = model.uiState
        preferences.failNextCommit = true
        model.deleteMeal(0)
        assertTrue(model.uiState.isUpdatingMeals)
        assertEquals(before.entries, model.uiState.entries)
        drain()

        assertEquals(before.entries, model.uiState.entries)
        assertEquals(before.nutritionHistory, model.uiState.nutritionHistory)
        assertEquals(before.summary, model.uiState.summary)
        assertEquals(before.entries, repository.entries(date))
        assertNull(model.uiState.deletedMeal)
        assertNotNull(model.uiState.mealStorageError)
        assertTrue(photo(first).exists())
        assertFalse(model.uiState.isUpdatingMeals)

        model.dismissMealStorageError()
        model.deleteMeal(0)
        drain()
        assertEquals(listOf(second), repository.entries(date))
        assertEquals(first, model.uiState.deletedMeal!!.entry)
        assertNull(model.uiState.mealStorageError)
    }

    @Test fun failedSecondDeletePreservesEarlierUndoAndBothPhotos() {
        model.deleteMeal(0)
        drain()
        val before = model.uiState
        preferences.failNextCommit = true
        model.deleteMeal(0)
        drain()

        assertEquals(before.deletedMeal, model.uiState.deletedMeal)
        assertEquals(before.entries, repository.entries(date))
        assertEquals(before.nutritionHistory, model.uiState.nutritionHistory)
        assertTrue(photo(first).exists())
        assertTrue(photo(second).exists())

        model.dismissMealStorageError()
        model.undoDelete(before.deletedMeal!!.eventId)
        drain()
        assertEquals(listOf(first, second), repository.entries(date))
    }

    @Test fun failedUndoRemainsRetryableWithoutDuplicateRecordsOrPhotoCleanup() {
        model.deleteMeal(0)
        drain()
        val before = model.uiState
        val event = before.deletedMeal!!.eventId
        preferences.failNextCommit = true
        model.undoDelete(event)
        // A stale snackbar dismissal must not remove a photo while undo is saving.
        model.consumeDelete(event)
        drain()
        assertEquals(before.entries, model.uiState.entries)
        assertEquals(before.nutritionHistory, model.uiState.nutritionHistory)
        assertEquals(before.deletedMeal, model.uiState.deletedMeal)
        assertEquals(event, model.uiState.mealStorageError!!.undoEventId)
        assertEquals(listOf(second), repository.entries(date))
        model.consumeDelete(event)
        drain()
        assertTrue(photo(first).exists())

        model.undoDelete(event)
        drain()
        model.undoDelete(event)
        drain()
        assertEquals(listOf(first, second), repository.entries(date))
        assertEquals(300, model.uiState.summary.calories)
        assertNull(model.uiState.deletedMeal)
        assertNull(model.uiState.mealStorageError)
        assertTrue(photo(first).exists())
    }

    @Test fun successfulReplacementCleansOnlyThePreviousDeletedPhoto() {
        model.deleteMeal(0)
        drain()
        val oldEvent = model.uiState.deletedMeal!!.eventId
        model.deleteMeal(0)
        assertTrue(photo(first).exists())
        drain()
        assertFalse(photo(first).exists())
        assertTrue(photo(second).exists())
        model.undoDelete(oldEvent)
        model.consumeDelete(oldEvent)
        drain()
        assertEquals(second, model.uiState.deletedMeal!!.entry)
        model.consumeDelete(model.uiState.deletedMeal!!.eventId)
        drain()
        assertFalse(photo(second).exists())
        assertNull(model.uiState.deletedMeal)
    }

    @Test fun dateChangesDuringDeleteAndUndoDoNotReplaceTheVisibleDay() {
        val yesterday = date.minusDays(1)
        model.deleteMeal(0)
        model.selectDate(yesterday)
        drain()
        assertEquals(yesterday, model.uiState.selectedDate)
        assertTrue(model.uiState.entries.isEmpty())
        val event = model.uiState.deletedMeal!!.eventId
        preferences.failNextCommit = true
        model.undoDelete(event)
        drain()
        assertEquals(yesterday, model.uiState.selectedDate)
        assertTrue(model.uiState.entries.isEmpty())
        assertEquals(event, model.uiState.deletedMeal!!.eventId)
        model.undoDelete(event)
        drain()
        assertTrue(model.uiState.entries.isEmpty())
        assertEquals(listOf(first, second), repository.entries(date))
        assertEquals(300, model.uiState.nutritionHistory.single { it.date == date }.summary.calories)
    }

    @Test fun duplicateActionsCannotRacePendingPersistence() {
        model.deleteMeal(0)
        model.deleteMeal(1)
        drain()
        assertEquals(listOf(second), repository.entries(date))
        val event = model.uiState.deletedMeal!!.eventId
        model.undoDelete(event)
        model.undoDelete(event)
        model.deleteMeal(0)
        drain()
        assertEquals(listOf(first, second), repository.entries(date))
    }

    @Test fun thrownWriteFailureIsHandledWithoutLosingUndo() {
        model.deleteMeal(0)
        drain()
        val event = model.uiState.deletedMeal!!.eventId
        preferences.nextError = IllegalStateException("Storage is unavailable")
        model.undoDelete(event)
        drain()
        assertEquals(event, model.uiState.mealStorageError!!.undoEventId)
        assertEquals(listOf(second), repository.entries(date))
        assertTrue(photo(first).exists())
    }

    @Test fun cancellationIsNotReportedAsAStorageFailure() {
        preferences.nextError = CancellationException("ViewModel was cleared")
        model.deleteMeal(0)
        drain()
        assertNull(model.uiState.mealStorageError)
        assertFalse(model.uiState.isUpdatingMeals)
        assertEquals(listOf(first, second), repository.entries(date))
    }

    @Test fun failedDeleteRestoresVisibleRecordsReadDuringCommit() {
        preferences.failNextCommit = true
        preferences.afterMemoryCommit = { model.selectDate(date) }
        model.deleteMeal(0)
        drain()
        assertEquals(listOf(first, second), model.uiState.entries)
        assertEquals(300, model.uiState.summary.calories)
        assertEquals(model.uiState.summary, model.uiState.nutritionHistory.single { it.date == date }.summary)
    }

    @Test fun failedUndoRestoresVisibleRecordsReadDuringCommit() {
        model.deleteMeal(0)
        drain()
        preferences.failNextCommit = true
        preferences.afterMemoryCommit = { model.selectDate(date) }
        model.undoDelete(model.uiState.deletedMeal!!.eventId)
        drain()
        assertEquals(listOf(second), model.uiState.entries)
        assertEquals(200, model.uiState.summary.calories)
        assertEquals(model.uiState.summary, model.uiState.nutritionHistory.single { it.date == date }.summary)
    }

    @Test fun migrationRefreshPreservesPendingWriteGuardAndUndoState() {
        model.deleteMeal(0)
        drain()
        val deleted = model.uiState.deletedMeal
        model.deleteMeal(0)
        model.importLegacyData("""{"version":1,"settings":null,"goal":"2000","keys":{},"logs":{}}""")
        assertTrue(model.uiState.isUpdatingMeals)
        assertEquals(deleted, model.uiState.deletedMeal)
        model.undoDelete(deleted!!.eventId)
        drain()
        assertTrue(repository.entries(date).isEmpty())
        assertEquals(second, model.uiState.deletedMeal!!.entry)
    }

    @Test fun cancelledSaveReleasesWriteGuardAndRestoresRetryPanel() {
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        try {
            val panel = MealPanel.Result(
                PreparedImage(bitmap, "AQID", ""), "",
                AnalysisResult(true, emptyList(), 0, Confidence.LOW, ""),
            )
            val setter = ShikeViewModel::class.java.getDeclaredMethod("setUiState", ShikeUiState::class.java)
            setter.isAccessible = true
            setter.invoke(model, model.uiState.copy(mealPanel = panel))
            preferences.nextError = CancellationException("Save cancelled")
            model.saveMeal()
            drain()
            assertFalse(model.uiState.isUpdatingMeals)
            assertEquals(panel, model.uiState.mealPanel)
            assertEquals(listOf(first, second), repository.entries(date))
            model.deleteMeal(0)
            drain()
            assertEquals(listOf(second), repository.entries(date))
        } finally { bitmap.recycle() }
    }

    private fun meal(name: String, calories: Int): MealEntry {
        val id = UUID.randomUUID().toString()
        return MealEntry(id, name, calories, 1.0, 2.0, 3.0, "12:00", "", "",
            photoFile = MealPhotoArchive(application.filesDir).save(id, byteArrayOf(1, 2, 3)))
    }

    private fun photo(entry: MealEntry) = File(application.filesDir, "meal_photos/${entry.photoFile}")

    private fun drain() {
        do {
            shadowOf(Looper.getMainLooper()).idle()
            io.runAll()
            shadowOf(Looper.getMainLooper()).idle()
        } while (io.hasTasks())
    }

    private class QueuedDispatcher : CoroutineDispatcher() {
        private val tasks = ArrayDeque<Runnable>()
        override fun dispatch(context: CoroutineContext, block: Runnable) { tasks.add(block) }
        fun hasTasks() = tasks.isNotEmpty()
        fun runAll() { while (tasks.isNotEmpty()) tasks.removeFirst().run() }
    }

    /** commit(false) still updates SharedPreferences memory, just like Android does. */
    private class FailingPreferences(private val delegate: SharedPreferences) : SharedPreferences by delegate {
        var failNextCommit = false
        var nextError: Exception? = null
        var afterMemoryCommit: (() -> Unit)? = null
        override fun edit(): SharedPreferences.Editor {
            val editor = delegate.edit()
            return object : SharedPreferences.Editor by editor {
                override fun commit(): Boolean {
                    nextError?.let { nextError = null; throw it }
                    val persisted = editor.commit()
                    afterMemoryCommit?.let { afterMemoryCommit = null; it() }
                    if (!failNextCommit) return persisted
                    failNextCommit = false
                    return false
                }
            }
        }
    }
}
