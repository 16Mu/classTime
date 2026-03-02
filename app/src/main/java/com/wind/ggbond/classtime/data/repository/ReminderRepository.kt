package com.wind.ggbond.classtime.data.repository

import com.wind.ggbond.classtime.data.local.dao.ReminderDao
import com.wind.ggbond.classtime.data.local.entity.Reminder
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 提醒数据仓库 - 增强版
 */
@Singleton
class ReminderRepository @Inject constructor(
    private val reminderDao: ReminderDao
) {
    
    fun getAllActiveReminders(): Flow<List<Reminder>> {
        return reminderDao.getAllActiveReminders()
    }
    
    // 获取全部提醒（含已禁用），用于提醒管理界面
    fun getAllRemindersFlow(): Flow<List<Reminder>> {
        return reminderDao.getAllRemindersFlow()
    }
    
    suspend fun getAllReminders(): List<Reminder> {
        return reminderDao.getAllReminders()
    }
    
    fun getRemindersByCourse(courseId: Long): Flow<List<Reminder>> {
        return reminderDao.getRemindersByCourse(courseId)
    }
    
    suspend fun getReminderById(reminderId: Long): Reminder? {
        return reminderDao.getReminderById(reminderId)
    }
    
    /**
     * 获取指定时间范围内的提醒
     */
    suspend fun getRemindersInTimeRange(startTime: Long, endTime: Long): List<Reminder> {
        return reminderDao.getRemindersInTimeRange(startTime, endTime)
    }
    
    /**
     * 获取今天的提醒
     */
    suspend fun getTodayReminders(): List<Reminder> {
        val todayStart = LocalDate.now().atStartOfDay()
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val todayEnd = todayStart + 24 * 60 * 60 * 1000
        return getRemindersInTimeRange(todayStart, todayEnd)
    }
    
    /**
     * 获取本周的提醒
     */
    suspend fun getThisWeekReminders(): List<Reminder> {
        val weekStart = LocalDate.now().with(java.time.DayOfWeek.MONDAY).atStartOfDay()
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val weekEnd = weekStart + 7 * 24 * 60 * 60 * 1000
        return getRemindersInTimeRange(weekStart, weekEnd)
    }
    
    /**
     * 获取指定星期的提醒
     */
    suspend fun getRemindersByDay(dayOfWeek: Int): List<Reminder> {
        return reminderDao.getRemindersByDay(dayOfWeek)
    }
    
    suspend fun insertReminder(reminder: Reminder): Long {
        return reminderDao.insertReminder(reminder)
    }
    
    suspend fun insertReminders(reminders: List<Reminder>): List<Long> {
        return reminderDao.insertReminders(reminders)
    }
    
    suspend fun updateReminder(reminder: Reminder) {
        reminderDao.updateReminder(reminder)
    }
    
    suspend fun deleteReminder(reminder: Reminder) {
        reminderDao.deleteReminder(reminder)
    }
    
    suspend fun deleteRemindersByCourse(courseId: Long) {
        reminderDao.deleteRemindersByCourse(courseId)
    }
    
    suspend fun deleteExpiredReminders(timestamp: Long = System.currentTimeMillis()): Int {
        return reminderDao.deleteExpiredReminders(timestamp)
    }
    
    suspend fun deleteAllReminders() {
        reminderDao.deleteAllReminders()
    }
    
    /**
     * 获取活跃提醒数量
     */
    suspend fun getActiveReminderCount(): Int {
        return reminderDao.getActiveReminderCount()
    }
    
    /**
     * 获取指定时间范围内的提醒数量
     */
    suspend fun getReminderCountInRange(startTime: Long, endTime: Long): Int {
        return reminderDao.getReminderCountInRange(startTime, endTime)
    }
}



