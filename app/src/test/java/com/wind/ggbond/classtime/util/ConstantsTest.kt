package com.wind.ggbond.classtime.util

import org.junit.Assert.*
import org.junit.Test

class ConstantsTest {

    @Test
    fun `Course constants - should have valid ranges`() {
        assertTrue("最大课程名称长度应>0", Constants.Course.MAX_COURSE_NAME_LENGTH > 0)
        assertTrue("最大教师名称长度应>0", Constants.Course.MAX_TEACHER_NAME_LENGTH > 0)
        assertTrue("最大教室名称长度应>0", Constants.Course.MAX_CLASSROOM_NAME_LENGTH > 0)

        assertEquals("最小节次应为1", 1, Constants.Course.MIN_SECTION_NUMBER)
        assertTrue("最大节次应>=最小节次", Constants.Course.MAX_SECTION_NUMBER >= Constants.Course.MIN_SECTION_NUMBER)
        assertTrue("最大节数应>=最小节数", Constants.Course.MAX_SECTION_COUNT >= Constants.Course.MIN_SECTION_COUNT)
    }

    @Test
    fun `Course constants - day of week range should be 1-7`() {
        assertEquals("星期最小值应为1", 1, Constants.Course.MIN_DAY_OF_WEEK)
        assertEquals("星期最大值应为7", 7, Constants.Course.MAX_DAY_OF_WEEK)
    }

    @Test
    fun `Semester constants - week number should be valid`() {
        assertEquals("最小周数应为1", 1, Constants.Semester.MIN_WEEK_NUMBER)
        assertTrue("最大周数应合理（<=35）", Constants.Semester.MAX_WEEK_NUMBER <= 35)
        assertTrue("默认总周数应在范围内",
            Constants.Semester.DEFAULT_TOTAL_WEEKS in Constants.Semester.MIN_WEEK_NUMBER..Constants.Semester.MAX_WEEK_NUMBER
        )
    }

    @Test
    fun `UI constants - animation durations should be positive`() {
        assertTrue("短动画时长应>0", Constants.UI.ANIMATION_DURATION_SHORT > 0)
        assertTrue("中等动画时长应>0", Constants.UI.ANIMATION_DURATION_MEDIUM > 0)
        assertTrue("长动画时长应>0", Constants.UI.ANIMATION_DURATION_LONG > 0)
        assertTrue("中等动画时长应大于短动画",
            Constants.UI.ANIMATION_DURATION_MEDIUM > Constants.UI.ANIMATION_DURATION_SHORT
        )
        assertTrue("长动画时长应大于中等动画",
            Constants.UI.ANIMATION_DURATION_LONG > Constants.UI.ANIMATION_DURATION_MEDIUM
        )
    }

    @Test
    fun `Network constants - timeouts should be reasonable`() {
        assertTrue("HTTP超时应>0", Constants.Network.HTTP_TIMEOUT_SECONDS > 0)
        assertTrue("读取超时>=普通超时",
            Constants.Network.HTTP_READ_TIMEOUT_SECONDS >= Constants.Network.HTTP_TIMEOUT_SECONDS
        )
        assertTrue("重试次数应>0", Constants.Network.MAX_RETRY_ATTEMPTS > 0)
        assertTrue("重试延迟应>0", Constants.Network.RETRY_DELAY_MS > 0)
    }

    @Test
    fun `Database constants - should have valid configuration`() {
        assertTrue("数据库名称不应为空", Constants.Database.DATABASE_NAME.isNotEmpty())
        assertTrue("数据库版本应>0", Constants.Database.DATABASE_VERSION > 0)
        assertTrue("备份保留天数应>0", Constants.Database.BACKUP_RETENTION_DAYS > 0)
    }

    @Test
    fun `Worker constants - should have non-empty tags`() {
        assertTrue("提醒工作标签不应为空", Constants.Worker.REMINDER_WORK_TAG.isNotEmpty())
        assertTrue("同步工作标签不应为空", Constants.Worker.SYNC_WORK_TAG.isNotEmpty())
        assertTrue("清理工作标签不应为空", Constants.Worker.CLEANUP_WORK_TAG.isNotEmpty())
        assertTrue("退避延迟应>0", Constants.Worker.BACKOFF_DELAY_MS > 0)
    }
}
