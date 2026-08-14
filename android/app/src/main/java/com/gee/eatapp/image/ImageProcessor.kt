package com.gee.eatapp.image

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.util.Base64
import androidx.annotation.RequiresApi
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

data class PreparedImage(
    val preview: Bitmap,
    val analysisBase64: String,
    val thumbnailBase64: String,
)

class ImageProcessor(private val resolver: ContentResolver) {
    suspend fun prepare(uri: Uri): PreparedImage = withContext(Dispatchers.IO) {
        val declaredSize = resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
        if (declaredSize > MAX_SOURCE_BYTES) throw IllegalArgumentException("原图不能超过 25 MB")
        val decoded = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            decodeModern(uri)
        } else {
            decodeLegacy(uri)
        }
        val preview = decoded.scaleDown(MAX_ANALYSIS_EDGE)
        if (preview !== decoded) decoded.recycle()
        val analysisBytes = preview.toJpeg(ANALYSIS_QUALITY)
        if (analysisBytes.size > MAX_OUTPUT_BYTES) throw IllegalArgumentException("压缩后的图片不能超过 8 MB")
        val thumbnail = preview.scaleDown(MAX_THUMBNAIL_EDGE)
        val thumbnailBytes = thumbnail.toJpeg(THUMBNAIL_QUALITY)
        if (thumbnail !== preview) thumbnail.recycle()
        PreparedImage(
            preview = preview,
            analysisBase64 = Base64.encodeToString(analysisBytes, Base64.NO_WRAP),
            thumbnailBase64 = Base64.encodeToString(thumbnailBytes, Base64.NO_WRAP),
        )
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun decodeModern(uri: Uri): Bitmap {
        val source = ImageDecoder.createSource(resolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val width = info.size.width
            val height = info.size.height
            val largest = maxOf(width, height)
            if (largest > MAX_DECODE_EDGE) {
                val scale = MAX_DECODE_EDGE.toFloat() / largest
                decoder.setTargetSize(
                    (width * scale).toInt().coerceAtLeast(1),
                    (height * scale).toInt().coerceAtLeast(1),
                )
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }

    private fun decodeLegacy(uri: Uri): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: throw IllegalArgumentException("无法读取这张图片，请换一张重试")
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IllegalArgumentException("无法读取这张图片，请换一张重试")
        }
        var sampleSize = 1
        while (maxOf(bounds.outWidth / sampleSize, bounds.outHeight / sampleSize) > MAX_DECODE_EDGE) {
            sampleSize *= 2
        }
        val bitmap = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sampleSize })
        } ?: throw IllegalArgumentException("无法读取这张图片，请换一张重试")
        val orientation = resolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
        return bitmap.applyOrientation(orientation)
    }

    private fun Bitmap.applyOrientation(orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(-90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            else -> return this
        }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true).also {
            if (it !== this) recycle()
        }
    }

    private fun Bitmap.scaleDown(maxEdge: Int): Bitmap {
        val largest = maxOf(width, height)
        if (largest <= maxEdge) return this
        val scale = maxEdge.toFloat() / largest
        return scale(
            (width * scale).toInt().coerceAtLeast(1),
            (height * scale).toInt().coerceAtLeast(1),
        )
    }

    private fun Bitmap.toJpeg(quality: Int): ByteArray = ByteArrayOutputStream().use { output ->
        if (!compress(Bitmap.CompressFormat.JPEG, quality, output)) {
            throw IllegalArgumentException("当前设备无法处理这张图片")
        }
        output.toByteArray()
    }

    private companion object {
        const val MAX_SOURCE_BYTES = 25L * 1024L * 1024L
        const val MAX_OUTPUT_BYTES = 8 * 1024 * 1024
        const val MAX_DECODE_EDGE = 2048
        const val MAX_ANALYSIS_EDGE = 1280
        const val MAX_THUMBNAIL_EDGE = 120
        const val ANALYSIS_QUALITY = 85
        const val THUMBNAIL_QUALITY = 70
    }
}
