package com.example.imaging

import android.graphics.Bitmap
import android.graphics.ImageFormat
import androidx.camera.core.ImageProxy
import kotlin.math.max
import kotlin.math.sqrt

enum class FocusPeakingColor(val label: String, val rgb: Int) {
    CRIMSON("Crimson", 0xFF3B5C),
    CYAN_TEAL("Cyan", 0x3FE0D0),
    GOLD("Gold", 0xF5C451);

    fun next(): FocusPeakingColor {
        val all = entries
        return all[(ordinal + 1) % all.size]
    }
}

enum class FocusPeakingSensitivity(val label: String, val sobelThreshold: Float) {
    LOW("Fine", 170f),
    MEDIUM("Balanced", 120f),
    HIGH("Wide", 82f);

    fun next(): FocusPeakingSensitivity {
        val all = entries
        return all[(ordinal + 1) % all.size]
    }
}

/**
 * Stateful real-time 3x3 Sobel edge detector for CameraX ImageAnalysis frames.
 * Supports native YUV_420_888 (reading plane 0 luminance directly without YUV->RGB conversion)
 * as well as RGBA_8888 frames. Designed to run on a single-threaded ImageAnalysis executor
 * with reusable buffers to avoid per-frame GC allocations.
 */
class FocusPeakingAnalyzer {
    private var rowBuffer = ByteArray(0)
    private var lumBuffer = IntArray(0)
    private var outPixels = IntArray(0)
    private var workBitmap: Bitmap? = null

    fun detectSobelEdges(
        proxy: ImageProxy,
        color: FocusPeakingColor,
        sensitivity: FocusPeakingSensitivity,
        mirrorHorizontal: Boolean
    ): Bitmap? {
        if (proxy.planes.isEmpty()) return null
        val plane = proxy.planes[0]
        val srcW = proxy.width
        val srcH = proxy.height
        if (srcW < 6 || srcH < 6) return null

        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val buffer = plane.buffer
        buffer.rewind()
        val bufferLimit = buffer.limit()
        if (rowStride <= 0 || pixelStride <= 0 || bufferLimit <= 0) return null

        val longest = max(srcW, srcH)
        val step = max(1, longest / 320)
        val outW = srcW / step
        val outH = srcH / step
        if (outW < 3 || outH < 3) return null

        val total = outW * outH
        if (lumBuffer.size < total) lumBuffer = IntArray(total)
        if (outPixels.size < total) outPixels = IntArray(total)
        if (rowBuffer.size < rowStride) rowBuffer = ByteArray(rowStride)

        val isYuv = proxy.format == ImageFormat.YUV_420_888

        // 1. Strided luminance extraction
        for (oy in 0 until outH) {
            val sy = oy * step
            val rowStartByte = sy * rowStride
            if (rowStartByte >= bufferLimit) break
            buffer.position(rowStartByte)
            val toRead = minOf(rowStride, buffer.remaining())
            buffer.get(rowBuffer, 0, toRead)

            val outRow = oy * outW
            if (isYuv) {
                for (ox in 0 until outW) {
                    val byteIdx = ox * step * pixelStride
                    lumBuffer[outRow + ox] = if (byteIdx < toRead) {
                        rowBuffer[byteIdx].toInt() and 0xFF
                    } else {
                        0
                    }
                }
            } else {
                for (ox in 0 until outW) {
                    val byteIdx = ox * step * pixelStride
                    if (byteIdx + 2 < toRead) {
                        val r = rowBuffer[byteIdx].toInt() and 0xFF
                        val g = rowBuffer[byteIdx + 1].toInt() and 0xFF
                        val b = rowBuffer[byteIdx + 2].toInt() and 0xFF
                        lumBuffer[outRow + ox] = (77 * r + 150 * g + 29 * b) ushr 8
                    } else {
                        lumBuffer[outRow + ox] = 0
                    }
                }
            }
        }

        // 2. 3x3 Sobel gradient magnitude pass
        val threshold = sensitivity.sobelThreshold
        val thresholdSq = (threshold * threshold).toInt()
        val softSpan = threshold * 1.35f
        val rgbMask = color.rgb and 0x00FFFFFF

        for (x in 0 until outW) {
            outPixels[x] = 0
            outPixels[(outH - 1) * outW + x] = 0
        }
        for (y in 1 until outH - 1) {
            val rowIdx = y * outW
            val prevRow = rowIdx - outW
            val nextRow = rowIdx + outW
            outPixels[rowIdx] = 0
            outPixels[rowIdx + outW - 1] = 0

            for (x in 1 until outW - 1) {
                val tl = lumBuffer[prevRow + x - 1]
                val tc = lumBuffer[prevRow + x]
                val tr = lumBuffer[prevRow + x + 1]
                val ml = lumBuffer[rowIdx + x - 1]
                val mr = lumBuffer[rowIdx + x + 1]
                val bl = lumBuffer[nextRow + x - 1]
                val bc = lumBuffer[nextRow + x]
                val br = lumBuffer[nextRow + x + 1]

                val gx = (-tl + tr) + ((-ml + mr) shl 1) + (-bl + br)
                val gy = (-tl - (tc shl 1) - tr) + (bl + (bc shl 1) + br)
                val magSq = gx * gx + gy * gy

                if (magSq >= thresholdSq) {
                    val mag = sqrt(magSq.toFloat())
                    val norm = ((mag - threshold) / softSpan).coerceIn(0.28f, 1.0f)
                    val alpha = (norm * 240f).toInt().coerceIn(70, 245)
                    outPixels[rowIdx + x] = (alpha shl 24) or rgbMask
                } else {
                    outPixels[rowIdx + x] = 0
                }
            }
        }

        val baseBmp = workBitmap?.takeIf { it.width == outW && it.height == outH && it.isMutable }
            ?: Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888).also { workBitmap = it }
        baseBmp.setPixels(outPixels, 0, outW, 0, 0, outW, outH)

        // 3. Orient tiny peaking bitmap to match CameraX PreviewView display orientation
        val oriented = MediaExportHelper.applyRotationAndFlip(
            bitmap = baseBmp,
            rotationDegrees = proxy.imageInfo.rotationDegrees.toFloat(),
            mirrorHorizontal = mirrorHorizontal
        )
        return if (oriented === baseBmp) {
            oriented.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            oriented
        }
    }
}
