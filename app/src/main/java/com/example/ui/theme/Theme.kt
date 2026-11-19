package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val AureliaDarkColorScheme = darkColorScheme(
    primary = ChampagneGold,
    onPrimary = ObsidianTeal,
    primaryContainer = DeepBronze,
    onPrimaryContainer = WarmAmberGlow,
    secondary = LuminousTeal,
    onSecondary = ObsidianTeal,
    secondaryContainer = DeepEmerald,
    onSecondaryContainer = SoftSeafoam,
    tertiary = WarmAmberGlow,
    onTertiary = ObsidianTeal,
    background = ObsidianTeal,
    onBackground = WarmIvory,
    surface = DeepStudioTeal,
    onSurface = WarmIvory,
    surfaceVariant = ElevatedTealSurface,
    onSurfaceVariant = MutedSage,
    surfaceContainerLow = DeepStudioTeal,
    surfaceContainer = ElevatedTealSurface,
    surfaceContainerHigh = HighTealSurface,
    outline = SubtleOutline,
    outlineVariant = Color(0xFF1E3034),
    error = CoralError,
    onError = ObsidianTeal
)

val AureliaShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun AureliaTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = AureliaDarkColorScheme,
        typography = AureliaTypography,
        shapes = AureliaShapes,
        content = content
    )
}
