package com.wind.ggbond.classtime.widget.data

import androidx.compose.ui.graphics.Color

object WidgetCourseColors {

    fun getCourseColor(index: Int, isDarkTheme: Boolean = false): Color {
        val colors = if (isDarkTheme) DARK_COLORS else LIGHT_COLORS
        return colors[index % colors.size]
    }

    fun getCourseColorHex(index: Int, isDarkTheme: Boolean = false): String {
        val color = getCourseColor(index, isDarkTheme)
        return String.format("#%06X", (0xFFFFFF and color.value.toInt()))
    }

    private val LIGHT_COLORS = listOf(
        Color(0xFFF44336),
        Color(0xFFE91E63),
        Color(0xFF9C27B0),
        Color(0xFF673AB7),
        Color(0xFF3F51B5),
        Color(0xFF2196F3),
        Color(0xFF03A9F4),
        Color(0xFF00BCD4),
        Color(0xFF009688),
        Color(0xFF4CAF50),
        Color(0xFF8BC34A),
        Color(0xFFCDDC39),
        Color(0xFFFFEB3B),
        Color(0xFFFFC107),
        Color(0xFFFF9800),
        Color(0xFFFF5722),
        Color(0xFF795548),
        Color(0xFF607D8B)
    )

    private val DARK_COLORS = listOf(
        Color(0xFFEF5350),
        Color(0xFFEC407A),
        Color(0xFFAB47BC),
        Color(0xFF7E57C2),
        Color(0xFF5C6BC0),
        Color(0xFF42A5F5),
        Color(0xFF29B6F6),
        Color(0xFF26C6DA),
        Color(0xFF26A69A),
        Color(0xFF66BB6A),
        Color(0xFF9CCC65),
        Color(0xFFD4E157),
        Color(0xFFFFEE58),
        Color(0xFFFFCA28),
        Color(0xFFFFA726),
        Color(0xFFFF7043),
        Color(0xFF8D6E63),
        Color(0xFF78909C)
    )
}
