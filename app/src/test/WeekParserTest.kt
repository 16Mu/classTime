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
}
