package com.wind.ggbond.classtime.ui.theme

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp

enum class WallpaperTransparencyLevel(val alphaMultiplier: Float) {
    HIGH(0.95f),
    MEDIUM(0.75f),
    LOW(0.55f),
    FULL_TRANSPARENT(0.3f)
}

enum class DesktopTransparencyLevel(val alpha: Float) {
    OPAQUE(1.0f),
    SEMI_TRANSPARENT(0.4f),
    FULLY_TRANSPARENT(0.0f)
}

@Composable
fun WallpaperAwareSurface(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    tonalElevation: androidx.compose.ui.unit.Dp = 0.dp,
    shadowElevation: androidx.compose.ui.unit.Dp = 0.dp,
    shape: androidx.compose.ui.graphics.Shape = androidx.compose.ui.graphics.RectangleShape,
    border: androidx.compose.foundation.BorderStroke? = null,
    level: WallpaperTransparencyLevel = WallpaperTransparencyLevel.MEDIUM,
    desktopLevel: DesktopTransparencyLevel = DesktopTransparencyLevel.SEMI_TRANSPARENT,
    content: @Composable () -> Unit
) {
    val isWallpaperEnabled = LocalWallpaperEnabled.current
    val wallpaperAlpha = LocalWallpaperAlpha.current
    val glassEffectEnabled = LocalGlassEffectEnabled.current
    val desktopModeEnabled = LocalDesktopModeEnabled.current

    val adjustedColor = if (desktopModeEnabled && isWallpaperEnabled) {
        color.copy(alpha = (color.alpha * desktopLevel.alpha).coerceIn(0f, 1f))
    } else if (isWallpaperEnabled && glassEffectEnabled) {
        val finalAlpha = (wallpaperAlpha * level.alphaMultiplier).coerceIn(0f, 1f)
        color.copy(alpha = (color.alpha * finalAlpha).coerceIn(0f, 1f))
    } else {
        color
    }

    Surface(
        modifier = modifier,
        color = adjustedColor,
        contentColor = contentColor,
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation,
        shape = shape,
        border = border,
        content = content
    )
}

@Composable
fun Modifier.wallpaperAwareBackground(
    color: Color,
    level: WallpaperTransparencyLevel = WallpaperTransparencyLevel.MEDIUM,
    desktopLevel: DesktopTransparencyLevel = DesktopTransparencyLevel.SEMI_TRANSPARENT
): Modifier {
    val isWallpaperEnabled = LocalWallpaperEnabled.current
    val wallpaperAlpha = LocalWallpaperAlpha.current
    val glassEffectEnabled = LocalGlassEffectEnabled.current
    val desktopModeEnabled = LocalDesktopModeEnabled.current

    return this.then(
        Modifier.background(
            if (desktopModeEnabled && isWallpaperEnabled) {
                color.copy(alpha = (color.alpha * desktopLevel.alpha).coerceIn(0f, 1f))
            } else if (isWallpaperEnabled && glassEffectEnabled) {
                val finalAlpha = (wallpaperAlpha * level.alphaMultiplier).coerceIn(0f, 1f)
                color.copy(alpha = (color.alpha * finalAlpha).coerceIn(0f, 1f))
            } else {
                color
            }
        )
    )
}
