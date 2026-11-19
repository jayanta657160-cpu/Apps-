package com.example.imaging

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import com.example.model.GradingRecipe
import com.example.model.ParticleStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

data class AmbientParticle(
    val x: Float,
    val y: Float,
    val radiusFrac: Float,
    val speedX: Float,
    val speedY: Float,
    val phase: Float,
    val isTeal: Boolean,
    val baseAlpha: Float
)

data class TouchLightMote(
    val normX: Float,
    val normY: Float,
    val vx: Float,
    val vy: Float,
    val radiusFrac: Float,
    val isTeal: Boolean,
    val birthTimeSec: Float
)

object ParticleArtEngine {
    private val ambientSeeds: List<AmbientParticle> by lazy {
        val rng = Random(20261118)
        List(72) { idx ->
            AmbientParticle(
                x = rng.nextFloat(),
                y = rng.nextFloat(),
                radiusFrac = 0.005f + rng.nextFloat() * 0.018f,
                speedX = (rng.nextFloat() - 0.5f) * 0.035f,
                speedY = -0.012f - rng.nextFloat() * 0.035f,
                phase = rng.nextFloat() * (2f * PI.toFloat()),
                isTeal = idx % 3 == 0,
                baseAlpha = 0.35f + rng.nextFloat() * 0.55f
            )
        }
    }

    fun getAmbientParticles(density: Float): List<AmbientParticle> {
        val count = (density.coerceIn(0f, 1f) * ambientSeeds.size).toInt()
        return ambientSeeds.take(count)
    }

    /**
     * Composites the active Particle Art layer onto a graded high-res Bitmap when
     * the user enables "Include Particles on Export".
     */
    fun bakeParticlesOntoBitmap(
        gradedBitmap: Bitmap,
        recipe: GradingRecipe,
        touchMotes: List<TouchLightMote> = emptyList(),
        timeSec: Float = 2.4f
    ): Bitmap {
        if (recipe.particleStyle == ParticleStyle.NONE || !recipe.bakeParticlesOnExport) {
            return gradedBitmap
        }
        val out = gradedBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = AndroidCanvas(out)
        val w = out.width.toFloat()
        val h = out.height.toFloat()
        val minDim = minOf(w, h)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
        }

        val glowScale = 0.45f + recipe.particleGlow * 0.95f
        val speedScale = 0.25f + recipe.particleSpeed * 1.4f

        if (recipe.particleStyle == ParticleStyle.SUNBEAM_PRISM) {
            // Soft diagonal warm golden sunbeam wash in top-right corner
            val beamRadius = maxOf(w, h) * 0.72f
            val beamAlpha = (48 * glowScale).toInt().coerceIn(0, 120)
            paint.shader = RadialGradient(
                w * 0.85f,
                h * 0.10f,
                beamRadius,
                intArrayOf(
                    android.graphics.Color.argb(beamAlpha, 245, 206, 130),
                    android.graphics.Color.argb(beamAlpha / 3, 232, 185, 104),
                    android.graphics.Color.argb(0, 232, 185, 104)
                ),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(w * 0.85f, h * 0.10f, beamRadius, paint)
        }

        val seeds = if (recipe.particleStyle == ParticleStyle.TOUCH_TRAIL) {
            emptyList()
        } else {
            getAmbientParticles(recipe.particleDensity)
        }

        for (p in seeds) {
            val t = timeSec * speedScale
            val px = (((p.x + p.speedX * t + sin((t + p.phase).toDouble()).toFloat() * 0.02f) % 1f) + 1f) % 1f * w
            val py = (((p.y + p.speedY * t) % 1f) + 1f) % 1f * h
            val radius = (p.radiusFrac * minDim * (0.8f + recipe.particleGlow * 0.7f)).coerceAtLeast(3f)
            val pulse = 0.65f + 0.35f * sin((t * 2f + p.phase).toDouble()).toFloat()
            val alpha = (p.baseAlpha * pulse * glowScale * 175f).toInt().coerceIn(10, 210)

            val useTeal = when (recipe.particleStyle) {
                ParticleStyle.TEAL_FIREFLIES -> p.isTeal
                ParticleStyle.GOLDEN_DUST, ParticleStyle.SUNBEAM_PRISM -> false
                else -> p.isTeal
            }
            val rgb = if (useTeal) Triple(96, 212, 200) else Triple(246, 204, 126)
            paint.shader = RadialGradient(
                px, py, radius,
                intArrayOf(
                    android.graphics.Color.argb(alpha, 255, 248, 230),
                    android.graphics.Color.argb((alpha * 0.65f).toInt(), rgb.first, rgb.second, rgb.third),
                    android.graphics.Color.argb(0, rgb.first, rgb.second, rgb.third)
                ),
                floatArrayOf(0f, 0.4f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(px, py, radius, paint)
        }

        for (m in touchMotes) {
            val age = (timeSec - m.birthTimeSec).coerceIn(0f, 3f)
            val life = (1f - age / 3f).coerceIn(0f, 1f)
            if (life <= 0.01f) continue
            val px = (m.normX + m.vx * age).coerceIn(0f, 1f) * w
            val py = (m.normY + m.vy * age).coerceIn(0f, 1f) * h
            val radius = (m.radiusFrac * minDim * (0.9f + recipe.particleGlow * 0.8f)).coerceAtLeast(4f)
            val alpha = (life * glowScale * 210f).toInt().coerceIn(0, 235)
            val rgb = if (m.isTeal) Triple(96, 212, 200) else Triple(246, 204, 126)
            paint.shader = RadialGradient(
                px, py, radius,
                intArrayOf(
                    android.graphics.Color.argb(alpha, 255, 250, 235),
                    android.graphics.Color.argb((alpha * 0.6f).toInt(), rgb.first, rgb.second, rgb.third),
                    android.graphics.Color.argb(0, rgb.first, rgb.second, rgb.third)
                ),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(px, py, radius, paint)
        }

        return out
    }
}

@Composable
fun ParticleArtOverlay(
    recipe: GradingRecipe,
    modifier: Modifier = Modifier,
    touchMotes: MutableList<TouchLightMote> = remember { mutableStateListOf() },
    allowTouchPaint: Boolean = true,
    onTimeTick: ((Float) -> Unit)? = null,
    onViewfinderTap: ((Offset) -> Unit)? = null
) {
    var timeSec by remember { mutableFloatStateOf(0f) }

    val active = recipe.particleStyle != ParticleStyle.NONE
    LaunchedEffect(active, recipe.particleSpeed) {
        if (!active) return@LaunchedEffect
        val startNanos = System.nanoTime()
        while (true) {
            withFrameNanos { frameNanos ->
                val t = (frameNanos - startNanos) / 1_000_000_000f
                timeSec = t
                onTimeTick?.invoke(t)
                if (touchMotes.isNotEmpty()) {
                    touchMotes.removeAll { (t - it.birthTimeSec) > 3.2f }
                }
            }
        }
    }

    val interactiveModifier = if (allowTouchPaint && recipe.particleStyle == ParticleStyle.TOUCH_TRAIL) {
        modifier
            .pointerInput(recipe.particleDensity, recipe.particleGlow) {
                detectDragGestures { change, _ ->
                    val normX = (change.position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                    val normY = (change.position.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                    repeat(3) { idx ->
                        touchMotes.add(
                            TouchLightMote(
                                normX = normX + (Random.nextFloat() - 0.5f) * 0.03f,
                                normY = normY + (Random.nextFloat() - 0.5f) * 0.03f,
                                vx = (Random.nextFloat() - 0.5f) * 0.05f,
                                vy = -0.02f - Random.nextFloat() * 0.05f,
                                radiusFrac = 0.008f + Random.nextFloat() * 0.018f,
                                isTeal = idx % 2 == 0,
                                birthTimeSec = timeSec
                            )
                        )
                    }
                    if (touchMotes.size > 160) {
                        touchMotes.subList(0, touchMotes.size - 160).clear()
                    }
                }
            }
            .pointerInput(recipe.particleDensity, onViewfinderTap) {
                detectTapGestures { offset ->
                    onViewfinderTap?.invoke(offset)
                    val normX = (offset.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                    val normY = (offset.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                    repeat(8) { idx ->
                        val angle = (idx / 8f) * (2f * PI.toFloat())
                        touchMotes.add(
                            TouchLightMote(
                                normX = normX,
                                normY = normY,
                                vx = cos(angle) * 0.055f,
                                vy = sin(angle) * 0.055f,
                                radiusFrac = 0.010f + Random.nextFloat() * 0.016f,
                                isTeal = idx % 3 == 0,
                                birthTimeSec = timeSec
                            )
                        )
                    }
                }
            }
    } else {
        modifier
    }

    Canvas(modifier = interactiveModifier) {
        if (!active) return@Canvas
        val w = size.width
        val h = size.height
        val minDim = minOf(w, h)
        val glowScale = 0.45f + recipe.particleGlow * 0.95f
        val speedScale = 0.25f + recipe.particleSpeed * 1.4f
        val t = timeSec * speedScale

        if (recipe.particleStyle == ParticleStyle.SUNBEAM_PRISM) {
            val rayAlpha = (0.16f * glowScale).coerceIn(0.04f, 0.34f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFF6D28B).copy(alpha = rayAlpha),
                        Color(0xFFE8B968).copy(alpha = rayAlpha * 0.35f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.85f, h * 0.12f),
                    radius = maxOf(w, h) * 0.72f
                ),
                center = Offset(w * 0.85f, h * 0.12f),
                radius = maxOf(w, h) * 0.72f,
                blendMode = BlendMode.Screen
            )

            // Subtle diagonal prism flare shaft
            val beamPath = Path().apply {
                moveTo(w * 0.65f, 0f)
                lineTo(w, 0f)
                lineTo(w * 0.35f, h)
                lineTo(w * 0.05f, h)
                close()
            }
            drawPath(
                path = beamPath,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFF7DA9A).copy(alpha = rayAlpha * 0.45f),
                        Color.Transparent
                    ),
                    start = Offset(w * 0.85f, 0f),
                    end = Offset(w * 0.18f, h)
                ),
                blendMode = BlendMode.Screen
            )
        }

        if (recipe.particleStyle != ParticleStyle.TOUCH_TRAIL) {
            val particles = ParticleArtEngine.getAmbientParticles(recipe.particleDensity)
            for (p in particles) {
                val px = (((p.x + p.speedX * t + sin((t + p.phase).toDouble()).toFloat() * 0.02f) % 1f) + 1f) % 1f * w
                val py = (((p.y + p.speedY * t) % 1f) + 1f) % 1f * h
                val radius = (p.radiusFrac * minDim * (0.8f + recipe.particleGlow * 0.7f)).coerceAtLeast(3f)
                val pulse = 0.65f + 0.35f * sin((t * 2f + p.phase).toDouble()).toFloat()
                val alpha = (p.baseAlpha * pulse * glowScale * 0.72f).coerceIn(0.05f, 0.88f)
                val useTeal = when (recipe.particleStyle) {
                    ParticleStyle.TEAL_FIREFLIES -> p.isTeal
                    ParticleStyle.GOLDEN_DUST, ParticleStyle.SUNBEAM_PRISM -> false
                    else -> p.isTeal
                }
                val glowColor = if (useTeal) Color(0xFF5AD4C6) else Color(0xFFF4C977)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFFFF8E8).copy(alpha = alpha),
                            glowColor.copy(alpha = alpha * 0.6f),
                            Color.Transparent
                        ),
                        center = Offset(px, py),
                        radius = radius
                    ),
                    center = Offset(px, py),
                    radius = radius,
                    blendMode = BlendMode.Screen
                )
            }
        }

        for (m in touchMotes) {
            val age = (timeSec - m.birthTimeSec).coerceIn(0f, 3.2f)
            val life = (1f - age / 3.2f).coerceIn(0f, 1f)
            if (life <= 0.01f) continue
            val px = (m.normX + m.vx * age).coerceIn(0f, 1f) * w
            val py = (m.normY + m.vy * age).coerceIn(0f, 1f) * h
            val radius = (m.radiusFrac * minDim * (0.9f + recipe.particleGlow * 0.8f)).coerceAtLeast(5f)
            val alpha = (life * glowScale * 0.85f).coerceIn(0f, 0.92f)
            val glowColor = if (m.isTeal) Color(0xFF5AD4C6) else Color(0xFFF4C977)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFFFAEB).copy(alpha = alpha),
                        glowColor.copy(alpha = alpha * 0.65f),
                        Color.Transparent
                    ),
                    center = Offset(px, py),
                    radius = radius
                ),
                center = Offset(px, py),
                radius = radius,
                blendMode = BlendMode.Screen
            )
        }
    }
}
