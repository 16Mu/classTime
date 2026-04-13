package com.wind.ggbond.classtime.data.repository

import com.wind.ggbond.classtime.data.local.dao.ReminderDao
import com.wind.ggbond.classtime.data.local.entity.Reminder
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderRepository @Inject constructor(
    reminderDao: ReminderDao
) : BaseRepository<Reminder, ReminderDao>(reminderDao) {

    override suspend fun getAll(): List<Reminder> = dao.getAllReminders()

    override fun getAllFlow(): Flow<List<Reminder>> = dao.getAllRemindersFlow()

    override suspend fun getById(id: Long): Reminder? = dao.getReminderById(id)

    override fun getByIdFlow(id: Long): Flow<Reminder?> = dao.getReminderByIdFlow(id)

    override suspend fun insert(entity: Reminder): Long = dao.insertReminder(entity)

    override suspend fun insertAll(entities: List<Reminder>): List<Long> =
        dao.insertReminders(entities)

    override suspend fun update(entity: Reminder) = dao.updateReminder(entity)

    override suspend fun delete(entity: Reminder) = dao.deleteReminder(entity)

    override suspend fun deleteById(id: Long) = dao.deleteReminderById(id)

    override suspend fun deleteAll() = dao.deleteAllReminders()

    fun getAllActiveReminders(): Flow<List<Reminder>> = dao.getAllActiveReminders()

    fun getRemindersByCourse(courseId: Long): Flow<List<Reminder>> =
        dao.getRemindersByCourse(courseId)

    suspend fun getRemindersInTimeRange(startTime: Long, endTime: Long): List<Reminder> =
        dao.getRemindersInTimeRange(startTime, endTime)

    suspend fun getTodayReminders(): List<Reminder> {
        val todayStart = LocalDate.now().atStartOfDay()
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return getRemindersInTimeRange(todayStart, todayStart + 24 * 60 * 60 * 1000)
    }

    suspend fun getThisWeekReminders(): List<Reminder> {
        val weekStart = LocalDate.now().with(java.time.DayOfWeek.MONDAY).atStartOfDay()
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return getRemindersInTimeRange(weekStart, weekStart + 7 * 24 * 60 * 60 * 1000)
    }

    suspend fun getRemindersByDay(dayOfWeek: Int): List<Reminder> =
        dao.getRemindersByDay(dayOfWeek)

    suspend fun deleteRemindersByCourse(courseId: Long) =
        dao.deleteRemindersByCourse(courseId)

    suspend fun deleteRemindersBySchedule(scheduleId: Long) =
        dao.deleteRemindersBySchedule(scheduleId)

    suspend fun deleteExpiredReminders(timestamp: Long = System.currentTimeMillis()): Int =
        dao.deleteExpiredReminders(timestamp)

    suspend fun getActiveReminderCount(): Int = dao.getActiveReminderCount()

    suspend fun getReminderCountInRange(startTime: Long, endTime: Long): Int =
        dao.getReminderCountInRange(startTime, endTime)
}
