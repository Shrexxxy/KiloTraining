package app.calorietraining

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlin.math.roundToInt

internal fun Context.newVenueCapture(): File =
    File.createTempFile("camera-", ".jpg", File(cacheDir, "venue-camera").apply { mkdirs() })

internal fun Context.saveVenuePhoto(uri: Uri, capture: File?): String {
    val bitmap = decodePhoto {
        capture?.inputStream() ?: contentResolver.openInputStream(uri) ?: throw IOException("Фото недоступно")
    }
    try {
        val target = File.createTempFile("venue-", ".jpg", File(filesDir, "photos").apply { mkdirs() })
        try {
            target.outputStream().use {
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it)) throw IOException("Не удалось записать фото")
            }
            return target.absolutePath
        } catch (e: Exception) {
            target.delete()
            throw e
        }
    } finally {
        bitmap.recycle()
    }
}

private fun photoSize(width: Int, height: Int): Pair<Int, Int> {
    val scale = (1280.0 / maxOf(width, height)).coerceAtMost(1.0)
    return (width * scale).roundToInt().coerceAtLeast(1) to (height * scale).roundToInt().coerceAtLeast(1)
}

private fun decodePhoto(open: () -> InputStream): Bitmap {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    open().use { BitmapFactory.decodeStream(it, null, options) }
    if (options.outWidth <= 0 || options.outHeight <= 0) throw IOException("Не удалось прочитать фото")
    val (width, height) = photoSize(options.outWidth, options.outHeight)
    var sample = 1
    while (maxOf(options.outWidth, options.outHeight) / (sample * 2) >= 1280) sample *= 2
    val orientation = runCatching { open().use {
        ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    } }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    val source = open().use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: throw IOException("Не удалось прочитать фото")
    val scaled = Bitmap.createScaledBitmap(source, width, height, true)
    if (scaled !== source) source.recycle()
    val matrix = android.graphics.Matrix().apply {
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
            ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(270f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { setScale(-1f, 1f); postRotate(270f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { setScale(-1f, 1f); postRotate(90f) }
        }
    }
    return Bitmap.createBitmap(scaled, 0, 0, width, height, matrix, true).also {
        if (it !== scaled) scaled.recycle()
    }
}


/** Предоставляет камере доступ к временному файлу снимка. */
class VenuePhotoProvider : androidx.core.content.FileProvider()
