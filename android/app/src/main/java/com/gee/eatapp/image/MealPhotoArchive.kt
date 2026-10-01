package com.gee.eatapp.image

import com.gee.eatapp.data.MealEntry
import java.io.File
import java.io.OutputStream
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Clear photos live outside preferences; legacy records can still export their thumbnail. */
class MealPhotoArchive(
    filesDir: File,
    private val decodeThumbnail: (String) -> ByteArray = {
        android.util.Base64.decode(it, android.util.Base64.DEFAULT)
    },
) {
    private val directory = File(filesDir, "meal_photos")

    fun save(id: String, jpeg: ByteArray): String {
        require(id.matches(Regex("[A-Za-z0-9-]{1,80}")))
        require(jpeg.isNotEmpty() && jpeg.size <= 8 * 1024 * 1024)
        check(directory.isDirectory || directory.mkdirs()) { "无法创建照片目录" }
        val name = "$id.jpg"
        val temporary = File(directory, "$name.tmp")
        try {
            temporary.outputStream().use { it.write(jpeg); it.fd.sync() }
            check(temporary.renameTo(File(directory, name))) { "无法保存食物照片" }
        } finally {
            temporary.delete()
        }
        return name
    }

    fun delete(name: String) { file(name)?.delete() }

    fun bytes(entry: MealEntry): ByteArray {
        val photo = file(entry.photoFile)
        if (photo?.isFile == true) return photo.readBytes()
        return runCatching { decodeThumbnail(entry.thumbnailBase64) }
            .getOrNull()?.takeIf { it.isNotEmpty() }
            ?: throw IllegalStateException("这条记录没有可用照片")
    }

    fun exportDay(date: LocalDate, entries: List<MealEntry>, output: OutputStream) {
        require(entries.isNotEmpty()) { "这一天没有照片" }
        ZipOutputStream(output).use { zip ->
            entries.forEachIndexed { index, entry ->
                val time = entry.time.replace(Regex("[^0-9-]"), "-").take(30)
                zip.putNextEntry(ZipEntry("$date/${index + 1}-$time.jpg"))
                zip.write(bytes(entry))
                zip.closeEntry()
            }
            zip.putNextEntry(ZipEntry("$date/records.csv"))
            val csv = buildString {
                append("\uFEFF日期,时间,食物,热量(kcal),蛋白质(g),碳水(g),脂肪(g),备注,估算说明,模型\n")
                entries.forEach { entry ->
                    append(listOf(date, entry.time, entry.name, entry.calories, entry.proteinGrams,
                        entry.carbsGrams, entry.fatGrams, entry.note, entry.analysisNotes, entry.modelLabel)
                        .joinToString(",") { csvCell(it.toString()) })
                    append('\n')
                }
            }
            zip.write(csv.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
    }

    private fun file(name: String): File? =
        name.takeIf { it.matches(Regex("[A-Za-z0-9-]{1,80}\\.jpg")) }?.let { File(directory, it) }

    private fun csvCell(value: String): String {
        val safe = if (value.trimStart().startsWithAnyFormula()) "'$value" else value
        return "\"${safe.replace("\"", "\"\"")}\""
    }

    private fun String.startsWithAnyFormula() = firstOrNull() in listOf('=', '+', '-', '@', '\t', '\r')
}
