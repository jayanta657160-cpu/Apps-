package com.example.imaging

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlin.math.min

/**
 * 1-Click on-device foreground subject segmentation & background removal using ML Kit Selfie Segmentation.
 * Strictly preserves the subject's exact face, features, hair, clothes, and details.
 */
object BackgroundRemovalEngine {

    private val segmenter by lazy {
        val options = SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
            .build()
        Segmentation.getClient(options)
    }

    suspend fun removeBackground(source: Bitmap): Result<Bitmap> = withContext(Dispatchers.Default) {
        runCatching {
            val safeSource = if (source.config == Bitmap.Config.HARDWARE) {
                source.copy(Bitmap.Config.ARGB_8888, false)
            } else {
                source
            }

            val inputImage = InputImage.fromBitmap(safeSource, 0)
            val mask = suspendCancellableCoroutine { cont ->
                segmenter.process(inputImage)
                    .addOnSuccessListener { result ->
                        if (cont.isActive) cont.resume(result)
                    }
                    .addOnFailureListener { error ->
                        if (cont.isActive) cont.resumeWithException(error)
                    }
            }

            val mw = mask.width
            val mh = mask.height
            val buf = mask.buffer
            buf.rewind()
            val floatBuf = buf.asFloatBuffer()
            val confidences = FloatArray(mw * mh)
            floatBuf.get(confidences)

            val w = safeSource.width
            val h = safeSource.height
            val total = w * h
            val srcPixels = IntArray(total)
            safeSource.getPixels(srcPixels, 0, w, 0, 0, w, h)

            val outPixels = IntArray(total)
            val scaleX = if (w > 1) (mw - 1).toFloat() / (w - 1).toFloat() else 0f
            val scaleY = if (h > 1) (mh - 1).toFloat() / (h - 1).toFloat() else 0f

            var fgPixels = 0
            var bgPixels = 0

            for (y in 0 until h) {
                val fy = (y * scaleY).coerceIn(0f, (mh - 1).toFloat())
                val y0 = fy.toInt()
                val y1 = min(mh - 1, y0 + 1)
                val dy = fy - y0
                val row0 = y0 * mw
                val row1 = y1 * mw
                val dstRow = y * w

                for (x in 0 until w) {
                    val fx = (x * scaleX).coerceIn(0f, (mw - 1).toFloat())
                    val x0 = fx.toInt()
                    val x1 = min(mw - 1, x0 + 1)
                    val dx = fx - x0

                    val c00 = confidences[row0 + x0]
                    val c10 = confidences[row0 + x1]
                    val c01 = confidences[row1 + x0]
                    val c11 = confidences[row1 + x1]

                    val top = c00 + (c10 - c00) * dx
                    val bot = c01 + (c11 - c01) * dx
                    val conf = top + (bot - top) * dy

                    val alphaF = smoothstep(0.28f, 0.72f, conf)
                    val alphaInt = (alphaF * 255f + 0.5f).toInt().coerceIn(0, 255)
                    val idx = dstRow + x
                    if (alphaInt <= 4) {
                        outPixels[idx] = 0
                        bgPixels++
                    } else {
                        if (alphaInt >= 180) fgPixels++
                        val origAlpha = (srcPixels[idx] ushr 24) and 0xFF
                        val finalAlpha = (origAlpha * alphaInt) / 255
                        outPixels[idx] = (finalAlpha shl 24) or (srcPixels[idx] and 0x00FFFFFF)
                    }
                }
            }

            if (fgPixels < max(16, total / 120)) {
                error("No person or portrait subject detected for 1-click background removal.")
            }
            if (bgPixels < max(16, total / 120)) {
                error("Subject fills the entire frame; no background region to remove.")
            }

            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
                setHasAlpha(true)
                setPixels(outPixels, 0, w, 0, 0, w, h)
            }
        }
    }

    private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}
