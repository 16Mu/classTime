package com.wind.ggbond.classtime.widget

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.glance.LocalContext
import androidx.glance.text.FontWeight
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

@Composable fun dayNightColorProvider(day: Color, night: Color): ColorProvider {
    val isNightMode = (LocalContext.current.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    return ColorProvider(if (isNightMode) night else day)
}

fun parseCourseColor(hex: String?): Color = try { Color(android.graphics.Color.parseColor(hex ?: "#5C6BC0")) } catch (_: Exception) { Color(0xFF5C6BC0) }

@Composable fun textPrimary() = TextStyle(color = dayNightColorProvider(WidgetColors.textPrimaryDay, WidgetColors.textPrimaryNight), fontWeight = FontWeight.Bold)
@Composable fun textSecondary(size: Int = 11) = TextStyle(color = dayNightColorProvider(WidgetColors.textSecondaryDay, WidgetColors.textSecondaryNight), fontSize = size.sp)
@Composable fun textTertiary(size: Int = 10) = TextStyle(color = dayNightColorProvider(WidgetColors.textTertiaryDay, WidgetColors.textTertiaryNight), fontSize = size.sp)
@Composable fun courseItemBg(isOngoing: Boolean = false, courseColor: Color? = null) =
    if (isOngoing && courseColor != null) dayNightColorProvider(courseColor.copy(alpha = 0.12f), courseColor.copy(alpha = 0.18f))
    else dayNightColorProvider(WidgetColors.courseItemBgDay, WidgetColors.courseItemBgNight)

object WidgetColors {
    val cardBgDay = Color(0xFFFFFBF5); val cardBgNight = Color(0xFF3E2723)
    val dividerDay = Color(0xFFE8DDD0); val dividerNight = Color(0xFF5D4037)
    val courseItemBgDay = Color(0xFFFFF3E6); val courseItemBgNight = Color(0xFF4E342E)
    val textPrimaryDay = Color(0xFF3E2723); val textPrimaryNight = Color(0xFFFFF9F0)
    val textSecondaryDay = Color(0xFF6D4C41); val textSecondaryNight = Color(0xFFE8D5C4)
    val textTertiaryDay = Color(0xFF8D6E63); val textTertiaryNight = Color(0xFFBCAA95)
    val accentBgDay = Color(0xFFFFE8CC); val accentBgNight = Color(0xFF5D4037)
    val accentTextDay = Color(0xFF6D4C41); val accentTextNight = Color(0xFFE8D5C4)
    val warningBgDay = Color(0xFFFFE8CC); val warningBgNight = Color(0xFF4E342E)
    val warningTextDay = Color(0xFF8D6E63); val warningTextNight = Color(0xFFFFCC80)
    val emptyPrimaryDay = Color(0xFFD7C3B0); val emptyPrimaryNight = Color(0xFF5D4037)
    val emptySecondaryDay = Color(0xFF8D6E63); val emptySecondaryNight = Color(0xFF6D4C41)
}
