package com.wind.ggbond.classtime.data.local.dao

import androidx.room.*
import com.wind.ggbond.classtime.data.local.entity.Reminder
import kotlinx.coroutines.flow.Flow

/**
 * 提醒数据访问对象 - 增强版
 */
@Dao
interface ReminderDao {
    
    @Query("SELECT * FROM reminders WHERE isEnabled = 1 ORDER BY triggerTime")
    fun getAllActiveReminders(): Flow<List<Reminder>>
    
    // 获取全部提醒（含已禁用），用于提醒管理界面
    @Query("SELECT * FROM reminders ORDER BY triggerTime")
    fun getAllRemindersFlow(): Flow<List<Reminder>>
    
    @Query("SELECT * FROM reminders ORDER BY triggerTime")
    suspend fun getAllReminders(): List<Reminder>
    
    @Query("SELECT * FROM reminders WHERE courseId = :courseId ORDER BY triggerTime")
    fun getRemindersByCourse(courseId: Long): Flow<List<Reminder>>
    
    @Query("SELECT * FROM reminders WHERE id = :reminderId")
    suspend fun getReminderById(reminderId: Long): Reminder?

    @Query("SELECT * FROM reminders WHERE id = :reminderId")
    fun getReminderByIdFlow(reminderId: Long): Flow<Reminder?>
    
    @Query("SELECT * FROM reminders WHERE triggerTime BETWEEN :startTime AND :endTime AND isEnabled = 1 ORDER BY triggerTime")
    suspend fun getRemindersInTimeRange(startTime: Long, endTime: Long): List<Reminder>
    
    @Query("SELECT * FROM reminders WHERE dayOfWeek = :dayOfWeek AND isEnabled = 1 ORDER BY triggerTime")
    suspend fun getRemindersByDay(dayOfWeek: Int): List<Reminder>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: Reminder): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminders(reminders: List<Reminder>): List<Long>
    
    @Update
    suspend fun updateReminder(reminder: Reminder)
    
    @Delete
    suspend fun deleteReminder(reminder: Reminder)
    
    @Query("DELETE FROM reminders WHERE courseId = :courseId")
    suspend fun deleteRemindersByCourse(courseId: Long)
    
    @Query("DELETE FROM reminders WHERE triggerTime < :timestamp")
    suspend fun deleteExpiredReminders(timestamp: Long): Int
    
    @Query("DELETE FROM reminders")
    suspend fun deleteAllReminders()
    
    @Query("SELECT COUNT(*) FROM reminders WHERE isEnabled = 1")
    suspend fun getActiveReminderCount(): Int
    
    @Query("SELECT COUNT(*) FROM reminders WHERE triggerTime BETWEEN :startTime AND :endTime AND isEnabled = 1")
    suspend fun getReminderCountInRange(startTime: Long, endTime: Long): Int
}


