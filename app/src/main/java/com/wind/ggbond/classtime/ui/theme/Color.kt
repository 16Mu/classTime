package com.wind.ggbond.classtime.ui.theme

import androidx.compose.ui.graphics.Color

// ==================== 主题色 - 温暖米白色系 ====================
// Light Theme Colors - 温暖舒适
val Primary = Color(0xFFD4A574)        // 暖米色 - 温暖的卡其色调
val PrimaryVariant = Color(0xFFB8956A) // 深米色
val Secondary = Color(0xFFE8D5C4)       // 浅米白 - 柔和的米白
val SecondaryVariant = Color(0xFFD7C3B0)

val OnPrimary = Color(0xFF3E2723)      // 深棕色文字
val PrimaryContainer = Color(0xFFFFF9F0)  // 极浅米白容器
val OnPrimaryContainer = Color(0xFF5D4037)

// Dark Theme Colors - 温暖深色
val PrimaryDark = Color(0xFFE8C5A0)        // 浅暖米色
val PrimaryVariantDark = Color(0xFFF5D9BB) // 更浅的米色
val SecondaryDark = Color(0xFFD7C3B0)      // 米白
val SecondaryVariantDark = Color(0xFFE8D5C4)

val OnPrimaryDark = Color(0xFF3E2723)      // 深棕色文字
val PrimaryContainerDark = Color(0xFF4E342E) // 深棕容器
val OnPrimaryContainerDark = Color(0xFFFFF9F0)

// Background Colors - 米白色系
val BackgroundLight = Color(0xFFFFFBF5)    // 极浅米白背景
val SurfaceLight = Color(0xFFFFF9F0)       // 米白表面
val SurfaceVariantLight = Color(0xFFFFF3E6) // 浅米色变体

val BackgroundDark = Color(0xFF3E2723)     // 深棕背景
val SurfaceDark = Color(0xFF4E342E)        // 中棕表面
val SurfaceVariantDark = Color(0xFF5D4037) // 浅棕变体

// ==================== 课程表UI优化配色方案 - 米白色系 ====================
// 温暖舒适的米白配色体系，清晰不疲劳

// 基础UI颜色 - 浅色模式
val ScheduleBackground = Color(0xFFFFFBF5)        // 整体背景 - 米白
val ScheduleGridLine = Color(0xFFE8DDD0)          // 网格线/分隔线 - 浅米色线
val ScheduleSectionBackground = Color(0xFFFFF3E6) // 节次栏背景 - 浅米色
val ScheduleTodayHighlight = Color(0xFFFFE8CC)    // 今日高亮 - 暖米色背景

// 基础UI颜色 - 深色模式
val ScheduleBackgroundDark = Color(0xFF3E2723)         // 整体背景 - 深棕
val ScheduleGridLineDark = Color(0xFF5D4037)           // 网格线/分隔线 - 中棕
val ScheduleSectionBackgroundDark = Color(0xFF4E342E)  // 节次栏背景 - 中棕
val ScheduleTodayHighlightDark = Color(0xFF6D4C41)     // 今日高亮 - 浅棕背景

// 文字层次颜色 - 浅色模式（米白主题）
val ScheduleTextPrimary = Color(0xFF3E2723)       // 主体文字 - 深棕
val ScheduleTextSecondary = Color(0xFF6D4C41)     // 次级文字 - 中棕

// 文字层次颜色 - 深色模式
val ScheduleTextPrimaryDark = Color(0xFFFFF9F0)   // 主体文字 - 米白
val ScheduleTextSecondaryDark = Color(0xFFE8D5C4) // 次级文字 - 浅米色

// 课程类型色块 - 温暖米白色系，柔和低饱和度
val CourseColorBasic = Color(0xFFFFE8CC)          // 公共基础课 - 浅暖米
val CourseColorMajor = Color(0xFFFFD6AD)          // 专业必修课 - 暖杏色
val CourseColorElective = Color(0xFFFFF3E6)       // 通识/选修课 - 浅米白
val CourseColorPractical = Color(0xFFD4E7C5)      // 实验/体育类 - 浅绿米

// ==================== 课程卡片颜色 - 精选10色，多色相低饱和度 ====================
// 多色相区分不同课程，低饱和度保持柔和风格，适合长时间查看
val CourseColors = listOf(
    Color(0xFFFFE0B2), // 暖橙 - 保留标志性暖色
    Color(0xFFBBDEFB), // 淡蓝 - 天空系
    Color(0xFFC8E6C9), // 淡绿 - 自然系
    Color(0xFFE1BEE7), // 淡紫 - 薰衣草系
    Color(0xFFFFCDD2), // 淡粉 - 玫瑰系
    Color(0xFFFFF9C4), // 淡黄 - 柠檬系
    Color(0xFFB2EBF2), // 淡青 - 薄荷系
    Color(0xFFD7CCC8), // 米驼 - 保留原始暖色
    Color(0xFFF8BBD0), // 淡玫红 - 花卉系
    Color(0xFFB3E5FC), // 浅天蓝 - 晴空系
)

// ==================== 渐变色定义 - 温暖米白系 ====================
// 用于高级UI组件
val GradientColors = listOf(
    listOf(Color(0xFFD4A574), Color(0xFFE8C5A0)), // 米色渐变
    listOf(Color(0xFFFFD6AD), Color(0xFFFFE8CC)), // 暖杏渐变
    listOf(Color(0xFFE8D5C4), Color(0xFFFFF3E6)), // 米白渐变
    listOf(Color(0xFFE5D4C1), Color(0xFFFFF0DB)), // 沙米渐变
    listOf(Color(0xFFD7C3B0), Color(0xFFFCE4D6)), // 深浅米渐变
)

// ==================== 功能色 ====================
val Success = Color(0xFF10B981)
val Warning = Color(0xFFF59E0B)
val Error = Color(0xFFEF4444)
val Info = Color(0xFF3B82F6)

// ==================== 文字颜色 - 米白主题 ====================
val TextPrimary = Color(0xFF3E2723)      // 主文字 - 深棕
val TextSecondary = Color(0xFF5D4037)    // 次级文字 - 中棕
val TextTertiary = Color(0xFF8D6E63)     // 三级文字 - 浅棕

val TextPrimaryDark = Color(0xFFFFF9F0)  // 主文字 - 米白
val TextSecondaryDark = Color(0xFFE8D5C4) // 次级文字 - 浅米
val TextTertiaryDark = Color(0xFFBCAA95) // 三级文字 - 米灰



