package com.wind.ggbond.classtime.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Light Color Scheme - 温暖米白主题
 */
private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    secondary = Secondary,
    onSecondary = Color(0xFF3E2723),
    secondaryContainer = Color(0xFFFFF3E6),
    onSecondaryContainer = Color(0xFF3E2723),
    tertiary = Color(0xFFB8956A),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE8CC),
    onTertiaryContainer = Color(0xFF3E2723),
    error = Error,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = BackgroundLight,
    onBackground = TextPrimary,
    surface = SurfaceLight,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = TextSecondary,
    outline = Color(0xFFD7C3B0),
    outlineVariant = Color(0xFFE8DDD0)
)

/**
 * Dark Color Scheme - 温暖深棕主题
 */
private val DarkColorScheme = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = OnPrimaryDark,
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,
    secondary = SecondaryDark,
    onSecondary = Color(0xFF3E2723),
    secondaryContainer = Color(0xFF6D4C41),
    onSecondaryContainer = SecondaryVariantDark,
    tertiary = Color(0xFFE8C5A0),
    onTertiary = Color(0xFF3E2723),
    tertiaryContainer = Color(0xFF6D4C41),
    onTertiaryContainer = Color(0xFFFFF9F0),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = BackgroundDark,
    onBackground = TextPrimaryDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = TextSecondaryDark,
    outline = Color(0xFF8D6E63),
    outlineVariant = Color(0xFF6D4C41)
)

/**
 * 课程表应用主题
 * 
 * @param darkTheme 是否使用深色主题，默认跟随系统
 * @param dynamicColor 是否使用动态颜色（Android 12+），默认关闭以保持一致的品牌色
 * @param customDynamicColorScheme 自定义的动态配色方案（从背景图片生成）
 * @param content Composable内容
 */
@Composable
fun CourseScheduleTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    customDynamicColorScheme: androidx.compose.material3.ColorScheme? = null,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        // 优先使用自定义动态配色方案（从背景图片生成）
        customDynamicColorScheme != null -> customDynamicColorScheme
        
        // 动态颜色支持（Android 12+ 系统壁纸）
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        
        // 深色主题（默认静态配色）
        darkTheme -> DarkColorScheme
        
        // 浅色主题（默认静态配色）
        else -> LightColorScheme
    }

    // 设置状态栏和导航栏颜色
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            
            val windowInsetsController = WindowCompat.getInsetsController(window, view)
            windowInsetsController.isAppearanceLightStatusBars = !darkTheme
            windowInsetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    // 根据主题选择课表专用颜色方案
    val scheduleColors = when {
        customDynamicColorScheme != null -> {
            val cs = customDynamicColorScheme
            ScheduleColorScheme(
                background = cs.background,
                gridLine = cs.outlineVariant,
                sectionBackground = cs.surfaceContainerLow,
                todayHighlight = cs.primaryContainer.copy(alpha = 0.5f),
                textPrimary = cs.onSurface,
                textSecondary = cs.onSurfaceVariant
            )
        }
        darkTheme -> DarkScheduleColors
        else -> LightScheduleColors
    }

    CompositionLocalProvider(
        LocalScheduleColors provides scheduleColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
