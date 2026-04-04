package com.wind.ggbond.classtime.util

import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min

/**
 * Material You (Monet) 风格的动态调色板生成器
 *
 * 基于种子颜色自动生成24色调色盘，支持饱和度等级和暗色模式适配。
 * 算法核心：以种子色相为基准，按15°间隔均匀分布色相环，
 * 结合周期性明度波动产生视觉层次感。
 */
object MonetColorPalette {

    /**
     * 饱和度等级枚举
     * 控制生成颜色的鲜艳程度
     */
    enum class SaturationLevel(val value: Float, val displayName: String) {
        SOFT(0.3f, "柔和"),
        STANDARD(0.5f, "标准"),
        VIBRANT(0.7f, "鲜艳")
    }

    private const val COLOR_COUNT = 24
    private const val HUE_STEP = 15 // 360 / 24
    private const val DEFAULT_LIGHTNESS_MIN = 0.45f
    private const val DEFAULT_LIGHTNESS_MAX = 0.75f

    private val courseColorMap = ConcurrentHashMap<String, String>()

    /**
     * 将ARGB整数转换为HSL浮点数组
     *
     * @param argb ARGB格式颜色整数（如 0xFFFF5722）
     * @return FloatArray[色相(0-360), 饱和度(0-1), 明度(0-1)]
     */
    private fun argbToHsl(argb: Int): FloatArray {
        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f

        val maxVal = maxOf(r, g, b)
        val minVal = minOf(r, g, b)
        val delta = maxVal - minVal

        val lightness = (maxVal + minVal) / 2f
        var saturation = 0f
        var hue = 0f

        if (delta > 0f) {
            saturation = if (lightness > 0.5f) {
                delta / (2f - maxVal - minVal)
            } else {
                delta / (maxVal + minVal)
            }

            hue = when (maxVal) {
                r -> ((g - b) / delta + if (g < b) 6f else 0f) * 60f
                g -> ((b - r) / delta + 2f) * 60f
                else -> ((r - g) / delta + 4f) * 60f
            }
        }

        return floatArrayOf(hue, saturation, lightness)
    }

    /**
     * 将HSL三分量转换为ARGB整数（Alpha=255）
     *
     * @param h 色相 (0-360)
     * @param s 饱和度 (0-1)
     * @param l 明度 (0-1)
     * @return ARGB格式颜色整数
     */
    private fun hslToArgb(h: Float, s: Float, l: Float): Int {
        val c = (1f - kotlin.math.abs(2f * l - 1f)) * s
        val x = c * (1f - kotlin.math.abs((h / 60f % 2f) - 1f))
        val m = l - c / 2f

        var r = 0f
        var g = 0f
        var b = 0f

        when {
            h < 60f -> { r = c; g = x; b = 0f }
            h < 120f -> { r = x; g = c; b = 0f }
            h < 180f -> { r = 0f; g = c; b = x }
            h < 240f -> { r = 0f; g = x; b = c }
            h < 300f -> { r = x; g = 0f; b = c }
            else -> { r = c; g = 0f; b = x }
        }

        val ri = ((r + m) * 255f).toInt().coerceIn(0, 255)
        val gi = ((g + m) * 255f).toInt().coerceIn(0, 255)
        val bi = ((b + m) * 255f).toInt().coerceIn(0, 255)

        return (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
    }

    /**
     * 调整种子颜色的色相值（预留扩展点）
     *
     * @param seedColor 种子颜色（ARGB整数）
     * @return 调整后的色相值（0-360）
     */
    private fun adjustSeedHue(seedColor: Int): Float {
        return argbToHsl(seedColor)[0]
    }

    /**
     * 根据HSL分量生成单个颜色整数
     *
     * @param hue 色相
     * @param saturation 饱和度
     * @param lightness 明度
     * @return ARGB格式颜色整数
     */
    private fun generateSingleColor(hue: Float, saturation: Float, lightness: Float): Int {
        return hslToArgb(hue, saturation, lightness)
    }

    /**
     * 基于种子颜色生成完整的24色调色盘
     *
     * 算法流程：
     * 1. 解析种子颜色提取基准色相
     * 2. 以基准色相为起点，按15°间隔生成24个色相点
     * 3. 统一应用目标饱和度
     * 4. 明度在[0.45, 0.75]范围内按4组周期性波动
     * 5. 暗色模式时明度统一+0.15并约束至[0.35, 0.85]
     *
     * @param seedColor 种子颜色（ARGB整数），用于确定调色盘的色相基调
     * @param saturationLevel 目标饱和度等级
     * @param isDarkMode 是否为暗色模式（暗色模式下明度会整体提升）
     * @return 包含24个"#RRGGBB"格式颜色字符串的列表
     */
    fun generatePalette(
        seedColor: Int,
        saturationLevel: SaturationLevel,
        isDarkMode: Boolean = false
    ): List<String> {
        val baseHue = adjustSeedHue(seedColor)
        val targetSaturation = saturationLevel.value
        val lightnessRange = DEFAULT_LIGHTNESS_MAX - DEFAULT_LIGHTNESS_MIN

        return (0 until COLOR_COUNT).map { i ->
            val hue = (baseHue + i * HUE_STEP) % 360f
            val groupLightnessOffset = (i % 4).toFloat() * (lightnessRange / 4f)
            var lightness = DEFAULT_LIGHTNESS_MIN + groupLightnessOffset

            if (isDarkMode) {
                lightness += 0.15f
            }

            lightness = lightness.coerceIn(0.35f, 0.85f)

            val argb = generateSingleColor(hue, targetSaturation, lightness)
            String.format("#%06X", argb and 0x00FFFFFF)
        }
    }

    /**
     * 为单个课程获取唯一颜色
     *
     * 优先从缓存中查找已分配的颜色；若未命中则从生成的调色盘中筛选可用颜色，
     * 若全部已被占用则选择使用频次最低的颜色。结果会存入缓存供后续复用。
     *
     * @param courseName 课程名称（作为缓存键）
     * @param seedColor 种子颜色
     * @param saturationLevel 饱和度等级
     * @param existingColors 已被占用的颜色列表（用于避免重复）
     * @return "#RRGGBB"格式的颜色字符串
     */
    fun getColorForCourse(
        courseName: String,
        seedColor: Int,
        saturationLevel: SaturationLevel,
        existingColors: List<String> = emptyList()
    ): String {
        courseColorMap[courseName]?.let { return it }

        val palette = generatePalette(seedColor, saturationLevel)
        val availableColors = palette.filter { it !in existingColors }

        val selectedColor = if (availableColors.isNotEmpty()) {
            availableColors.first()
        } else {
            val colorUsageCount = existingColors.groupingBy { it }.eachCount()
            palette.minByOrNull { colorUsageCount[it] ?: 0 } ?: palette.first()
        }

        courseColorMap[courseName] = selectedColor
        return selectedColor
    }

    /**
     * 批量为课程列表分配互不重复的颜色
     *
     * 对课程列表去重后保持原始顺序，逐一调用 [getColorForCourse] 分配颜色，
     * 每次将已分配的颜色累积传入 [existingColors]，确保同批次内不重复。
     *
     * @param courseNames 课程名称列表
     * @param seedColor 种子颜色
     * @param saturationLevel 饱和度等级
     * @return 课程名称到颜色字符串的映射
     */
    fun assignColorsForCourses(
        courseNames: List<String>,
        seedColor: Int,
        saturationLevel: SaturationLevel
    ): Map<String, String> {
        val uniqueCourseNames = courseNames.distinct()
        val result = mutableMapOf<String, String>()
        val usedColors = mutableListOf<String>()

        uniqueCourseNames.forEach { courseName ->
            val color = if (courseColorMap.containsKey(courseName)) {
                courseColorMap.getValue(courseName)
            } else {
                getColorForCourse(courseName, seedColor, saturationLevel, usedColors)
            }
            result[courseName] = color
            usedColors.add(color)
        }

        return result
    }

    /**
     * 清空课程颜色缓存
     *
     * 调用后所有课程将重新通过算法分配颜色。
     */
    fun clearCache() {
        courseColorMap.clear()
    }
}
