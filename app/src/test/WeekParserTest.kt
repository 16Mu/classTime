package com.wind.ggbond.classtime.util

import io.mockk.any
import io.mockk.every
import io.mockk.mockkStatic
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class WeekParserTest {

    @Before
    fun setUp() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.w(any(), any()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0
    }

    @Test
    fun `parseWeekExpression - should parse simple range`() {
        val result = WeekParser.parseWeekExpression("1-16周")

        assertEquals(16, result.size)
        assertEquals(1, result.first())
        assertEquals(16, result.last())
    }

    @Test
    fun `parseWeekExpression - should parse odd weeks`() {
        val result = WeekParser.parseWeekExpression("1-16单周")

        assertTrue("所有周次应为奇数", result.all { it % 2 == 1 })
        assertTrue("应包含第1周", result.contains(1))
        assertTrue("应包含第15周", result.contains(15))
        assertFalse("不应包含偶数周", result.contains(2))
    }

    @Test
    fun `parseWeekExpression - should parse even weeks`() {
        val result = WeekParser.parseWeekExpression("1-16双周")

        assertTrue("所有周次应为偶数", result.all { it % 2 == 0 })
        assertTrue("应包含第2周", result.contains(2))
        assertTrue("应包含第16周", result.contains(16))
        assertFalse("不应包含奇数周", result.contains(1))
    }

    @Test
    fun `parseWeekExpression - should parse comma separated values`() {
        val result = WeekParser.parseWeekExpression("1,3,5,7")

        assertEquals(4, result.size)
        assertTrue(result.containsAll(listOf(1, 3, 5, 7)))
    }

    @Test
    fun `parseWeekExpression - should handle complex expression`() {
        val result = WeekParser.parseWeekExpression("1-3单周,4-6双周,7-15周,19周")

        assertTrue("结果不应为空", result.isNotEmpty())
        assertTrue("应包含第1周（单周）", result.contains(1))
        assertTrue("应包含第4周（双周）", result.contains(4))
        assertTrue("应包含第19周", result.contains(19))
    }

    @Test
    fun `parseWeekExpression - should return empty for blank input`() {
        val result = WeekParser.parseWeekExpression("")
        assertTrue("空输入应返回空列表", result.isEmpty())

        val result2 = WeekParser.parseWeekExpression("   ")
        assertTrue("空白输入应返回空列表", result2.isEmpty())
    }

    @Test
    fun `formatWeekList - should format consecutive weeks as range`() {
        val weeks = listOf(1, 2, 3, 4, 5)

        val result = WeekParser.formatWeekList(weeks)

        assertTrue("连续周次应包含范围符号", result.contains("1-5"))
        assertTrue("应以'周'结尾", result.endsWith("周"))
    }

    @Test
    fun `formatWeekList - should format odd weeks with suffix`() {
        val weeks = listOf(1, 3, 5, 7, 9)

        val result = WeekParser.formatWeekList(weeks)

        assertTrue("奇数周应以'单周'结尾", result.endsWith("单周"))
    }

    @Test
    fun `formatWeekList - should format even weeks with suffix`() {
        val weeks = listOf(2, 4, 6, 8, 10)

        val result = WeekParser.formatWeekList(weeks)

        assertTrue("偶数周应以'双周'结尾", result.endsWith("双周"))
    }

    @Test
    fun `parseWeekExpression - 全角逗号分隔应正确解析`() {
        val result = WeekParser.parseWeekExpression("1，3，5")

        assertEquals(3, result.size)
        assertTrue(result.containsAll(listOf(1, 3, 5)))
    }

    @Test
    fun `parseWeekExpression - 带节次信息的表达式应正确解析`() {
        val result = WeekParser.parseWeekExpression("(7-8节)1-3周(单)")

        assertTrue("应包含奇数周", result.containsAll(listOf(1, 3)))
        assertFalse("不应包含偶数周", result.contains(2))
    }

    @Test
    fun `parseWeekExpression - 全角括号节次信息应正确解析`() {
        val result = WeekParser.parseWeekExpression("（7-8节）1-3周（单）")

        assertTrue("应包含奇数周", result.containsAll(listOf(1, 3)))
        assertFalse("不应包含偶数周", result.contains(2))
    }

    @Test
    fun `parseWeekExpression - 无节字的括号节次应正确解析`() {
        val result = WeekParser.parseWeekExpression("(7-8)1-3周")

        assertTrue("应包含1-3周", result.containsAll(listOf(1, 2, 3)))
    }

    @Test
    fun `parseWeekExpression - 全角括号无节字应正确解析`() {
        val result = WeekParser.parseWeekExpression("（7-8）1-3周")

        assertTrue("应包含1-3周", result.containsAll(listOf(1, 2, 3)))
    }

    @Test
    fun `parseWeekExpression - 仅数字应解析为单周`() {
        val result = WeekParser.parseWeekExpression("5")

        assertEquals(1, result.size)
        assertTrue(result.contains(5))
    }

    @Test
    fun `parseWeekExpression - 纯文本无数字应返回空列表`() {
        val result = WeekParser.parseWeekExpression("abc")

        assertTrue("纯文本应返回空列表", result.isEmpty())
    }

    @Test
    fun `parseWeekExpression - 混合数字和文本应提取数字部分`() {
        val result = WeekParser.parseWeekExpression("第1-16周")

        assertEquals(16, result.size)
        assertEquals(1, result.first())
        assertEquals(16, result.last())
    }

    @Test
    fun `parseWeekExpression - 周次0应被包含在结果中`() {
        val result = WeekParser.parseWeekExpression("0-3")

        assertTrue("0周应被包含", result.contains(0))
        assertTrue("1-3周应被包含", result.containsAll(listOf(1, 2, 3)))
    }

    @Test
    fun `parseWeekExpression - 负数周次应被解析`() {
        val result = WeekParser.parseWeekExpression("-1-3")

        assertTrue("应包含1-3周", result.containsAll(listOf(1, 2, 3)))
    }

    @Test
    fun `parseWeekExpression - 超大周数应被解析`() {
        val result = WeekParser.parseWeekExpression("1-100")

        assertEquals(100, result.size)
        assertEquals(1, result.first())
        assertEquals(100, result.last())
    }

    @Test
    fun `parseWeekExpression - 单个超大周数应被解析`() {
        val result = WeekParser.parseWeekExpression("999")

        assertEquals(1, result.size)
        assertTrue(result.contains(999))
    }

    @Test
    fun `parseWeekExpression - 复杂混合格式表达式`() {
        val result = WeekParser.parseWeekExpression("(1-2节)1-3单周,4-6双周,7-15周,19周")

        assertTrue("应包含第1周", result.contains(1))
        assertTrue("应包含第4周", result.contains(4))
        assertTrue("应包含第7-15周", result.containsAll((7..15).toList()))
        assertTrue("应包含第19周", result.contains(19))
        assertFalse("不应包含第2周(单周段)", result.contains(2))
        assertFalse("不应包含第5周(双周段)", result.contains(5))
    }

    @Test
    fun `parseWeekExpression - 连续单周标记应只取奇数`() {
        val result = WeekParser.parseWeekExpression("1-5单周")

        assertTrue("所有周次应为奇数", result.all { it % 2 == 1 })
        assertEquals(3, result.size)
    }

    @Test
    fun `parseWeekExpression - 连续双周标记应只取偶数`() {
        val result = WeekParser.parseWeekExpression("2-6双周")

        assertTrue("所有周次应为偶数", result.all { it % 2 == 0 })
        assertEquals(3, result.size)
    }

    @Test
    fun `parseWeekExpression - 多个逗号连续应不崩溃`() {
        val result = WeekParser.parseWeekExpression(",,,1-3,,5,,")

        assertTrue("应能处理多余逗号", result.containsAll(listOf(1, 2, 3, 5)))
    }

    @Test
    fun `parseWeekExpression - 范围起始大于结束应返回空或反向`() {
        val result = WeekParser.parseWeekExpression("5-3")

        assertTrue("起始大于结束应返回空列表", result.isEmpty())
    }

    @Test
    fun `parseWeekExpression - 单周标记紧跟数字应正确解析`() {
        val result = WeekParser.parseWeekExpression("1单,3单")

        assertTrue("应包含1", result.contains(1))
        assertTrue("应包含3", result.contains(3))
    }

    @Test
    fun `parseWeekExpression - 双周标记紧跟数字应正确解析`() {
        val result = WeekParser.parseWeekExpression("2双,4双")

        assertTrue("应包含2", result.contains(2))
        assertTrue("应包含4", result.contains(4))
    }

    @Test
    fun `formatWeekList - 空列表应返回空字符串`() {
        val result = WeekParser.formatWeekList(emptyList())

        assertEquals("空列表应返回空字符串", "", result)
    }

    @Test
    fun `formatWeekList - 单个周次应格式化为数字加周`() {
        val result = WeekParser.formatWeekList(listOf(5))

        assertTrue("单个周次应包含数字5", result.contains("5"))
        assertTrue("单个周次应以周结尾", result.endsWith("周"))
    }

    @Test
    fun `formatWeeksForDisplay - 空列表应返回空字符串`() {
        val result = WeekParser.formatWeeksForDisplay(emptyList())

        assertEquals("", result)
    }

    @Test
    fun `formatWeeksForDisplay - 单个周次应返回数字`() {
        val result = WeekParser.formatWeeksForDisplay(listOf(3))

        assertEquals("3", result)
    }

    @Test
    fun `formatWeeksForDisplay - 连续周次应格式化为范围`() {
        val result = WeekParser.formatWeeksForDisplay(listOf(1, 2, 3, 4, 5))

        assertTrue("连续周次应包含范围", result.contains("1-5"))
    }

    @Test
    fun `formatWeeksForDisplay - 奇数连续周次应标记单周`() {
        val result = WeekParser.formatWeeksForDisplay(listOf(1, 3, 5, 7))

        assertTrue("奇数连续周次应包含单周标记", result.contains("单周"))
    }

    @Test
    fun `formatWeeksForDisplay - 偶数连续周次应标记双周`() {
        val result = WeekParser.formatWeeksForDisplay(listOf(2, 4, 6, 8))

        assertTrue("偶数连续周次应包含双周标记", result.contains("双周"))
    }

    @Test
    fun `parseWeekExpression - Tab和换行混合应不崩溃`() {
        val result = WeekParser.parseWeekExpression("1-3\t周\n,5周")

        assertTrue("应能处理Tab和换行", result.isNotEmpty())
    }

    @Test
    fun `parseWeekExpression - 反向范围应返回空列表`() {
        val result = WeekParser.parseWeekExpression("10-1")

        assertTrue("反向范围应返回空列表", result.isEmpty())
    }
}
