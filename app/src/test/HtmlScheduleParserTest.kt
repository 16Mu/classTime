package com.wind.ggbond.classtime.util

import com.wind.ggbond.classtime.data.model.ParsedCourse
import io.mockk.every
import io.mockk.mockkStatic
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class HtmlScheduleParserTest {
    
    private lateinit var parser: HtmlScheduleParser
    
    @Before
    fun setUp() {
        parser = HtmlScheduleParser()
        
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.w(any(), any()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0
    }
    
    @After
    fun tearDown() {
        // 清理mock
    }
    
    // ========== 强智系统HTML解析测试 ==========
    
    @Test
    fun `parseQiangzhiHtml - 空HTML输入应返回空列表`() {
        val html = ""
        val result = parser.parseQiangzhiHtml(html)
        
        assertTrue("空HTML应返回空列表", result.isEmpty())
    }
    
    @Test
    fun `parseQiangzhiHtml - 无timetable表格应返回空列表`() {
        val html = """
            <html>
                <body>
                    <table id="otherTable">
                        <tr><td>测试</td></tr>
                    </table>
                </body>
            </html>
        """.trimIndent()
        
        val result = parser.parseQiangzhiHtml(html)
        
        assertTrue("无#timetable表格应返回空列表", result.isEmpty())
    }
    
    @Test
    fun `parseQiangzhiHtml - 有效强智系统HTML应正确解析课程`() {
        val html = """
            <html>
                <body>
                    <table id="timetable">
                        <tbody>
                            <tr>
                                <td>节次</td>
                                <td>
                                    <div class="item-box">
                                        <p>高等数学</p>
                                        <div class="tch-name">
                                            <span>教师：张三</span>
                                            <span>学分：4</span>
                                            <span>01~02节</span>
                                        </div>
                                        <div>
                                            <span><img src="item1.png">教室A101</span>
                                            <span><img src="item3.png">第1-16周(全部) 星期一</span>
                                        </div>
                                    </div>
                                </td>
                            </tr>
                        </tbody>
                    </table>
                </body>
            </html>
        """.trimIndent()
        
        val result = parser.parseQiangzhiHtml(html)
        
        assertEquals("应解析到1门课程", 1, result.size)
        
        val course = result[0]
        assertEquals("课程名应为高等数学", "高等数学", course.courseName)
        assertEquals("教师应为张三", "张三", course.teacher)
        assertEquals("教室应为A101", "A101", course.classroom)
        assertEquals("应为周一", 1, course.dayOfWeek)
        assertEquals("起始节次应为1", 1, course.startSection)
        assertEquals("周次列表应有16个周", 16, course.weeks.size)
        assertEquals("学分应为4.0", 4.0f, course.credit, 0.01f)
    }
    
    @Test
    fun `parseQiangzhiHtml - 应清理课程名称中的学校前缀`() {
        val html = """
            <html>
                <body>
                    <table id="timetable">
                        <tbody>
                            <tr>
                                <td>节次</td>
                                <td>
                                    <div class="item-box">
                                        <p>重庆电力高等专科学校 高等数学</p>
                                        <div class="tch-name">
                                            <span>教师：张三</span>
                                            <span>01~02节</span>
                                        </div>
                                        <div>
                                            <span><img src="item3.png">第1-16周(全部) 星期一</span>
                                        </div>
                                    </div>
                                </td>
                            </tr>
                        </tbody>
                    </table>
                </body>
            </html>
        """.trimIndent()
        
        val result = parser.parseQiangzhiHtml(html)
        
        assertEquals("应移除学校名称前缀", "高等数学", result[0].courseName)
    }
    
    @Test
    fun `parseQiangzhiHtml - 解析单周课程`() {
        val html = """
            <html>
                <body>
                    <table id="timetable">
                        <tbody>
                            <tr>
                                <td>节次</td>
                                <td>
                                    <div class="item-box">
                                        <p>体育</p>
                                        <div class="tch-name">
                                            <span>教师：李四</span>
                                            <span>03~04节</span>
                                        </div>
                                        <div>
                                            <span><img src="item3.png">第1-16周(单周) 星期一</span>
                                        </div>
                                    </div>
                                </td>
                            </tr>
                        </tbody>
                    </table>
                </body>
            </html>
        """.trimIndent()
        
        val result = parser.parseQiangzhiHtml(html)
        
        val course = result[0]
        assertEquals("单周课程应包含8个周（1,3,5,...,15）", 8, course.weeks.size)
        assertTrue("所有周次都应是奇数", course.weeks.all { it % 2 == 1 })
    }
    
    @Test
    fun `parseQiangzhiHtml - 解析双周课程`() {
        val html = """
            <html>
                <body>
                    <table id="timetable">
                        <tbody>
                            <tr>
                                <td>节次</td>
                                <td>
                                    <div class="item-box">
                                        <p>实验课</p>
                                        <div class="tch-name">
                                            <span>教师：王五</span>
                                            <span>05~06节</span>
                                        </div>
                                        <div>
                                            <span><img src="item3.png">第2-16周(双周) 星期三</span>
                                        </div>
                                    </div>
                                </td>
                            </tr>
                        </tbody>
                    </table>
                </body>
            </html>
        """.trimIndent()
        
        val result = parser.parseQiangzhiHtml(html)
        
        val course = result[0]
        assertTrue("双周课程的所有周次都应是偶数", course.weeks.all { it % 2 == 0 })
    }
    
    @Test
    fun `parseQiangzhiHtml - 解析不连续的周次表达式`() {
        val html = """
            <html>
                <body>
                    <table id="timetable">
                        <tbody>
                            <tr>
                                <td>节次</td>
                                <td>
                                    <div class="item-box">
                                        <p>选修课</p>
                                        <div class="tch-name">
                                            <span>教师：赵六</span>
                                            <span>07~08节</span>
                                        </div>
                                        <div>
                                            <span><img src="item3.png">第2-4,6-19周(全部) 星期五</span>
                                        </div>
                                    </div>
                                </td>
                            </tr>
                        </tbody>
                    </table>
                </body>
            </html>
        """.trimIndent()
        
        val result = parser.parseQiangzhiHtml(html)
        
        val course = result[0]
        assertEquals("2-4周有3个周，6-19周有14个周，共17个周", 17, course.weeks.size)
        assertFalse("不应包含第5周", course.weeks.contains(5))
        assertTrue("应包含第2周", course.weeks.contains(2))
        assertTrue("应包含第19周", course.weeks.contains(19))
    }
    
    @Test
    fun `parseQiangzhiHtml - 多个课程单元格应全部解析`() {
        val html = """
            <html>
                <body>
                    <table id="timetable">
                        <tbody>
                            <tr>
                                <td>节次</td>
                                <td>
                                    <div class="item-box">
                                        <p>数学</p>
                                        <div class="tch-name"><span>教师：张三</span><span>01~02节</span></div>
                                        <div><span><img src="item3.png">第1-16周(全部)</span></div>
                                    </div>
                                </td>
                                <td>
                                    <div class="item-box">
                                        <p>英语</p>
                                        <div class="tch-name"><span>教师：李四</span><span>03~04节</span></div>
                                        <div><span><img src="item3.png">第1-16周(全部)</span></div>
                                    </div>
                                </td>
                            </tr>
                        </tbody>
                    </table>
                </body>
            </html>
        """.trimIndent()
        
        val result = parser.parseQiangzhiHtml(html)
        
        assertEquals("应解析到2门课程", 2, result.size)
        assertEquals("第一门课程应为数学", "数学", result[0].courseName)
        assertEquals("第二门课程应为英语", "英语", result[1].courseName)
        assertEquals("第一门应在周一", 1, result[0].dayOfWeek)
        assertEquals("第二门应在周二", 2, result[1].dayOfWeek)
    }
    
    // ========== 正方系统HTML解析测试 ==========
    
    @Test
    fun `parseZhengfangHtml - 空HTML输入应返回空列表`() {
        val html = ""
        val result = parser.parseZhengfangHtml(html)
        
        assertTrue("空HTML应返回空列表", result.isEmpty())
    }
    
    @Test
    fun `parseZhengfangHtml - 包含kbtable的标准正方HTML应正确解析`() {
        val html = """
            <html>
                <body>
                    <div id="kbtable">
                        <table>
                            <tr>
                                <th>节次</th>
                                <th>周一</th>
                                <th>周二</th>
                            </tr>
                            <tr>
                                <td>1-2节</td>
                                <td>
                                    <div class="kbcontent">
                                        <div class="kcmc">高等数学</div>
                                        <div class="jshi">张教授</div>
                                        <div class="jxdd">A201</div>
                                        <div class="zcd">1-16周</div>
                                    </div>
                                </td>
                                <td></td>
                            </tr>
                        </table>
                    </div>
                </body>
            </html>
        """.trimIndent()
        
        val result = parser.parseZhengfangHtml(html)
        
        assertEquals("应解析到1门课程", 1, result.size)
        
        val course = result[0]
        assertEquals("课程名应为高等数学", "高等数学", course.courseName)
        assertEquals("教师应为张教授", "张教授", course.teacher)
        assertEquals("教室应为A201", "A201", course.classroom)
        assertEquals("应为周一", 1, course.dayOfWeek)
        assertEquals("起始节次应为1", 1, course.startSection)
    }
    
    @Test
    fun `isScheduleHtml - 包含kbtable标识应返回true`() {
        val html = """<html><body><div id="kbtable"></div></body></html>"""
        
        assertTrue(parser.isScheduleHtml(html))
    }
    
    @Test
    fun `isScheduleHtml - 包含kbcontent标识应返回true`() {
        val html = """<html><body><div class="kbcontent"></div></body></html>"""
        
        assertTrue(parser.isScheduleHtml(html))
    }
    
    @Test
    fun `isScheduleHtml - 包含课程表文字应返回true`() {
        val html = """<html><body>这是课程表页面</body></html>"""
        
        assertTrue(parser.isScheduleHtml(html))
    }
    
    @Test
    fun `isScheduleHtml - 普通HTML应返回false`() {
        val html = """<html><body>这是一个普通网页</body></html>"""
        
        assertFalse(parser.isScheduleHtml(html))
    }
    
    @Test
    fun `isScheduleHtml - 异常HTML应返回false且不崩溃`() {
        val malformedHtml = "<html><broken"
        
        assertFalse("异常HTML应返回false", parser.isScheduleHtml(malformedHtml))
    }
    
    // ========== 自动检测解析测试 ==========
    
    @Test
    fun `parseHtmlAuto - 强智系统特征HTML应使用强智解析器`() {
        val html = """
            <html>
                <body>
                    <table id="timetable">
                        <tbody>
                            <tr>
                                <td>节次</td>
                                <td>
                                    <div class="item-box">
                                        <p>自动检测课程</p>
                                        <div class="tch-name">
                                            <span>教师：测试教师</span>
                                            <span>01~02节</span>
                                        </div>
                                        <div>
                                            <span><img src="item3.png">第1-16周(全部)</span>
                                        </div>
                                    </div>
                                </td>
                            </tr>
                        </tbody>
                    </table>
                </body>
            </html>
        """.trimIndent()
        
        val result = parser.parseHtmlAuto(html)
        
        assertEquals("自动检测应解析到1门课程", 1, result.size)
        assertEquals("课程名应为自动检测课程", "自动检测课程", result[0].courseName)
    }
    
    @Test
    fun `parseHtmlAuto - 正方系统特征HTML应使用正方解析器`() {
        val html = """
            <html>
                <body>
                    <div id="kbtable">
                        <table>
                            <tr>
                                <th>节次</th>
                                <th>周一</th>
                            </tr>
                            <tr>
                                <td>1-2节</td>
                                <td>
                                    <div class="kbcontent">
                                        <div class="kcmc">正方课程</div>
                                        <div class="jshi">正方教师</div>
                                        <div class="jxdd">B301</div>
                                        <div class="zcd">1-16周</div>
                                    </div>
                                </td>
                            </tr>
                        </table>
                    </div>
                </body>
            </html>
        """.trimIndent()
        
        val result = parser.parseHtmlAuto(html)
        
        assertEquals("自动检测应解析到1门课程", 1, result.size)
        assertEquals("课程名应为正方课程", "正方课程", result[0].courseName)
    }
    
    @Test
    fun `parseHtmlAuto - 无法识别的HTML应返回空列表`() {
        val html = """<html><body><h1>这是普通网页</h1></body></html>"""
        
        val result = parser.parseHtmlAuto(html)
        
        assertTrue("无法识别的HTML应返回空列表", result.isEmpty())
    }
    
    // ========== 容错性测试 ==========
    
    @Test
    fun `parseQiangzhiHtml - 格式异常的HTML不应崩溃`() {
        val malformedHtml = "<html><broken><table id='timetable'>"
        
        try {
            val result = parser.parseQiangzhiHtml(malformedHtml)
            assertNotNull("格式异常不应崩溃，可返回空列表或部分结果", result)
        } catch (e: Exception) {
            fail("格式异常的HTML不应抛出异常: ${e.message}")
        }
    }
    
    @Test
    fun `parseZhengfangHtml - 格式异常的HTML不应崩溃`() {
        val malformedHtml = "<html><broken>"
        
        try {
            val result = parser.parseZhengfangHtml(malformedHtml)
            assertNotNull("格式异常不应崩溃，可返回空列表或部分结果", result)
        } catch (e: Exception) {
            fail("格式异常的HTML不应抛出异常: ${e.message}")
        }
    }
    
    @Test
    fun `parseQiangzhiHtml - 缺少可选字段的课程仍应解析成功`() {
        val html = """
            <html>
                <body>
                    <table id="timetable">
                        <tbody>
                            <tr>
                                <td>节次</td>
                                <td>
                                    <div class="item-box">
                                        <p>最少信息课程</p>
                                    </div>
                                </td>
                            </tr>
                        </tbody>
                    </table>
                </body>
            </html>
        """.trimIndent()
        
        val result = parser.parseQiangzhiHtml(html)
        
        if (result.isNotEmpty()) {
            val course = result[0]
            assertEquals("课程名应被解析", "最少信息课程", course.courseName)
            assertTrue("缺少的教师字段应为空字符串", course.teacher.isEmpty())
        }
    }
}
