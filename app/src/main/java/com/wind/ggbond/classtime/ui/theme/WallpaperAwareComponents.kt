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

/**
 * 壁纸感知的 Surface 组件
 * 
 * 当用户启用壁纸时，自动将背景色调整为半透明，让壁纸透出来
 * 使用方法：将所有需要适配壁纸的 Surface 替换为 WallpaperAwareSurface
 * 
 * 示例：
 * ```kotlin
 * // ❌ 旧写法（不透明）
 * Surface(color = MaterialTheme.colorScheme.surface) { ... }
 * 
 * // ✅ 新写法（自动适配）
 * WallpaperAwareSurface { ... }
 * ```
 */
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
    content: @Composable () -> Unit
) {
    val isWallpaperEnabled = LocalWallpaperEnabled.current
    val wallpaperAlpha = LocalWallpaperAlpha.current

    val adjustedColor = if (isWallpaperEnabled) {
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

/**
 * 壁纸感知的背景 Modifier 扩展
 *
 * 用于简单的背景色场景（非 Surface 组件）
 *
 * 示例：
 * ```kotlin
 * // ❌ 旧写法
 * Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
 *
 * // ✅ 新写法
 * Modifier.wallpaperAwareBackground(MaterialTheme.colorScheme.surfaceVariant)
 * ```
 */
@Composable
fun Modifier.wallpaperAwareBackground(
    color: Color,
    level: WallpaperTransparencyLevel = WallpaperTransparencyLevel.MEDIUM
): Modifier {
    val isWallpaperEnabled = LocalWallpaperEnabled.current
    val wallpaperAlpha = LocalWallpaperAlpha.current

    return this.then(
        Modifier.background(
            if (isWallpaperEnabled) {
                val finalAlpha = (wallpaperAlpha * level.alphaMultiplier).coerceIn(0f, 1f)
                color.copy(alpha = (color.alpha * finalAlpha).coerceIn(0f, 1f))
            } else {
                color
            }
        )
    )
}
