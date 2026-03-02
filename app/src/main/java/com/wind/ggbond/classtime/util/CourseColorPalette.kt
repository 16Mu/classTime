package com.wind.ggbond.classtime.util

import androidx.compose.ui.graphics.Color
import kotlin.math.abs

/**
 * 课程颜色调色板
 * 参考主流课程表APP（超级课程表、小爱课程表、WakeUp课程表等）的配色方案
 * 特点：
 * 1. 饱和度适中，不刺眼
 * 2. 色彩丰富，容易区分
 * 3. 深浅搭配，视觉舒适
 * 4. 覆盖色环，避免单调
 * 5. 智能分配，确保不同课程使用明显不同的颜色
 */
object CourseColorPalette {
    
    /**
     * 主流课程表APP风格的配色方案
     * 按色相差异排序，确保相邻课程颜色差异明显
     */
    private val colorPalette = listOf(
        "#5B9BD5",  // 蓝色 - 理工科
        "#F5A864",  // 橙色 - 艺术设计
        "#6FBE6E",  // 绿色 - 自然科学
        "#9B7BD7",  // 紫色 - 文学哲学
        "#F57C82",  // 红色 - 重点课程
        "#52B3D9",  // 青色 - 计算机
        "#FFD666",  // 黄色 - 外语
        "#C89B7D",  // 棕色 - 历史人文
        "#4A90E2",  // 深蓝 - 理论课程
        "#FF8C69",  // 珊瑚橙 - 实践课程
        "#4DB897",  // 青绿 - 环境科学
        "#B28FCE",  // 薰衣草紫 - 心理学
        "#EF7A82",  // 粉红 - 社会学
        "#58C1D3",  // 湖蓝 - 信息技术
        "#FFC44C",  // 金黄 - 经济管理
        "#B8956F",  // 卡其棕 - 考古历史
        "#6B8DD6",  // 钴蓝 - 物理化学
        "#FFB347",  // 金橙 - 音乐戏剧
        "#5FCF80",  // 翠绿 - 生物医学
        "#A68EC5",  // 丁香紫 - 教育学
        "#F59BB0",  // 粉色 - 艺术鉴赏
        "#7FA3B8",  // 灰蓝 - 选修课
        "#FA9FB5",  // 桃粉 - 体育健康
        "#8BA7BB",  // 雾蓝 - 通识教育
    )
    
    /**
     * 课程名称到颜色的映射缓存（用于确保相同课程名始终得到相同颜色）
     */
    private val courseColorMap = java.util.concurrent.ConcurrentHashMap<String, String>()
    
    /**
     * 智能分配颜色
     * 确保：
     * 1. 相同课程名称始终返回相同颜色
     * 2. 不同课程尽可能使用差异明显的颜色
     * 
     * @param courseName 课程名称
     * @param existingColors 已经使用的颜色列表（用于避免重复）
     * @return 16进制颜色字符串
     */
    fun getColorForCourse(courseName: String, existingColors: List<String> = emptyList()): String {
        // 如果该课程名称已经分配过颜色，直接返回
        courseColorMap[courseName]?.let { return it }
        
        // 找出尚未使用的颜色
        val unusedColors = colorPalette.filter { it !in existingColors }
        
        val selectedColor = if (unusedColors.isNotEmpty()) {
            // 优先使用未使用的颜色，按顺序选择（因为已按色相差异排序）
            unusedColors.first()
        } else {
            // 如果所有颜色都已使用，选择使用次数最少的颜色
            val colorUsageCount = existingColors.groupingBy { it }.eachCount()
            colorPalette.minByOrNull { colorUsageCount[it] ?: 0 } ?: colorPalette[0]
        }
        
        // 缓存结果
        courseColorMap[courseName] = selectedColor
        return selectedColor
    }
    
    /**
     * 批量为课程列表分配颜色
     * 确保同一批次内不同课程使用不同颜色
     * 
     * @param courseNames 课程名称列表
     * @return 课程名称到颜色的映射
     */
    fun assignColorsForCourses(courseNames: List<String>): Map<String, String> {
        // 去重并保持顺序
        val uniqueCourseNames = courseNames.distinct()
        val result = mutableMapOf<String, String>()
        val usedColors = mutableListOf<String>()
        
        uniqueCourseNames.forEach { courseName ->
            // 如果已经分配过颜色，使用缓存的颜色
            val color = if (courseColorMap.containsKey(courseName)) {
                courseColorMap[courseName]!!
            } else {
                // 否则分配新颜色
                getColorForCourse(courseName, usedColors)
            }
            result[courseName] = color
            usedColors.add(color)
        }
        
        return result
    }
    
    /**
     * 清除颜色缓存（通常不需要调用，除非要重置整个配色方案）
     */
    fun clearCache() {
        courseColorMap.clear()
    }
    
    /**
     * 根据索引获取颜色（用于手动选择）
     * 
     * @param index 颜色索引
     * @return 16进制颜色字符串
     */
    fun getColorByIndex(index: Int): String {
        return colorPalette[index % colorPalette.size]
    }
    
    /**
     * 获取所有可用颜色
     * 
     * @return 颜色列表
     */
    fun getAllColors(): List<String> {
        return colorPalette
    }
    
    /**
     * 获取颜色总数
     */
    fun getColorCount(): Int {
        return colorPalette.size
    }
}

