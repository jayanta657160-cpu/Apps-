package com.example.imaging

import android.graphics.ColorMatrix as AndroidColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.os.Build
import androidx.camera.view.PreviewView
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix as ComposeColorMatrix
import com.example.model.GradingRecipe
import kotlin.math.pow

/**
 * Custom 4x5 ColorMatrixFilter that translates a [GradingRecipe] and scene [LightingProfile]
 * into a hardware-accelerated 'Teal & Warm' cinematic grading LUT effect for CameraX PreviewView.
 *
 * Uses multiplicative channel gains for warm golden highlights + additive shadow bias for teal
 * shadows, combined with adaptive white balance, contrast, deep emerald green cross-coupling,
 * and saturation/vibrance.
 */
object TealWarmColorMatrixFilter {

    fun buildMatrixValues(
        recipe: GradingRecipe,
        lighting: LightingProfile
    ): FloatArray {
        val expGain = (2.0.pow(recipe.exposure.toDouble()).toFloat()) *
            (if (recipe.adaptiveLighting) lighting.autoExposureGain else 1.0f)

        val wbR = if (recipe.adaptiveLighting) lighting.wbRedGain else 1.0f
        val wbG = if (recipe.adaptiveLighting) lighting.wbGreenGain else 1.0f
        val wbB = if (recipe.adaptiveLighting) lighting.wbBlueGain else 1.0f

        val teal = recipe.tealShadows.coerceIn(0f, 1f)
        val warm = recipe.warmHighlights.coerceIn(0f, 1f)
        val green = recipe.deepGreens.coerceIn(0f, 1f)

        // Contrast scale & pivot
        val contrastScale = 1f + (recipe.contrast.coerceIn(0f, 1f) - 0.5f) * 0.42f
        val highlightCompress = 1f - recipe.highlightSoftness.coerceIn(0f, 1f) * 0.05f

        // Warm golden highlights via multiplicative gains
        val warmGainR = 1f + warm * 0.16f
        val warmGainG = 1f + warm * 0.05f
        val warmGainB = 1f - warm * 0.14f

        val scaleR = expGain * wbR * contrastScale * highlightCompress * warmGainR
        val scaleG = expGain * wbG * contrastScale * highlightCompress * warmGainG
        val scaleB = expGain * wbB * contrastScale * highlightCompress * warmGainB

        // Subtle emerald foliage & teal cross-channel coupling
        val rFromG = -green * 0.045f
        val gFromR = (1f - recipe.skinProtection) * 0.015f
        val bFromG = green * 0.045f + teal * 0.045f

        // Teal shadow lift in the offset row (affects shadows proportionally more than bright whites)
        val toeLift = 3.5f + (1f - recipe.contrast) * 3.0f
        val contrastPivot = 127.5f * (1f - contrastScale)
        val offsetR = contrastPivot + toeLift - teal * 14.0f + warm * 2.0f
        val offsetG = contrastPivot + toeLift + teal * 5.0f + warm * 1.5f
        val offsetB = contrastPivot + toeLift + teal * 16.5f - warm * 3.5f

        val baseMatrix = AndroidColorMatrix(
            floatArrayOf(
                scaleR, rFromG, 0f, 0f, offsetR,
                gFromR, scaleG, 0f, 0f, offsetG,
                0f, bFromG, scaleB, 0f, offsetB,
                0f, 0f, 0f, 1f, 0f
            )
        )

        // Apply vibrance/saturation boost cleanly without clipping skin tones
        val satBoost = 1f + recipe.vibrance.coerceIn(0f, 1f) * 0.24f * (1f - recipe.skinProtection * 0.35f)
        if (satBoost != 1f) {
            val satMatrix = AndroidColorMatrix().apply { setSaturation(satBoost) }
            baseMatrix.postConcat(satMatrix)
        }

        return baseMatrix.array.copyOf()
    }

    fun toAndroidColorMatrix(
        recipe: GradingRecipe,
        lighting: LightingProfile
    ): AndroidColorMatrix = AndroidColorMatrix(buildMatrixValues(recipe, lighting))

    fun toAndroidColorFilter(
        recipe: GradingRecipe,
        lighting: LightingProfile
    ): ColorMatrixColorFilter = ColorMatrixColorFilter(toAndroidColorMatrix(recipe, lighting))

    fun toComposeColorFilter(
        recipe: GradingRecipe,
        lighting: LightingProfile
    ): ColorFilter = ColorFilter.colorMatrix(ComposeColorMatrix(buildMatrixValues(recipe, lighting)))

    /**
     * Applies the custom ColorMatrixFilter directly to CameraX [PreviewView] on Android 12+ (API 31+)
     * using GPU [RenderEffect.createColorFilterEffect]. Requires [PreviewView.ImplementationMode.COMPATIBLE]
     * so the hardware TextureView honors view RenderEffects.
     */
    fun applyToPreviewView(
        previewView: PreviewView,
        recipe: GradingRecipe,
        lighting: LightingProfile,
        enabled: Boolean
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (enabled) {
                val filter = toAndroidColorFilter(recipe, lighting)
                previewView.setRenderEffect(RenderEffect.createColorFilterEffect(filter))
            } else {
                previewView.setRenderEffect(null)
            }
        }
    }
}
