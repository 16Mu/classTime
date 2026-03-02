package com.wind.ggbond.classtime.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils

/**
 * 根据背景色返回合适的主要内容色（黑或白），保证可读性。
 * 使用 WCAG 2.0 标准对比度算法：分别计算白色/黑色文字在该背景上的对比度，取高者。
 * 确保在浅黄、粉色等中间亮度背景上也能正确选择文字颜色。
 */
fun contentColorForBackground(background: Color): Color {
    val bgLuminance = background.luminance()
    // WCAG 对比度公式: CR = (L_lighter + 0.05) / (L_darker + 0.05)
    val contrastWithWhite = (1.0f + 0.05f) / (bgLuminance + 0.05f)  // 白色文字对比度
    val contrastWithBlack = (bgLuminance + 0.05f) / (0.0f + 0.05f)  // 黑色文字对比度
    // 选择对比度更高的颜色，确保最佳可读性
    return if (contrastWithBlack >= contrastWithWhite) Color(0xFF1A1A1A) else Color(0xFFFFFFFF)
}

/**
 * 次要内容色，基于主要内容色做轻微透明度处理。
 * 白色文字保持高不透明度，深色文字使用深灰色，确保清晰可见。
 */
fun secondaryContentColorForBackground(background: Color): Color {
    val base = contentColorForBackground(background)
    // 判断主内容色是否为深色系（亮度低于 0.5 即为深色）
    return if (base.luminance() > 0.5f) {
        base.copy(alpha = 0.95f)  // 白色系文字：保持高不透明度
    } else {
        Color(0xFF2D2D2D)  // 深色系文字：使用稍浅的深灰，与纯黑产生视觉层次
    }
}

/**
 * 针对浅色背景强化的顶部渐变遮罩强度。
 */
fun topGradientOverlayAlpha(background: Color): Float {
    val l = background.luminance()
    return if (l > 0.7f) 0.28f else if (l > 0.5f) 0.2f else 0.12f
}

/**
 * 暗色模式下调整课程颜色：降低亮度和饱和度，避免过亮/发光。
 * @param color 原始课程颜色
 * @param isDarkMode 是否为暗色模式
 * @return 适配后的颜色
 */
fun adjustColorForTheme(color: Color, isDarkMode: Boolean): Color {
    if (!isDarkMode) return color
    
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(color.toArgb(), hsl)
    
    // 降低亮度：从 0.5-0.7 区间映射到 0.4-0.55
    hsl[2] = (hsl[2] * 0.75f).coerceIn(0.35f, 0.6f)
    
    // 微降饱和度：避免过度鲜艳
    hsl[1] = (hsl[1] * 0.88f).coerceIn(0.3f, 0.85f)
    
    return Color(ColorUtils.HSLToColor(hsl))
}


