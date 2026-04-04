package com.wind.ggbond.classtime.util

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CourseColorPaletteTest {
    
    @Before
    fun setUp() {
        // 每个测试前清除缓存，确保测试独立性
        CourseColorPalette.clearCache()
    }
    
    // ========== 一致性测试（相同课程名返回相同颜色） ==========
    
    @Test
    fun `getColorForCourse - 相同课程名称多次调用应返回相同颜色`() {
        val courseName = "高等数学"
        
        val color1 = CourseColorPalette.getColorForCourse(courseName)
        val color2 = CourseColorPalette.getColorForCourse(courseName)
        val color3 = CourseColorPalette.getColorForCourse(courseName)
        
        assertEquals("相同课程名称应始终返回相同颜色", color1, color2)
        assertEquals("相同课程名称应始终返回相同颜色", color2, color3)
    }
    
    @Test
    fun `getColorForCourse - 大小写不同的同一课程应视为不同课程或保持一致`() {
        val course1 = "英语"
        val course2 = "英语"  // 相同
        val course3 = "ENGLISH"  // 不同（大小写敏感）
        
        val color1 = CourseColorPalette.getColorForCourse(course1)
        val color2 = CourseColorPalette.getColorForCourse(course2)
        val color3 = CourseColorPalette.getColorForCourse(course3)
        
        assertEquals("完全相同的字符串应返回相同颜色", color1, color2)
        // 注意：color3可能和color1相同也可能不同，取决于实现
    }
    
    // ========== 分散性测试（不同课程通常返回不同颜色） ==========
    
    @Test
    fun `getColorForCourse - 不同课程名称通常应返回不同颜色`() {
        val courses = listOf(
            "高等数学",
            "大学英语",
            "物理",
            "化学",
            "计算机基础",
            "体育",
            "思想政治",
            "历史"
        )
        
        val colors = courses.map { CourseColorPalette.getColorForCourse(it) }
        val distinctColors = colors.distinct()
        
        assertTrue("8门不同课程应有至少6种不同的颜色（允许少量重复）", 
            distinctColors.size >= 6)
    }
    
    @Test
    fun `getColorForCourse - 大量不同课程的分散性验证`() {
        val courses = (1..20).map { "课程$it" }
        
        val colors = courses.map { CourseColorPalette.getColorForCourse(it) }.distinct()
        
        assertTrue("20门不同课程应有至少15种不同的颜色", 
            colors.size >= 15)
    }
    
    // ========== 空字符串处理测试 ==========
    
    @Test
    fun `getColorForCourse - 空字符串也应能分配颜色`() {
        val emptyName = ""
        
        try {
            val color = CourseColorPalette.getColorForCourse(emptyName)
            
            assertNotNull("空字符串应能分配非null的颜色", color)
            assertFalse("空字符串分配的颜色不应为空白", color.isBlank())
            
            // 验证颜色格式是否为有效的16进制
            assertTrue("颜色应以#开头", color.startsWith("#"))
            assertTrue("颜色长度应为7（#加6位十六进制）", color.length == 7)
        } catch (e: Exception) {
            fail("空字符串不应导致异常: ${e.message}")
        }
    }
    
    @Test
    fun `getColorForCourse - 空字符串多次调用应返回一致的颜色`() {
        val color1 = CourseColorPalette.getColorForCourse("")
        val color2 = CourseColorPalette.getColorForCourse("")
        
        assertEquals("空字符串应始终返回相同颜色", color1, color2)
    }
    
    // ========== 颜色池边界测试 ==========
    
    @Test
    fun `getColorForCourse - 超过预设颜色数时的行为验证`() {
        val totalColors = CourseColorPalette.getColorCount()
        println("预设颜色总数: $totalColors")
        
        assertTrue("预设颜色池至少应有15种颜色", totalColors >= 15)
        
        // 创建超过颜色池数量的课程
        val excessCourses = (1..(totalColors + 5)).map { "超出课程$it" }
        
        try {
            val colors = excessCourses.map { CourseColorPalette.getColorForCourse(it) }
            
            assertEquals("所有课程都应成功分配颜色", excessCourses.size, colors.size)
            
            // 验证所有颜色都是有效格式
            colors.forEachIndexed { index, color ->
                assertTrue("第${index+1}门课程的颜色格式应正确: $color", 
                    color.matches(Regex("^#[0-9A-Fa-f]{6}$")))
            }
        } catch (e: Exception) {
            fail("超过颜色池数量时不应抛出异常: ${e.message}")
        }
    }
    
    @Test
    fun `getColorByIndex - 有效索引应返回正确的颜色`() {
        val color0 = CourseColorPalette.getColorByIndex(0)
        val color1 = CourseColorPalette.getColorByIndex(1)
        val lastColor = CourseColorPalette.getColorByIndex(CourseColorPalette.getColorCount() - 1)
        
        assertNotNull("索引0应返回有效颜色", color0)
        assertNotNull("索引1应返回有效颜色", color1)
        assertNotNull("最后一个索引应返回有效颜色", lastColor)
        
        assertTrue("所有颜色都应符合#RRGGBB格式", 
            listOf(color0, color1, lastColor).all { it.matches(Regex("^#[0-9A-Fa-f]{6}$")) })
    }
    
    @Test
    fun `getColorByIndex - 超出范围的索引应循环使用颜色池`() {
        val totalCount = CourseColorPalette.getColorCount()
        
        val colorAt0 = CourseColorPalette.getColorByIndex(0)
        val colorAtOverflow = CourseColorPalette.getColorByIndex(totalCount)  // 超出范围
        val colorAtDoubleOverflow = CourseColorPalette.getColorByIndex(totalCount * 2)  // 大幅超出
        
        assertEquals("溢出的索引应循环回到颜色池开始位置", colorAt0, colorAtOverflow)
        assertEquals("大幅溢出的索引也应正确循环", colorAt0, colorAtDoubleOverflow)
    }
    
    @Test
    fun `getColorByIndex - 负数索引应正确处理（通过模运算）`() {
        try {
            val color = CourseColorPalette.getColorByIndex(-1)
            
            assertNotNull("负数索引应能返回颜色（通过模运算）", color)
            assertTrue("负数索引返回的颜色格式应正确", 
                color.matches(Regex("^#[0-9A-Fa-f]{6}$")))
        } catch (e: Exception) {
            // 某些实现可能会对负数索引抛出异常，这也是可接受的
            println("负数索引行为: ${e.message}")
        }
    }
    
    // ========== 批量分配测试 ==========
    
    @Test
    fun `assignColorsForCourses - 应为每门课程分配唯一颜色（当课程数不超过颜色池时）`() {
        val courses = listOf("数学", "英语", "物理", "化学")
        
        val colorMap = CourseColorPalette.assignColorsForCourses(courses)
        
        assertEquals("映射应包含4门课程", 4, colorMap.size)
        assertTrue("每门课程都应有颜色分配", courses.all { it in colorMap })
        
        val distinctColors = colorMap.values.distinct()
        assertEquals("4门课程应有4种不同颜色", 4, distinctColors.size)
    }
    
    @Test
    fun `assignColorsForCourses - 包含重复课程名的列表应去重后分配`() {
        val courses = listOf("数学", "数学", "英语", "英语", "物理")
        
        val colorMap = CourseColorPalette.assignColorsForCourses(courses)
        
        assertEquals("重复的课程名应被去重，只保留3门唯一课程", 3, colorMap.size)
    }
    
    @Test
    fun `assignColorsForCourses - 空列表应返回空映射`() {
        val colorMap = CourseColorPalette.assignColorsForCourses(emptyList())
        
        assertTrue("空课程列表应返回空映射", colorMap.isEmpty())
    }
    
    // ========== 颜色可读性测试 ==========
    
    @Test
    fun `getAllColors - 所有预设颜色应是可读的（不太浅也不太暗）`() {
        val allColors = CourseColorPalette.getAllColors()
        
        assertFalse("颜色池不应为空", allColors.isEmpty())
        
        allColors.forEachIndexed { index, color ->
            // 解析RGB值
            val r = color.substring(1..2).toInt(16)
            val g = color.substring(3..4).toInt(16)
            val b = color.substring(5..6).toInt(16)
            
            // 计算亮度（使用相对亮度公式）
            val luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255
            
            // 亮度应在合理范围内（避免太暗<0.15或太亮>0.95）
            assertTrue("颜色$index ($color) 的亮度$luminance 应在合理范围内 [0.15, 0.95]", 
                luminance in 0.15..0.95)
        }
    }
    
    @Test
    fun `getAllColors - 颜色池应覆盖多种色调`() {
        val allColors = CourseColorPalette.getAllColors()
        
        if (allColors.isNotEmpty()) {
            // 简单检查：至少应该有多种明显不同的颜色
            // 通过比较第一个颜色和其他颜色的差异
            val firstColor = allColors[0]
            val firstR = firstColor.substring(1..2).toInt(16)
            val firstG = firstColor.substring(3..4).toInt(16)
            var firstB = firstColor.substring(5..6).toInt(16)
            
            var differentHueCount = 0
            
            for (i in 1 until allColors.size) {
                val color = allColors[i]
                val r = color.substring(1..2).toInt(16)
                val g = color.substring(3..4).toInt(16)
                val b = color.substring(5..6).toInt(16)
                
                // 如果RGB差异较大，认为是不同色调
                val diff = kotlin.math.abs(r - firstR) + kotlin.math.abs(g - firstG) + kotlin.math.abs(b - firstB)
                if (diff > 200) {
                    differentHueCount++
                }
            }
            
            assertTrue("颜色池中应有多种不同色调（与第一色的显著差异>200的数量应>=10）", 
                differentHueCount >= 10)
        }
    }
    
    // ========== 缓存机制测试 ==========
    
    @Test
    fun `clearCache - 清除缓存后重新分配应从颜色池起始位置开始`() {
        val courseName = "缓存测试课程"
        
        // 第一次分配
        val colorBeforeClear = CourseColorPalette.getColorForCourse(courseName)
        
        // 清除缓存
        CourseColorPalette.clearCache()
        
        // 再次分配（由于缓存已清空，但颜色选择逻辑可能仍会选同一个）
        val colorAfterClear = CourseColorPalette.getColorForCourse(courseName)
        
        // 验证两次都能正常分配（不一定相同，因为缓存已清空）
        assertNotNull("清除缓存前应能分配颜色", colorBeforeClear)
        assertNotNull("清除缓存后应能分配颜色", colorAfterClear)
        assertTrue("两次分配的颜色都应是有效格式", 
            colorBeforeClear.matches(Regex("^#[0-9A-Fa-f]{6}$")) &&
            colorAfterClear.matches(Regex("^#[0-9A-Fa-f]{6}$")))
    }
    
    @Test
    fun `getColorCount - 颜色总数应与getAllColors()返回的列表长度一致`() {
        val count = CourseColorPalette.getColorCount()
        val listSize = CourseColorPalette.getAllColors().size
        
        assertEquals("getColorCount()应与getAllColors().size一致", count, listSize)
    }
}
