package com.wind.ggbond.classtime.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.wind.ggbond.classtime.data.local.converter.Converters
import com.wind.ggbond.classtime.data.local.dao.*
import com.wind.ggbond.classtime.data.local.entity.AutoLoginLog
import com.wind.ggbond.classtime.data.local.entity.AutoUpdateLog
import com.wind.ggbond.classtime.data.local.entity.ClassTime
import com.wind.ggbond.classtime.data.local.entity.Course
import com.wind.ggbond.classtime.data.local.entity.CourseAdjustment
import com.wind.ggbond.classtime.data.local.entity.Exam
import com.wind.ggbond.classtime.data.local.entity.Reminder
import com.wind.ggbond.classtime.data.local.entity.Schedule
import com.wind.ggbond.classtime.data.local.entity.SchoolEntity

/**
 * 课程数据库
 */
@Database(
    entities = [
        Course::class,
        ClassTime::class,
        Reminder::class,
        Schedule::class,
        SchoolEntity::class,
        CourseAdjustment::class,
        Exam::class,
        AutoUpdateLog::class,
        AutoLoginLog::class
    ],
    version = 13,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class CourseDatabase : RoomDatabase() {
    
    abstract fun courseDao(): CourseDao
    abstract fun classTimeDao(): ClassTimeDao
    abstract fun reminderDao(): ReminderDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun schoolDao(): SchoolDao
    abstract fun courseAdjustmentDao(): CourseAdjustmentDao
    abstract fun examDao(): ExamDao
    abstract fun autoUpdateLogDao(): AutoUpdateLogDao
    abstract fun autoLoginLogDao(): AutoLoginLogDao
    
    companion object {
        const val DATABASE_NAME = "course_schedule.db"
    }
}

