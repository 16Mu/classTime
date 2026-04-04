package com.wind.ggbond.classtime.widget.data

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class WidgetModelsTest {

    @Test
    fun `WidgetDisplayData empty - should create valid empty data`() {
        val emptyData = WidgetDisplayData.empty("无课表数据")

        assertEquals("空消息应匹配", "无课表数据", emptyData.emptyMessage)
        assertTrue("课程列表应为空", emptyData.courseItems.isEmpty())
        assertNotNull("日期文本不应为null", emptyData.dateText)
        assertNotNull("星期文本不应为null", emptyData.dayOfWeekText)
    }

    @Test
    fun `WidgetDisplayData - should hold correct data`() {
        val data = WidgetDisplayData(
            dateText = "4月4日",
            dayOfWeekText = "周五",
            weekNumberText = "第10周",
            courseItems = listOf(
                WidgetCourseItem(
                    courseName = "高等数学",
                    classroom = "教A101",
                    teacher = "张三",
                    startSection = 1,
                    sectionCount = 2,
                    startTimeText = "08:00",
                    endTimeText = "08:45",
                    color = "#F44336",
                    isOngoing = true
                )
            ),
            emptyMessage = null,
            progressText = "已上1节/共1节"
        )

        assertEquals("4月4日", data.dateText)
        assertEquals("周五", data.dayOfWeekText)
        assertEquals("第10周", data.weekNumberText)
        assertEquals(1, data.courseItems.size)
        assertNull(data.emptyMessage)
        assertEquals("已上1节/共1节", data.progressText)
    }

    @Test
    fun `WidgetCourseItem - should store all fields correctly`() {
        val item = WidgetCourseItem(
            courseName = "大学英语",
            classroom = "教B202",
            teacher = "李四",
            startSection = 3,
            sectionCount = 2,
            startTimeText = "10:00",
            endTimeText = "10:45",
            color = "#2196F3",
            isOngoing = false
        )

        assertEquals("大学英语", item.courseName)
        assertEquals("教B202", item.classroom)
        assertEquals("李四", item.teacher)
        assertEquals(3, item.startSection)
        assertEquals(2, item.sectionCount)
        assertFalse(item.isOngoing)
    }

    @Test
    fun `NextClassDisplayData - should represent no class state`() {
        val noClass = NextClassDisplayData(hasNextClass = false)

        assertFalse(noClass.hasNextClass)
        assertEquals("", noClass.courseName)
        assertNotNull("消息可为null", noClass.message)
    }

    @Test
    fun `NextClassDisplayData - should represent ongoing class`() {
        val ongoing = NextClassDisplayData(
            hasNextClass = true,
            isOngoing = true,
            courseName = "线性代数",
            classroom = "教C301",
            timeText = "08:00-08:45",
            sectionText = "第1-2节"
        )

        assertTrue(ongoing.isOngoing)
        assertEquals("线性代数", ongoing.courseName)
        assertEquals("08:00-08:45", ongoing.timeText)
    }

    @Test
    fun `WeekOverviewData empty - should create valid empty overview`() {
        val emptyOverview = WeekOverviewData.empty("无课表")

        assertEquals("无课表", emptyOverview.emptyMessage)
        assertTrue(emptyOverview.days.isEmpty())
        assertTrue(emptyOverview.todayDayOfWeek in 1..7)
    }

    @Test
    fun `DayCourseInfo - should store course summary correctly`() {
        val dayInfo = DayCourseInfo(dayOfWeek = 1, courseCount = 4, courseNames = listOf("数学", "英语", "物理"))

        assertEquals(1, dayInfo.dayOfWeek)
        assertEquals(4, dayInfo.courseCount)
        assertEquals(3, dayInfo.courseNames.size)
    }
}
