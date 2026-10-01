package com.gee.eatapp.image

import com.gee.eatapp.data.MealEntry
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.Base64
import java.util.zip.ZipInputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MealPhotoArchiveTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun entry(photo: String = "", thumbnail: String = "") = MealEntry(
        "meal-1", "=SUM(1,2)\n米饭", 300, 10.0, 50.0, 8.0, "12:30", "备注,含\"引号\"", thumbnail,
        photoFile = photo, analysisNotes = "约150克", modelLabel = "视觉模型",
    )

    @Test fun savesClearPhotoAndExportsDatedZipWithEscapedNutrition() {
        val archive = MealPhotoArchive(temporary.root)
        val bytes = byteArrayOf(1, 2, 3, 4)
        val file = archive.save("meal-1", bytes)
        val entry = entry(file)
        assertArrayEquals(bytes, archive.bytes(entry))
        val output = ByteArrayOutputStream()
        archive.exportDay(LocalDate.of(2026, 10, 1), listOf(entry), output)
        val files = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(output.toByteArray())).use { zip ->
            while (true) {
                val item = zip.nextEntry ?: break
                files[item.name] = zip.readBytes()
            }
        }
        assertArrayEquals(bytes, files["2026-10-01/1-12-30.jpg"])
        val csv = String(files.getValue("2026-10-01/records.csv"), Charsets.UTF_8)
        assertTrue(csv.startsWith("\uFEFF日期"))
        assertTrue(csv.contains("\"'=SUM(1,2)\n米饭\""))
        assertTrue(csv.contains("\"备注,含\"\"引号\"\"\""))
    }

    @Test fun legacyThumbnailRemainsDownloadable() {
        val bytes = byteArrayOf(5, 6, 7)
        assertArrayEquals(bytes, MealPhotoArchive(temporary.root).bytes(entry(thumbnail = Base64.getEncoder().encodeToString(bytes))))
    }

    @Test fun deletionRemovesPhotoAndFallsBackToLegacyThumbnail() {
        val archive = MealPhotoArchive(temporary.root)
        val file = archive.save("meal-1", byteArrayOf(1))
        archive.delete(file)
        assertArrayEquals(byteArrayOf(2), archive.bytes(entry(file, "Ag==")))
        assertFalse(temporary.root.resolve("meal_photos/$file.tmp").exists())
    }

    @Test fun rejectsPathTraversalAndMissingPhotos() {
        val archive = MealPhotoArchive(temporary.root)
        assertThrows(IllegalArgumentException::class.java) { archive.save("../escape", byteArrayOf(1)) }
        assertThrows(IllegalStateException::class.java) { archive.bytes(entry("../escape.jpg")) }
        assertThrows(IllegalArgumentException::class.java) { archive.exportDay(LocalDate.now(), emptyList(), ByteArrayOutputStream()) }
    }
}
