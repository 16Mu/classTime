package com.wind.ggbond.classtime.widget

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.glance.LocalContext
import androidx.glance.unit.ColorProvider

/**
 * Glance 1.1.1 日/夜间模式颜色适配辅助
 *
 * Glance 1.1.1 移除了 ColorProvider(day, night) 工厂函数，
 * 此方法通过系统配置检测当前主题模式，返回对应颜色的 ColorProvider。
 *
 * @param day 浅色模式下使用的颜色
 * @param night 深色模式下使用的颜色
 * @return 适配当前主题模式的 ColorProvider
 */
@Composable
fun dayNightColorProvider(day: Color, night: Color): ColorProvider {
    // 通过 Glance 提供的 LocalContext 获取系统夜间模式状态
    val context = LocalContext.current
    val isNightMode = (context.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    return ColorProvider(if (isNightMode) night else day)
}

/**
 * Widget 配色常量 - 米白色系
 * 
 * 与主应用 Color.kt 中的温暖米白色系保持一致，
 * 集中管理所有 Widget 配色，避免硬编码分散在各处。
 */
object WidgetColors {
    // ==================== 背景色 ====================
    /** Widget 卡片背景（浅色模式） */
    val cardBgDay = Color(0xFFFFFBF5)       // 极浅米白
    /** Widget 卡片背景（深色模式） */
    val cardBgNight = Color(0xFF3E2723)     // 深棕

    // ==================== 分隔线 ====================
    val dividerDay = Color(0xFFE8DDD0)      // 浅米色线
    val dividerNight = Color(0xFF5D4037)    // 中棕线

    // ==================== 课程卡片背景 ====================
    val courseItemBgDay = Color(0xFFFFF3E6)   // 浅米色
    val courseItemBgNight = Color(0xFF4E342E) // 中棕

    // ==================== 主文字 ====================
    val textPrimaryDay = Color(0xFF3E2723)    // 深棕
    val textPrimaryNight = Color(0xFFFFF9F0)  // 米白

    // ==================== 次级文字 ====================
    val textSecondaryDay = Color(0xFF6D4C41)  // 中棕
    val textSecondaryNight = Color(0xFFE8D5C4) // 浅米

    // ==================== 三级文字 ====================
    val textTertiaryDay = Color(0xFF8D6E63)   // 浅棕
    val textTertiaryNight = Color(0xFFBCAA95)  // 米灰

    // ==================== 强调色（课程数量标签） ====================
    val accentBgDay = Color(0xFFFFE8CC)       // 暖米色背景
    val accentBgNight = Color(0xFF5D4037)     // 深棕背景
    val accentTextDay = Color(0xFF6D4C41)     // 中棕文字
    val accentTextNight = Color(0xFFE8D5C4)   // 浅米文字

    // ==================== 状态色（即将上课标签） ====================
    val warningBgDay = Color(0xFFFFE8CC)      // 暖米色
    val warningBgNight = Color(0xFF4E342E)    // 中棕
    val warningTextDay = Color(0xFF8D6E63)    // 浅棕
    val warningTextNight = Color(0xFFFFCC80)  // 暖金

    // ==================== 空状态文字 ====================
    val emptyPrimaryDay = Color(0xFFD7C3B0)   // 深米色
    val emptyPrimaryNight = Color(0xFF5D4037)  // 中棕
    val emptySecondaryDay = Color(0xFF8D6E63) // 浅棕
    val emptySecondaryNight = Color(0xFF6D4C41) // 深棕
}
