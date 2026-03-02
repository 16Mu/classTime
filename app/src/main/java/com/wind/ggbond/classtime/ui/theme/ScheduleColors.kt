package com.wind.ggbond.classtime.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 课表专用颜色方案
 * 通过 CompositionLocal 在主题中提供，避免在组件内手动判断 isDarkTheme
 */
@Immutable
data class ScheduleColorScheme(
    val background: Color,        // 整体背景
    val gridLine: Color,          // 网格线/分隔线
    val sectionBackground: Color, // 节次栏背景
    val todayHighlight: Color,    // 今日高亮
    val textPrimary: Color,       // 主体文字
    val textSecondary: Color      // 次级文字
)

// 浅色方案
val LightScheduleColors = ScheduleColorScheme(
    background = ScheduleBackground,
    gridLine = ScheduleGridLine,
    sectionBackground = ScheduleSectionBackground,
    todayHighlight = ScheduleTodayHighlight,
    textPrimary = ScheduleTextPrimary,
    textSecondary = ScheduleTextSecondary
)

// 深色方案
val DarkScheduleColors = ScheduleColorScheme(
    background = ScheduleBackgroundDark,
    gridLine = ScheduleGridLineDark,
    sectionBackground = ScheduleSectionBackgroundDark,
    todayHighlight = ScheduleTodayHighlightDark,
    textPrimary = ScheduleTextPrimaryDark,
    textSecondary = ScheduleTextSecondaryDark
)

// CompositionLocal 提供课表颜色，默认使用浅色方案
val LocalScheduleColors = staticCompositionLocalOf { LightScheduleColors }
