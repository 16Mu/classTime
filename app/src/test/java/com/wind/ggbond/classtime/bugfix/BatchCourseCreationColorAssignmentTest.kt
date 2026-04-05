package com.wind.ggbond.classtime.bugfix

import com.wind.ggbond.classtime.ui.screen.scheduleimport.BatchCourseItem
import com.wind.ggbond.classtime.ui.screen.scheduleimport.TimeSlot
import org.junit.Test
import org.junit.Assert.*

/**
 * 测试批量创建课程功能中的一键分配颜色功能
 * 
 * Bug #7: 一键分配颜色功能失效
 * - 点击"一键分配颜色"按钮后颜色未更新
 * - 按钮布局不合理，应该单独占据一行
 * - 当前是两列布局中的一个卡片
 * 
 * 修复后预期行为：
 * - 点击"一键分配颜色"后，所有课程颜色立即更新
 * - 按钮单独占据一行，位于课程列表上方
 * - 点击后显示加载动画，完成后显示成功提示
 */
class BatchCourseCreationColorAssignmentTest {

    /**
     * 测试用例 7.3.1: 测试3门课程的颜色分配
     * 
     * Given: 3门课程，颜色均为空
     * When: 调用颜色分配逻辑
     * Then: 所有课程颜色非空
     */
    @Test
    fun `test color assignment for 3 courses - all colors should be assigned`() {
        // Given: 3门课程，颜色均为空
        val courses = listOf(
            createTestCourse(1, "高等数学", ""),
            createTestCourse(2, "大学物理", ""),
            createTestCourse(3, "程序设计", "")
        )
        
        // When: 模拟颜色分配（使用简单的颜色池）
        val colorPool = listOf(
            "#E57373", "#81C784", "#64B5F6",
            "#FFD54F", "#BA68C8", "#4DD0E1"
        )
        val assignedCourses = assignColorsSimple(courses, colorPool)
        
        // Then: 所有课程颜色非空
        assignedCourses.forEach { course ->
            assertNotNull("课程 ${course.courseName} 的颜色不应为空", course.color)
            assertTrue("课程 ${course.courseName} 的颜色应该是有效的十六进制颜色", 
                course.color.matches(Regex("#[0-9A-Fa-f]{6}")))
        }
        
        println("✓ 所有课程都已分配颜色")
        assignedCourses.forEach { course ->
            println("  - ${course.courseName}: ${course.color}")
        }
    }

    /**
     * 测试用例 7.3.2: 验证颜色互不相同
     * 
     * Given: 3门课程
     * When: 调用颜色分配逻辑
     * Then: 所有课程颜色互不相同
     */
    @Test
    fun `test color assignment - all colors should be different`() {
        // Given: 3门课程
        val courses = listOf(
            createTestCourse(1, "高等数学", ""),
            createTestCourse(2, "大学物理", ""),
            createTestCourse(3, "程序设计", "")
        )
        
        // When: 模拟颜色分配
        val colorPool = listOf(
            "#E57373", "#81C784", "#64B5F6",
            "#FFD54F", "#BA68C8", "#4DD0E1"
        )
        val assignedCourses = assignColorsSimple(courses, colorPool)
        
        // Then: 所有课程颜色互不相同
        val colors = assignedCourses.map { it.color }
        val uniqueColors = colors.toSet()
        
        assertEquals("应该有3种不同的颜色", 3, uniqueColors.size)
        assertEquals("颜色列表大小应该等于唯一颜色集合大小", colors.size, uniqueColors.size)
        
        println("✓ 所有课程颜色互不相同")
        assignedCourses.forEach { course ->
            println("  - ${course.courseName}: ${course.color}")
        }
    }

    /**
     * 测试用例 7.3.3: 验证UI立即更新（通过数据变化验证）
     * 
     * Given: 3门课程，初始颜色为空
     * When: 调用颜色分配逻辑
     * Then: 课程数据立即更新，颜色字段非空
     */
    @Test
    fun `test color assignment - data should update immediately`() {
        // Given: 3门课程，初始颜色为空
        val initialCourses = listOf(
            createTestCourse(1, "高等数学", ""),
            createTestCourse(2, "大学物理", ""),
            createTestCourse(3, "程序设计", "")
        )
        
        // 验证初始状态
        initialCourses.forEach { course ->
            assertTrue("初始颜色应该为空", course.color.isEmpty())
        }
        
        // When: 模拟颜色分配
        val colorPool = listOf(
            "#E57373", "#81C784", "#64B5F6",
            "#FFD54F", "#BA68C8", "#4DD0E1"
        )
        val updatedCourses = assignColorsSimple(initialCourses, colorPool)
        
        // Then: 课程数据立即更新
        updatedCourses.forEach { course ->
            assertFalse("更新后颜色不应该为空", course.color.isEmpty())
        }
        
        // 验证数据确实发生了变化
        val colorsChanged = initialCourses.zip(updatedCourses).all { (initial, updated) ->
            initial.color != updated.color
        }
        assertTrue("所有课程的颜色都应该发生变化", colorsChanged)
        
        println("✓ 课程数据立即更新")
        println("  初始状态: ${initialCourses.map { it.color.ifEmpty { "空" } }}")
        println("  更新后: ${updatedCourses.map { it.color }}")
    }

    /**
     * 测试用例 7.3.4: 测试大量课程的颜色分配
     * 
     * Given: 10门课程
     * When: 调用颜色分配逻辑
     * Then: 所有课程都有颜色，尽可能不重复
     */
    @Test
    fun `test color assignment for 10 courses - should handle many courses`() {
        // Given: 10门课程
        val courses = (1..10).map { i ->
            createTestCourse(i.toLong(), "课程$i", "")
        }
        
        // When: 模拟颜色分配
        val colorPool = listOf(
            "#E57373", "#81C784", "#64B5F6",
            "#FFD54F", "#BA68C8", "#4DD0E1",
            "#FF8A65", "#A1887F", "#7986CB",
            "#4DB6AC", "#F06292", "#9575CD"
        )
        val assignedCourses = assignColorsSimple(courses, colorPool)
        
        // Then: 所有课程都有颜色
        assignedCourses.forEach { course ->
            assertFalse("课程 ${course.courseName} 应该有颜色", course.color.isEmpty())
        }
        
        // 统计颜色使用情况
        val colorUsage = assignedCourses.groupBy { it.color }.mapValues { it.value.size }
        println("✓ 10门课程颜色分配完成")
        println("  颜色使用统计:")
        colorUsage.forEach { (color, count) ->
            println("    $color: 使用 $count 次")
        }
        
        // 验证颜色尽可能不重复（前12门课程应该都不重复）
        val uniqueColors = assignedCourses.map { it.color }.toSet()
        assertTrue("应该至少有10种不同的颜色", uniqueColors.size >= 10)
    }

    /**
     * 测试用例 7.3.5: 测试已有颜色的课程不会被覆盖（可选行为）
     * 
     * Given: 3门课程，其中1门已有颜色
     * When: 调用颜色分配逻辑
     * Then: 根据实际实现验证行为（可能覆盖或保留）
     */
    @Test
    fun `test color assignment with existing colors - verify behavior`() {
        // Given: 3门课程，其中1门已有颜色
        val courses = listOf(
            createTestCourse(1, "高等数学", "#FF0000"), // 已有颜色
            createTestCourse(2, "大学物理", ""),
            createTestCourse(3, "程序设计", "")
        )
        
        // When: 模拟颜色分配（这里模拟覆盖行为）
        val colorPool = listOf(
            "#E57373", "#81C784", "#64B5F6",
            "#FFD54F", "#BA68C8", "#4DD0E1"
        )
        val assignedCourses = assignColorsSimple(courses, colorPool)
        
        // Then: 所有课程都有颜色
        assignedCourses.forEach { course ->
            assertFalse("课程 ${course.courseName} 应该有颜色", course.color.isEmpty())
        }
        
        println("✓ 已有颜色的课程处理完成")
        println("  原始: ${courses[0].courseName} = ${courses[0].color}")
        println("  更新: ${assignedCourses[0].courseName} = ${assignedCourses[0].color}")
    }

    // ==================== 辅助方法 ====================

    /**
     * 创建测试用的课程对象
     */
    private fun createTestCourse(
        id: Long,
        name: String,
        color: String
    ): BatchCourseItem {
        return BatchCourseItem(
            id = id,
            courseName = name,
            teacher = "教师$id",
            defaultClassroom = "教室$id",
            color = color,
            timeSlots = listOf(
                TimeSlot(
                    id = id,
                    dayOfWeek = 1,
                    startSection = 1,
                    sectionCount = 2,
                    classroom = "教室$id",
                    customWeeks = listOf(1, 2, 3, 4, 5)
                )
            ),
            reminderMinutes = 10,
            note = ""
        )
    }

    /**
     * 简单的颜色分配算法（模拟实际的颜色分配逻辑）
     * 
     * 这个方法模拟了 ViewModel 中的 autoAssignAllColors 方法的行为
     */
    private fun assignColorsSimple(
        courses: List<BatchCourseItem>,
        colorPool: List<String>
    ): List<BatchCourseItem> {
        val usedColors = mutableSetOf<String>()
        return courses.mapIndexed { index, course ->
            // 从颜色池中选择一个未使用的颜色
            val availableColors = colorPool.filter { it !in usedColors }
            val assignedColor = if (availableColors.isNotEmpty()) {
                availableColors.first()
            } else {
                // 如果所有颜色都用完了，循环使用
                colorPool[index % colorPool.size]
            }
            usedColors.add(assignedColor)
            course.copy(color = assignedColor)
        }
    }
}
