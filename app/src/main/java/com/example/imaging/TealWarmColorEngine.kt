package com.example.imaging

import android.graphics.Bitmap
import com.example.model.GradingRecipe
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

data class LightingProfile(
    val detectedScene: String = "Balanced Daylight",
    val meanLuminance: Float = 0.48f,
    val shadowP10: Float = 0.12f,
    val highlightP95: Float = 0.88f,
    val wbRedGain: Float = 1.0f,
    val wbGreenGain: Float = 1.0f,
    val wbBlueGain: Float = 1.0f,
    val autoExposureGain: Float = 1.0f
)

/**
 * Deterministic, non-generative Teal & Warm DSLR color grading engine.
 * Strictly preserves spatial geometry, faces, skin texture, clothing, and details.
 * Only adjusts color, white balance, exposure, contrast, highlights, shadows, vibrance, and saturation.
 */
object TealWarmColorEngine {
    const val LUT_SIZE = 33
    private const val LUT_MAX = LUT_SIZE - 1

    fun analyzeLighting(bitmap: Bitmap): LightingProfile {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return LightingProfile()

        val stepX = max(1, width / 96)
        val stepY = max(1, height / 96)
        val hist = IntArray(256)
        val rowBuffer = IntArray(width)

        var sampleCount = 0
        var sumR = 0.0
        var sumG = 0.0
        var sumB = 0.0
        var sumLum = 0.0

        var y = 0
        while (y < height) {
            bitmap.getPixels(rowBuffer, 0, width, 0, y, width, 1)
            var x = 0
            while (x < width) {
                val px = rowBuffer[x]
                if ((px ushr 24) >= 128) {
                    val r = (px ushr 16) and 0xFF
                    val g = (px ushr 8) and 0xFF
                    val b = px and 0xFF
                    val lumInt = ((77 * r + 150 * g + 29 * b) shr 8).coerceIn(0, 255)
                    hist[lumInt]++
                    sumR += r
                    sumG += g
                    sumB += b
                    sumLum += lumInt / 255.0
                    sampleCount++
                }
                x += stepX
            }
            y += stepY
        }

        if (sampleCount == 0) return LightingProfile()

        val meanLum = (sumLum / sampleCount).toFloat().coerceIn(0.05f, 0.95f)
        val p10Target = (sampleCount * 0.10f).toInt()
        val p95Target = (sampleCount * 0.95f).toInt()
        var acc = 0
        var p10 = 25
        var p95 = 230
        var foundP10 = false
        for (i in 0..255) {
            acc += hist[i]
            if (!foundP10 && acc >= p10Target) {
                p10 = i
                foundP10 = true
            }
            if (acc >= p95Target) {
                p95 = i
                break
            }
        }

        val avgR = (sumR / sampleCount).toFloat().coerceAtLeast(1f)
        val avgG = (sumG / sampleCount).toFloat().coerceAtLeast(1f)
        val avgB = (sumB / sampleCount).toFloat().coerceAtLeast(1f)
        val avgGray = (avgR + avgG + avgB) / 3f

        // Gentle adaptive white-balance correction (35% strength) to clean muddy casts while keeping natural ambience
        val wbR = (1f + ((avgGray / avgR) - 1f) * 0.35f).coerceIn(0.86f, 1.16f)
        val wbG = (1f + ((avgGray / avgG) - 1f) * 0.25f).coerceIn(0.90f, 1.10f)
        val wbB = (1f + ((avgGray / avgB) - 1f) * 0.35f).coerceIn(0.86f, 1.16f)

        // Adaptive exposure: lift dim frames gently, tame clipped sunlit frames slightly
        val targetMid = 0.47f
        val exposureRatio = (targetMid / meanLum).coerceIn(0.80f, 1.32f)
        val autoExposure = 1f + (exposureRatio - 1f) * 0.45f

        val shadowNorm = p10 / 255f
        val highlightNorm = p95 / 255f
        val warmthBias = (avgR - avgB) / 255f

        val scene = when {
            meanLum < 0.32f -> "Twilight / Reception"
            highlightNorm > 0.88f && warmthBias > 0.04f -> "Golden Hour"
            meanLum > 0.62f -> "Bright Daylight"
            warmthBias > 0.05f -> "Warm Indoor"
            else -> "Lush Shade"
        }

        return LightingProfile(
            detectedScene = scene,
            meanLuminance = meanLum,
            shadowP10 = shadowNorm,
            highlightP95 = highlightNorm,
            wbRedGain = wbR,
            wbGreenGain = wbG,
            wbBlueGain = wbB,
            autoExposureGain = autoExposure
        )
    }

    /**
     * Generates a 33x33x33 3D LUT encapsulating adaptive WB, exposure, detailed black toe,
     * soft highlight shoulder, skin-tone protection, deep foliage greens, teal shadows,
     * warm golden highlights, and skin-safe vibrance.
     */
    fun buildLut33(recipe: GradingRecipe, lighting: LightingProfile): IntArray {
        val lut = IntArray(LUT_SIZE * LUT_SIZE * LUT_SIZE)
        val expGain = (2.0.pow(recipe.exposure.toDouble()).toFloat()) *
            (if (recipe.adaptiveLighting) lighting.autoExposureGain else 1.0f)

        val wbR = if (recipe.adaptiveLighting) lighting.wbRedGain else 1.0f
        val wbG = if (recipe.adaptiveLighting) lighting.wbGreenGain else 1.0f
        val wbB = if (recipe.adaptiveLighting) lighting.wbBlueGain else 1.0f

        // Adapt split-toning & contrast subtly based on scene dynamic range and luminance so dark scenes
        // stay detailed, flat shade gains depth, and high-key bridal whites stay clean.
        val rangeSpan = (lighting.highlightP95 - lighting.shadowP10).coerceIn(0.25f, 0.95f)
        val rangeContrastScale = if (recipe.adaptiveLighting) {
            (0.72f / rangeSpan).coerceIn(0.86f, 1.18f)
        } else {
            1.0f
        }
        val sceneShadowScale = if (recipe.adaptiveLighting && lighting.shadowP10 < 0.09f) 0.85f else 1.0f
        val sceneHighlightScale = if (recipe.adaptiveLighting && lighting.highlightP95 > 0.90f) 0.82f else 1.0f

        val tealStrength = recipe.tealShadows * 0.16f * sceneShadowScale
        val warmStrength = recipe.warmHighlights * 0.18f * sceneHighlightScale
        val greenStrength = recipe.deepGreens
        val contrastAmount = (recipe.contrast * 0.42f * rangeContrastScale).coerceIn(0.05f, 0.52f)
        val blackLift = 0.014f + (1f - recipe.contrast) * 0.010f
        val shoulderStart = 0.78f - recipe.highlightSoftness * 0.12f

        var idx = 0
        for (bi in 0 until LUT_SIZE) {
            val b0 = bi.toFloat() / LUT_MAX
            for (gi in 0 until LUT_SIZE) {
                val g0 = gi.toFloat() / LUT_MAX
                for (ri in 0 until LUT_SIZE) {
                    val r0 = ri.toFloat() / LUT_MAX

                    // 1. White balance + exposure with soft highlight rolloff before clipping
                    var r = softClip(r0 * wbR * expGain, shoulderStart)
                    var g = softClip(g0 * wbG * expGain, shoulderStart)
                    var b = softClip(b0 * wbB * expGain, shoulderStart)

                    // 2. Detailed blacks (toe lift) + cinematic S-curve contrast
                    r = applyToneCurve(r, blackLift, contrastAmount)
                    g = applyToneCurve(g, blackLift, contrastAmount)
                    b = applyToneCurve(b, blackLift, contrastAmount)

                    val lum = (0.299f * r + 0.587f * g + 0.114f * b).coerceIn(0f, 1f)

                    // 3. Hue & saturation classification for skin protection and deep foliage greens
                    val maxC = max(r, max(g, b))
                    val minC = min(r, min(g, b))
                    val delta = maxC - minC
                    val sat = if (maxC > 1e-4f) delta / maxC else 0f
                    val hueDeg = computeHueDegrees(r, g, b, maxC, delta)

                    // Smooth skin-tone mask: warm peach/tan/brown hues (6°..46°), moderate saturation
                    val skinHueWeight = smoothBand(hueDeg, center = 25f, halfWidth = 22f)
                    val skinSatWeight = smoothstep(0.06f, 0.22f, sat) * (1f - smoothstep(0.62f, 0.82f, sat))
                    val skinLumWeight = smoothstep(0.12f, 0.28f, lum)
                    val skinMask = (skinHueWeight * skinSatWeight * skinLumWeight * recipe.skinProtection)
                        .coerceIn(0f, 1f)

                    // Smooth foliage green mask (70°..168°)
                    val greenHueWeight = smoothBand(hueDeg, center = 118f, halfWidth = 48f)
                    val greenSatWeight = smoothstep(0.08f, 0.25f, sat)
                    val greenMask = (greenHueWeight * greenSatWeight * (1f - skinMask)).coerceIn(0f, 1f)

                    // 4. Deepen & enrich greens (wedding foliage: rich emerald instead of neon yellow-green)
                    if (greenMask > 0.001f && greenStrength > 0.001f) {
                        val gDepth = greenMask * greenStrength
                        r *= (1f - 0.17f * gDepth)
                        g *= (1f - 0.05f * gDepth)
                        b += 0.045f * gDepth * lum
                    }

                    // 5. Luminance-weighted Teal Shadows & Warm Golden Highlights
                    // Keep deep blacks detailed and neutral bright whites crisp & clean
                    val blackSafety = smoothstep(0.02f, 0.12f, lum)
                    val shadowWeight = smoothstep(0.46f, 0.12f, lum) * blackSafety * (1f - 0.82f * skinMask)

                    val neutralBright = smoothstep(0.74f, 0.90f, minC) * (1f - smoothstep(0.04f, 0.24f, sat))
                    val whiteClean = (1f - smoothstep(0.84f, 0.98f, minC)) * (1f - 0.85f * neutralBright)
                    val highlightWeight = smoothstep(0.42f, 0.84f, lum) * whiteClean * (1f - 0.48f * skinMask)

                    if (shadowWeight > 0.001f) {
                        val t = tealStrength * shadowWeight
                        r -= t * 0.72f
                        g += t * 0.18f
                        b += t * 0.68f
                    }
                    if (highlightWeight > 0.001f) {
                        val w = warmStrength * highlightWeight
                        r += w * 0.78f
                        g += w * 0.42f
                        b -= w * 0.72f
                    }

                    // 6. Skin-safe Vibrance with oversaturation guard for high-chroma pixels
                    val lumAfterTone = (0.299f * r + 0.587f * g + 0.114f * b).coerceIn(0f, 1f)
                    val vibFactor = recipe.vibrance * 0.52f * (1f - sat) * (1f - 0.70f * skinMask) *
                        (1f - smoothstep(0.82f, 0.96f, minC))
                    val overSatGuard = if (sat > 0.76f) 1f - smoothstep(0.76f, 1.0f, sat) * 0.18f else 1f
                    val chromaScale = (1f + vibFactor) * overSatGuard
                    if (chromaScale != 1f) {
                        r = lumAfterTone + (r - lumAfterTone) * chromaScale
                        g = lumAfterTone + (g - lumAfterTone) * chromaScale
                        b = lumAfterTone + (b - lumAfterTone) * chromaScale
                    }

                    val rOut = (r.coerceIn(0f, 1f) * 255f + 0.5f).toInt().coerceIn(0, 255)
                    val gOut = (g.coerceIn(0f, 1f) * 255f + 0.5f).toInt().coerceIn(0, 255)
                    val bOut = (b.coerceIn(0f, 1f) * 255f + 0.5f).toInt().coerceIn(0, 255)
                    lut[idx++] = (0xFF shl 24) or (rOut shl 16) or (gOut shl 8) or bOut
                }
            }
        }
        return lut
    }

    /**
     * Applies a 33x33x33 3D LUT with trilinear interpolation to [source].
     * Strictly preserves spatial pixel coordinates and original fine detail.
     */
    fun applyLut(source: Bitmap, lut: IntArray, reusableOutput: Bitmap? = null): Bitmap {
        val width = source.width
        val height = source.height
        val out = if (reusableOutput != null &&
            reusableOutput.width == width &&
            reusableOutput.height == height &&
            reusableOutput.isMutable
        ) {
            reusableOutput
        } else {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        }

        val total = width * height
        val pixels = IntArray(total)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        val scale = LUT_MAX / 255f
        val strideG = LUT_SIZE
        val strideB = LUT_SIZE * LUT_SIZE

        for (i in 0 until total) {
            val px = pixels[i]
            val a = px and 0xFF000000.toInt()
            val rf = ((px ushr 16) and 0xFF) * scale
            val gf = ((px ushr 8) and 0xFF) * scale
            val bf = (px and 0xFF) * scale

            val r0 = rf.toInt().coerceIn(0, LUT_MAX - 1)
            val g0 = gf.toInt().coerceIn(0, LUT_MAX - 1)
            val b0 = bf.toInt().coerceIn(0, LUT_MAX - 1)

            val dr = rf - r0
            val dg = gf - g0
            val db = bf - b0

            val base = b0 * strideB + g0 * strideG + r0
            val c000 = lut[base]
            val c100 = lut[base + 1]
            val c010 = lut[base + strideG]
            val c110 = lut[base + strideG + 1]
            val c001 = lut[base + strideB]
            val c101 = lut[base + strideB + 1]
            val c011 = lut[base + strideB + strideG]
            val c111 = lut[base + strideB + strideG + 1]

            val rOut = trilinearChannel(c000, c100, c010, c110, c001, c101, c011, c111, 16, dr, dg, db)
            val gOut = trilinearChannel(c000, c100, c010, c110, c001, c101, c011, c111, 8, dr, dg, db)
            val bOut = trilinearChannel(c000, c100, c010, c110, c001, c101, c011, c111, 0, dr, dg, db)

            pixels[i] = a or (rOut shl 16) or (gOut shl 8) or bOut
        }

        out.setPixels(pixels, 0, width, 0, 0, width, height)
        return out
    }

    fun computeHistogram(bitmap: Bitmap): FloatArray {
        val bins = FloatArray(32)
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return bins
        val stepX = max(1, width / 64)
        val stepY = max(1, height / 64)
        val row = IntArray(width)
        var count = 0
        var y = 0
        while (y < height) {
            bitmap.getPixels(row, 0, width, 0, y, width, 1)
            var x = 0
            while (x < width) {
                val px = row[x]
                if ((px ushr 24) >= 128) {
                    val r = (px ushr 16) and 0xFF
                    val g = (px ushr 8) and 0xFF
                    val b = px and 0xFF
                    val lum = ((77 * r + 150 * g + 29 * b) shr 8).coerceIn(0, 255)
                    bins[(lum * 31) / 255] += 1f
                    count++
                }
                x += stepX
            }
            y += stepY
        }
        val maxVal = bins.maxOrNull()?.coerceAtLeast(1f) ?: 1f
        for (i in bins.indices) {
            bins[i] = bins[i] / maxVal
        }
        return bins
    }

    private fun trilinearChannel(
        c000: Int, c100: Int, c010: Int, c110: Int,
        c001: Int, c101: Int, c011: Int, c111: Int,
        shift: Int, dr: Float, dg: Float, db: Float
    ): Int {
        val v000 = (c000 ushr shift) and 0xFF
        val v100 = (c100 ushr shift) and 0xFF
        val v010 = (c010 ushr shift) and 0xFF
        val v110 = (c110 ushr shift) and 0xFF
        val v001 = (c001 ushr shift) and 0xFF
        val v101 = (c101 ushr shift) and 0xFF
        val v011 = (c011 ushr shift) and 0xFF
        val v111 = (c111 ushr shift) and 0xFF

        val x00 = v000 + (v100 - v000) * dr
        val x10 = v010 + (v110 - v010) * dr
        val x01 = v001 + (v101 - v001) * dr
        val x11 = v011 + (v111 - v011) * dr

        val y0 = x00 + (x10 - x00) * dg
        val y1 = x01 + (x11 - x01) * dg

        return (y0 + (y1 - y0) * db + 0.5f).toInt().coerceIn(0, 255)
    }

    private fun softClip(x: Float, shoulderStart: Float): Float {
        if (x <= shoulderStart) return x.coerceAtLeast(0f)
        val over = x - shoulderStart
        val headroom = 1f - shoulderStart
        return (shoulderStart + headroom * (over / (over + headroom))).coerceIn(0f, 1f)
    }

    private fun applyToneCurve(x: Float, blackLift: Float, contrastAmount: Float): Float {
        val xc = x.coerceIn(0f, 1f)
        // Smooth S-curve via Hermite interpolation blended with linear
        val sCurve = xc * xc * (3f - 2f * xc)
        val contrasted = xc + (sCurve - xc) * contrastAmount
        // Lift deep toe gently so blacks keep detail without looking washed out
        val lifted = blackLift * (1f - xc) * (1f - xc) + contrasted
        return lifted.coerceIn(0f, 1f)
    }

    private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        if (edge0 == edge1) return 0f
        val t = if (edge1 > edge0) {
            ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        } else {
            ((edge0 - x) / (edge0 - edge1)).coerceIn(0f, 1f)
        }
        return t * t * (3f - 2f * t)
    }

    private fun smoothBand(hue: Float, center: Float, halfWidth: Float): Float {
        var diff = abs(hue - center)
        if (diff > 180f) diff = 360f - diff
        if (diff >= halfWidth) return 0f
        val t = 1f - diff / halfWidth
        return t * t * (3f - 2f * t)
    }

    private fun computeHueDegrees(r: Float, g: Float, b: Float, maxC: Float, delta: Float): Float {
        if (delta < 1e-4f) return 0f
        var h = when (maxC) {
            r -> ((g - b) / delta) % 6f
            g -> ((b - r) / delta) + 2f
            else -> ((r - g) / delta) + 4f
        }
        h *= 60f
        if (h < 0f) h += 360f
        return h
    }
}
