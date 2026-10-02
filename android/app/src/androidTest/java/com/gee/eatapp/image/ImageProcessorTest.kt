package com.gee.eatapp.image

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ImageProcessorTest {
    @Test fun legacyDecoderReadsValidJpegAfterBoundsOnlyPass() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File.createTempFile("legacy_decode_", ".jpg", context.cacheDir)
        val source = Bitmap.createBitmap(80, 60, Bitmap.Config.ARGB_8888)
        try {
            file.outputStream().use { assertTrue(source.compress(Bitmap.CompressFormat.JPEG, 90, it)) }
            val decoded = ImageProcessor(context.contentResolver).decodeLegacy(Uri.fromFile(file))
            try {
                assertEquals(80, decoded.width)
                assertEquals(60, decoded.height)
            } finally { decoded.recycle() }
        } finally {
            source.recycle()
            file.delete()
        }
    }

    @Test fun legacyDecoderRejectsNonImageWithoutAllocatingABitmap() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File.createTempFile("invalid_decode_", ".jpg", context.cacheDir)
        try {
            file.writeText("this is not an image")
            assertThrows(IllegalArgumentException::class.java) {
                ImageProcessor(context.contentResolver).decodeLegacy(Uri.fromFile(file))
            }
        } finally { file.delete() }
    }
}
