package com.wind.ggbond.classtime.domain.usecase

import com.wind.ggbond.classtime.data.local.entity.Course
import com.wind.ggbond.classtime.data.local.entity.CourseAdjustment
import com.wind.ggbond.classtime.data.repository.CourseAdjustmentRepository
import com.wind.ggbond.classtime.data.repository.CourseRepository
import com.wind.ggbond.classtime.service.contract.IAlarmScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AdjustmentUseCaseTest {

    private lateinit var adjustmentRepository: CourseAdjustmentRepository
    private lateinit var courseRepository: CourseRepository
    private lateinit var alarmScheduler: IAlarmScheduler
    private lateinit var adjustmentUseCase: AdjustmentUseCase

    @Before
    fun setUp() {
        adjustmentRepository = mockk(relaxed = true)
        courseRepository = mockk(relaxed = true)
        alarmScheduler = mockk(relaxed = true)
        adjustmentUseCase = AdjustmentUseCase(adjustmentRepository, courseRepository, alarmScheduler)
    }

    private fun buildCourse(
        id: Long = 1L,
        scheduleId: Long = 100L,
        dayOfWeek: Int = 1,
        startSection: Int = 1,
        sectionCount: Int = 2,
        weeks: List<Int> = listOf(1, 2, 3),
        courseName: String = "高等数学",
        classroom: String = "A101"
    ): Course {
        return Course(
            id = id,
            scheduleId = scheduleId,
            courseName = courseName,
            dayOfWeek = dayOfWeek,
            startSection = startSection,
            sectionCount = sectionCount,
            weeks = weeks,
            classroom = classroom
        )
    }

    private fun buildAdjustment(
        id: Long = 1L,
        originalCourseId: Long = 1L,
        scheduleId: Long = 100L,
        originalWeekNumber: Int = 1,
        originalDayOfWeek: Int = 1,
        originalStartSection: Int = 1,
        originalSectionCount: Int = 2,
        newWeekNumber: Int = 2,
        newDayOfWeek: Int = 2,
        newStartSection: Int = 3,
        newSectionCount: Int = 2,
        newClassroom: String = "B202"
    ): CourseAdjustment {
        return CourseAdjustment(
            id = id,
            originalCourseId = originalCourseId,
            scheduleId = scheduleId,
            originalWeekNumber = originalWeekNumber,
            originalDayOfWeek = originalDayOfWeek,
            originalStartSection = originalStartSection,
            originalSectionCount = originalSectionCount,
            newWeekNumber = newWeekNumber,
            newDayOfWeek = newDayOfWeek,
            newStartSection = newStartSection,
            newSectionCount = newSectionCount,
            newClassroom = newClassroom
        )
    }

    @Test
    fun `checkAdjustmentConflict - 无冲突时返回无冲突结果`() = runTest {
        coEvery { adjustmentRepository.checkNewTimeConflict(any(), any(), any(), any(), any()) } returns emptyList()
        coEvery { courseRepository.getCoursesInTimeRange(any(), any(), any(), any()) } returns emptyList()

        val result = adjustmentUseCase.checkAdjustmentConflict(
            scheduleId = 100L,
            weekNumber = 2,
            dayOfWeek = 2,
            startSection = 3,
            sectionCount = 2
        )

        assertFalse(result.hasConflict)
        assertEquals("", result.message)
    }

    @Test
    fun `checkAdjustmentConflict - 与课程时间冲突时返回冲突结果`() = runTest {
        val conflictCourse = buildCourse(id = 2L, courseName = "大学英语")
        coEvery { adjustmentRepository.checkNewTimeConflict(any(), any(), any(), any(), any()) } returns emptyList()
        coEvery { courseRepository.getCoursesInTimeRange(any(), any(), any(), any()) } returns listOf(conflictCourse)

        val result = adjustmentUseCase.checkAdjustmentConflict(
            scheduleId = 100L,
            weekNumber = 2,
            dayOfWeek = 2,
            startSection = 3,
            sectionCount = 2,
            excludeCourseId = 1L
        )

        assertTrue(result.hasConflict)
        assertTrue(result.message.contains("大学英语"))
        assertEquals(listOf(conflictCourse), result.conflictingCourses)
    }

    @Test
    fun `checkAdjustmentConflict - 与调课记录时间冲突时返回冲突结果`() = runTest {
        val conflictAdjustment = buildAdjustment()
        coEvery { adjustmentRepository.checkNewTimeConflict(any(), any(), any(), any(), any()) } returns listOf(conflictAdjustment)
        coEvery { courseRepository.getCoursesInTimeRange(any(), any(), any(), any()) } returns emptyList()

        val result = adjustmentUseCase.checkAdjustmentConflict(
            scheduleId = 100L,
            weekNumber = 2,
            dayOfWeek = 2,
            startSection = 3,
            sectionCount = 2
        )

        assertTrue(result.hasConflict)
        assertEquals("与其他调课记录时间冲突", result.message)
        assertEquals(listOf(conflictAdjustment), result.conflictingAdjustments)
    }

    @Test
    fun `checkAdjustmentConflict - 排除指定课程ID`() = runTest {
        val course1 = buildCourse(id = 1L, courseName = "高等数学")
        val course2 = buildCourse(id = 2L, courseName = "大学英语")
        coEvery { adjustmentRepository.checkNewTimeConflict(any(), any(), any(), any(), any()) } returns emptyList()
        coEvery { courseRepository.getCoursesInTimeRange(any(), any(), any(), any()) } returns listOf(course1, course2)

        val result = adjustmentUseCase.checkAdjustmentConflict(
            scheduleId = 100L,
            weekNumber = 1,
            dayOfWeek = 1,
            startSection = 1,
            sectionCount = 2,
            excludeCourseId = 1L
        )

        assertTrue(result.hasConflict)
        assertEquals(1, result.conflictingCourses.size)
        assertEquals(2L, result.conflictingCourses.first().id)
    }

    @Test
    fun `checkAdjustmentConflict - 周次不在课程周次列表中时不视为冲突`() = runTest {
        val course = buildCourse(id = 2L, weeks = listOf(5, 6, 7), courseName = "物理")
        coEvery { adjustmentRepository.checkNewTimeConflict(any(), any(), any(), any(), any()) } returns emptyList()
        coEvery { courseRepository.getCoursesInTimeRange(any(), any(), any(), any()) } returns listOf(course)

        val result = adjustmentUseCase.checkAdjustmentConflict(
            scheduleId = 100L,
            weekNumber = 2,
            dayOfWeek = 1,
            startSection = 1,
            sectionCount = 2,
            excludeCourseId = 1L
        )

        assertFalse(result.hasConflict)
    }

    @Test
    fun `createAdjustment - 正确构建调课记录`() {
        val course = buildCourse(
            id = 10L,
            scheduleId = 200L,
            dayOfWeek = 1,
            startSection = 1,
            sectionCount = 2
        )

        val adjustment = adjustmentUseCase.createAdjustment(
            course = course,
            originalWeekNumber = 3,
            newWeekNumber = 5,
            newDayOfWeek = 3,
            newStartSection = 5,
            newSectionCount = 3,
            newClassroom = "C303",
            reason = "教师出差"
        )

        assertEquals(10L, adjustment.originalCourseId)
        assertEquals(200L, adjustment.scheduleId)
        assertEquals(3, adjustment.originalWeekNumber)
        assertEquals(1, adjustment.originalDayOfWeek)
        assertEquals(1, adjustment.originalStartSection)
        assertEquals(2, adjustment.originalSectionCount)
        assertEquals(5, adjustment.newWeekNumber)
        assertEquals(3, adjustment.newDayOfWeek)
        assertEquals(5, adjustment.newStartSection)
        assertEquals(3, adjustment.newSectionCount)
        assertEquals("C303", adjustment.newClassroom)
        assertEquals("教师出差", adjustment.reason)
    }

    @Test
    fun `createAdjustment - 原始时间信息来自课程`() {
        val course = buildCourse(
            dayOfWeek = 4,
            startSection = 7,
            sectionCount = 3
        )

        val adjustment = adjustmentUseCase.createAdjustment(
            course = course,
            originalWeekNumber = 1,
            newWeekNumber = 2,
            newDayOfWeek = 5,
            newStartSection = 1,
            newSectionCount = 2,
            newClassroom = "",
            reason = ""
        )

        assertEquals(4, adjustment.originalDayOfWeek)
        assertEquals(7, adjustment.originalStartSection)
        assertEquals(3, adjustment.originalSectionCount)
    }

    @Test
    fun `saveAdjustment - 保存并重调度提醒`() = runTest {
        val adjustment = buildAdjustment(id = 0L)
        coEvery { adjustmentRepository.saveAdjustment(any()) } returns 42L

        val savedId = adjustmentUseCase.saveAdjustment(adjustment)

        assertEquals(42L, savedId)
        coVerify { adjustmentRepository.saveAdjustment(adjustment) }
        coVerify { alarmScheduler.rescheduleRemindersForAdjustment(match { it.id == 42L }) }
    }

    @Test
    fun `cancelAdjustment - 调用仓库取消调课`() = runTest {
        val adjustment = buildAdjustment()

        adjustmentUseCase.cancelAdjustment(adjustment)

        coVerify { adjustmentRepository.cancelAdjustment(adjustment) }
    }

    @Test
    fun `cancelAllAdjustments - 批量取消所有调课记录`() = runTest {
        val adjustment1 = buildAdjustment(id = 1L)
        val adjustment2 = buildAdjustment(id = 2L)
        val adjustment3 = buildAdjustment(id = 3L)

        adjustmentUseCase.cancelAllAdjustments(listOf(adjustment1, adjustment2, adjustment3))

        coVerify { adjustmentRepository.cancelAdjustment(adjustment1) }
        coVerify { adjustmentRepository.cancelAdjustment(adjustment2) }
        coVerify { adjustmentRepository.cancelAdjustment(adjustment3) }
    }

    @Test
    fun `cancelAllAdjustments - 空列表不调用仓库`() = runTest {
        adjustmentUseCase.cancelAllAdjustments(emptyList())

        coVerify(exactly = 0) { adjustmentRepository.cancelAdjustment(any()) }
    }

    @Test
    fun `buildAdjustmentMap - 正确构建映射`() {
        val adj1 = buildAdjustment(originalCourseId = 1L, newWeekNumber = 2, newDayOfWeek = 3, newStartSection = 1)
        val adj2 = buildAdjustment(originalCourseId = 2L, newWeekNumber = 3, newDayOfWeek = 4, newStartSection = 5)

        val map = adjustmentUseCase.buildAdjustmentMap(listOf(adj1, adj2))

        assertEquals(2, map.size)
        assertTrue(map.containsKey("1_2_3_1"))
        assertTrue(map.containsKey("2_3_4_5"))
        assertEquals(adj1, map["1_2_3_1"])
        assertEquals(adj2, map["2_3_4_5"])
    }

    @Test
    fun `buildAdjustmentMap - 空列表返回空映射`() {
        val map = adjustmentUseCase.buildAdjustmentMap(emptyList())
        assertTrue(map.isEmpty())
    }

    @Test
    fun `mergeCoursesWithAdjustments - 调课到同一周时原时间不显示新时间显示`() {
        val course = buildCourse(id = 1L, dayOfWeek = 1, startSection = 1, sectionCount = 2, weeks = listOf(1, 2))
        val adjustment = buildAdjustment(
            originalCourseId = 1L,
            originalWeekNumber = 1,
            originalDayOfWeek = 1,
            originalStartSection = 1,
            originalSectionCount = 2,
            newWeekNumber = 1,
            newDayOfWeek = 3,
            newStartSection = 5,
            newSectionCount = 2
        )

        val result = adjustmentUseCase.mergeCoursesWithAdjustments(
            courses = listOf(course),
            adjustments = listOf(adjustment),
            weekNumber = 1
        )

        val mondayCourses = result[1] ?: emptyList()
        val wednesdayCourses = result[3] ?: emptyList()

        assertTrue("周一不应显示原课程", mondayCourses.none { it.id == 1L && it.startSection == 1 })
        assertTrue("周三应显示调课后课程", wednesdayCourses.any { it.id == 1L && it.startSection == 5 && it.dayOfWeek == 3 })
    }

    @Test
    fun `mergeCoursesWithAdjustments - 调课到不同周时原周不显示课程`() {
        val course = buildCourse(id = 1L, dayOfWeek = 1, startSection = 1, sectionCount = 2, weeks = listOf(1, 2))
        val adjustment = buildAdjustment(
            originalCourseId = 1L,
            originalWeekNumber = 1,
            originalDayOfWeek = 1,
            originalStartSection = 1,
            originalSectionCount = 2,
            newWeekNumber = 2,
            newDayOfWeek = 2,
            newStartSection = 3,
            newSectionCount = 2
        )

        val week1Result = adjustmentUseCase.mergeCoursesWithAdjustments(
            courses = listOf(course),
            adjustments = listOf(adjustment),
            weekNumber = 1
        )

        val mondayCourses = week1Result[1] ?: emptyList()
        assertTrue("第1周周一不应显示被调走的课程", mondayCourses.none { it.id == 1L })
    }

    @Test
    fun `mergeCoursesWithAdjustments - 从其他周调入本周时显示课程`() {
        val course = buildCourse(id = 1L, dayOfWeek = 1, startSection = 1, sectionCount = 2, weeks = listOf(1, 2))
        val adjustment = buildAdjustment(
            originalCourseId = 1L,
            originalWeekNumber = 1,
            originalDayOfWeek = 1,
            originalStartSection = 1,
            originalSectionCount = 2,
            newWeekNumber = 2,
            newDayOfWeek = 2,
            newStartSection = 3,
            newSectionCount = 2
        )

        val week2Result = adjustmentUseCase.mergeCoursesWithAdjustments(
            courses = listOf(course),
            adjustments = listOf(adjustment),
            weekNumber = 2
        )

        val tuesdayCourses = week2Result[2] ?: emptyList()
        assertTrue("第2周周二应显示调入的课程", tuesdayCourses.any { it.id == 1L && it.dayOfWeek == 2 && it.startSection == 3 })
    }

    @Test
    fun `mergeCoursesWithAdjustments - 无调课记录时正常显示课程`() {
        val course = buildCourse(id = 1L, dayOfWeek = 1, startSection = 1, sectionCount = 2, weeks = listOf(1))

        val result = adjustmentUseCase.mergeCoursesWithAdjustments(
            courses = listOf(course),
            adjustments = emptyList(),
            weekNumber = 1
        )

        val mondayCourses = result[1] ?: emptyList()
        assertTrue("周一应显示正常课程", mondayCourses.any { it.id == 1L })
    }

    @Test
    fun `mergeCoursesWithAdjustments - 课程周次不包含当前周时不显示`() {
        val course = buildCourse(id = 1L, dayOfWeek = 1, startSection = 1, sectionCount = 2, weeks = listOf(5, 6))

        val result = adjustmentUseCase.mergeCoursesWithAdjustments(
            courses = listOf(course),
            adjustments = emptyList(),
            weekNumber = 1
        )

        val mondayCourses = result[1] ?: emptyList()
        assertTrue("第1周不应显示第5-6周的课程", mondayCourses.none { it.id == 1L })
    }

    @Test
    fun `mergeCoursesWithAdjustments - 调课时更换教室`() {
        val course = buildCourse(id = 1L, dayOfWeek = 1, startSection = 1, sectionCount = 2, weeks = listOf(1), classroom = "A101")
        val adjustment = buildAdjustment(
            originalCourseId = 1L,
            originalWeekNumber = 1,
            originalDayOfWeek = 1,
            originalStartSection = 1,
            originalSectionCount = 2,
            newWeekNumber = 1,
            newDayOfWeek = 2,
            newStartSection = 3,
            newSectionCount = 2,
            newClassroom = "B202"
        )

        val result = adjustmentUseCase.mergeCoursesWithAdjustments(
            courses = listOf(course),
            adjustments = listOf(adjustment),
            weekNumber = 1
        )

        val tuesdayCourses = result[2] ?: emptyList()
        val adjustedCourse = tuesdayCourses.first { it.id == 1L }
        assertEquals("B202", adjustedCourse.classroom)
    }

    @Test
    fun `mergeCoursesWithAdjustments - 调课时新教室为空则保留原教室`() {
        val course = buildCourse(id = 1L, dayOfWeek = 1, startSection = 1, sectionCount = 2, weeks = listOf(1), classroom = "A101")
        val adjustment = buildAdjustment(
            originalCourseId = 1L,
            originalWeekNumber = 1,
            originalDayOfWeek = 1,
            originalStartSection = 1,
            originalSectionCount = 2,
            newWeekNumber = 1,
            newDayOfWeek = 2,
            newStartSection = 3,
            newSectionCount = 2,
            newClassroom = ""
        )

        val result = adjustmentUseCase.mergeCoursesWithAdjustments(
            courses = listOf(course),
            adjustments = listOf(adjustment),
            weekNumber = 1
        )

        val tuesdayCourses = result[2] ?: emptyList()
        val adjustedCourse = tuesdayCourses.first { it.id == 1L }
        assertEquals("A101", adjustedCourse.classroom)
    }
}
