package com.gee.eatapp.ui

import android.app.DatePickerDialog
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.gee.eatapp.data.MealEntry
import com.gee.eatapp.data.ShikeRepository
import com.gee.eatapp.image.MealPhotoArchive
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun DateJumpButton(date: LocalDate, onSelectDate: (LocalDate) -> Unit) {
    val context = LocalContext.current
    TextButton(onClick = {
        DatePickerDialog(context, { _, year, month, day ->
            onSelectDate(LocalDate.of(year, month + 1, day))
        }, date.year, date.monthValue - 1, date.dayOfMonth).apply {
            datePicker.maxDate = System.currentTimeMillis()
            show()
        }
    }) { Text("选择日期 · 查看历史照片") }
}

@Composable
internal fun MealPhotoGallery(date: LocalDate, entries: List<MealEntry>) {
    val context = LocalContext.current
    val archive = remember(context) { MealPhotoArchive(context.filesDir) }
    val scope = rememberCoroutineScope()
    var previewId by rememberSaveable(date.toString()) { mutableStateOf<String?>(null) }
    var exportDate by rememberSaveable { mutableStateOf(date.toString()) }
    var exportId by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    val singleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")) { uri ->
        if (uri != null) {
            val requestedDate = exportDate
            val requestedId = exportId
            busy = true
            scope.launch {
                message = try {
                    withContext(Dispatchers.IO) {
                        val entry = ShikeRepository(context).entries(LocalDate.parse(requestedDate))
                            .firstOrNull { it.id == requestedId } ?: error("记录已被删除")
                        val bytes = archive.bytes(entry)
                        context.contentResolver.openOutputStream(uri, "w")?.use { it.write(bytes) }
                            ?: error("无法写入所选位置")
                    }
                    "照片已下载到所选位置"
                } catch (error: Exception) {
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    "下载失败：${error.message ?: "请重试"}"
                } finally { busy = false }
            }
        }
    }
    val dayLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            val requestedDate = LocalDate.parse(exportDate)
            busy = true
            scope.launch {
                message = try {
                    withContext(Dispatchers.IO) {
                        val records = ShikeRepository(context).entries(requestedDate)
                        context.contentResolver.openOutputStream(uri, "w")?.use {
                            archive.exportDay(requestedDate, records, it)
                        } ?: error("无法写入所选位置")
                    }
                    "已导出 $requestedDate 的照片与营养记录"
                } catch (error: Exception) {
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    "导出失败：${error.message ?: "请重试"}"
                } finally { busy = false }
            }
        }
    }
    if (entries.isNotEmpty()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("食物相册 · ${entries.size} 张", modifier = Modifier.weight(1f))
            TextButton(enabled = !busy, onClick = {
                exportDate = date.toString()
                dayLauncher.launch("shike-$date.zip")
            }) { Text("下载当日图片") }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            entries.forEach { entry ->
                val thumbnail = remember(entry.thumbnailBase64) {
                    runCatching {
                        val bytes = Base64.decode(entry.thumbnailBase64, Base64.NO_WRAP)
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                    }.getOrNull()
                }
                Column(Modifier.width(104.dp).clickable { previewId = entry.id }) {
                    if (thumbnail != null) {
                        Image(thumbnail, "查看${entry.name}的照片", Modifier.size(104.dp).clip(MaterialTheme.shapes.medium), contentScale = ContentScale.Crop)
                    } else {
                        Surface(Modifier.size(104.dp), shape = MaterialTheme.shapes.medium) {
                            Box(contentAlignment = Alignment.Center) { Text("查看照片") }
                        }
                    }
                    Text("${entry.time} · ${entry.calories} 千卡", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall)
    val preview = entries.firstOrNull { it.id == previewId }
    if (preview != null) {
        val bitmap by produceState<ImageBitmap?>(null, preview.id) {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = archive.bytes(preview)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                }.getOrNull()
            }
        }
        Dialog(onDismissRequest = { previewId = null }) {
            Surface(shape = MaterialTheme.shapes.extraLarge) {
                Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(preview.name, style = MaterialTheme.typography.titleLarge)
                    Text("$date · ${preview.time} · ${preview.calories} 千卡")
                    bitmap?.let { Image(it, preview.name, Modifier.fillMaxWidth().heightIn(max = 360.dp), contentScale = ContentScale.Fit) }
                        ?: Text("正在读取照片，或照片已不可用")
                    if (preview.photoFile.isBlank()) Text("旧记录仅保留缩略图，下载清晰度有限。", style = MaterialTheme.typography.bodySmall)
                    Text("蛋白质 ${preview.proteinGrams}g · 碳水 ${preview.carbsGrams}g · 脂肪 ${preview.fatGrams}g")
                    if (preview.note.isNotBlank()) Text(preview.note)
                    if (preview.analysisNotes.isNotBlank()) Text(preview.analysisNotes)
                    if (preview.modelLabel.isNotBlank()) Text(preview.modelLabel, style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { previewId = null }) { Text("关闭") }
                        Button(enabled = !busy && bitmap != null, onClick = {
                            exportDate = date.toString()
                            exportId = preview.id
                            singleLauncher.launch("shike-$date-${preview.time.replace(':', '-')}.jpg")
                        }) { Text("下载照片") }
                    }
                }
            }
        }
    }
}
