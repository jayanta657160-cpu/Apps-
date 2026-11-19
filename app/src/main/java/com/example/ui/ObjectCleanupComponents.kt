package com.example.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.imaging.CleanupStroke
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.DeepStudioTeal
import com.example.ui.theme.ElevatedTealSurface
import com.example.ui.theme.LuminousTeal
import com.example.ui.theme.ObsidianTeal
import com.example.ui.theme.WarmIvory
import kotlin.math.min

enum class CleanupBrushSize(val label: String, val ratio: Float) {
    SMALL("Fine", 0.024f),
    MEDIUM("Med", 0.044f),
    LARGE("Wide", 0.072f)
}

@Composable
fun ObjectCleanupOverlay(
    bitmap: Bitmap,
    pendingStrokes: MutableList<CleanupStroke>,
    brushSize: CleanupBrushSize,
    modifier: Modifier = Modifier
) {
    val activePoints = remember { mutableStateListOf<Pair<Float, Float>>() }
    var isDrawing by remember { mutableStateOf(false) }

    fun toImageNorm(pos: Offset, viewW: Float, viewH: Float): Pair<Float, Float>? {
        val bw = bitmap.width.toFloat().coerceAtLeast(1f)
        val bh = bitmap.height.toFloat().coerceAtLeast(1f)
        val scale = min(viewW / bw, viewH / bh)
        val dispW = bw * scale
        val dispH = bh * scale
        val left = (viewW - dispW) * 0.5f
        val top = (viewH - dispH) * 0.5f
        if (pos.x < left || pos.x > left + dispW || pos.y < top || pos.y > top + dispH) return null
        return ((pos.x - left) / dispW).coerceIn(0f, 1f) to ((pos.y - top) / dispH).coerceIn(0f, 1f)
    }

    Canvas(
        modifier = modifier
            .pointerInput(bitmap, brushSize) {
                detectTapGestures { offset ->
                    val pt = toImageNorm(offset, size.width.toFloat(), size.height.toFloat())
                    if (pt != null) {
                        pendingStrokes.add(CleanupStroke(listOf(pt), brushSize.ratio))
                    }
                }
            }
            .pointerInput(bitmap, brushSize) {
                detectDragGestures(
                    onDragStart = { start ->
                        activePoints.clear()
                        val pt = toImageNorm(start, size.width.toFloat(), size.height.toFloat())
                        if (pt != null) {
                            activePoints.add(pt)
                            isDrawing = true
                        }
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val pt = toImageNorm(change.position, size.width.toFloat(), size.height.toFloat())
                        if (pt != null) {
                            activePoints.add(pt)
                        }
                    },
                    onDragEnd = {
                        if (activePoints.isNotEmpty()) {
                            pendingStrokes.add(CleanupStroke(activePoints.toList(), brushSize.ratio))
                        }
                        activePoints.clear()
                        isDrawing = false
                    },
                    onDragCancel = {
                        activePoints.clear()
                        isDrawing = false
                    }
                )
            }
    ) {
        val bw = bitmap.width.toFloat().coerceAtLeast(1f)
        val bh = bitmap.height.toFloat().coerceAtLeast(1f)
        val scale = min(size.width / bw, size.height / bh)
        val dispW = bw * scale
        val dispH = bh * scale
        val left = (size.width - dispW) * 0.5f
        val top = (size.height - dispH) * 0.5f
        val minDisp = min(dispW, dispH)

        fun drawStrokeList(pts: List<Pair<Float, Float>>, ratio: Float) {
            if (pts.isEmpty()) return
            val strokePx = (ratio * minDisp * 2f).coerceAtLeast(8f)
            val maskColor = Color(0xFFFF5A79).copy(alpha = 0.58f)
            if (pts.size == 1) {
                val p = pts[0]
                drawCircle(
                    color = maskColor,
                    radius = strokePx * 0.5f,
                    center = Offset(left + p.first * dispW, top + p.second * dispH)
                )
            } else {
                val path = Path().apply {
                    moveTo(left + pts[0].first * dispW, top + pts[0].second * dispH)
                    for (i in 1 until pts.size) {
                        lineTo(left + pts[i].first * dispW, top + pts[i].second * dispH)
                    }
                }
                drawPath(
                    path = path,
                    color = maskColor,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }

        for (stroke in pendingStrokes) {
            drawStrokeList(stroke.points, stroke.radiusPxRatio)
        }
        if (isDrawing && activePoints.isNotEmpty()) {
            drawStrokeList(activePoints, brushSize.ratio)
        }
    }
}

@Composable
fun ObjectCleanupBar(
    brushSize: CleanupBrushSize,
    onSelectBrushSize: (CleanupBrushSize) -> Unit,
    hasPendingStrokes: Boolean,
    canUndo: Boolean,
    isProcessing: Boolean,
    onApply: () -> Unit,
    onClearPending: () -> Unit,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = DeepStudioTeal,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, ChampagneGold.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CleanupBrushSize.entries.forEach { sizeOption ->
                FilterChip(
                    selected = brushSize == sizeOption,
                    onClick = { onSelectBrushSize(sizeOption) },
                    label = { Text(sizeOption.label, style = MaterialTheme.typography.labelSmall) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = LuminousTeal,
                        selectedLabelColor = ObsidianTeal,
                        containerColor = ElevatedTealSurface,
                        labelColor = WarmIvory
                    ),
                    modifier = Modifier.testTag("cleanup_brush_${sizeOption.name.lowercase()}")
                )
            }

            if (hasPendingStrokes) {
                IconButton(
                    onClick = onClearPending,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("cleanup_clear_mask_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Clear selection mask",
                        tint = WarmIvory
                    )
                }
            }

            if (canUndo) {
                IconButton(
                    onClick = onUndo,
                    enabled = !isProcessing,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("cleanup_undo_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "Undo object cleanup",
                        tint = ChampagneGold
                    )
                }
            }

            Spacer(modifier = Modifier.width(2.dp))

            Button(
                onClick = onApply,
                enabled = hasPendingStrokes && !isProcessing,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ChampagneGold,
                    contentColor = ObsidianTeal
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .height(44.dp)
                    .testTag("cleanup_apply_button")
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Remove", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
