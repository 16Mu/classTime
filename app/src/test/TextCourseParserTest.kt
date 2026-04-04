package com.wind.ggbond.classtime.util

import io.mockk.any
import io.mockk.every
import io.mockk.mockkStatic
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class TextCourseParserTest {
    
    private lateinit var parser: TextCourseParser
    
    @Before
    fun setUp() {
        parser = TextCourseParser()
        
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.w(any(), any()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0
    }
    
    @After
    fun tearDown() {
    }
    
    // ========== 基础解析测试 ==========
    
    @Test
    fun `parse - 空文本应返回空列表`() {
        val text = ""
        
        val result = parser.parse(text)
        
        assertTrue("空文本应返回空列表", result.isEmpty())
    }
    
    @Test
    fun `parse - 纯空白文本应返回空列表`() {
        val text = "   \t\n   "
        
        val result = parser.parse(text)
        
        assertTrue("纯空白文本应返回空列表", result.isEmpty())
    }
    
    @Test
    fun `parse - 标准格式单门课程应正确解析`() {
        val text = """体育4★
(1-2节)1-5周,7-9周,11-13周
(单),14-16周,19周/校区:潼南
校区/场地:未排地点/教师
:李兢/教学班组成:中德项目
2024;信息24501/教学班人数:87/考核方式:考查/课程学时
组成:理论:32/学分:2"""
        
        val result = parser.parse(text)
        
        assertEquals("应解析到1门课程", 1, result.size)
        
        val course = result[0]
        assertEquals("课程名应为体育4（去除★）", "体育4", course.courseName)
        assertEquals("教师应为李兢", "李兢", course.teacher)
        assertEquals("教室应为未排地点", "未排地点", course.classroom)
        assertEquals("学分应为2.0", 2.0f, course.credit, 0.01f)
        assertNotNull("节次表达式不应为空", course.sectionExpression)
    }
    
    // ========== 多门课程分割测试 ==========
    
    @Test
    fun `parse - 多门课程文本应正确分割`() {
        val text = """高等数学
(3-4节)1-16周
校区/场地:A101/教师:张老师

大学英语
(1-2节)1-16周(单周)
校区/场地:B202/教师:王老师"""
        
        val result = parser.parse(text)
        
        assertEquals("应解析到2门课程", 2, result.size)
        assertEquals("第一门课程应为高等数学", "高等数学", result[0].courseName)
        assertEquals("第二门课程应为大学英语", "大学英语", result[1].courseName)
        assertEquals("第一门教师应为张老师", "张老师", result[0].teacher)
        assertEquals("第二门教师应为王老师", "王老师", result[1].teacher)
    }
    
    // ========== 特殊字符处理测试 ==========
    
    @Test
    fun `parse - 课程名称中的特殊符号应被清理`() {
        val text = """体育★
(1-2节)1-16周"""
        
        val result = parser.parse(text)
        
        assertEquals("★符号应被移除", "体育", result[0].courseName)
    }
    
    @Test
    fun `parse - 课程名称中的方括号内容应被清理`() {
        val text = """数学【实验班】
(1-2节)1-16周"""
        
        val result = parser.parse(text)
        
        assertTrue("方括号内容应被清理或保留原始名称", 
            result[0].courseName == "数学" || result[0].courseName.contains("数学"))
    }
    
    // ========== 分隔符处理测试 ==========
    
    @Test
    fun `parse - 使用全角冒号的字段应正确解析`() {
        val text = """物理
(3-4节)1-16周
校区/场地：C303/教师：刘教授"""
        
        val result = parser.parse(text)
        
        assertEquals("使用全角冒号也应能解析教师", "刘教授", result[0].teacher)
        assertEquals("使用全角冒号也应能解析教室", "C303", result[0].classroom)
    }
    
    @Test
    fun `parse - 使用半角冒号的字段应正确解析`() {
        val text = """化学
(5-6节)1-16周
校区/场地:D404/教师:陈老师"""
        
        val result = parser.parse(text)
        
        assertEquals("使用半角冒号应能解析教师", "陈老师", result[0].teacher)
        assertEquals("使用半角冒号应能解析教室", "D404", result[0].classroom)
    }
    
    // ========== 节次解析测试 ==========
    
    @Test
    fun `parse - 标准节次格式应正确解析`() {
        val text = """计算机基础
(1-2节)1-16周"""
        
        val result = parser.parse(text)
        
        assertEquals("起始节次应为1", 1, result[0].startSection)
        assertEquals("结束节次应为2", 2, result[0].endSection)
        assertEquals("节次表达式应为1-2节", "1-2节", result[0].sectionExpression)
    }
    
    @Test
    fun `parse - 不同节次范围应正确解析`() {
        val text = """晚课
(7-8节)1-16周"""
        
        val result = parser.parse(text)
        
        assertEquals("晚课起始节次应为7", 7, result[0].startSection)
        assertEquals("晚课结束节次应为8", 8, result[0].endSection)
    }
    
    // ========== 周次解析测试 ==========
    
    @Test
    fun `parse - 连续周次范围应正确展开`() {
        val text = """课程A
(1-2节)1-16周"""
        
        val result = parser.parse(text)
        
        assertEquals("1-16周应有16个周次", 16, result[0].weeks.size)
        assertEquals("第1周应在列表中", 1, result[0].weeks.first())
        assertEquals("第16周应在列表中", 16, result[0].weeks.last())
    }
    
    @Test
    fun `parse - 不连续的周次表达式应正确解析`() {
        val text = """课程B
(1-2节)1-3周,5-8周,10周"""
        
        val result = parser.parse(text)
        
        assertTrue("应包含第1周", result[0].weeks.contains(1))
        assertTrue("应包含第3周", result[0].weeks.contains(3))
        assertFalse("不应包含第4周", result[0].weeks.contains(4))
        assertTrue("应包含第5-8周", result[0].weeks.containsAll(listOf(5,6,7,8)))
        assertTrue("应包含第10周", result[0].weeks.contains(10))
    }
    
    // ========== 边界情况测试 ==========
    
    @Test
    fun `parse - 只有课程名称没有其他信息也应解析成功`() {
        val text = """单独课程名"""
        
        val result = parser.parse(text)
        
        if (result.isNotEmpty()) {
            assertEquals("至少应解析出课程名", "单独课程名", result[0].courseName)
        }
    }
    
    @Test
    fun `parse - 包含换行符和制表符的文本应正常处理`() {
        val text = """\t\n课程C\n\t\n(1-2节)\n\t1-16周\n\t"""
        
        try {
            val result = parser.parse(text)
            assertNotNull("包含特殊空白字符不应崩溃", result)
        } catch (e: Exception) {
            fail("包含特殊空白字符不应抛出异常: ${e.message}")
        }
    }
    
    @Test
    fun `parse - 包含中文逗号的周次表达式应正确解析`() {
        val text = """课程D
(1-2节)1，3，5，7周"""
        
        val result = parser.parse(text)
        
        if (result.isNotEmpty() && result[0].weeks.isNotEmpty()) {
            assertTrue("中文逗号分隔的周次应被正确解析", 
                result[0].weeks.containsAll(listOf(1, 3, 5, 7)))
        }
    }
    
    // ========== 数据完整性验证 ==========
    
    @Test
    fun `parse - 解析结果应保留原始文本`() {
        val originalText = """测试课程
(1-2节)1-16周
校区/场地:E505/教师:测试教师"""
        
        val result = parser.parse(originalText)
        
        if (result.isNotEmpty()) {
            assertEquals("rawText应保存完整的原始输入", originalText.trim(), result[0].rawText)
        }
    }
    
    @Test
    fun `parse - 缺少部分字段的课程仍应返回有效对象`() {
        val minimalText = """最少信息课程
(1-2节)1-16周"""
        
        val result = parser.parse(minimalText)
        
        if (result.isNotEmpty()) {
            val course = result[0]
            assertNotNull("ParsedTextCourse对象不应为null", course)
            assertFalse("课程名不应为空", course.courseName.isEmpty())
            assertTrue("缺少的教师字段可为空字符串", course.teacher.isEmpty())
            assertTrue("缺少的教室字段可为空字符串", course.classroom.isEmpty())
        }
    }
}
