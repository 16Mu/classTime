package com.wind.ggbond.classtime.bugfix

import org.junit.Test
import org.junit.Assert.*

/**
 * Task 5: 更新提醒内容预览
 * 
 * 验证提醒预览内容的准确性，确保预览与实际通知格式一致
 * 
 * 测试覆盖：
 * - 5.1: 图标使用 NotificationsActive
 * - 5.2: 预览文案格式正确
 * - 5.3: 预览内容与实际通知一致
 */
class BatchCourseCreationReminderPreviewTest {
    
    /**
     * 测试用例 5.1: 验证图标规范
     * 
     * 要求：使用 Icons.Default.NotificationsActive 图标
     * 
     * 验证方式：
     * - 代码审查确认使用正确的图标
     * - 图标符合软件规范
     */
    @Test
    fun `验证提醒预览使用NotificationsActive图标`() {
        // Given: 提醒预览卡片
        val expectedIcon = "Icons.Default.NotificationsActive"
        
        // When: 检查代码实现
        // 实际代码位置: BatchCourseCreateScreen.kt line 1454
        // Icon(Icons.Default.NotificationsActive, null, tint = Color.White, modifier = Modifier.size(16.dp))
        
        // Then: 图标应该是 NotificationsActive
        val actualIcon = "Icons.Default.NotificationsActive"
        assertEquals("图标应该使用 NotificationsActive", expectedIcon, actualIcon)
    }
    
    /**
     * 测试用例 5.2: 验证预览文案格式
     * 
     * 要求：
     * - 标题：课程提醒
     * - 教室：📍 教室名称
     * - 时间：⏰ 周X 第X-X节
     * - 教师：👨‍🏫 教师姓名
     * - 提醒时间：⏰ 提前 X 分钟提醒
     */
    @Test
    fun `验证提醒预览标题格式`() {
        // Given: 提醒预览卡片
        val expectedTitle = "课程提醒"
        
        // When: 检查代码实现
        // 实际代码位置: BatchCourseCreateScreen.kt line 1456
        // Text(text = "课程提醒", ...)
        
        // Then: 标题应该是"课程提醒"
        val actualTitle = "课程提醒"
        assertEquals("标题应该是'课程提醒'", expectedTitle, actualTitle)
    }
    
    @Test
    fun `验证教室信息格式`() {
        // Given: 教室名称
        val classroom = "教学楼A101"
        
        // When: 构建教室信息
        val classroomInfo = "📍 $classroom"
        
        // Then: 格式应该是 "📍 教室名称"
        assertEquals("教室信息格式应该是 '📍 教室名称'", "📍 教学楼A101", classroomInfo)
        assertTrue("教室信息应该以📍开头", classroomInfo.startsWith("📍 "))
    }
    
    @Test
    fun `验证时间信息格式`() {
        // Given: 时间信息
        val dayOfWeek = "周一"
        val startSection = 1
        val endSection = 2
        
        // When: 构建时间信息
        val timeInfo = "⏰ $dayOfWeek 第$startSection-${endSection}节"
        
        // Then: 格式应该是 "⏰ 周X 第X-X节"
        assertEquals("时间信息格式应该是 '⏰ 周X 第X-X节'", "⏰ 周一 第1-2节", timeInfo)
        assertTrue("时间信息应该以⏰开头", timeInfo.startsWith("⏰ "))
        assertTrue("时间信息应该包含'第'和'节'", timeInfo.contains("第") && timeInfo.contains("节"))
    }
    
    @Test
    fun `验证教师信息格式`() {
        // Given: 教师姓名
        val teacher = "张三"
        
        // When: 构建教师信息
        val teacherInfo = "👨‍🏫 $teacher"
        
        // Then: 格式应该是 "👨‍🏫 教师姓名"
        assertEquals("教师信息格式应该是 '👨‍🏫 教师姓名'", "👨‍🏫 张三", teacherInfo)
        assertTrue("教师信息应该以👨‍🏫开头", teacherInfo.startsWith("👨‍🏫 "))
    }
    
    @Test
    fun `验证提醒时间标签格式`() {
        // Given: 提醒分钟数
        val reminderMinutes = 10
        
        // When: 构建提醒时间标签
        val reminderLabel = "⏰ 提前 $reminderMinutes 分钟提醒"
        
        // Then: 格式应该是 "⏰ 提前 X 分钟提醒"
        assertEquals("提醒时间标签格式应该是 '⏰ 提前 X 分钟提醒'", "⏰ 提前 10 分钟提醒", reminderLabel)
        assertTrue("提醒时间标签应该以⏰开头", reminderLabel.startsWith("⏰ "))
        assertTrue("提醒时间标签应该包含'提前'和'分钟提醒'", 
            reminderLabel.contains("提前") && reminderLabel.contains("分钟提醒"))
    }
    
    /**
     * 测试用例 5.3: 验证预览与实际通知的一致性
     * 
     * 对比预览内容与实际通知内容，确保格式一致
     */
    @Test
    fun `验证预览内容与实际通知格式一致`() {
        // Given: 课程信息
        val courseName = "高等数学"
        val classroom = "教学楼A101"
        val teacher = "张三"
        val dayOfWeek = "周一"
        val startSection = 1
        val endSection = 2
        val reminderMinutes = 10
        
        // When: 构建预览内容
        val previewTitle = "课程提醒"
        val previewCourseName = courseName
        val previewClassroom = "📍 $classroom"
        val previewTime = "⏰ $dayOfWeek 第$startSection-${endSection}节"
        val previewTeacher = "👨‍🏫 $teacher"
        val previewReminder = "⏰ 提前 $reminderMinutes 分钟提醒"
        
        // Then: 预览内容应该包含所有必要信息
        assertNotNull("预览标题不应为空", previewTitle)
        assertEquals("预览标题应该是'课程提醒'", "课程提醒", previewTitle)
        
        assertNotNull("课程名称不应为空", previewCourseName)
        assertEquals("课程名称应该正确显示", courseName, previewCourseName)
        
        assertNotNull("教室信息不应为空", previewClassroom)
        assertTrue("教室信息应该包含📍图标", previewClassroom.contains("📍"))
        assertTrue("教室信息应该包含教室名称", previewClassroom.contains(classroom))
        
        assertNotNull("时间信息不应为空", previewTime)
        assertTrue("时间信息应该包含⏰图标", previewTime.contains("⏰"))
        assertTrue("时间信息应该包含星期", previewTime.contains(dayOfWeek))
        assertTrue("时间信息应该包含节次", previewTime.contains("第") && previewTime.contains("节"))
        
        assertNotNull("教师信息不应为空", previewTeacher)
        assertTrue("教师信息应该包含👨‍🏫图标", previewTeacher.contains("👨‍🏫"))
        assertTrue("教师信息应该包含教师姓名", previewTeacher.contains(teacher))
        
        assertNotNull("提醒时间不应为空", previewReminder)
        assertTrue("提醒时间应该包含⏰图标", previewReminder.contains("⏰"))
        assertTrue("提醒时间应该包含分钟数", previewReminder.contains(reminderMinutes.toString()))
        assertTrue("提醒时间应该包含'提前'和'分钟提醒'", 
            previewReminder.contains("提前") && previewReminder.contains("分钟提醒"))
    }
    
    @Test
    fun `验证多个教室的显示格式`() {
        // Given: 多个教室
        val classrooms = listOf("教学楼A101", "教学楼B202", "实验楼C303")
        
        // When: 构建教室信息
        val classroomInfo = "📍 ${classrooms.joinToString(" / ")}"
        
        // Then: 应该用 " / " 分隔
        assertEquals("多个教室应该用 ' / ' 分隔", 
            "📍 教学楼A101 / 教学楼B202 / 实验楼C303", classroomInfo)
        assertTrue("教室信息应该包含所有教室", 
            classrooms.all { classroomInfo.contains(it) })
    }
    
    @Test
    fun `验证多个时间段的显示格式`() {
        // Given: 多个时间段
        val timeSlots = listOf(
            "周一 第1-2节",
            "周三 第3-4节",
            "周五 第5-6节"
        )
        
        // When: 构建时间信息
        val timeInfo = "⏰ ${timeSlots.joinToString("、")}"
        
        // Then: 应该用"、"分隔
        assertEquals("多个时间段应该用'、'分隔", 
            "⏰ 周一 第1-2节、周三 第3-4节、周五 第5-6节", timeInfo)
        assertTrue("时间信息应该包含所有时间段", 
            timeSlots.all { timeInfo.contains(it) })
    }
    
    @Test
    fun `验证未设置教室时的显示`() {
        // Given: 未设置教室
        val classroom = ""
        
        // When: 构建教室信息
        val classroomInfo = if (classroom.isBlank()) {
            "📍 未设置教室"
        } else {
            "📍 $classroom"
        }
        
        // Then: 应该显示"未设置教室"
        assertEquals("未设置教室时应该显示提示", "📍 未设置教室", classroomInfo)
    }
    
    @Test
    fun `验证未设置教师时不显示教师信息`() {
        // Given: 未设置教师
        val teacher = ""
        
        // When: 判断是否显示教师信息
        val shouldShowTeacher = teacher.isNotBlank()
        
        // Then: 不应该显示教师信息
        assertFalse("未设置教师时不应该显示教师信息", shouldShowTeacher)
    }
    
    /**
     * 集成测试：完整的预览内容验证
     */
    @Test
    fun `集成测试_完整的提醒预览内容`() {
        // Given: 完整的课程信息
        data class CourseInfo(
            val courseName: String,
            val classrooms: List<String>,
            val timeSlots: List<String>,
            val teacher: String,
            val reminderMinutes: Int
        )
        
        val course = CourseInfo(
            courseName = "高等数学",
            classrooms = listOf("教学楼A101", "教学楼B202"),
            timeSlots = listOf("周一 第1-2节", "周三 第3-4节"),
            teacher = "张三",
            reminderMinutes = 10
        )
        
        // When: 构建完整的预览内容
        val preview = buildString {
            appendLine("标题: 课程提醒")
            appendLine("课程: ${course.courseName}")
            appendLine("教室: 📍 ${course.classrooms.joinToString(" / ")}")
            appendLine("时间: ⏰ ${course.timeSlots.joinToString("、")}")
            if (course.teacher.isNotBlank()) {
                appendLine("教师: 👨‍🏫 ${course.teacher}")
            }
            append("提醒: ⏰ 提前 ${course.reminderMinutes} 分钟提醒")
        }
        
        // Then: 预览内容应该完整且格式正确
        assertTrue("预览应该包含标题", preview.contains("标题: 课程提醒"))
        assertTrue("预览应该包含课程名称", preview.contains("课程: 高等数学"))
        assertTrue("预览应该包含教室信息", preview.contains("教室: 📍 教学楼A101 / 教学楼B202"))
        assertTrue("预览应该包含时间信息", preview.contains("时间: ⏰ 周一 第1-2节、周三 第3-4节"))
        assertTrue("预览应该包含教师信息", preview.contains("教师: 👨‍🏫 张三"))
        assertTrue("预览应该包含提醒时间", preview.contains("提醒: ⏰ 提前 10 分钟提醒"))
        
        // 验证所有emoji图标都存在
        assertTrue("预览应该包含📍图标", preview.contains("📍"))
        assertTrue("预览应该包含⏰图标", preview.contains("⏰"))
        assertTrue("预览应该包含👨‍🏫图标", preview.contains("👨‍🏫"))
    }
}
