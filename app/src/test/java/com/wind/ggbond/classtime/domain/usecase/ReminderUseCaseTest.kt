package com.wind.ggbond.classtime.domain.usecase

import com.wind.ggbond.classtime.data.local.entity.ClassTime
import com.wind.ggbond.classtime.data.local.entity.Course
import com.wind.ggbond.classtime.data.local.entity.CourseAdjustment
import com.wind.ggbond.classtime.data.local.entity.Reminder
import com.wind.ggbond.classtime.data.local.entity.Schedule
import com.wind.ggbond.classtime.data.repository.ReminderRepository
import com.wind.ggbond.classtime.ui.screen.reminder.ReminderFilterType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class ReminderUseCaseTest {

    private lateinit var reminderRepository: ReminderRepository
    private lateinit var reminderUseCase: ReminderUseCase

    private val testSchedule = Schedule(
        id = 1,
        name = "测试课表",
        startDate = LocalDate.of(2026, 3, 2),
        totalWeeks = 16
    )

    private val testClassTimes = listOf(
        ClassTime(sectionNumber = 1, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(8, 45)),
        ClassTime(sectionNumber = 2, startTime = LocalTime.of(8, 55), endTime = LocalTime.of(9, 40)),
        ClassTime(sectionNumber = 3, startTime = LocalTime.of(10, 0), endTime = LocalTime.of(10, 45)),
        ClassTime(sectionNumber = 4, startTime = LocalTime.of(10, 55), endTime = LocalTime.of(11, 40))
    )

    @Before
    fun setup() {
        reminderRepository = mockk(relaxed = true)
        reminderUseCase = ReminderUseCase(reminderRepository)
    }

    @Test
    fun `calculateReminderTime returns correct time for future course`() {
        val futureDate = LocalDate.now().plusDays(7)
        val weekNumber = 2
        val course = Course(
            id = 1,
            courseName = "高等数学",
            dayOfWeek = 1,
            startSection = 1,
            sectionCount = 2,
            weeks = listOf(1, 2, 3),
            reminderMinutes = 10,
            scheduleId = 1,
            reminderEnabled = true
        )

        val schedule = testSchedule.copy(startDate = futureDate.minusWeeks(1))
        val result = reminderUseCase.calculateReminderTime(course, schedule, weekNumber, testClassTimes)

        assertNotNull(result)
    }

    @Test
    fun `calculateReminderTime returns null when classTime not found`() {
        val course = Course(
            id = 1,
            courseName = "高等数学",
            dayOfWeek = 1,
            startSection = 99,
            sectionCount = 2,
            weeks = listOf(1, 2, 3),
            reminderMinutes = 10,
            scheduleId = 1,
            reminderEnabled = true
        )

        val result = reminderUseCase.calculateReminderTime(course, testSchedule, 1, testClassTimes)
        assertNull(result)
    }

    @Test
    fun `calculateCourseDate returns correct date for monday`() {
        val result = reminderUseCase.calculateCourseDate(testSchedule, 1, 1)
        assertEquals(LocalDate.of(2026, 3, 2), result)
    }

    @Test
    fun `calculateCourseDate returns correct date for wednesday`() {
        val result = reminderUseCase.calculateCourseDate(testSchedule, 1, 3)
        assertEquals(LocalDate.of(2026, 3, 4), result)
    }

    @Test
    fun `isCourseDatePassed returns true for past date`() {
        assertTrue(reminderUseCase.isCourseDatePassed(LocalDate.of(2020, 1, 1)))
    }

    @Test
    fun `isCourseDatePassed returns false for future date`() {
        assertFalse(reminderUseCase.isCourseDatePassed(LocalDate.now().plusDays(1)))
    }

    @Test
    fun `isCourseDatePassed returns false for today`() {
        assertFalse(reminderUseCase.isCourseDatePassed(LocalDate.now()))
    }

    @Test
    fun `isReminderExpired returns true for past timestamp`() {
        assertTrue(reminderUseCase.isReminderExpired(System.currentTimeMillis() - 1000))
    }

    @Test
    fun `isReminderExpired returns false for future timestamp`() {
        assertFalse(reminderUseCase.isReminderExpired(System.currentTimeMillis() + 100000))
    }

    @Test
    fun `shouldCheckNextCourseReminder returns true for even sections`() {
        assertTrue(reminderUseCase.shouldCheckNextCourseReminder(2))
        assertTrue(reminderUseCase.shouldCheckNextCourseReminder(4))
        assertTrue(reminderUseCase.shouldCheckNextCourseReminder(6))
        assertTrue(reminderUseCase.shouldCheckNextCourseReminder(8))
        assertTrue(reminderUseCase.shouldCheckNextCourseReminder(10))
    }

    @Test
    fun `shouldCheckNextCourseReminder returns false for odd sections`() {
        assertFalse(reminderUseCase.shouldCheckNextCourseReminder(1))
        assertFalse(reminderUseCase.shouldCheckNextCourseReminder(3))
        assertFalse(reminderUseCase.shouldCheckNextCourseReminder(5))
        assertFalse(reminderUseCase.shouldCheckNextCourseReminder(7))
        assertFalse(reminderUseCase.shouldCheckNextCourseReminder(9))
        assertFalse(reminderUseCase.shouldCheckNextCourseReminder(11))
    }

    @Test
    fun `isSameCourseAndClassroom returns true for same name and classroom`() {
        val course1 = Course(id = 1, courseName = "数学", classroom = "A101", dayOfWeek = 1, startSection = 1, scheduleId = 1)
        val course2 = Course(id = 2, courseName = "数学", classroom = "A101", dayOfWeek = 1, startSection = 3, scheduleId = 1)
        assertTrue(reminderUseCase.isSameCourseAndClassroom(course1, course2))
    }

    @Test
    fun `isSameCourseAndClassroom returns false for different name`() {
        val course1 = Course(id = 1, courseName = "数学", classroom = "A101", dayOfWeek = 1, startSection = 1, scheduleId = 1)
        val course2 = Course(id = 2, courseName = "英语", classroom = "A101", dayOfWeek = 1, startSection = 3, scheduleId = 1)
        assertFalse(reminderUseCase.isSameCourseAndClassroom(course1, course2))
    }

    @Test
    fun `isSameCourseAndClassroom returns false for different classroom`() {
        val course1 = Course(id = 1, courseName = "数学", classroom = "A101", dayOfWeek = 1, startSection = 1, scheduleId = 1)
        val course2 = Course(id = 2, courseName = "数学", classroom = "B202", dayOfWeek = 1, startSection = 3, scheduleId = 1)
        assertFalse(reminderUseCase.isSameCourseAndClassroom(course1, course2))
    }

    @Test
    fun `toggleReminderEnabled toggles isEnabled`() {
        val reminder = Reminder(id = 1, courseId = 1, minutesBefore = 10, isEnabled = true, weekNumber = 1, dayOfWeek = 1, triggerTime = 1000L)
        val toggled = reminderUseCase.toggleReminderEnabled(reminder)
        assertFalse(toggled.isEnabled)

        val toggledBack = reminderUseCase.toggleReminderEnabled(toggled)
        assertTrue(toggledBack.isEnabled)
    }

    @Test
    fun `filterReminders returns all for ALL type`() {
        val now = System.currentTimeMillis()
        val reminders = listOf(
            Reminder(id = 1, courseId = 1, minutesBefore = 10, isEnabled = true, weekNumber = 1, dayOfWeek = 1, triggerTime = now + 100000),
            Reminder(id = 2, courseId = 2, minutesBefore = 10, isEnabled = true, weekNumber = 1, dayOfWeek = 2, triggerTime = now - 100000)
        )
        val result = reminderUseCase.filterReminders(reminders, ReminderFilterType.ALL)
        assertEquals(2, result.size)
    }

    @Test
    fun `filterReminders returns expired for EXPIRED type`() {
        val now = System.currentTimeMillis()
        val reminders = listOf(
            Reminder(id = 1, courseId = 1, minutesBefore = 10, isEnabled = true, weekNumber = 1, dayOfWeek = 1, triggerTime = now + 100000),
            Reminder(id = 2, courseId = 2, minutesBefore = 10, isEnabled = true, weekNumber = 1, dayOfWeek = 2, triggerTime = now - 100000)
        )
        val result = reminderUseCase.filterReminders(reminders, ReminderFilterType.EXPIRED)
        assertEquals(1, result.size)
        assertEquals(now - 100000, result[0].triggerTime)
    }

    @Test
    fun `groupRemindersByDate groups correctly`() {
        val today = LocalDate.now()
        val todayStart = today.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val tomorrowStart = todayStart + ReminderUseCase.MS_PER_DAY

        val reminders = listOf(
            Reminder(id = 1, courseId = 1, minutesBefore = 10, isEnabled = true, weekNumber = 1, dayOfWeek = 1, triggerTime = todayStart + 3600000),
            Reminder(id = 2, courseId = 2, minutesBefore = 10, isEnabled = true, weekNumber = 1, dayOfWeek = 2, triggerTime = tomorrowStart + 3600000)
        )

        val grouped = reminderUseCase.groupRemindersByDate(reminders)
        assertEquals(2, grouped.size)
        assertTrue(grouped.containsKey(today))
        assertTrue(grouped.containsKey(today.plusDays(1)))
    }

    @Test
    fun `getReminderStats calculates correctly`() = runTest {
        val now = System.currentTimeMillis()
        val todayStart = LocalDate.now().atStartOfDay().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val todayEnd = todayStart + ReminderUseCase.MS_PER_DAY

        val reminders = listOf(
            Reminder(id = 1, courseId = 1, minutesBefore = 10, isEnabled = true, weekNumber = 1, dayOfWeek = 1, triggerTime = now - 100000),
            Reminder(id = 2, courseId = 2, minutesBefore = 10, isEnabled = true, weekNumber = 1, dayOfWeek = 2, triggerTime = todayStart + 3600000),
            Reminder(id = 3, courseId = 3, minutesBefore = 10, isEnabled = true, weekNumber = 1, dayOfWeek = 3, triggerTime = todayEnd + 86400000L),
            Reminder(id = 4, courseId = 4, minutesBefore = 10, isEnabled = false, weekNumber = 1, dayOfWeek = 4, triggerTime = todayStart + 7200000)
        )

        coEvery { reminderRepository.getAll() } returns reminders

        val stats = reminderUseCase.getReminderStats()
        assertEquals(4, stats.totalReminders)
        assertEquals(3, stats.activeReminders)
        assertEquals(1, stats.todayReminders)
    }

    @Test
    fun `deleteReminder calls repository`() = runTest {
        val reminder = Reminder(id = 1, courseId = 1, minutesBefore = 10, isEnabled = true, weekNumber = 1, dayOfWeek = 1, triggerTime = 1000L)
        reminderUseCase.deleteReminder(reminder)
        coVerify { reminderRepository.delete(reminder) }
    }

    @Test
    fun `cleanExpiredReminders calls repository`() = runTest {
        reminderUseCase.cleanExpiredReminders()
        coVerify { reminderRepository.deleteExpiredReminders() }
    }

    @Test
    fun `calculateAdjustedReminderTime returns null for past date`() {
        val course = Course(
            id = 1, courseName = "数学", dayOfWeek = 1, startSection = 1,
            sectionCount = 2, weeks = listOf(1), reminderMinutes = 10,
            scheduleId = 1, reminderEnabled = true
        )
        val adjustment = CourseAdjustment(
            id = 1, originalCourseId = 1, scheduleId = 1,
            originalWeekNumber = 1, originalDayOfWeek = 1, originalStartSection = 1, originalSectionCount = 2,
            newWeekNumber = 1, newDayOfWeek = 1, newStartSection = 1, newSectionCount = 2
        )
        val pastSchedule = testSchedule.copy(startDate = LocalDate.of(2020, 1, 6))

        val result = reminderUseCase.calculateAdjustedReminderTime(course, pastSchedule, adjustment, testClassTimes)
        assertNull(result)
    }

    @Test
    fun `calculateNextCourseReminderInfo returns null for odd end section`() {
        val course = Course(
            id = 1, courseName = "数学", dayOfWeek = 1, startSection = 1,
            sectionCount = 1, weeks = listOf(1), reminderMinutes = 10,
            scheduleId = 1, reminderEnabled = true
        )
        val result = reminderUseCase.calculateNextCourseReminderInfo(
            course, testSchedule, 1, testClassTimes, emptyList()
        )
        assertNull(result)
    }

    @Test
    fun `findNextCourse returns null when no matching course`() {
        val currentCourse = Course(
            id = 1, courseName = "数学", dayOfWeek = 1, startSection = 1,
            sectionCount = 2, weeks = listOf(1), reminderMinutes = 10,
            scheduleId = 1, reminderEnabled = true
        )
        val result = reminderUseCase.findNextCourse(
            currentCourse, testSchedule, 1, testClassTimes, emptyList()
        )
        assertNull(result)
    }
}
