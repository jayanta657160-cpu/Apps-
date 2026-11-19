package com.example.imaging

import android.graphics.Bitmap
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

data class CleanupStroke(
    val points: List<Pair<Float, Float>>,
    val radiusPxRatio: Float
)

/**
 * Deterministic, non-generative local object-cleanup engine.
 * Only heals pixels explicitly brushed by the user using surrounding boundary diffusion;
 * unbrushed pixels (faces, subjects, clothing, composition) remain bit-for-bit untouched.
 */
object ObjectCleanupEngine {

    fun removeMaskedObject(
        source: Bitmap,
        strokes: List<CleanupStroke>
    ): Bitmap {
        if (strokes.isEmpty()) return source
        val w = source.width
        val h = source.height
        if (w <= 4 || h <= 4) return source

        val minDim = min(w, h).toFloat()
        val mask = BooleanArray(w * h)
        var minX = w
        var minY = h
        var maxX = -1
        var maxY = -1

        for (stroke in strokes) {
            if (stroke.points.isEmpty()) continue
            val rPx = (stroke.radiusPxRatio * minDim).coerceIn(4f, minDim * 0.25f)
            val rSq = rPx * rPx

            fun stampDisk(cx: Float, cy: Float) {
                val x0 = max(1, floor(cx - rPx).toInt())
                val x1 = min(w - 2, ceil(cx + rPx).toInt())
                val y0 = max(1, floor(cy - rPx).toInt())
                val y1 = min(h - 2, ceil(cy + rPx).toInt())
                for (y in y0..y1) {
                    val dy = y - cy
                    val row = y * w
                    for (x in x0..x1) {
                        val dx = x - cx
                        if (dx * dx + dy * dy <= rSq) {
                            mask[row + x] = true
                            if (x < minX) minX = x
                            if (x > maxX) maxX = x
                            if (y < minY) minY = y
                            if (y > maxY) maxY = y
                        }
                    }
                }
            }

            if (stroke.points.size == 1) {
                val p = stroke.points[0]
                stampDisk(p.first * w, p.second * h)
            } else {
                for (i in 0 until stroke.points.size - 1) {
                    val a = stroke.points[i]
                    val b = stroke.points[i + 1]
                    val ax = a.first * w
                    val ay = a.second * h
                    val bx = b.first * w
                    val by = b.second * h
                    val dist = hypot(bx - ax, by - ay)
                    val steps = max(1, ceil(dist / max(2f, rPx * 0.35f)).toInt())
                    for (s in 0..steps) {
                        val t = s.toFloat() / steps
                        stampDisk(ax + (bx - ax) * t, ay + (by - ay) * t)
                    }
                }
            }
        }

        if (maxX < minX || maxY < minY) return source

        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)

        val pad = 4
        val bx0 = max(1, minX - pad)
        val by0 = max(1, minY - pad)
        val bx1 = min(w - 2, maxX + pad)
        val by1 = min(h - 2, maxY + pad)

        val known = BooleanArray(w * h)
        for (i in mask.indices) {
            known[i] = !mask[i]
        }

        val rChan = FloatArray(w * h)
        val gChan = FloatArray(w * h)
        val bChan = FloatArray(w * h)
        for (y in by0..by1) {
            val row = y * w
            for (x in bx0..bx1) {
                val idx = row + x
                val px = pixels[idx]
                rChan[idx] = ((px ushr 16) and 0xFF).toFloat()
                gChan[idx] = ((px ushr 8) and 0xFF).toFloat()
                bChan[idx] = (px and 0xFF).toFloat()
            }
        }

        // 1. Inward onion-peel fill from known surrounding boundary pixels
        val frontier = IntArray((bx1 - bx0 + 1) * (by1 - by0 + 1))
        val dx8 = intArrayOf(-1, 0, 1, -1, 1, -1, 0, 1)
        val dy8 = intArrayOf(-1, -1, -1, 0, 0, 1, 1, 1)
        val w8 = floatArrayOf(0.707f, 1f, 0.707f, 1f, 1f, 0.707f, 1f, 0.707f)

        var safety = 0
        while (safety++ < 512) {
            var frontierCount = 0
            for (y in minY..maxY) {
                val row = y * w
                for (x in minX..maxX) {
                    val idx = row + x
                    if (known[idx]) continue
                    var hasKnownNeighbor = false
                    for (k in 0 until 8) {
                        val nIdx = (y + dy8[k]) * w + (x + dx8[k])
                        if (known[nIdx]) {
                            hasKnownNeighbor = true
                            break
                        }
                    }
                    if (hasKnownNeighbor) {
                        frontier[frontierCount++] = idx
                    }
                }
            }
            if (frontierCount == 0) break

            for (f in 0 until frontierCount) {
                val idx = frontier[f]
                val x = idx % w
                val y = idx / w
                var sumR = 0f
                var sumG = 0f
                var sumB = 0f
                var sumW = 0f
                for (k in 0 until 8) {
                    val nIdx = (y + dy8[k]) * w + (x + dx8[k])
                    if (known[nIdx]) {
                        val wt = w8[k]
                        sumR += rChan[nIdx] * wt
                        sumG += gChan[nIdx] * wt
                        sumB += bChan[nIdx] * wt
                        sumW += wt
                    }
                }
                if (sumW > 0f) {
                    rChan[idx] = sumR / sumW
                    gChan[idx] = sumG / sumW
                    bChan[idx] = sumB / sumW
                }
            }
            for (f in 0 until frontierCount) {
                known[frontier[f]] = true
            }
        }

        // 2. Multi-pass harmonic diffusion inside masked region for smooth tonal continuity
        repeat(16) {
            for (y in minY..maxY) {
                val row = y * w
                for (x in minX..maxX) {
                    val idx = row + x
                    if (!mask[idx]) continue
                    val top = idx - w
                    val bot = idx + w
                    val left = idx - 1
                    val right = idx + 1
                    rChan[idx] = (rChan[top] + rChan[bot] + rChan[left] + rChan[right]) * 0.25f
                    gChan[idx] = (gChan[top] + gChan[bot] + gChan[left] + gChan[right]) * 0.25f
                    bChan[idx] = (bChan[top] + bChan[bot] + bChan[left] + bChan[right]) * 0.25f
                }
            }
        }

        for (y in minY..maxY) {
            val row = y * w
            for (x in minX..maxX) {
                val idx = row + x
                if (!mask[idx]) continue
                val a = pixels[idx] and 0xFF000000.toInt()
                val r = rChan[idx].toInt().coerceIn(0, 255)
                val g = gChan[idx].toInt().coerceIn(0, 255)
                val b = bChan[idx].toInt().coerceIn(0, 255)
                pixels[idx] = a or (r shl 16) or (g shl 8) or b
            }
        }

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }
}
