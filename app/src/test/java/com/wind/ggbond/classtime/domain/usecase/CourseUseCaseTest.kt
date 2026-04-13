package com.wind.ggbond.classtime.domain.usecase

import com.wind.ggbond.classtime.data.local.entity.Course
import com.wind.ggbond.classtime.data.repository.CourseRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CourseUseCaseTest {

    private lateinit var courseUseCase: CourseUseCase
    private val courseRepository: CourseRepository = mockk(relaxed = true)

    private val testCourse = Course(
        id = 1,
        courseName = "高等数学",
        dayOfWeek = 1,
        startSection = 1,
        sectionCount = 2,
        weeks = listOf(1, 2, 3, 4, 5),
        scheduleId = 100,
        color = "#5B9BD5"
    )

    private val testCourse2 = Course(
        id = 2,
        courseName = "大学英语",
        dayOfWeek = 1,
        startSection = 3,
        sectionCount = 2,
        weeks = listOf(1, 2, 3),
        scheduleId = 100,
        color = "#F5A864"
    )

    @Before
    fun setUp() {
        courseUseCase = CourseUseCase(courseRepository)
        mockkObject(com.wind.ggbond.classtime.util.CourseColorProvider)
    }

    @After
    fun tearDown() {
        unmockkObject(com.wind.ggbond.classtime.util.CourseColorProvider)
    }

    // ========== 冲突检测测试 ==========

    @Test
    fun `checkCourseConflict - 无冲突时返回hasConflict为false`() = runTest {
        coEvery {
            courseRepository.detectConflictWithWeeks(
                scheduleId = 100,
                dayOfWeek = 1,
                startSection = 1,
                sectionCount = 2,
                weeks = listOf(1, 2, 3),
                excludeCourseId = null
            )
        } returns emptyList()

        val result = courseUseCase.checkCourseConflict(
            scheduleId = 100,
            dayOfWeek = 1,
            startSection = 1,
            sectionCount = 2,
            weeks = listOf(1, 2, 3)
        )

        assertFalse(result.hasConflict)
        assertTrue(result.conflictingCourses.isEmpty())
        assertNull(result.message)
    }

    @Test
    fun `checkCourseConflict - 有冲突时返回hasConflict为true并包含冲突信息`() = runTest {
        coEvery {
            courseRepository.detectConflictWithWeeks(
                scheduleId = 100,
                dayOfWeek = 1,
                startSection = 1,
                sectionCount = 2,
                weeks = listOf(1, 2, 3),
                excludeCourseId = null
            )
        } returns listOf(testCourse)

        val result = courseUseCase.checkCourseConflict(
            scheduleId = 100,
            dayOfWeek = 1,
            startSection = 1,
            sectionCount = 2,
            weeks = listOf(1, 2, 3)
        )

        assertTrue(result.hasConflict)
        assertEquals(1, result.conflictingCourses.size)
        assertEquals("高等数学", result.conflictingCourses[0].courseName)
        assertNotNull(result.message)
        assertTrue(result.message!!.contains("高等数学"))
    }

    @Test
    fun `checkCourseConflict - 多个冲突课程时消息包含所有课程名`() = runTest {
        coEvery {
            courseRepository.detectConflictWithWeeks(
                scheduleId = 100,
                dayOfWeek = 1,
                startSection = 1,
                sectionCount = 2,
                weeks = listOf(1, 2, 3),
                excludeCourseId = null
            )
        } returns listOf(testCourse, testCourse2)

        val result = courseUseCase.checkCourseConflict(
            scheduleId = 100,
            dayOfWeek = 1,
            startSection = 1,
            sectionCount = 2,
            weeks = listOf(1, 2, 3)
        )

        assertTrue(result.hasConflict)
        assertEquals(2, result.conflictingCourses.size)
        assertTrue(result.message!!.contains("高等数学"))
        assertTrue(result.message!!.contains("大学英语"))
    }

    @Test
    fun `checkCourseConflict - 排除指定课程ID`() = runTest {
        coEvery {
            courseRepository.detectConflictWithWeeks(
                scheduleId = 100,
                dayOfWeek = 1,
                startSection = 1,
                sectionCount = 2,
                weeks = listOf(1, 2, 3),
                excludeCourseId = 1L
            )
        } returns emptyList()

        val result = courseUseCase.checkCourseConflict(
            scheduleId = 100,
            dayOfWeek = 1,
            startSection = 1,
            sectionCount = 2,
            weeks = listOf(1, 2, 3),
            excludeCourseId = 1L
        )

        assertFalse(result.hasConflict)
        coVerify {
            courseRepository.detectConflictWithWeeks(
                scheduleId = 100,
                dayOfWeek = 1,
                startSection = 1,
                sectionCount = 2,
                weeks = listOf(1, 2, 3),
                excludeCourseId = 1L
            )
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `checkCourseConflict - dayOfWeek超出范围时抛出异常`() = runTest {
        courseUseCase.checkCourseConflict(
            scheduleId = 100,
            dayOfWeek = 8,
            startSection = 1,
            sectionCount = 2,
            weeks = listOf(1)
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `checkCourseConflict - startSection为负数时抛出异常`() = runTest {
        courseUseCase.checkCourseConflict(
            scheduleId = 100,
            dayOfWeek = 1,
            startSection = 0,
            sectionCount = 2,
            weeks = listOf(1)
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `checkCourseConflict - sectionCount为0时抛出异常`() = runTest {
        courseUseCase.checkCourseConflict(
            scheduleId = 100,
            dayOfWeek = 1,
            startSection = 1,
            sectionCount = 0,
            weeks = listOf(1)
        )
    }

    // ========== 颜色分配测试 ==========

    @Test
    fun `getColorForCourse - 委托给CourseColorProvider`() = runTest {
        coEvery {
            com.wind.ggbond.classtime.util.CourseColorProvider.getColorForCourse("高等数学", emptyList())
        } returns "#5B9BD5"

        val color = courseUseCase.getColorForCourse("高等数学", emptyList())

        assertEquals("#5B9BD5", color)
    }

    @Test
    fun `getColorForCourse - 传入已有颜色列表`() = runTest {
        val existingColors = listOf("#5B9BD5", "#F5A864")
        coEvery {
            com.wind.ggbond.classtime.util.CourseColorProvider.getColorForCourse("物理", existingColors)
        } returns "#6FBE6E"

        val color = courseUseCase.getColorForCourse("物理", existingColors)

        assertEquals("#6FBE6E", color)
    }

    @Test
    fun `getExistingCoursesColors - 正常返回课程颜色列表`() = runTest {
        val courses = listOf(
            testCourse,
            testCourse2
        )
        every { courseRepository.getAllCoursesBySchedule(100) } returns flowOf(courses)

        val colors = courseUseCase.getExistingCoursesColors(100)

        assertEquals(2, colors.size)
        assertEquals("#5B9BD5", colors[0])
        assertEquals("#F5A864", colors[1])
    }

    @Test
    fun `getExistingCoursesColors - 无课程时返回空列表`() = runTest {
        every { courseRepository.getAllCoursesBySchedule(100) } returns flowOf(emptyList())

        val colors = courseUseCase.getExistingCoursesColors(100)

        assertTrue(colors.isEmpty())
    }

    @Test
    fun `getExistingCoursesColors - 异常时返回空列表`() = runTest {
        every { courseRepository.getAllCoursesBySchedule(100) } throws RuntimeException("DB error")

        val colors = courseUseCase.getExistingCoursesColors(100)

        assertTrue(colors.isEmpty())
    }

    // ========== 删除课程逻辑测试 ==========

    @Test
    fun `deleteCourseForWeek - 只有一周时完整删除课程`() = runTest {
        val singleWeekCourse = testCourse.copy(weeks = listOf(3))

        val result = courseUseCase.deleteCourseForWeek(singleWeekCourse, 3)

        assertTrue(result.wasFullDelete)
        assertEquals(singleWeekCourse, result.originalCourse)
        assertEquals(3, result.weekNumber)
        coVerify { courseRepository.deleteCourse(singleWeekCourse) }
    }

    @Test
    fun `deleteCourseForWeek - 多周时移除指定周次`() = runTest {
        val result = courseUseCase.deleteCourseForWeek(testCourse, 3)

        assertFalse(result.wasFullDelete)
        assertEquals(testCourse, result.originalCourse)
        assertEquals(3, result.weekNumber)
        coVerify {
            courseRepository.updateCourse(match { course ->
                course.weeks == listOf(1, 2, 4, 5)
            })
        }
    }

    @Test
    fun `deleteCourseForWeek - 移除后仍剩一周时更新而非删除`() = runTest {
        val twoWeekCourse = testCourse.copy(weeks = listOf(1, 2))

        val result = courseUseCase.deleteCourseForWeek(twoWeekCourse, 1)

        assertFalse(result.wasFullDelete)
        coVerify {
            courseRepository.updateCourse(match { course ->
                course.weeks == listOf(2)
            })
        }
    }

    @Test
    fun `deleteCourseForWeek - 移除最后一周时完整删除`() = runTest {
        val twoWeekCourse = testCourse.copy(weeks = listOf(1, 2))

        val result = courseUseCase.deleteCourseForWeek(twoWeekCourse, 2)

        assertFalse(result.wasFullDelete)
        coVerify {
            courseRepository.updateCourse(match { course ->
                course.weeks == listOf(1)
            })
        }
    }

    // ========== 撤销删除逻辑测试 ==========

    @Test
    fun `undoDeleteCourse - 完整删除的撤销时重新插入课程`() = runTest {
        courseUseCase.undoDeleteCourse(testCourse, 3, wasFullDelete = true)

        coVerify {
            courseRepository.insertCourse(match { course ->
                course.courseName == "高等数学" && course.id == 0L
            })
        }
    }

    @Test
    fun `undoDeleteCourse - 部分删除的撤销时恢复周次`() = runTest {
        val currentCourse = testCourse.copy(weeks = listOf(1, 2, 4, 5))
        coEvery { courseRepository.getCourseById(1L) } returns currentCourse

        courseUseCase.undoDeleteCourse(testCourse, 3, wasFullDelete = false)

        coVerify {
            courseRepository.updateCourse(match { course ->
                3 in course.weeks && course.weeks.containsAll(listOf(1, 2, 3, 4, 5))
            })
        }
    }

    @Test
    fun `undoDeleteCourse - 部分删除但课程已不存在时重新插入`() = runTest {
        coEvery { courseRepository.getCourseById(1L) } returns null

        courseUseCase.undoDeleteCourse(testCourse, 3, wasFullDelete = false)

        coVerify {
            courseRepository.insertCourse(match { course ->
                course.courseName == "高等数学" && course.id == 0L
            })
        }
    }

    // ========== 缓存相关测试 ==========

    @Test
    fun `clearCache - 清除缓存后重新计算`() = runTest {
        val courses = listOf(testCourse)
        val result1 = courseUseCase.getCoursesForWeek(courses, 1)
        assertFalse(result1.isEmpty())

        courseUseCase.clearCache()

        val result2 = courseUseCase.getCoursesForWeek(courses, 1)
        assertFalse(result2.isEmpty())
    }

    @Test
    fun `getCoursesForWeek - 正确按星期分组课程`() = runTest {
        val mondayCourse = testCourse.copy(dayOfWeek = 1, weeks = listOf(1))
        val tuesdayCourse = testCourse.copy(id = 2, dayOfWeek = 2, weeks = listOf(1))

        val result = courseUseCase.getCoursesForWeek(listOf(mondayCourse, tuesdayCourse), 1)

        assertTrue(result[1]!!.contains(mondayCourse))
        assertTrue(result[2]!!.contains(tuesdayCourse))
        assertTrue(result[3]!!.isEmpty())
    }

    @Test
    fun `getCoursesForWeek - 仅返回指定周次的课程`() = runTest {
        val week1Course = testCourse.copy(weeks = listOf(1))
        val week2Course = testCourse.copy(id = 2, weeks = listOf(2))

        val result = courseUseCase.getCoursesForWeek(listOf(week1Course, week2Course), 1)

        assertTrue(result[1]!!.contains(week1Course))
        assertFalse(result[1]!!.contains(week2Course))
    }

    @Test
    fun `getCoursesForWeek - 课程按startSection排序`() = runTest {
        val laterCourse = testCourse.copy(startSection = 3, weeks = listOf(1))
        val earlierCourse = testCourse.copy(id = 2, startSection = 1, weeks = listOf(1))

        val result = courseUseCase.getCoursesForWeek(listOf(laterCourse, earlierCourse), 1)

        assertEquals(earlierCourse, result[1]!![0])
        assertEquals(laterCourse, result[1]!![1])
    }
}
