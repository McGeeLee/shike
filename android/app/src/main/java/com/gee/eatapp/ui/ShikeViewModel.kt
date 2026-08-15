package com.gee.eatapp.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gee.eatapp.BuildConfig
import com.gee.eatapp.data.AnalysisResult
import com.gee.eatapp.data.AppSettings
import com.gee.eatapp.data.Confidence
import com.gee.eatapp.data.DailySummary
import com.gee.eatapp.data.DeletedMeal
import com.gee.eatapp.data.ImageInputSupport
import com.gee.eatapp.data.MealEntry
import com.gee.eatapp.data.ProviderCatalog
import com.gee.eatapp.data.ShikeRepository
import com.gee.eatapp.data.effectiveModel
import com.gee.eatapp.data.normalizeBaseUrl
import com.gee.eatapp.image.ImageProcessor
import com.gee.eatapp.image.PreparedImage
import com.gee.eatapp.network.FoodAnalysisClient
import com.gee.eatapp.update.AppRelease
import com.gee.eatapp.update.AppUpdateClient
import com.gee.eatapp.update.UpdateCheckStore
import com.gee.eatapp.update.isNewerVersion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.io.File
import java.util.UUID

enum class ConnectionStatusKind { IDLE, LOADING, SUCCESS, ERROR }

data class SettingsDraft(
    val providerId: String,
    val selectedModel: String,
    val customBaseUrl: String,
    val apiKey: String,
    val goalInput: String,
    val dynamicColorEnabled: Boolean,
    val selections: Map<String, String>,
    val discoveredModels: Map<String, List<String>> = emptyMap(),
    val statusMessage: String = "",
    val statusKind: ConnectionStatusKind = ConnectionStatusKind.IDLE,
    val errorMessage: String = "",
    val isLoading: Boolean = false,
)

sealed interface MealPanel {
    data object Hidden : MealPanel
    data object Preparing : MealPanel
    data class Preview(val image: PreparedImage, val note: String = "") : MealPanel
    data class Analyzing(val image: PreparedImage, val note: String) : MealPanel
    data class Result(val image: PreparedImage, val note: String, val result: AnalysisResult) : MealPanel
    data class Error(val message: String, val image: PreparedImage? = null, val note: String = "") : MealPanel
}

data class ShikeUiState(
    val selectedDate: LocalDate = LocalDate.now(),
    val entries: List<MealEntry> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val goal: Int = ShikeRepository.DEFAULT_GOAL,
    val settingsDraft: SettingsDraft? = null,
    val mealPanel: MealPanel = MealPanel.Hidden,
    val imageSourceRequestId: Long = 0,
    val deletedMeal: DeletedMeal? = null,
    val availableUpdate: AppRelease? = null,
    val isCheckingForUpdate: Boolean = false,
    val isDownloadingUpdate: Boolean = false,
    val downloadedUpdatePath: String? = null,
    val updateStatusMessage: String = "",
) {
    val summary: DailySummary get() = DailySummary.from(entries)
}

class ShikeViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ShikeRepository(application)
    private val analysisClient = FoodAnalysisClient()
    private val updateClient = AppUpdateClient()
    private val updateCheckStore = UpdateCheckStore(application)
    private val imageProcessor = ImageProcessor(application.contentResolver)
    private var imageJob: Job? = null
    private var analysisJob: Job? = null
    private var updateDownloadJob: Job? = null
    private var eventCounter = 0L

    var uiState by mutableStateOf(loadState(LocalDate.now()))
        private set

    init {
        checkForUpdate(manual = false)
    }

    fun previousDay() = selectDate(uiState.selectedDate.minusDays(1))

    fun nextDay() {
        if (uiState.selectedDate < LocalDate.now()) selectDate(uiState.selectedDate.plusDays(1))
    }

    fun today() = selectDate(LocalDate.now())

    fun requestImageSource() {
        uiState = uiState.copy(imageSourceRequestId = ++eventCounter)
    }

    fun consumeImageSourceRequest(requestId: Long) {
        if (uiState.imageSourceRequestId == requestId) uiState = uiState.copy(imageSourceRequestId = 0)
    }

    fun prepareImage(uri: Uri) {
        imageJob?.cancel()
        analysisJob?.cancel()
        uiState = uiState.copy(mealPanel = MealPanel.Preparing)
        imageJob = viewModelScope.launch {
            runCatching { imageProcessor.prepare(uri) }
                .onSuccess { uiState = uiState.copy(mealPanel = MealPanel.Preview(it)) }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    uiState = uiState.copy(
                        mealPanel = MealPanel.Error(error.message ?: "图片读取失败"),
                    )
                }
        }
    }

    fun updateMealNote(note: String) {
        val panel = uiState.mealPanel as? MealPanel.Preview ?: return
        uiState = uiState.copy(mealPanel = panel.copy(note = note.take(500)))
    }

    fun analyzeMeal() {
        val preview = uiState.mealPanel as? MealPanel.Preview ?: return
        analysisJob?.cancel()
        val settings = uiState.settings
        val apiKey = repository.apiKey(settings.providerId)
        uiState = uiState.copy(mealPanel = MealPanel.Analyzing(preview.image, preview.note))
        analysisJob = viewModelScope.launch {
            runCatching {
                analysisClient.analyze(settings, apiKey, preview.image.analysisBase64, preview.note)
            }.onSuccess { result ->
                uiState = if (result.isFood && result.foods.isNotEmpty()) {
                    uiState.copy(mealPanel = MealPanel.Result(preview.image, preview.note, result))
                } else {
                    uiState.copy(
                        mealPanel = MealPanel.Error("照片中没有识别到食物", preview.image, preview.note),
                    )
                }
            }.onFailure { error ->
                if (error is CancellationException) throw error
                uiState = uiState.copy(
                    mealPanel = MealPanel.Error(
                        error.message ?: "识别失败，请重试",
                        preview.image,
                        preview.note,
                    ),
                )
            }
        }
    }

    fun retryMeal() {
        val error = uiState.mealPanel as? MealPanel.Error ?: return
        val image = error.image ?: return
        uiState = uiState.copy(mealPanel = MealPanel.Preview(image, error.note))
    }

    fun saveMeal() {
        val panel = uiState.mealPanel as? MealPanel.Result ?: return
        val result = panel.result
        val entry = MealEntry(
            id = UUID.randomUUID().toString(),
            name = result.foods.joinToString("、") { it.name },
            calories = result.totalCalories,
            proteinGrams = result.foods.sumOf { it.proteinGrams },
            carbsGrams = result.foods.sumOf { it.carbsGrams },
            fatGrams = result.foods.sumOf { it.fatGrams },
            time = LocalTime.now().format(TIME_FORMAT),
            note = panel.note,
            thumbnailBase64 = panel.image.thumbnailBase64,
        )
        val entries = uiState.entries + entry
        repository.saveEntries(uiState.selectedDate, entries)
        uiState = uiState.copy(entries = entries, mealPanel = MealPanel.Hidden)
    }

    fun dismissMealPanel() {
        imageJob?.cancel()
        analysisJob?.cancel()
        uiState = uiState.copy(mealPanel = MealPanel.Hidden)
    }

    fun deleteMeal(index: Int) {
        if (index !in uiState.entries.indices) return
        val entries = uiState.entries.toMutableList()
        val entry = entries.removeAt(index)
        repository.saveEntries(uiState.selectedDate, entries)
        uiState = uiState.copy(
            entries = entries,
            deletedMeal = DeletedMeal(++eventCounter, uiState.selectedDate, entry, index),
        )
    }

    fun undoDelete(eventId: Long) {
        val deleted = uiState.deletedMeal?.takeIf { it.eventId == eventId } ?: return
        val entries = repository.entries(deleted.date).toMutableList()
        entries.add(deleted.index.coerceIn(0, entries.size), deleted.entry)
        repository.saveEntries(deleted.date, entries)
        uiState = uiState.copy(
            entries = if (deleted.date == uiState.selectedDate) entries else uiState.entries,
            deletedMeal = null,
        )
    }

    fun consumeDelete(eventId: Long) {
        if (uiState.deletedMeal?.eventId == eventId) uiState = uiState.copy(deletedMeal = null)
    }

    fun openSettings() {
        val settings = repository.settings()
        val selectedModel = settings.effectiveModel()
        uiState = uiState.copy(
            settingsDraft = SettingsDraft(
                providerId = settings.providerId,
                selectedModel = selectedModel,
                customBaseUrl = settings.customBaseUrl,
                apiKey = repository.apiKey(settings.providerId),
                goalInput = repository.goal().toString(),
                dynamicColorEnabled = settings.dynamicColorEnabled,
                selections = mapOf(settings.providerId to selectedModel),
            ),
        )
    }

    fun dismissSettings() {
        uiState = uiState.copy(settingsDraft = null)
    }

    fun selectProvider(providerId: String) {
        val draft = uiState.settingsDraft ?: return
        val provider = ProviderCatalog.find(providerId) ?: return
        val selections = draft.selections + (draft.providerId to draft.selectedModel)
        val selected = selections[providerId]
            ?: draft.discoveredModels[providerId]?.firstOrNull()
            ?: provider.models.firstOrNull()?.id
            ?: ""
        uiState = uiState.copy(
            settingsDraft = draft.copy(
                providerId = providerId,
                selectedModel = selected,
                apiKey = repository.apiKey(providerId),
                selections = selections + (providerId to selected),
                statusMessage = "",
                statusKind = ConnectionStatusKind.IDLE,
                errorMessage = "",
            ),
        )
    }

    fun selectModel(model: String) = updateDraft { draft ->
        draft.copy(
            selectedModel = model.take(200),
            selections = draft.selections + (draft.providerId to model.take(200)),
            errorMessage = "",
        )
    }

    fun updateCustomBaseUrl(value: String) = updateDraft { it.copy(customBaseUrl = value.take(2048), errorMessage = "") }

    fun updateApiKey(value: String) = updateDraft { it.copy(apiKey = value, errorMessage = "") }

    fun updateGoal(value: String) = updateDraft {
        it.copy(goalInput = value.filter(Char::isDigit).take(6), errorMessage = "")
    }

    fun updateDynamicColor(enabled: Boolean) = updateDraft {
        it.copy(dynamicColorEnabled = enabled)
    }

    fun checkForUpdate(manual: Boolean = true) {
        if (uiState.isCheckingForUpdate) return
        if (!manual && !updateCheckStore.shouldAutoCheck()) return
        updateCheckStore.recordAttempt()
        uiState = uiState.copy(
            isCheckingForUpdate = true,
            updateStatusMessage = if (manual) "正在检查新版本…" else "",
        )
        viewModelScope.launch {
            runCatching { updateClient.latestRelease(BuildConfig.VERSION_NAME) }
                .onSuccess { release ->
                    val update = release?.takeIf {
                        isNewerVersion(it.versionName, BuildConfig.VERSION_NAME)
                    }
                    uiState = uiState.copy(
                        availableUpdate = update ?: uiState.availableUpdate,
                        isCheckingForUpdate = false,
                        downloadedUpdatePath = if (
                            update != null && update.versionName != uiState.availableUpdate?.versionName
                        ) {
                            null
                        } else {
                            uiState.downloadedUpdatePath
                        },
                        updateStatusMessage = when {
                            update != null -> "发现新版本 v${update.versionName}"
                            manual && release == null -> "暂未找到已发布版本"
                            manual -> "当前已是最新版本"
                            else -> ""
                        },
                    )
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    uiState = uiState.copy(
                        isCheckingForUpdate = false,
                        updateStatusMessage = if (manual) {
                            error.message ?: "检查失败，请稍后重试"
                        } else {
                            ""
                        },
                    )
                }
        }
    }

    fun dismissUpdate() {
        updateDownloadJob?.cancel()
        uiState.downloadedUpdatePath?.let { File(it).delete() }
        uiState = uiState.copy(
            availableUpdate = null,
            isDownloadingUpdate = false,
            downloadedUpdatePath = null,
        )
    }

    fun downloadUpdate() {
        val release = uiState.availableUpdate ?: return
        if (uiState.isDownloadingUpdate) return
        updateDownloadJob?.cancel()
        uiState = uiState.copy(
            isDownloadingUpdate = true,
            downloadedUpdatePath = null,
            updateStatusMessage = "正在下载并校验更新包…",
        )
        updateDownloadJob = viewModelScope.launch {
            try {
                val apk = updateClient.downloadRelease(getApplication(), release)
                uiState = uiState.copy(
                    isDownloadingUpdate = false,
                    downloadedUpdatePath = apk.absolutePath,
                    updateStatusMessage = "下载完成并已通过 SHA-256 校验",
                )
            } catch (error: CancellationException) {
                uiState = uiState.copy(
                    isDownloadingUpdate = false,
                    updateStatusMessage = "已取消下载",
                )
                throw error
            } catch (error: Throwable) {
                uiState = uiState.copy(
                    isDownloadingUpdate = false,
                    updateStatusMessage = error.message ?: "更新包下载失败，请重试",
                )
            } finally {
                updateDownloadJob = null
            }
        }
    }

    fun cancelUpdateDownload() {
        updateDownloadJob?.cancel()
        updateDownloadJob = null
        uiState = uiState.copy(
            isDownloadingUpdate = false,
            updateStatusMessage = "已取消下载",
        )
    }

    fun reportUpdateInstallError(message: String) {
        if (uiState.availableUpdate == null) return
        uiState = uiState.copy(updateStatusMessage = message)
    }

    fun discoverModels(connectionTest: Boolean) {
        val draft = uiState.settingsDraft ?: return
        val requestSettings = draft.toSettings()
        updateDraft {
            it.copy(
                isLoading = true,
                statusKind = ConnectionStatusKind.LOADING,
                statusMessage = if (connectionTest) "正在测试连接…" else "正在读取可用模型…",
                errorMessage = "",
            )
        }
        viewModelScope.launch {
            runCatching { analysisClient.listAvailableModels(requestSettings, draft.apiKey) }
                .onSuccess { models ->
                    val current = uiState.settingsDraft ?: return@onSuccess
                    if (current.providerId != draft.providerId) return@onSuccess
                    val provider = ProviderCatalog.find(current.providerId) ?: return@onSuccess
                    val selected = current.selectedModel.takeIf(models::contains) ?: models.first()
                    uiState = uiState.copy(
                        settingsDraft = current.copy(
                            selectedModel = selected,
                            selections = current.selections + (current.providerId to selected),
                            discoveredModels = current.discoveredModels + (current.providerId to models),
                            isLoading = false,
                            statusKind = ConnectionStatusKind.SUCCESS,
                            statusMessage = if (provider.imageInputSupport == ImageInputSupport.UNSUPPORTED) {
                                "连接成功，发现 ${models.size} 个模型；但这些模型当前不能接收食物照片。"
                            } else {
                                "${if (connectionTest) "连接成功" else "获取成功"}，发现 ${models.size} 个可选模型。"
                            },
                        ),
                    )
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    val current = uiState.settingsDraft ?: return@onFailure
                    if (current.providerId != draft.providerId) return@onFailure
                    uiState = uiState.copy(
                        settingsDraft = current.copy(
                            isLoading = false,
                            statusKind = ConnectionStatusKind.ERROR,
                            statusMessage = error.message ?: "无法获取模型，请检查接口设置",
                        ),
                    )
                }
        }
    }

    fun saveSettings() {
        val draft = uiState.settingsDraft ?: return
        runCatching {
            val goal = draft.goalInput.toIntOrNull()
                ?.takeIf { it in 1..ShikeRepository.MAX_GOAL }
                ?: throw IllegalArgumentException("每日目标需在 1 到 100000 千卡之间")
            if (draft.selectedModel.isBlank()) {
                throw IllegalArgumentException("请先获取并选择一个支持图片的模型")
            }
            val provider = ProviderCatalog.find(draft.providerId)
                ?: throw IllegalArgumentException("请选择有效的模型服务商")
            if (provider.imageInputSupport == ImageInputSupport.UNSUPPORTED) {
                throw IllegalArgumentException(provider.guidance)
            }
            val settings = draft.toSettings().let {
                if (it.providerId == "custom") it.copy(customBaseUrl = normalizeBaseUrl(it.customBaseUrl)) else it
            }
            repository.saveSettings(settings, goal, draft.apiKey)
            uiState = uiState.copy(settings = settings, goal = goal, settingsDraft = null)
        }.onFailure { error ->
            updateDraft { it.copy(errorMessage = error.message ?: "设置保存失败") }
        }
    }

    fun availableModels(draft: SettingsDraft): List<ModelChoice> {
        val provider = ProviderCatalog.find(draft.providerId) ?: return emptyList()
        val discovered = draft.discoveredModels[provider.id]
        val ids = discovered ?: if (provider.id == "custom") {
            listOfNotNull(draft.selectedModel.takeIf(String::isNotBlank))
        } else {
            provider.models.map { it.id }
        }
        val labels = provider.models.associate { it.id to it.label }
        return ids.map { ModelChoice(it, labels[it] ?: it) }
    }

    fun legacyMigrationNeeded(): Boolean = !repository.legacyMigrationComplete()

    fun importLegacyData(raw: String) {
        repository.importLegacyData(raw)
        uiState = loadState(uiState.selectedDate).copy(
            settingsDraft = uiState.settingsDraft,
            mealPanel = uiState.mealPanel,
            availableUpdate = uiState.availableUpdate,
            isCheckingForUpdate = uiState.isCheckingForUpdate,
            isDownloadingUpdate = uiState.isDownloadingUpdate,
            downloadedUpdatePath = uiState.downloadedUpdatePath,
            updateStatusMessage = uiState.updateStatusMessage,
        )
    }

    private fun selectDate(date: LocalDate) {
        if (date > LocalDate.now()) return
        uiState = uiState.copy(selectedDate = date, entries = repository.entries(date))
    }

    private fun loadState(date: LocalDate) = ShikeUiState(
        selectedDate = date,
        entries = repository.entries(date),
        settings = repository.settings(),
        goal = repository.goal(),
    )

    private fun updateDraft(block: (SettingsDraft) -> SettingsDraft) {
        val draft = uiState.settingsDraft ?: return
        uiState = uiState.copy(settingsDraft = block(draft))
    }

    private fun SettingsDraft.toSettings() = AppSettings(
        providerId = providerId,
        model = if (providerId == "custom") "" else selectedModel,
        customBaseUrl = customBaseUrl.trim(),
        customModel = if (providerId == "custom") selectedModel else "",
        dynamicColorEnabled = dynamicColorEnabled,
    )

    companion object {
        private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")
    }
}

data class ModelChoice(val id: String, val label: String)

fun Confidence.label(): String = when (this) {
    Confidence.LOW -> "置信度较低"
    Confidence.MEDIUM -> "置信度中等"
    Confidence.HIGH -> "置信度较高"
}
