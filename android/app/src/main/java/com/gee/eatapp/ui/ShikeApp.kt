package com.gee.eatapp.ui

import android.content.Context
import android.content.ClipData
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.gee.eatapp.BuildConfig
import com.gee.eatapp.data.AppSettings
import com.gee.eatapp.data.MealEntry
import com.gee.eatapp.data.ProviderCatalog
import com.gee.eatapp.data.effectiveModel
import com.gee.eatapp.data.simplifiedChinese
import com.gee.eatapp.image.PreparedImage
import com.gee.eatapp.update.AppRelease
import com.gee.eatapp.ui.theme.ShikeDimensions
import com.gee.eatapp.ui.theme.ShikeTheme
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun ShikeApp(viewModel: ShikeViewModel) {
    val state = viewModel.uiState
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var showImageSourceDialog by rememberSaveable { mutableStateOf(false) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var pendingInstallPath by rememberSaveable { mutableStateOf<String?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.prepareImage(uri)
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { captured ->
        val uri = pendingCameraUri
        pendingCameraUri = null
        if (captured && uri != null) viewModel.prepareImage(uri)
    }
    val unknownSourcesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        val path = pendingInstallPath
        pendingInstallPath = null
        if (path != null) {
            val message = when {
                !canInstallPackages(context) -> "未授予安装未知应用权限"
                !openPackageInstaller(context, path) -> "无法打开系统安装器"
                else -> null
            }
            if (message != null) viewModel.reportUpdateInstallError(message)
        }
    }

    LaunchedEffect(state.imageSourceRequestId) {
        if (state.imageSourceRequestId != 0L) {
            showImageSourceDialog = true
            viewModel.consumeImageSourceRequest(state.imageSourceRequestId)
        }
    }
    LaunchedEffect(state.deletedMeal?.eventId) {
        val deleted = state.deletedMeal ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = "已删除“${deleted.entry.name.ifBlank { "这条记录" }}”",
            actionLabel = "撤销",
            withDismissAction = true,
        )
        if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete(deleted.eventId)
        else viewModel.consumeDelete(deleted.eventId)
    }

    ShikeHomeScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onPreviousDay = viewModel::previousDay,
        onNextDay = viewModel::nextDay,
        onToday = viewModel::today,
        onOpenSettings = viewModel::openSettings,
        onAddMeal = { showImageSourceDialog = true },
        onDeleteMeal = viewModel::deleteMeal,
    )

    if (showImageSourceDialog) {
        ImageSourceDialog(
            cameraAvailable = remember(context) {
                Intent(MediaStore.ACTION_IMAGE_CAPTURE).resolveActivity(context.packageManager) != null
            },
            onDismiss = { showImageSourceDialog = false },
            onCamera = {
                showImageSourceDialog = false
                createCaptureUri(context).also {
                    pendingCameraUri = it
                    cameraLauncher.launch(it)
                }
            },
            onGallery = {
                showImageSourceDialog = false
                galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
        )
    }

    state.settingsDraft?.let { draft ->
        SettingsSheet(
            draft = draft,
            modelChoices = viewModel.availableModels(draft),
            onDismiss = viewModel::dismissSettings,
            onProviderSelected = viewModel::selectProvider,
            onModelSelected = viewModel::selectModel,
            onBaseUrlChanged = viewModel::updateCustomBaseUrl,
            onApiKeyChanged = viewModel::updateApiKey,
            onGoalChanged = viewModel::updateGoal,
            onDynamicColorChanged = viewModel::updateDynamicColor,
            currentVersionName = BuildConfig.VERSION_NAME,
            isCheckingForUpdate = state.isCheckingForUpdate,
            updateStatusMessage = state.updateStatusMessage,
            onCheckForUpdate = { viewModel.checkForUpdate() },
            onFetchModels = { viewModel.discoverModels(false) },
            onTestConnection = { viewModel.discoverModels(true) },
            onSave = viewModel::saveSettings,
        )
    }

    if (state.mealPanel != MealPanel.Hidden) {
        MealPanelSheet(
            panel = state.mealPanel,
            isToday = state.selectedDate == LocalDate.now(),
            onDismiss = viewModel::dismissMealPanel,
            onNoteChanged = viewModel::updateMealNote,
            onAnalyze = viewModel::analyzeMeal,
            onRetry = viewModel::retryMeal,
            onSave = viewModel::saveMeal,
        )
    }

    state.availableUpdate?.let { release ->
        UpdateAvailableDialog(
            release = release,
            currentVersionName = BuildConfig.VERSION_NAME,
            isDownloading = state.isDownloadingUpdate,
            downloaded = state.downloadedUpdatePath != null,
            statusMessage = state.updateStatusMessage,
            onDismiss = viewModel::dismissUpdate,
            onCancelDownload = viewModel::cancelUpdateDownload,
            onDownload = viewModel::downloadUpdate,
            onInstall = {
                state.downloadedUpdatePath?.let { path ->
                    if (canInstallPackages(context)) {
                        if (!openPackageInstaller(context, path)) {
                            viewModel.reportUpdateInstallError("无法打开系统安装器")
                        }
                    } else {
                        pendingInstallPath = path
                        val intent = Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:${context.packageName}"),
                        )
                        runCatching { unknownSourcesLauncher.launch(intent) }
                            .onFailure {
                                pendingInstallPath = null
                                viewModel.reportUpdateInstallError("无法打开安装权限设置")
                            }
                    }
                }
            },
            onOpenRelease = {
                if (openReleasePage(context, release.releaseUrl)) {
                    viewModel.dismissUpdate()
                } else {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("未找到可打开更新页面的应用")
                    }
                }
            },
        )
    }
}

@Composable
fun ShikeHomeScreen(
    state: ShikeUiState,
    snackbarHostState: SnackbarHostState,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onToday: () -> Unit,
    onOpenSettings: () -> Unit,
    onAddMeal: () -> Unit,
    onDeleteMeal: (Int) -> Unit,
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { scaffoldPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = ShikeDimensions.ContentMaxWidth)
                    .verticalScroll(rememberScrollState())
                    .padding(
                        start = ShikeDimensions.ScreenHorizontal,
                        top = ShikeDimensions.ScreenTop,
                        end = ShikeDimensions.ScreenHorizontal,
                        bottom = ShikeDimensions.ScreenBottom,
                    ),
            ) {
                AppHeader(onOpenSettings)
                DateNavigator(state.selectedDate, onPreviousDay, onNextDay, onToday)
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    if (maxWidth >= ShikeDimensions.WideBreakpoint) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Column(
                                modifier = Modifier.weight(0.9f),
                                verticalArrangement = Arrangement.spacedBy(ShikeDimensions.SectionGap),
                            ) {
                                SummaryCard(state, onOpenSettings)
                                CaptureButton(state.selectedDate == LocalDate.now(), onAddMeal)
                                ModelLabel(state.settings)
                            }
                            EntryCard(
                                date = state.selectedDate,
                                entries = state.entries,
                                onDeleteMeal = onDeleteMeal,
                                modifier = Modifier.weight(1.1f),
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(ShikeDimensions.SectionGap)) {
                            SummaryCard(state, onOpenSettings)
                            CaptureButton(state.selectedDate == LocalDate.now(), onAddMeal)
                            ModelLabel(state.settings)
                            EntryCard(state.selectedDate, state.entries, onDeleteMeal)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppHeader(onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(52.dp).padding(start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("食刻", style = MaterialTheme.typography.headlineSmall)
        IconButton(onClick = onOpenSettings, modifier = Modifier.size(ShikeDimensions.TouchTarget)) {
            Icon(Icons.Rounded.Settings, contentDescription = "打开设置")
        }
    }
}

@Composable
private fun DateNavigator(
    date: LocalDate,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onToday: () -> Unit,
) {
    val today = LocalDate.now()
    Box(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(bottom = 8.dp)) {
        Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPreviousDay, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Rounded.ChevronLeft, contentDescription = "前一天", tint = MaterialTheme.colorScheme.primary)
            }
            Text(
                text = dateLabel(date),
                modifier = Modifier.widthIn(min = 130.dp).testTag("dateLabel"),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium,
            )
            IconButton(
                onClick = onNextDay,
                enabled = date < today,
                modifier = Modifier.size(44.dp),
            ) {
                Icon(Icons.Rounded.ChevronRight, contentDescription = "后一天")
            }
        }
        if (date != today) {
            TextButton(
                onClick = onToday,
                modifier = Modifier.align(Alignment.CenterEnd),
                colors = ButtonDefaults.textButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Text("回到今天", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun SummaryCard(state: ShikeUiState, onOpenSettings: () -> Unit) {
    val summary = state.summary
    val remaining = state.goal - summary.calories
    val progress = (summary.calories.toFloat() / state.goal).coerceIn(0f, 1f)
    Card(
        modifier = Modifier.fillMaxWidth().testTag("summaryCard"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(ShikeDimensions.CardPadding)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    summary.calories.toString(),
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 44.sp,
                )
                Text(
                    " / ${state.goal} 千卡",
                    modifier = Modifier.padding(start = 4.dp, bottom = 5.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    if (remaining >= 0) "还可摄入 $remaining 千卡" else "已超出 ${-remaining} 千卡",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = onOpenSettings, modifier = Modifier.height(44.dp)) {
                    Text("设置目标", color = MaterialTheme.colorScheme.primary)
                }
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .semantics {
                        progressBarRangeInfo = ProgressBarRangeInfo(
                            current = summary.calories.toFloat(),
                            range = 0f..state.goal.toFloat(),
                        )
                    },
                color = if (summary.calories > state.goal) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primaryContainer,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(ShikeDimensions.SmallGap),
            ) {
                MacroBox("蛋白质", summary.proteinGrams, Modifier.weight(1f))
                MacroBox("碳水", summary.carbsGrams, Modifier.weight(1f))
                MacroBox("脂肪", summary.fatGrams, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MacroBox(label: String, grams: Double, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
        Text("${grams.roundToInt()}g", fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
private fun CaptureButton(isToday: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(58.dp).testTag("captureButton"),
        shape = MaterialTheme.shapes.large,
        contentPadding = ButtonDefaults.ContentPadding,
    ) {
        Icon(Icons.Rounded.CameraAlt, contentDescription = null, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Text(if (isToday) "拍照记录这一餐" else "补记这一天的一餐", fontSize = 17.sp)
    }
}

@Composable
private fun ModelLabel(settings: AppSettings) {
    val provider = ProviderCatalog.find(settings.providerId)
    Text(
        text = provider?.let { "识别引擎：${it.name} · ${settings.effectiveModel().ifBlank { "未设置模型" }}" }.orEmpty(),
        modifier = Modifier.fillMaxWidth().padding(top = 0.dp, bottom = 2.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun EntryCard(
    date: LocalDate,
    entries: List<MealEntry>,
    onDeleteMeal: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth().testTag("entryCard"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(ShikeDimensions.CompactCardPadding)) {
            Text(
                if (date == LocalDate.now()) "今日记录" else "当日记录",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(10.dp))
            if (entries.isEmpty()) {
                Text(
                    if (date == LocalDate.now()) "还没有记录，拍张照开始吧" else "这一天没有记录",
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                entries.forEachIndexed { index, entry ->
                    EntryRow(entry, onDelete = { onDeleteMeal(index) })
                    if (index != entries.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryRow(entry: MealEntry, onDelete: () -> Unit) {
    val thumbnail = remember(entry.thumbnailBase64) {
        runCatching {
            val bytes = Base64.decode(entry.thumbnailBase64, Base64.NO_WRAP)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull()
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (thumbnail != null) {
            Image(
                bitmap = thumbnail,
                contentDescription = "${entry.name}的缩略图",
                modifier = Modifier.size(52.dp).clip(MaterialTheme.shapes.small),
                contentScale = ContentScale.Crop,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            Text(
                buildString {
                    append(entry.time)
                    if (entry.note.isNotBlank()) append(" · ${entry.note}")
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text("${entry.calories} 千卡", fontWeight = FontWeight.Bold, maxLines = 1)
        IconButton(onClick = onDelete, modifier = Modifier.size(44.dp)) {
            Icon(
                Icons.Rounded.DeleteOutline,
                contentDescription = "删除${entry.name}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ImageSourceDialog(
    cameraAvailable: Boolean,
    onDismiss: () -> Unit,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加一餐") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (cameraAvailable) {
                    Button(onClick = onCamera, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.CameraAlt, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("拍摄食物照片")
                    }
                }
                OutlinedButton(onClick = onGallery, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.PhotoLibrary, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("从相册选择")
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(
    draft: SettingsDraft,
    modelChoices: List<ModelChoice>,
    onDismiss: () -> Unit,
    onProviderSelected: (String) -> Unit,
    onModelSelected: (String) -> Unit,
    onBaseUrlChanged: (String) -> Unit,
    onApiKeyChanged: (String) -> Unit,
    onGoalChanged: (String) -> Unit,
    onDynamicColorChanged: (Boolean) -> Unit,
    currentVersionName: String,
    isCheckingForUpdate: Boolean,
    updateStatusMessage: String,
    onCheckForUpdate: () -> Unit,
    onFetchModels: () -> Unit,
    onTestConnection: () -> Unit,
    onSave: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetMaxWidth = ShikeDimensions.SheetMaxWidth,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Text("设置", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            DropdownField(
                label = "服务商",
                selectedId = draft.providerId,
                options = ProviderCatalog.all.map { ModelChoice(it.id, it.name) },
                onSelected = onProviderSelected,
            )
            Spacer(Modifier.height(14.dp))
            DropdownField(
                label = if (draft.providerId == "custom") "可用模型" else "模型",
                selectedId = draft.selectedModel,
                options = modelChoices,
                onSelected = onModelSelected,
                placeholder = "请先自动获取模型",
            )
            if (draft.providerId == "custom") {
                Text(
                    "模型直接从接口的 /v1/models 读取，不需要手填或猜测。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = draft.customBaseUrl,
                    onValueChange = onBaseUrlChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("接口地址（OpenAI 兼容 Base URL）") },
                    placeholder = { Text("https://api.example.com/v1") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
            }
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = draft.apiKey,
                onValueChange = onApiKeyChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("API Key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                supportingText = { Text("密钥使用 Android Keystore 加密后保存在本机。") },
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onFetchModels,
                    enabled = !draft.isLoading,
                    modifier = Modifier.weight(1f),
                ) { Text("自动获取模型") }
                Button(
                    onClick = onTestConnection,
                    enabled = !draft.isLoading,
                    modifier = Modifier.weight(1f),
                ) { Text("测试连接") }
            }
            Text(
                draft.statusMessage,
                modifier = Modifier.fillMaxWidth().heightIn(min = 34.dp).padding(top = 6.dp),
                color = when (draft.statusKind) {
                    ConnectionStatusKind.SUCCESS -> MaterialTheme.colorScheme.secondary
                    ConnectionStatusKind.ERROR -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = draft.goalInput,
                onValueChange = onGoalChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("每日热量目标（千卡）") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Spacer(Modifier.height(8.dp))
            DynamicColorSetting(
                enabled = draft.dynamicColorEnabled,
                available = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                onChanged = onDynamicColorChanged,
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            UpdateCheckSetting(
                currentVersionName = currentVersionName,
                isChecking = isCheckingForUpdate,
                statusMessage = updateStatusMessage,
                onCheck = onCheckForUpdate,
            )
            Text(
                draft.errorMessage,
                modifier = Modifier.fillMaxWidth().heightIn(min = 28.dp).padding(top = 6.dp),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("取消") }
                Button(onClick = onSave, modifier = Modifier.weight(1f)) { Text("保存") }
            }
        }
    }
}

@Composable
internal fun UpdateCheckSetting(
    currentVersionName: String,
    isChecking: Boolean,
    statusMessage: String,
    onCheck: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text("应用更新", style = MaterialTheme.typography.bodyLarge)
            Text(
                "当前版本 v$currentVersionName",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            if (statusMessage.isNotBlank()) {
                Text(
                    statusMessage,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        OutlinedButton(
            onClick = onCheck,
            enabled = !isChecking,
            modifier = Modifier.testTag("checkForUpdateButton"),
        ) {
            if (isChecking) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(if (isChecking) "检查中" else "检查更新")
        }
    }
}

@Composable
internal fun UpdateAvailableDialog(
    release: AppRelease,
    currentVersionName: String,
    isDownloading: Boolean,
    downloaded: Boolean,
    statusMessage: String,
    onDismiss: () -> Unit,
    onCancelDownload: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onOpenRelease: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (isDownloading) onCancelDownload() else onDismiss() },
        title = { Text("发现新版本 v${release.versionName}") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "当前版本 v$currentVersionName。食刻会下载已签名 APK、校验 SHA-256，再交给 Android 系统安装器确认安装。",
                )
                Text(
                    "更新内容",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    release.releaseNotes.ifBlank { "本次发布暂未提供更新说明。" },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (isDownloading) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (
                    statusMessage.isNotBlank() &&
                    statusMessage != "发现新版本 v${release.versionName}"
                ) {
                    Text(
                        statusMessage,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(
                    "Android 8 或更高版本首次安装时，系统可能要求授权食刻安装未知应用。安装操作始终需要你的确认。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = if (downloaded) onInstall else onDownload,
                enabled = !isDownloading,
                modifier = Modifier.testTag("updateActionButton"),
            ) {
                if (isDownloading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    when {
                        isDownloading -> "下载中"
                        downloaded -> "安装更新"
                        else -> "下载更新"
                    },
                )
            }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = onOpenRelease,
                    enabled = !isDownloading,
                    modifier = Modifier.testTag("openUpdateReleaseButton"),
                ) { Text("发布页") }
                TextButton(onClick = if (isDownloading) onCancelDownload else onDismiss) {
                    Text(if (isDownloading) "取消下载" else "稍后")
                }
            }
        },
    )
}

@Composable
internal fun DynamicColorSetting(
    enabled: Boolean,
    available: Boolean,
    onChanged: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .clip(MaterialTheme.shapes.medium)
            .toggleable(
                value = enabled,
                enabled = available,
                role = Role.Switch,
                onValueChange = onChanged,
            )
            .padding(horizontal = 4.dp, vertical = 8.dp)
            .testTag("dynamicColorSetting"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text("使用 Material 动态色", style = MaterialTheme.typography.bodyLarge)
            Text(
                if (available) "根据系统壁纸生成应用配色，保存后生效。" else "需要 Android 12 或更高版本。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = null,
            enabled = available,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownField(
    label: String,
    selectedId: String,
    options: List<ModelChoice>,
    onSelected: (String) -> Unit,
    placeholder: String = "请选择",
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.find { it.id == selectedId }?.label ?: selectedId.ifBlank { placeholder }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (options.isNotEmpty()) expanded = !expanded },
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            readOnly = true,
            enabled = options.isNotEmpty(),
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            singleLine = true,
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        onSelected(option.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MealPanelSheet(
    panel: MealPanel,
    isToday: Boolean,
    onDismiss: () -> Unit,
    onNoteChanged: (String) -> Unit,
    onAnalyze: () -> Unit,
    onRetry: () -> Unit,
    onSave: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetMaxWidth = ShikeDimensions.SheetMaxWidth,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
        ) {
            when (panel) {
                MealPanel.Hidden -> Unit
                MealPanel.Preparing -> LoadingContent("正在优化照片…")
                is MealPanel.Preview -> {
                    PreparedImageView(panel.image, "待识别的食物照片")
                    OutlinedTextField(
                        value = panel.note,
                        onValueChange = onNoteChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("补充说明（可选）") },
                        minLines = 2,
                        maxLines = 4,
                        supportingText = { Text("补充份量、价格或品牌等信息，可以提高估算准确度。") },
                    )
                    SheetActions("取消", "开始识别", onDismiss, onAnalyze)
                }
                is MealPanel.Analyzing -> {
                    PreparedImageView(panel.image, "食物照片")
                    LoadingContent("正在识别食物…")
                }
                is MealPanel.Result -> {
                    PreparedImageView(panel.image, "食物照片")
                    if (panel.note.isNotBlank()) {
                        Text(
                            "你的说明：${panel.note}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    panel.result.foods.forEachIndexed { index, food ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(food.name, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${food.portion} · 蛋白 ${formatNumber(food.proteinGrams)}g · 碳水 ${formatNumber(food.carbsGrams)}g · 脂肪 ${formatNumber(food.fatGrams)}g",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Text("${food.calories} 千卡", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                        if (index != panel.result.foods.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("合计", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text("${panel.result.totalCalories} 千卡", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    }
                    Text(
                        buildString {
                            append(panel.result.confidence.label())
                            if (panel.result.notes.isNotBlank()) append(" · ${panel.result.notes}")
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    SheetActions("取消", if (isToday) "记入今日" else "记入当日", onDismiss, onSave)
                }
                is MealPanel.Error -> {
                    panel.image?.let { PreparedImageView(it, "食物照片") }
                    Text(
                        panel.message,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                    if (panel.image != null) SheetActions("关闭", "返回修改", onDismiss, onRetry)
                    else OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("关闭") }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun PreparedImageView(image: PreparedImage, description: String) {
    val bitmap = remember(image.preview) { image.preview.asImageBitmap() }
    Image(
        bitmap = bitmap,
        contentDescription = description,
        modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp).clip(MaterialTheme.shapes.medium),
        contentScale = ContentScale.Crop,
    )
    Spacer(Modifier.height(14.dp))
}

@Composable
private fun LoadingContent(label: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
        Spacer(Modifier.height(12.dp))
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SheetActions(
    secondaryLabel: String,
    primaryLabel: String,
    onSecondary: () -> Unit,
    onPrimary: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedButton(onClick = onSecondary, modifier = Modifier.weight(1f)) { Text(secondaryLabel) }
        Button(onClick = onPrimary, modifier = Modifier.weight(1f)) { Text(primaryLabel) }
    }
}

private fun createCaptureUri(context: Context): Uri {
    val directory = File(context.cacheDir, "images").apply { mkdirs() }
    val file = File.createTempFile("meal_", ".jpg", directory)
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

private fun openReleasePage(context: Context, releaseUrl: String): Boolean {
    val uri = runCatching { Uri.parse(releaseUrl) }.getOrNull() ?: return false
    val trusted = uri.scheme.equals("https", ignoreCase = true) &&
        uri.host.equals("github.com", ignoreCase = true) &&
        uri.path?.startsWith("/McGeeLee/shike/releases/") == true
    if (!trusted) return false

    val intent = Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)
    if (intent.resolveActivity(context.packageManager) == null) return false
    return runCatching { context.startActivity(intent) }.isSuccess
}

private fun canInstallPackages(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

private fun openPackageInstaller(context: Context, apkPath: String): Boolean {
    val updateDirectory = runCatching { File(context.filesDir, "updates").canonicalFile }.getOrNull()
        ?: return false
    val apk = runCatching { File(apkPath).canonicalFile }.getOrNull() ?: return false
    if (apk.parentFile != updateDirectory || !apk.isFile || apk.extension.lowercase() != "apk") return false

    val uri = runCatching {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
    }.getOrNull() ?: return false
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        clipData = ClipData.newRawUri("食刻更新包", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    if (intent.resolveActivity(context.packageManager) == null) return false
    return runCatching { context.startActivity(intent) }.isSuccess
}

private fun dateLabel(date: LocalDate): String {
    val prefix = when (date) {
        LocalDate.now() -> "今天 · "
        LocalDate.now().minusDays(1) -> "昨天 · "
        else -> ""
    }
    return prefix + date.format(DateTimeFormatter.ofPattern("M月d日EEE", simplifiedChinese))
}

private fun formatNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.roundToInt().toString() else "%.1f".format(value)

@Preview(showBackground = true, widthDp = 412, heightDp = 915, locale = "zh-rCN")
@Composable
private fun ShikeHomePreview() {
    ShikeTheme {
        ShikeHomeScreen(
            state = ShikeUiState(),
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
