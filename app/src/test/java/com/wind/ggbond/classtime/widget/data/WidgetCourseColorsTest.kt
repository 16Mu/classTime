package com.wind.ggbond.classtime.widget.data

import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test

class WidgetCourseColorsTest {

    @Test
    fun `should cycle through 18 colors`() {
        for (i in 0..20) {
            val color = WidgetCourseColors.getCourseColor(i)
            assertNotNull("index $i should return valid color", color)
        }
    }

    @Test
    fun `should return same color for index and index plus 18`() {
        val color0 = WidgetCourseColors.getCourseColor(0)
        val color18 = WidgetCourseColors.getCourseColor(18)

        assertEquals("索引0和18（0+18）应返回相同颜色", color0, color18)
    }

    @Test
    fun `should return different colors for light and dark themes`() {
        val lightColor = WidgetCourseColors.getCourseColor(0, isDarkTheme = false)
        val darkColor = WidgetCourseColors.getCourseColor(0, isDarkTheme = true)

        assertNotEquals("浅色模式和深色模式的颜色应不同", lightColor, darkColor)
    }

    @Test
    fun `should return valid hex color string`() {
        val hex = WidgetCourseColors.getCourseColorHex(0)

        assertTrue("颜色应以#开头", hex.startsWith("#"))
        assertEquals("颜色长度应为7（#RRGGBB）", 7, hex.length)
    }

    @Test
    fun `should return valid hex format for all color indices`() {
        for (i in 0..17) {
            val hex = WidgetCourseColors.getCourseColorHex(i)
            assertTrue("index $i hex format incorrect: $hex", hex.matches(Regex("^#[0-9A-Fa-f]{6}$")))
        }
    }

    @Test
    fun `should handle negative index gracefully`() {
        val color = WidgetCourseColors.getCourseColor(-1)
        assertNotNull("负数索引应返回有效颜色", color)
    }
}
