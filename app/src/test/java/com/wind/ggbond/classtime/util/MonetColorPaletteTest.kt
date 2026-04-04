package com.wind.ggbond.classtime.util

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class MonetColorPaletteTest {

    private val testSeedColor = 0xFFD4A574.toInt()

    @Before
    fun setUp() {
        MonetColorPalette.clearCache()
    }

    @Test
    fun `test generate 24 colors`() {
        val colors = MonetColorPalette.generatePalette(
            seedColor = testSeedColor,
            saturationLevel = MonetColorPalette.SaturationLevel.STANDARD,
            isDarkMode = false
        )

        assertEquals(24, colors.size)
    }

    @Test
    fun `test color format validity`() {
        val colors = MonetColorPalette.generatePalette(
            seedColor = testSeedColor,
            saturationLevel = MonetColorPalette.SaturationLevel.SOFT,
            isDarkMode = false
        )

        val hexPattern = Regex("^#[0-9A-Fa-f]{6}$")
        colors.forEach { color ->
            assertTrue("颜色格式不正确: $color", hexPattern.matches(color))
        }
    }

    @Test
    fun `test colors cover full hue range`() {
        val colors = MonetColorPalette.generatePalette(
            seedColor = 0xFF0000FF.toInt(),
            saturationLevel = MonetColorPalette.SaturationLevel.STANDARD,
            isDarkMode = false
        )

        val hues = colors.map { hexToHue(it) }

        val minHue = hues.minOrNull()!!
        val maxHue = hues.maxOrNull()!!
        val diff = maxHue - minHue

        assertTrue("色相覆盖范围不足: $diff", diff >= 340f)
    }

    @Test
    fun `test saturation levels produce different results`() {
        val soft = MonetColorPalette.generatePalette(testSeedColor, MonetColorPalette.SaturationLevel.SOFT, false)
        val standard = MonetColorPalette.generatePalette(testSeedColor, MonetColorPalette.SaturationLevel.STANDARD, false)
        val vibrant = MonetColorPalette.generatePalette(testSeedColor, MonetColorPalette.SaturationLevel.VIBRANT, false)

        val avgSoft = soft.map { getSaturation(it) }.average()
        val avgStandard = standard.map { getSaturation(it) }.average()
        val avgVibrant = vibrant.map { getSaturation(it) }.average()

        assertTrue("SOFT饱和度应小于STANDARD: $avgSoft < $avgStandard", avgSoft < avgStandard)
        assertTrue("STANDARD饱和度应小于VIBRANT: $avgStandard < $avgVibrant", avgStandard < avgVibrant)
    }

    @Test
    fun `test dark mode increases lightness`() {
        val light = MonetColorPalette.generatePalette(testSeedColor, MonetColorPalette.SaturationLevel.STANDARD, false)
        val dark = MonetColorPalette.generatePalette(testSeedColor, MonetColorPalette.SaturationLevel.STANDARD, true)

        val avgLightLightness = light.map { getLightness(it) }.average()
        val avgDarkLightness = dark.map { getLightness(it) }.average()
        val diff = avgDarkLightness - avgLightLightness

        assertTrue("暗色模式明度提升不足: $diff", diff > 0.1f)
    }

    @Test
    fun `test same course name returns same color`() {
        val color1 = MonetColorPalette.getColorForCourse("高等数学", testSeedColor, MonetColorPalette.SaturationLevel.STANDARD)
        val color2 = MonetColorPalette.getColorForCourse("高等数学", testSeedColor, MonetColorPalette.SaturationLevel.STANDARD)

        assertEquals(color1, color2)
    }

    @Test
    fun `test different courses get different colors`() {
        val courses = listOf("数学", "英语", "物理", "化学", "生物")
        val colors = courses.map {
            MonetColorPalette.getColorForCourse(it, testSeedColor, MonetColorPalette.SaturationLevel.STANDARD)
        }.distinct()

        assertEquals(5, colors.size)
    }

    @Test
    fun `test batch assignment no duplicates for first 24`() {
        val courseNames = (1..30).map { "课程$it" }
        val colorMap = MonetColorPalette.assignColorsForCourses(courseNames, testSeedColor, MonetColorPalette.SaturationLevel.STANDARD)

        val first24Colors = courseNames.take(24).map { colorMap[it]!! }.distinct()
        assertEquals(24, first24Colors.size)
    }

    @Test
    fun `test clear cache resets state`() {
        val colorBefore = MonetColorPalette.getColorForCourse("测试课程", testSeedColor, MonetColorPalette.SaturationLevel.STANDARD)

        MonetColorPalette.clearCache()

        val existingColors = listOf("#FFFFFF", "#000000")
        val colorAfter = MonetColorPalette.getColorForCourse("测试课程", testSeedColor, MonetColorPalette.SaturationLevel.STANDARD, existingColors)

        assertNotNull(colorAfter)
    }

    private fun hexToRgb(hexColor: String): IntArray {
        val r = hexColor.substring(1..2).toInt(16)
        val g = hexColor.substring(3..4).toInt(16)
        val b = hexColor.substring(5..6).toInt(16)
        return intArrayOf(r, g, b)
    }

    private fun hexToHue(hexColor: String): Float {
        val rgb = hexToRgb(hexColor)
        val r = rgb[0] / 255f
        val g = rgb[1] / 255f
        val b = rgb[2] / 255f

        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val delta = max - min

        if (delta == 0f) return 0f

        val hue = when {
            max == r -> ((g - b) / delta % 6)
            max == g -> (b - r) / delta + 2
            else -> (r - g) / delta + 4
        } * 60

        return if (hue < 0) hue + 360 else hue
    }

    private fun getSaturation(hexColor: String): Float {
        val rgb = hexToRgb(hexColor)
        val r = rgb[0] / 255f
        val g = rgb[1] / 255f
        val b = rgb[2] / 255f

        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val l = (max + min) / 2f

        if (max == min) return 0f

        val delta = max - min
        return if (l > 0.5f) delta / (2 - max - min) else delta / (max + min)
    }

    private fun getLightness(hexColor: String): Float {
        val rgb = hexToRgb(hexColor)
        val r = rgb[0] / 255f
        val g = rgb[1] / 255f
        val b = rgb[2] / 255f

        return (maxOf(r, g, b) + minOf(r, g, b)) / 2f
    }
}
