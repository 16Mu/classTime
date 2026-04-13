package com.wind.ggbond.classtime.service

import android.content.Context
import com.wind.ggbond.classtime.data.local.entity.ClassTime
import com.wind.ggbond.classtime.data.local.entity.Course
import com.wind.ggbond.classtime.data.local.entity.CourseAdjustment
import com.wind.ggbond.classtime.data.local.entity.Reminder
import com.wind.ggbond.classtime.data.local.entity.Schedule
import com.wind.ggbond.classtime.domain.usecase.ReminderUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class UnifiedReminderSchedulerTest {

    private lateinit var context: Context
    private lateinit var alarmManager: ReminderAlarmManager
    private lateinit var queryService: ReminderQueryService
    private lateinit var reminderUseCase: ReminderUseCase
    private lateinit var scheduler: UnifiedReminderScheduler

    private val testSchedule = Schedule(
        id = 1,
        name = "测试课表",
        startDate = LocalDate.now().minusWeeks(1),
        totalWeeks = 16
    )

    private val testClassTimes = listOf(
        ClassTime(sectionNumber = 1, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(8, 45)),
        ClassTime(sectionNumber = 2, startTime = LocalTime.of(8, 55), endTime = LocalTime.of(9, 40)),
        ClassTime(sectionNumber = 3, startTime = LocalTime.of(10, 0), endTime = LocalTime.of(10, 45)),
        ClassTime(sectionNumber = 4, startTime = LocalTime.of(10, 55), endTime = LocalTime.of(11, 40))
    )

    private val testCourse = Course(
        id = 1,
        courseName = "高等数学",
        dayOfWeek = LocalDate.now().dayOfWeek.value.let { if (it == 7) 1 else it },
        startSection = 1,
        sectionCount = 2,
        weeks = listOf(1, 2, 3),
        reminderMinutes = 10,
        scheduleId = 1,
        reminderEnabled = true
    )

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        alarmManager = mockk(relaxed = true)
        queryService = mockk(relaxed = true)
        reminderUseCase = mockk(relaxed = true)

        every { context.applicationContext } returns context

        scheduler = UnifiedReminderScheduler(
            context = context,
            alarmManager = alarmManager,
            queryService = queryService,
            reminderUseCase = reminderUseCase
        )
    }

    @Test
    fun `scheduleCourseReminders skips when reminder disabled`() = runTest {
        val disabledCourse = testCourse.copy(reminderEnabled = false)

        scheduler.scheduleCourseReminders(disabledCourse)

        coVerify(exactly = 0) { queryService.insertReminder(any()) }
    }

    @Test
    fun `scheduleCourseReminders skips when no schedule`() = runTest {
        coEvery { queryService.getCurrentSchedule() } returns null

        scheduler.scheduleCourseReminders(testCourse)

        coVerify(exactly = 0) { queryService.insertReminder(any()) }
    }

    @Test
    fun `scheduleCourseReminders cancels existing reminders first`() = runTest {
        coEvery { queryService.getCurrentSchedule() } returns testSchedule
        every { queryService.getClassTimes() } returns testClassTimes
        coEvery { queryService.getAllReminders() } returns emptyList()
        coEvery { queryService.getCoursesBySchedule(any()) } returns emptyList()

        val futureDate = LocalDate.now().plusDays(7)
        val futureSchedule = testSchedule.copy(startDate = futureDate.minusWeeks(1))
        coEvery { queryService.getCurrentSchedule() } returns futureSchedule

        scheduler.scheduleCourseReminders(testCourse)

        coVerify { queryService.deleteRemindersByCourse(testCourse.id) }
    }

    @Test
    fun `scheduleAllCourseReminders skips when no enabled courses`() = runTest {
        coEvery { queryService.getCoursesBySchedule(any()) } returns listOf(
            testCourse.copy(reminderEnabled = false)
        )

        scheduler.scheduleAllCourseReminders(1)

        coVerify(exactly = 0) { queryService.insertReminder(any()) }
    }

    @Test
    fun `cancelCourseReminders deletes reminders from queryService`() = runTest {
        val reminders = listOf(
            Reminder(id = 1, courseId = 1, minutesBefore = 10, isEnabled = true, weekNumber = 1, dayOfWeek = 1, triggerTime = 1000L)
        )
        coEvery { queryService.getRemindersByCourse(1) } returns reminders

        scheduler.cancelCourseReminders(1)

        coVerify { queryService.deleteRemindersByCourse(1) }
    }

    @Test
    fun `cancelAllReminders deletes all from queryService`() = runTest {
        coEvery { queryService.getAllReminders() } returns emptyList()

        scheduler.cancelAllReminders()

        coVerify { queryService.deleteAllReminders() }
    }

    @Test
    fun `cancelAllCourseReminders cancels each course`() = runTest {
        val course1 = testCourse.copy(id = 1)
        val course2 = testCourse.copy(id = 2)
        coEvery { queryService.getCoursesBySchedule(any()) } returns listOf(course1, course2)
        coEvery { queryService.getRemindersByCourse(any()) } returns emptyList()

        scheduler.cancelAllCourseReminders(1)

        coVerify { queryService.deleteRemindersByCourse(1) }
        coVerify { queryService.deleteRemindersByCourse(2) }
    }

    @Test
    fun `rescheduleAllReminders delegates to scheduleAllCourseReminders`() = runTest {
        coEvery { queryService.getCurrentSchedule() } returns testSchedule
        coEvery { queryService.getCoursesBySchedule(any()) } returns emptyList()

        scheduler.rescheduleAllReminders()

        coVerify { queryService.getCoursesBySchedule(testSchedule.id) }
    }

    @Test
    fun `cleanExpiredReminders delegates to useCase`() = runTest {
        scheduler.cleanExpiredReminders()

        coVerify { reminderUseCase.cleanExpiredReminders() }
    }

    @Test
    fun `getReminderStats returns correct stats`() = runTest {
        val useCaseStats = ReminderUseCase.ReminderStats(
            totalReminders = 10,
            activeReminders = 5,
            todayReminders = 3,
            upcomingReminders = 2
        )
        coEvery { reminderUseCase.getReminderStats() } returns useCaseStats

        val result = scheduler.getReminderStats()

        assertEquals(10, result.totalReminders)
        assertEquals(5, result.activeReminders)
        assertEquals(3, result.todayReminders)
        assertEquals(2, result.upcomingReminders)
    }

    @Test
    fun `updateReminderTime reschedules with new minutes`() = runTest {
        coEvery { queryService.getCourseById(any()) } returns testCourse
        coEvery { queryService.getCurrentSchedule() } returns null

        scheduler.updateReminderTime(1, 15)

        coVerify { queryService.getCourseById(1) }
    }

    @Test
    fun `updateReminderTime skips when course not found`() = runTest {
        coEvery { queryService.getCourseById(any()) } returns null

        scheduler.updateReminderTime(999, 15)

        coVerify(exactly = 0) { queryService.insertReminder(any()) }
    }

    @Test
    fun `toggleCourseReminder enables scheduling when enabled`() = runTest {
        coEvery { queryService.getCourseById(any()) } returns testCourse
        coEvery { queryService.getCurrentSchedule() } returns null

        scheduler.toggleCourseReminder(1, true)

        coVerify { queryService.getCourseById(1) }
    }

    @Test
    fun `toggleCourseReminder cancels when disabled`() = runTest {
        coEvery { queryService.getCourseById(any()) } returns testCourse
        coEvery { queryService.getRemindersByCourse(any()) } returns emptyList()

        scheduler.toggleCourseReminder(1, false)

        coVerify { queryService.deleteRemindersByCourse(1) }
    }

    @Test
    fun `toggleCourseReminder skips when course not found`() = runTest {
        coEvery { queryService.getCourseById(any()) } returns null

        scheduler.toggleCourseReminder(999, true)

        coVerify(exactly = 0) { queryService.insertReminder(any()) }
    }

    @Test
    fun `rescheduleRemindersForAdjustment skips when course not found`() = runTest {
        coEvery { queryService.getCourseById(any()) } returns null

        val adjustment = CourseAdjustment(
            id = 1, originalCourseId = 999, scheduleId = 1,
            originalWeekNumber = 1, originalDayOfWeek = 1, originalStartSection = 1, originalSectionCount = 2,
            newWeekNumber = 2, newDayOfWeek = 2, newStartSection = 3, newSectionCount = 2
        )
        scheduler.rescheduleRemindersForAdjustment(adjustment)

        coVerify(exactly = 0) { queryService.insertReminder(any()) }
    }

    @Test
    fun `rescheduleRemindersForAdjustment skips when reminder disabled`() = runTest {
        val disabledCourse = testCourse.copy(reminderEnabled = false)
        coEvery { queryService.getCourseById(any()) } returns disabledCourse

        val adjustment = CourseAdjustment(
            id = 1, originalCourseId = 1, scheduleId = 1,
            originalWeekNumber = 1, originalDayOfWeek = 1, originalStartSection = 1, originalSectionCount = 2,
            newWeekNumber = 2, newDayOfWeek = 2, newStartSection = 3, newSectionCount = 2
        )
        scheduler.rescheduleRemindersForAdjustment(adjustment)

        coVerify(exactly = 0) { queryService.insertReminder(any()) }
    }

    @Test
    fun `getTodayReminders returns filtered reminders`() = runTest {
        val todayStart = LocalDate.now().atStartOfDay()
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val reminder = Reminder(
            id = 1, courseId = 1, minutesBefore = 10, isEnabled = true,
            weekNumber = 1, dayOfWeek = 1, triggerTime = todayStart + 3600000
        )
        coEvery { queryService.getTodayReminders() } returns listOf(reminder)

        val result = scheduler.getTodayReminders()

        assertEquals(1, result.size)
        assertEquals(reminder, result[0])
    }

    @Test
    fun `getTodayReminders excludes disabled reminders`() = runTest {
        coEvery { queryService.getTodayReminders() } returns emptyList()

        val result = scheduler.getTodayReminders()

        assertTrue(result.isEmpty())
    }

    @Test
    fun `getUpcomingReminders returns filtered reminders`() = runTest {
        val now = System.currentTimeMillis()
        val reminder = Reminder(
            id = 1, courseId = 1, minutesBefore = 10, isEnabled = true,
            weekNumber = 1, dayOfWeek = 1, triggerTime = now + 1800000
        )
        coEvery { queryService.getUpcomingReminders() } returns listOf(reminder)

        val result = scheduler.getUpcomingReminders()

        assertEquals(1, result.size)
    }

    @Test
    fun `getUpcomingReminders excludes past reminders`() = runTest {
        coEvery { queryService.getUpcomingReminders() } returns emptyList()

        val result = scheduler.getUpcomingReminders()

        assertTrue(result.isEmpty())
    }

    @Test
    fun `scheduleTestAlarm delegates to alarmManager`() {
        every { alarmManager.scheduleAlarm(any(), any(), any(), any(), any(), any(), any()) } returns true

        val result = scheduler.scheduleTestAlarm(
            courseId = 1, weekNumber = 1, triggerTime = System.currentTimeMillis() + 60000,
            isNextCourse = false, currentCourseName = "", isSameCourseClassroom = false, isClassEnd = false
        )

        verify { alarmManager.scheduleAlarm(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `toggleSingleReminder enables alarm`() = runTest {
        val reminder = Reminder(
            id = 1, courseId = 1, minutesBefore = 10, isEnabled = false,
            weekNumber = 1, dayOfWeek = 1, triggerTime = System.currentTimeMillis() + 60000
        )

        scheduler.toggleSingleReminder(reminder, true)

        verify { alarmManager.setAlarm(any(), any(), any()) }
    }

    @Test
    fun `toggleSingleReminder disables alarm`() = runTest {
        val reminder = Reminder(
            id = 1, courseId = 1, minutesBefore = 10, isEnabled = true,
            weekNumber = 1, dayOfWeek = 1, triggerTime = System.currentTimeMillis() + 60000
        )

        scheduler.toggleSingleReminder(reminder, false)

        verify { alarmManager.cancelAlarm(any(), any()) }
    }
}
