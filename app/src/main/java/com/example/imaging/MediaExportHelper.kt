package com.example.imaging

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.DrawableRes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object MediaExportHelper {

    suspend fun loadBitmapFromUri(
        context: Context,
        uri: Uri,
        maxEdgePx: Int = 2048
    ): Result<Bitmap> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOpts)
            } ?: error("Unable to open image stream.")

            var inSample = 1
            val longest = maxOf(boundsOpts.outWidth, boundsOpts.outHeight)
            while (longest / inSample > maxEdgePx) {
                inSample *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = inSample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            } ?: error("Unable to decode image.")

            val orientation = resolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL

            applyExifOrientation(decoded, orientation)
        }
    }

    suspend fun loadBitmapFromDrawable(
        context: Context,
        @DrawableRes resId: Int,
        maxEdgePx: Int = 1600
    ): Result<Bitmap> = withContext(Dispatchers.IO) {
        runCatching {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeResource(context.resources, resId, boundsOpts)
            var inSample = 1
            val longest = maxOf(boundsOpts.outWidth, boundsOpts.outHeight)
            while (longest / inSample > maxEdgePx) {
                inSample *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = inSample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeResource(context.resources, resId, decodeOpts)
                ?: error("Unable to load reference image.")
        }
    }

    suspend fun saveBitmapToGallery(
        context: Context,
        bitmap: Bitmap,
        prefix: String = "JDCam"
    ): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val safeBitmap = if (bitmap.config == Bitmap.Config.HARDWARE) {
                bitmap.copy(Bitmap.Config.ARGB_8888, false)
            } else {
                bitmap
            }
            val exportAsPng = hasTransparentPixels(safeBitmap)
            val nowMillis = System.currentTimeMillis()
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date(nowMillis))
            val ext = if (exportAsPng) "png" else "jpg"
            val mime = if (exportAsPng) "image/png" else "image/jpeg"
            val filename = "${prefix}_$timestamp.$ext"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, mime)
                put(MediaStore.Images.Media.DATE_TAKEN, nowMillis)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        "${Environment.DIRECTORY_PICTURES}/JDCam"
                    )
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(collection, values)
                ?: error("Could not create MediaStore entry.")

            try {
                resolver.openOutputStream(uri)?.use { out ->
                    val format = if (exportAsPng) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                    val quality = if (exportAsPng) 100 else 96
                    if (!safeBitmap.compress(format, quality, out)) {
                        error("Image compression failed.")
                    }
                } ?: error("Could not open output stream for MediaStore URI.")

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val done = ContentValues().apply {
                        put(MediaStore.Images.Media.IS_PENDING, 0)
                    }
                    resolver.update(uri, done, null, null)
                }
                uri
            } catch (t: Throwable) {
                resolver.delete(uri, null, null)
                throw t
            }
        }
    }

    internal fun hasTransparentPixels(bitmap: Bitmap): Boolean {
        if (!bitmap.hasAlpha()) return false
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return false
        val stepX = maxOf(1, w / 32)
        val stepY = maxOf(1, h / 32)
        val row = IntArray(w)
        var y = 0
        while (y < h) {
            bitmap.getPixels(row, 0, w, 0, y, w, 1)
            var x = 0
            while (x < w) {
                if (((row[x] ushr 24) and 0xFF) < 250) return true
                x += stepX
            }
            y += stepY
        }
        return false
    }

    fun applyRotationAndFlip(
        bitmap: Bitmap,
        rotationDegrees: Float,
        mirrorHorizontal: Boolean = false
    ): Bitmap {
        if (rotationDegrees == 0f && !mirrorHorizontal) return bitmap
        val matrix = Matrix().apply {
            if (rotationDegrees != 0f) postRotate(rotationDegrees)
            if (mirrorHorizontal) postScale(-1f, 1f)
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
