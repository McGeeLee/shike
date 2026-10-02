package com.gee.eatapp.image

import java.io.File
import java.io.IOException

/** Owns only the temporary source JPEGs created for the system camera. */
internal class CapturedPhotoStore(cacheDirectory: File) {
    private val directory = File(cacheDirectory, "images")

    fun create(): File {
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("无法创建拍照临时文件")
        return File.createTempFile("meal_", ".jpg", directory)
    }

    fun delete(filename: String) {
        if (isCaptureFilename(filename)) File(directory, filename).delete()
    }

    fun cleanStale(keepFilename: String? = null, nowMillis: Long = System.currentTimeMillis()) {
        directory.listFiles()?.forEach { file ->
            if (file.name != keepFilename && isCaptureFilename(file.name) &&
                nowMillis - file.lastModified() >= STALE_AFTER_MS
            ) file.delete()
        }
    }

    companion object {
        private val CAPTURE_NAME = Regex("meal_[A-Za-z0-9_-]+\\.jpg")
        private const val STALE_AFTER_MS = 24 * 60 * 60 * 1000L
        fun isCaptureFilename(filename: String): Boolean = CAPTURE_NAME.matches(filename)
    }
}
