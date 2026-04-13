package com.wind.ggbond.classtime.util

import io.mockk.every
import io.mockk.mockkStatic
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.Month

class DateUtilsTest {
    
    @Before
    fun setUp() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.w(any(), any()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0
    }
    
    @After
    fun tearDown() {
    }
    
    // ========== calculateWeekNumber 测试 ==========
    
    @Test
    fun `calculateWeekNumber - 学期第一天应返回第1周`() {
        val semesterStart = LocalDate.of(2024, Month.SEPTEMBER, 2)  // 周一
        
        val weekNumber = DateUtils.calculateWeekNumber(semesterStart, semesterStart)
        
        assertEquals("学期第一天应为第1周", 1, weekNumber)
    }
    
    @Test
    fun `calculateWeekNumber - 学期第一周内应返回第1周`() {
        val semesterStart = LocalDate.of(2024, Month.SEPTEMBER, 2)  // 周一
        val wednesday = LocalDate.of(2024, Month.SEPTEMBER, 4)     // 周三
        
        val weekNumber = DateUtils.calculateWeekNumber(semesterStart, wednesday)
        
        assertEquals("学期第一周内的任意日期应返回第1周", 1, weekNumber)
    }
    
    @Test
    fun `calculateWeekNumber - 第二周周一应返回第2周`() {
        val semesterStart = LocalDate.of(2024, Month.SEPTEMBER, 2)  // 周一
        val secondWeekMonday = LocalDate.of(2024, Month.SEPTEMBER, 9)  // 下周一
        
        val weekNumber = DateUtils.calculateWeekNumber(semesterStart, secondWeekMonday)
        
        assertEquals("第二周周一应返回第2周", 2, weekNumber)
    }
    
    @Test
    fun `calculateWeekNumber - 第三周周五应返回第3周`() {
        val semesterStart = LocalDate.of(2024, Month.SEPTEMBER, 2)  // 周一
        val thirdWeekFriday = LocalDate.of(2024, Month.SEPTEMBER, 20)  // 第三周周五
        
        val weekNumber = DateUtils.calculateWeekNumber(semesterStart, thirdWeekFriday)
        
        assertEquals("第三周周五应返回第3周", 3, weekNumber)
    }
    
    @Test
    fun `calculateWeekNumber - 第10周应正确计算`() {
        val semesterStart = LocalDate.of(2024, Month.SEPTEMBER, 2)  // 周一
        // 第10周周一 = 学期开始 + 9周
        val tenthWeekMonday = semesterStart.plusWeeks(9)
        
        val weekNumber = DateUtils.calculateWeekNumber(semesterStart, tenthWeekMonday)
        
        assertEquals("第10周应返回10", 10, weekNumber)
    }
    
    @Test
    fun `calculateWeekNumber - currentDate早于startDate应返回第1周`() {
        val semesterStart = LocalDate.of(2024, Month.SEPTEMBER, 2)
        val beforeStart = LocalDate.of(2024, Month.AUGUST, 26)  // 学期开始前一周
        
        val weekNumber = DateUtils.calculateWeekNumber(semesterStart, beforeStart)
        
        assertEquals("日期早于学期开始应返回第1周（而不是负数或0）", 1, weekNumber)
    }
    
    @Test
    fun `calculateWeekNumber - 跨年学期的周次计算应正确`() {
        // 假设学期从2024年9月开始，到2025年1月
        val semesterStart = LocalDate.of(2024, Month.SEPTEMBER, 2)
        val januaryDate = LocalDate.of(2025, Month.JANUARY, 6)  // 2025年1月（约第18周）
        
        val weekNumber = DateUtils.calculateWeekNumber(semesterStart, januaryDate)
        
        assertTrue("跨年后的日期应有合理的周次（>15且<25）", weekNumber in 15..25)
    }
    
    @Test
    fun `calculateWeekNumber - 极端大的周次应被限制在最大值范围内`() {
        val semesterStart = LocalDate.of(2024, Month.SEPTEMBER, 2)
        // 模拟一个非常远的日期（超过30周）
        val farFutureDate = semesterStart.plusWeeks(40)
        
        val weekNumber = DateUtils.calculateWeekNumber(semesterStart, farFutureDate)
        
        assertTrue("极端大的周次应被限制在MAX_WEEK_NUMBER（30）以内", 
            weekNumber <= Constants.Semester.MAX_WEEK_NUMBER)
    }
    
    // ========== getMondayOfWeek 测试 ==========
    
    @Test
    fun `getMondayOfWeek - 第1周的周一应为学期开始的那个周一或之前最近的周一`() {
        val semesterStart = LocalDate.of(2024, Month.SEPTEMBER, 4)  // 假设学期从周三开始
        
        val mondayOfWeek1 = DateUtils.getMondayOfWeek(semesterStart, 1)
        
        assertTrue("第1周的周一应在学期开始当天或之前", 
            !mondayOfWeek1.isAfter(semesterStart))
        assertEquals("第1周的周一应该是周一", 
            java.time.DayOfWeek.MONDAY, mondayOfWeek1.dayOfWeek)
    }
    
    @Test
    fun `getMondayOfWeek - 第2周的周一应为第1周周一加7天`() {
        val semesterStart = LocalDate.of(2024, Month.SEPTEMBER, 2)  // 周一
        
        val mondayOfWeek1 = DateUtils.getMondayOfWeek(semesterStart, 1)
        val mondayOfWeek2 = DateUtils.getMondayOfWeek(semesterStart, 2)
        
        val daysBetween = java.time.temporal.ChronoUnit.DAYS.between(mondayOfWeek1, mondayOfWeek2)
        assertEquals("第2周周一与第1周周一应相差7天", 7L, daysBetween)
    }
    
    @Test(expected = IllegalArgumentException::class)
    fun `getMondayOfWeek - 无效的周次（0）应抛出异常`() {
        val semesterStart = LocalDate.of(2024, Month.SEPTEMBER, 2)
        
        DateUtils.getMondayOfWeek(semesterStart, 0)  // 应抛出IllegalArgumentException
    }
    
    @Test(expected = IllegalArgumentException::class)
    fun `getMondayOfWeek - 负数周次应抛出异常`() {
        val semesterStart = LocalDate.of(2024, Month.SEPTEMBER, 2)
        
        DateUtils.getMondayOfWeek(semesterStart, -5)  // 应抛出IllegalArgumentException
    }
    
    @Test
    fun `getMondayOfWeek - 第20周的周一计算应正确`() {
        val semesterStart = LocalDate.of(2024, Month.SEPTEMBER, 2)  // 周一
        val expectedMonday = semesterStart.plusWeeks(19)  // 第20周周一
        
        val actualMonday = DateUtils.getMondayOfWeek(semesterStart, 20)
        
        assertEquals("第20周周一计算应正确", expectedMonday, actualMonday)
    }
    
    // ========== getDayOfWeekName 测试 ==========
    
    @Test
    fun `getDayOfWeekName - 有效输入应返回正确的中文星期名称`() {
        assertEquals("1应为周一", "周一", DateUtils.getDayOfWeekName(1))
        assertEquals("2应为周二", "周二", DateUtils.getDayOfWeekName(2))
        assertEquals("3应为周三", "周三", DateUtils.getDayOfWeekName(3))
        assertEquals("4应为周四", "周四", DateUtils.getDayOfWeekName(4))
        assertEquals("5应为周五", "周五", DateUtils.getDayOfWeekName(5))
        assertEquals("6应为周六", "周六", DateUtils.getDayOfWeekName(6))
        assertEquals("7应为周日", "周日", DateUtils.getDayOfWeekName(7))
    }
    
    @Test
    fun `getDayOfWeekName - 边界值0应返回空字符串`() {
        val result = DateUtils.getDayOfWeekName(0)
        
        assertEquals("超出范围的值（0）应返回空字符串", "", result)
    }
    
    @Test
    fun `getDayOfWeekName - 边界值8应返回空字符串`() {
        val result = DateUtils.getDayOfWeekName(8)
        
        assertEquals("超出范围的值（8）应返回空字符串", "", result)
    }
    
    @Test
    fun `getDayOfWeekName - 负数应返回空字符串`() {
        val result = DateUtils.getDayOfWeekName(-1)
        
        assertEquals("负数应返回空字符串", "", result)
    }
    
    // ========== getDayOfWeekShortName 测试 ==========
    
    @Test
    fun `getDayOfWeekShortName - 有效输入应返回正确的简短名称`() {
        assertEquals("1应为一", "一", DateUtils.getDayOfWeekShortName(1))
        assertEquals("2应为二", "二", DateUtils.getDayOfWeekShortName(2))
        assertEquals("3应为三", "三", DateUtils.getDayOfWeekShortName(3))
        assertEquals("4应为四", "四", DateUtils.getDayOfWeekShortName(4))
        assertEquals("5应为五", "五", DateUtils.getDayOfWeekShortName(5))
        assertEquals("6应为六", "六", DateUtils.getDayOfWeekShortName(6))
        assertEquals("7应为日", "日", DateUtils.getDayOfWeekShortName(7))
    }
    
    @Test
    fun `getDayOfWeekShortName - 无效输入应返回空字符串`() {
        assertEquals("0应返回空字符串", "", DateUtils.getDayOfWeekShortName(0))
        assertEquals("100应返回空字符串", "", DateUtils.getDayOfWeekShortName(100))
        assertEquals("-1应返回空字符串", "", DateUtils.getDayOfWeekShortName(-1))
    }
    
    // ========== 综合场景测试 ==========
    
    @Test
    fun `综合测试 - 完整的学期周次计算流程`() {
        // 场景：2024年秋季学期，9月2日开学（周一），共20周
        val semesterStart = LocalDate.of(2024, Month.SEPTEMBER, 2)
        
        // 验证开学当天的周次
        val startWeek = DateUtils.calculateWeekNumber(semesterStart, semesterStart)
        assertEquals("开学当天应为第1周", 1, startWeek)
        
        // 验证第1周周一的日期
        val week1Monday = DateUtils.getMondayOfWeek(semesterStart, 1)
        assertEquals("第1周周一应为9月2日", semesterStart, week1Monday)
        
        // 验证第10周的周一
        val week10Monday = DateUtils.getMondayOfWeek(semesterStart, 10)
        val expectedWeek10Monday = semesterStart.plusWeeks(9)
        assertEquals("第10周周一应为11月4日左右", expectedWeek10Monday, week10Monday)
        
        // 验证第10周某天的周次
        val week10Wednesday = week10Monday.plusDays(2)
        val calculatedWeek = DateUtils.calculateWeekNumber(semesterStart, week10Wednesday)
        assertEquals("第10周周三的周次应为10", 10, calculatedWeek)
        
        // 验证星期几的名称
        assertEquals("周三的中文名称为周三", "周三", DateUtils.getDayOfWeekName(3))
        assertEquals("周三的简称为三", "三", DateUtils.getDayOfWeekShortName(3))
    }

    @Test
    fun `calculateWeekNumber - 闰年2月29日学期开始应正确计算`() {
        val semesterStart = LocalDate.of(2024, Month.FEBRUARY, 29)
        val week2 = LocalDate.of(2024, Month.MARCH, 7)

        val weekNumber = DateUtils.calculateWeekNumber(semesterStart, week2)

        assertEquals("闰年2月29日开始的学期第2周应返回2", 2, weekNumber)
    }

    @Test
    fun `calculateWeekNumber - 非闰年3月1日学期开始应正确计算`() {
        val semesterStart = LocalDate.of(2023, Month.MARCH, 1)
        val week3 = LocalDate.of(2023, Month.MARCH, 15)

        val weekNumber = DateUtils.calculateWeekNumber(semesterStart, week3)

        assertEquals("非闰年3月1日开始的学期第3周应返回3", 3, weekNumber)
    }

    @Test
    fun `calculateWeekNumber - 闰年跨2月学期周次连续性`() {
        val semesterStart = LocalDate.of(2024, Month.FEBRUARY, 26)
        val beforeLeapDay = LocalDate.of(2024, Month.FEBRUARY, 28)
        val afterLeapDay = LocalDate.of(2024, Month.FEBRUARY, 29)
        val marchFirst = LocalDate.of(2024, Month.MARCH, 1)

        val weekBefore = DateUtils.calculateWeekNumber(semesterStart, beforeLeapDay)
        val weekLeap = DateUtils.calculateWeekNumber(semesterStart, afterLeapDay)
        val weekMarch = DateUtils.calculateWeekNumber(semesterStart, marchFirst)

        assertEquals("闰日前应为第1周", 1, weekBefore)
        assertEquals("闰日应为第1周", 1, weekLeap)
        assertEquals("3月1日应为第1周", 1, weekMarch)
    }

    @Test
    fun `calculateWeekNumber - 学期从2024年跨到2025年应正确计算`() {
        val semesterStart = LocalDate.of(2024, Month.SEPTEMBER, 2)
        val newYearDay = LocalDate.of(2025, Month.JANUARY, 1)
        val expectedWeek = ChronoUnit.DAYS.between(semesterStart, newYearDay) / 7 + 1

        val weekNumber = DateUtils.calculateWeekNumber(semesterStart, newYearDay)

        assertEquals(expectedWeek.toInt(), weekNumber)
    }

    @Test
    fun `calculateWeekNumber - 学期从2023年跨到2024闰年应正确计算`() {
        val semesterStart = LocalDate.of(2023, Month.SEPTEMBER, 4)
        val feb29_2024 = LocalDate.of(2024, Month.FEBRUARY, 29)

        val weekNumber = DateUtils.calculateWeekNumber(semesterStart, feb29_2024)

        assertTrue("跨入闰年的学期周次应在合理范围", weekNumber in 20..30)
    }

    @Test
    fun `calculateWeekNumber - 学期跨越两个闰年应正确计算`() {
        val semesterStart = LocalDate.of(2023, Month.SEPTEMBER, 4)
        val farDate = LocalDate.of(2025, Month.MARCH, 1)

        val weekNumber = DateUtils.calculateWeekNumber(semesterStart, farDate)

        assertTrue("跨越两个闰年的学期周次应被限制在MAX_WEEK_NUMBER", weekNumber <= Constants.Semester.MAX_WEEK_NUMBER)
    }

    @Test
    fun `calculateWeekNumber - 学期开始于12月31日跨年应正确计算`() {
        val semesterStart = LocalDate.of(2024, Month.DECEMBER, 31)
        val nextJan7 = LocalDate.of(2025, Month.JANUARY, 7)

        val weekNumber = DateUtils.calculateWeekNumber(semesterStart, nextJan7)

        assertEquals("跨年学期第2周应返回2", 2, weekNumber)
    }

    @Test
    fun `calculateWeekNumber - LocalDate最小值不应崩溃`() {
        val semesterStart = LocalDate.of(2000, Month.JANUARY, 1)
        val currentDate = LocalDate.of(2000, Month.JANUARY, 8)

        val weekNumber = DateUtils.calculateWeekNumber(semesterStart, currentDate)

        assertEquals("极早日期应正常计算", 2, weekNumber)
    }

    @Test
    fun `calculateWeekNumber - 学期开始和当前日期相同应返回1`() {
        val semesterStart = LocalDate.of(2024, Month.SEPTEMBER, 2)

        val weekNumber = DateUtils.calculateWeekNumber(semesterStart, semesterStart)

        assertEquals("相同日期应返回第1周", 1, weekNumber)
    }

    @Test
    fun `calculateWeekNumber - 学期第30周边界应返回30`() {
        val semesterStart = LocalDate.of(2024, Month.SEPTEMBER, 2)
        val week30 = semesterStart.plusWeeks(29)

        val weekNumber = DateUtils.calculateWeekNumber(semesterStart, week30)

        assertEquals("第30周应返回30", 30, weekNumber)
    }

    @Test
    fun `calculateWeekNumber - 超过30周应被限制在MAX_WEEK_NUMBER`() {
        val semesterStart = LocalDate.of(2024, Month.SEPTEMBER, 2)
        val week35 = semesterStart.plusWeeks(34)

        val weekNumber = DateUtils.calculateWeekNumber(semesterStart, week35)

        assertEquals("超过30周应被限制在MAX_WEEK_NUMBER", Constants.Semester.MAX_WEEK_NUMBER, weekNumber)
    }

    @Test
    fun `calculateWeekNumber - 学期开始于周六应正确计算`() {
        val semesterStart = LocalDate.of(2024, Month.SEPTEMBER, 7)
        val nextMonday = LocalDate.of(2024, Month.SEPTEMBER, 9)

        val week1 = DateUtils.calculateWeekNumber(semesterStart, semesterStart)
        val week2 = DateUtils.calculateWeekNumber(semesterStart, nextMonday)

        assertEquals("周六开始第1周应为1", 1, week1)
        assertEquals("下周一应为第2周", 2, week2)
    }

    @Test
    fun `calculateWeekNumber - 学期开始于周日应正确计算`() {
        val semesterStart = LocalDate.of(2024, Month.SEPTEMBER, 1)
        val nextMonday = LocalDate.of(2024, Month.SEPTEMBER, 2)

        val week1 = DateUtils.calculateWeekNumber(semesterStart, semesterStart)
        val week2 = DateUtils.calculateWeekNumber(semesterStart, nextMonday)

        assertEquals("周日开始第1周应为1", 1, week1)
        assertEquals("下周一应为第2周", 2, week2)
    }

    @Test
    fun `getMondayOfWeek - 学期从周三开始第1周周一应在学期开始前`() {
        val semesterStart = LocalDate.of(2024, Month.SEPTEMBER, 4)

        val monday = DateUtils.getMondayOfWeek(semesterStart, 1)

        assertEquals("第1周周一应为9月2日", LocalDate.of(2024, Month.SEPTEMBER, 2), monday)
    }

    @Test
    fun `getMondayOfWeek - 学期从周五开始第1周周一应在学期开始前`() {
        val semesterStart = LocalDate.of(2024, Month.SEPTEMBER, 6)

        val monday = DateUtils.getMondayOfWeek(semesterStart, 1)

        assertEquals("第1周周一应为9月2日", LocalDate.of(2024, Month.SEPTEMBER, 2), monday)
    }

    @Test
    fun `getMondayOfWeek - 跨年第20周周一应正确计算`() {
        val semesterStart = LocalDate.of(2024, Month.SEPTEMBER, 2)
        val expected = semesterStart.minusDays(0).plusWeeks(19)

        val monday = DateUtils.getMondayOfWeek(semesterStart, 20)

        assertEquals(expected, monday)
    }

    @Test
    fun `getDayOfWeekName - 极大值应返回空字符串`() {
        val result = DateUtils.getDayOfWeekName(Int.MAX_VALUE)

        assertEquals("极大值应返回空字符串", "", result)
    }

    @Test
    fun `getDayOfWeekName - 极小值应返回空字符串`() {
        val result = DateUtils.getDayOfWeekName(Int.MIN_VALUE)

        assertEquals("极小值应返回空字符串", "", result)
    }

    @Test
    fun `getDayOfWeekShortName - 极大值应返回空字符串`() {
        val result = DateUtils.getDayOfWeekShortName(Int.MAX_VALUE)

        assertEquals("极大值应返回空字符串", "", result)
    }

    @Test
    fun `getDayOfWeekShortName - 极小值应返回空字符串`() {
        val result = DateUtils.getDayOfWeekShortName(Int.MIN_VALUE)

        assertEquals("极小值应返回空字符串", "", result)
    }

    @Test
    fun `calculateWeekNumber - 学期恰好7天应返回第2周`() {
        val semesterStart = LocalDate.of(2024, Month.SEPTEMBER, 2)
        val day7 = semesterStart.plusDays(7)

        val weekNumber = DateUtils.calculateWeekNumber(semesterStart, day7)

        assertEquals("第7天应返回第2周", 2, weekNumber)
    }

    @Test
    fun `calculateWeekNumber - 学期第6天应返回第1周`() {
        val semesterStart = LocalDate.of(2024, Month.SEPTEMBER, 2)
        val day6 = semesterStart.plusDays(6)

        val weekNumber = DateUtils.calculateWeekNumber(semesterStart, day6)

        assertEquals("第6天应返回第1周", 1, weekNumber)
    }
}
