package com.wind.ggbond.classtime.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 数据库迁移配置
 * 
 * 重要：永远不要使用 fallbackToDestructiveMigration()！
 * 它会在迁移失败时删除所有用户数据。
 */
object Migrations {
    
    /**
     * Migration 1 -> 2
     * 添加: schools 表（学校配置）
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // 创建 schools 表
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `schools` (
                    `id` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `loginUrl` TEXT NOT NULL,
                    `scheduleUrl` TEXT NOT NULL,
                    `systemType` TEXT NOT NULL,
                    `jsonMapping` TEXT NOT NULL,
                    `isActive` INTEGER NOT NULL DEFAULT 1,
                    `createdAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            
            // 创建索引以提升查询性能
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_schools_name` ON `schools` (`name`)"
            )
            
            // 记录迁移日志（可选）
            android.util.Log.i("Migration", "Database migrated from version 1 to 2 successfully")
        }
    }
    
    /**
     * Migration 2 -> 3
     * 修复: 为 class_times 表添加唯一索引，防止重复的节次记录
     */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // 1. 创建临时表（带唯一索引）
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `class_times_new` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `sectionNumber` INTEGER NOT NULL,
                    `startTime` TEXT NOT NULL,
                    `endTime` TEXT NOT NULL,
                    `configName` TEXT NOT NULL DEFAULT 'default',
                    `createdAt` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            
            // 2. 为新表创建唯一索引
            database.execSQL(
                """
                CREATE UNIQUE INDEX IF NOT EXISTS `index_class_times_configName_sectionNumber` 
                ON `class_times_new` (`configName`, `sectionNumber`)
                """.trimIndent()
            )
            
            // 3. 复制数据（只保留每个配置和节次的第一条记录，去重）
            database.execSQL(
                """
                INSERT INTO `class_times_new` (`id`, `sectionNumber`, `startTime`, `endTime`, `configName`, `createdAt`)
                SELECT `id`, `sectionNumber`, `startTime`, `endTime`, `configName`, `createdAt`
                FROM `class_times`
                WHERE `id` IN (
                    SELECT MIN(`id`) 
                    FROM `class_times` 
                    GROUP BY `configName`, `sectionNumber`
                )
                """.trimIndent()
            )
            
            // 4. 删除旧表
            database.execSQL("DROP TABLE `class_times`")
            
            // 5. 重命名新表
            database.execSQL("ALTER TABLE `class_times_new` RENAME TO `class_times`")
            
            // 记录迁移日志
            android.util.Log.i("Migration", "Database migrated from version 2 to 3: Added unique index to class_times and removed duplicates")
        }
    }
    
    /**
     * Migration 3 -> 4
     * 添加: course_adjustments 表（临时调课记录）
     */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // 创建临时调课表
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `course_adjustments` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `originalCourseId` INTEGER NOT NULL,
                    `scheduleId` INTEGER NOT NULL,
                    `semesterId` INTEGER NOT NULL,
                    `originalWeekNumber` INTEGER NOT NULL,
                    `originalDayOfWeek` INTEGER NOT NULL,
                    `originalStartSection` INTEGER NOT NULL,
                    `originalSectionCount` INTEGER NOT NULL,
                    `newWeekNumber` INTEGER NOT NULL,
                    `newDayOfWeek` INTEGER NOT NULL,
                    `newStartSection` INTEGER NOT NULL,
                    `newSectionCount` INTEGER NOT NULL,
                    `reason` TEXT NOT NULL DEFAULT '',
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            
            // 创建索引以提升查询性能
            // 索引1: 根据课程和周次查询（最常用）
            database.execSQL(
                """
                CREATE INDEX IF NOT EXISTS `index_course_adjustments_course_week` 
                ON `course_adjustments` (`originalCourseId`, `originalWeekNumber`)
                """.trimIndent()
            )
            
            // 索引2: 根据新时间查询（用于冲突检测）
            database.execSQL(
                """
                CREATE INDEX IF NOT EXISTS `index_course_adjustments_new_time` 
                ON `course_adjustments` (`scheduleId`, `newWeekNumber`, `newDayOfWeek`, `newStartSection`)
                """.trimIndent()
            )
            
            // 索引3: 根据课表查询
            database.execSQL(
                """
                CREATE INDEX IF NOT EXISTS `index_course_adjustments_schedule` 
                ON `course_adjustments` (`scheduleId`)
                """.trimIndent()
            )
            
            // 记录迁移日志
            android.util.Log.i("Migration", "Database migrated from version 3 to 4: Added course_adjustments table")
        }
    }
    
    /**
     * Migration 4 -> 5
     * 添加: exams 表（考试安排）
     */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // 创建考试表
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `exams` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `courseId` INTEGER NOT NULL,
                    `courseName` TEXT NOT NULL,
                    `examType` TEXT NOT NULL,
                    `weekNumber` INTEGER NOT NULL,
                    `dayOfWeek` INTEGER,
                    `startSection` INTEGER,
                    `sectionCount` INTEGER NOT NULL DEFAULT 2,
                    `examDate` TEXT,
                    `examTime` TEXT NOT NULL DEFAULT '',
                    `location` TEXT NOT NULL DEFAULT '',
                    `seat` TEXT NOT NULL DEFAULT '',
                    `reminderEnabled` INTEGER NOT NULL DEFAULT 1,
                    `reminderDays` INTEGER NOT NULL DEFAULT 3,
                    `note` TEXT NOT NULL DEFAULT '',
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            
            // 创建索引以提升查询性能
            // 索引1: 根据课程查询
            database.execSQL(
                """
                CREATE INDEX IF NOT EXISTS `index_exams_courseId` 
                ON `exams` (`courseId`)
                """.trimIndent()
            )
            
            // 索引2: 根据周次查询（用于顶部横幅）
            database.execSQL(
                """
                CREATE INDEX IF NOT EXISTS `index_exams_weekNumber` 
                ON `exams` (`weekNumber`)
                """.trimIndent()
            )
            
            // 索引3: 根据周次和星期查询（用于课表显示）
            database.execSQL(
                """
                CREATE INDEX IF NOT EXISTS `index_exams_week_day` 
                ON `exams` (`weekNumber`, `dayOfWeek`)
                """.trimIndent()
            )
            
            // 记录迁移日志
            android.util.Log.i("Migration", "Database migrated from version 4 to 5: Added exams table")
        }
    }
    
    /**
     * Migration 5 -> 6
     * 添加: auto_update_logs 表（自动更新日志）
     */
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // 创建自动更新日志表
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `auto_update_logs` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `timestamp` INTEGER NOT NULL,
                    `triggerEvent` TEXT NOT NULL,
                    `result` TEXT NOT NULL,
                    `successMessage` TEXT,
                    `failureReason` TEXT,
                    `scheduleId` INTEGER,
                    `durationMs` INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
            
            // 创建索引以提升查询性能
            // 索引1: 根据时间倒序查询（最常用）
            database.execSQL(
                """
                CREATE INDEX IF NOT EXISTS `index_auto_update_logs_timestamp` 
                ON `auto_update_logs` (`timestamp` DESC)
                """.trimIndent()
            )
            
            // 索引2: 根据结果类型查询
            database.execSQL(
                """
                CREATE INDEX IF NOT EXISTS `index_auto_update_logs_result` 
                ON `auto_update_logs` (`result`)
                """.trimIndent()
            )
            
            // 记录迁移日志
            android.util.Log.i("Migration", "Database migrated from version 5 to 6: Added auto_update_logs table")
        }
    }
    
    /**
     * Migration 6 -> 7
     * 更新: 清空 schools 表，准备重新加载新的学校数据
     */
    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // 清空 schools 表，以便重新加载最新的学校数据
            database.execSQL("DELETE FROM `schools`")
            
            // 记录迁移日志
            android.util.Log.i("Migration", "Database migrated from version 6 to 7: Cleared schools table for reload")
        }
    }
    
    /**
     * Migration 7 -> 8
     * 修复: 重建 schools 表，添加所有缺失字段，字段名改为 isEnabled
     */
    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // 1. 创建新的 schools 表（完整字段）
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `schools_new` (
                    `id` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `shortName` TEXT NOT NULL,
                    `province` TEXT NOT NULL,
                    `systemType` TEXT NOT NULL,
                    `loginUrl` TEXT NOT NULL,
                    `scheduleUrl` TEXT NOT NULL,
                    `scheduleMethod` TEXT NOT NULL,
                    `scheduleParams` TEXT NOT NULL,
                    `dataFormat` TEXT NOT NULL,
                    `needCsrfToken` INTEGER NOT NULL,
                    `csrfTokenName` TEXT NOT NULL,
                    `jsonMapping` TEXT NOT NULL,
                    `description` TEXT NOT NULL,
                    `tips` TEXT NOT NULL,
                    `isEnabled` INTEGER NOT NULL DEFAULT 1,
                    `createdAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            
            // 2. 如果旧表存在数据，尝试迁移（只迁移基本字段）
            // 由于新表字段更多，旧数据无法完全迁移，所以直接删除旧表
            database.execSQL("DROP TABLE IF EXISTS `schools`")
            
            // 3. 重命名新表
            database.execSQL("ALTER TABLE `schools_new` RENAME TO `schools`")
            
            // 4. 创建索引
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_schools_name` ON `schools` (`name`)"
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_schools_province` ON `schools` (`province`)"
            )
            
            // 记录迁移日志
            android.util.Log.i("Migration", "Database migrated from version 7 to 8: Rebuilt schools table with complete fields")
        }
    }
    
    /**
     * Migration 8 -> 9
     * 更新: 将所有现有课程的 reminderEnabled 默认值改为 true
     */
    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // 将所有现有课程的 reminderEnabled 设置为 1 (true)
            database.execSQL(
                """
                UPDATE `courses` 
                SET `reminderEnabled` = 1 
                WHERE `reminderEnabled` = 0
                """.trimIndent()
            )
            
            // 记录迁移日志
            android.util.Log.i("Migration", "Database migrated from version 8 to 9: Set all courses' reminderEnabled to true by default")
        }
    }
    
    /**
     * Migration 9 -> 10
     * 添加: 为 schedules 表添加 classTimeConfigName 字段，支持每个课表独立的时间配置
     */
    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // 为 schedules 表添加 classTimeConfigName 字段
            database.execSQL(
                """
                ALTER TABLE `schedules` 
                ADD COLUMN `classTimeConfigName` TEXT NOT NULL DEFAULT 'default'
                """.trimIndent()
            )
            
            // 记录迁移日志
            android.util.Log.i("Migration", "Database migrated from version 9 to 10: Added classTimeConfigName field to schedules table")
        }
    }
    
    /**
     * Migration 10 -> 11
     * 修复: 补全 schools 表缺失的3个学期日期字段 + 创建 auto_login_logs 表
     * 
     * 说明：这些字段和表在 version 10 时加入了 Entity 定义，
     * 但没有对应的迁移脚本，之前被 fallbackToDestructiveMigration 掩盖。
     * 删除 fallbackToDestructiveMigration 后必须补全迁移脚本。
     */
    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // 1. 为 schools 表添加3个缺失的学期日期字段
            // 使用 try-catch 保护：列可能已通过之前的 fallbackToDestructiveMigration 添加过
            try {
                database.execSQL(
                    "ALTER TABLE `schools` ADD COLUMN `defaultSemesterStartDate` TEXT DEFAULT NULL"
                )
            } catch (_: Exception) {
                android.util.Log.d("Migration", "Column defaultSemesterStartDate already exists, skipping")
            }
            try {
                database.execSQL(
                    "ALTER TABLE `schools` ADD COLUMN `fallSemesterStartDate` TEXT DEFAULT NULL"
                )
            } catch (_: Exception) {
                android.util.Log.d("Migration", "Column fallSemesterStartDate already exists, skipping")
            }
            try {
                database.execSQL(
                    "ALTER TABLE `schools` ADD COLUMN `springSemesterStartDate` TEXT DEFAULT NULL"
                )
            } catch (_: Exception) {
                android.util.Log.d("Migration", "Column springSemesterStartDate already exists, skipping")
            }
            
            // 2. 创建 auto_login_logs 表（之前缺少迁移脚本）
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `auto_login_logs` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `timestamp` INTEGER NOT NULL,
                    `resultCode` TEXT NOT NULL,
                    `resultMessage` TEXT NOT NULL,
                    `username` TEXT,
                    `durationMs` INTEGER NOT NULL DEFAULT 0,
                    `success` INTEGER NOT NULL DEFAULT 0,
                    `remark` TEXT
                )
                """.trimIndent()
            )
            
            // 创建索引
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_auto_login_logs_timestamp` ON `auto_login_logs` (`timestamp` DESC)"
            )
            
            // 记录迁移日志
            android.util.Log.i("Migration", "Database migrated from version 10 to 11: Added school semester date fields + auto_login_logs table")
        }
    }
    
    /**
     * Migration 11 -> 12
     * 修复: 重建 exams 表，移除 Entity 中已删除的 examDate 列
     * 
     * 说明：Exam Entity 中已移除 examDate 字段，但迁移4→5创建的表仍包含该列，
     * Room 校验 schema 时会发现表结构与 Entity 定义不匹配导致崩溃。
     * SQLite 低版本不支持 DROP COLUMN，需要通过重建表的方式移除。
     */
    val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // 1. 创建不含 examDate 列的新表
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `exams_new` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `courseId` INTEGER NOT NULL,
                    `courseName` TEXT NOT NULL,
                    `examType` TEXT NOT NULL,
                    `weekNumber` INTEGER NOT NULL,
                    `dayOfWeek` INTEGER,
                    `startSection` INTEGER,
                    `sectionCount` INTEGER NOT NULL DEFAULT 2,
                    `examTime` TEXT NOT NULL DEFAULT '',
                    `location` TEXT NOT NULL DEFAULT '',
                    `seat` TEXT NOT NULL DEFAULT '',
                    `reminderEnabled` INTEGER NOT NULL DEFAULT 1,
                    `reminderDays` INTEGER NOT NULL DEFAULT 3,
                    `note` TEXT NOT NULL DEFAULT '',
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            
            // 2. 迁移旧数据（不含 examDate）
            database.execSQL(
                """
                INSERT INTO `exams_new` (
                    `id`, `courseId`, `courseName`, `examType`, `weekNumber`,
                    `dayOfWeek`, `startSection`, `sectionCount`,
                    `examTime`, `location`, `seat`,
                    `reminderEnabled`, `reminderDays`, `note`,
                    `createdAt`, `updatedAt`
                )
                SELECT 
                    `id`, `courseId`, `courseName`, `examType`, `weekNumber`,
                    `dayOfWeek`, `startSection`, `sectionCount`,
                    `examTime`, `location`, `seat`,
                    `reminderEnabled`, `reminderDays`, `note`,
                    `createdAt`, `updatedAt`
                FROM `exams`
                """.trimIndent()
            )
            
            // 3. 删除旧表
            database.execSQL("DROP TABLE `exams`")
            
            // 4. 重命名新表
            database.execSQL("ALTER TABLE `exams_new` RENAME TO `exams`")
            
            // 5. 重建索引
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_exams_courseId` ON `exams` (`courseId`)"
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_exams_weekNumber` ON `exams` (`weekNumber`)"
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_exams_week_day` ON `exams` (`weekNumber`, `dayOfWeek`)"
            )
            
            // 记录迁移日志
            android.util.Log.i("Migration", "Database migrated from version 11 to 12: Rebuilt exams table, removed examDate column")
        }
    }
    
    /**
     * Migration 12 -> 13
     * 架构重构：将 Semester 合并到 Schedule，课表成为用户管理的核心单位
     * 
     * 变更内容：
     * 1. schedules 表：新增 startDate、endDate、totalWeeks 字段（从关联的 Semester 迁移数据）
     * 2. schedules 表：移除 semesterId 字段
     * 3. courses 表：移除 semesterId 字段
     * 4. course_adjustments 表：移除 semesterId 字段
     * 5. semesters 表保留但不再使用（避免数据丢失风险）
     */
    val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // ========== 1. 重建 schedules 表：新增学期字段，移除 semesterId ==========
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `schedules_new` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `name` TEXT NOT NULL,
                    `schoolName` TEXT NOT NULL,
                    `startDate` TEXT NOT NULL,
                    `endDate` TEXT NOT NULL,
                    `totalWeeks` INTEGER NOT NULL,
                    `isCurrent` INTEGER NOT NULL,
                    `classTimeConfigName` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            
            // 从旧 schedules 表迁移数据，同时从关联的 semesters 表获取学期信息
            database.execSQL(
                """
                INSERT INTO `schedules_new` (
                    `id`, `name`, `schoolName`,
                    `startDate`, `endDate`, `totalWeeks`,
                    `isCurrent`, `classTimeConfigName`,
                    `createdAt`, `updatedAt`
                )
                SELECT 
                    s.`id`, s.`name`, s.`schoolName`,
                    COALESCE(sem.`startDate`, ''),
                    COALESCE(sem.`endDate`, ''),
                    COALESCE(sem.`totalWeeks`, 20),
                    s.`isCurrent`, s.`classTimeConfigName`,
                    s.`createdAt`, s.`updatedAt`
                FROM `schedules` s
                LEFT JOIN `semesters` sem ON s.`semesterId` = sem.`id`
                """.trimIndent()
            )
            
            database.execSQL("DROP TABLE `schedules`")
            database.execSQL("ALTER TABLE `schedules_new` RENAME TO `schedules`")
            
            // ========== 2. 重建 courses 表：移除 semesterId ==========
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `courses_new` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `courseName` TEXT NOT NULL,
                    `teacher` TEXT NOT NULL,
                    `classroom` TEXT NOT NULL,
                    `dayOfWeek` INTEGER NOT NULL,
                    `startSection` INTEGER NOT NULL,
                    `sectionCount` INTEGER NOT NULL,
                    `weeks` TEXT NOT NULL,
                    `weekExpression` TEXT NOT NULL,
                    `scheduleId` INTEGER NOT NULL,
                    `credit` REAL NOT NULL,
                    `courseCode` TEXT NOT NULL,
                    `note` TEXT NOT NULL,
                    `color` TEXT NOT NULL,
                    `reminderEnabled` INTEGER NOT NULL,
                    `reminderMinutes` INTEGER NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            
            database.execSQL(
                """
                INSERT INTO `courses_new` (
                    `id`, `courseName`, `teacher`, `classroom`,
                    `dayOfWeek`, `startSection`, `sectionCount`,
                    `weeks`, `weekExpression`, `scheduleId`,
                    `credit`, `courseCode`, `note`, `color`,
                    `reminderEnabled`, `reminderMinutes`,
                    `createdAt`, `updatedAt`
                )
                SELECT 
                    `id`, `courseName`, `teacher`, `classroom`,
                    `dayOfWeek`, `startSection`, `sectionCount`,
                    `weeks`, `weekExpression`, `scheduleId`,
                    `credit`, `courseCode`, `note`, `color`,
                    `reminderEnabled`, `reminderMinutes`,
                    `createdAt`, `updatedAt`
                FROM `courses`
                """.trimIndent()
            )
            
            database.execSQL("DROP TABLE `courses`")
            database.execSQL("ALTER TABLE `courses_new` RENAME TO `courses`")
            
            // ========== 3. 重建 course_adjustments 表：移除 semesterId ==========
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `course_adjustments_new` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `originalCourseId` INTEGER NOT NULL,
                    `scheduleId` INTEGER NOT NULL,
                    `originalWeekNumber` INTEGER NOT NULL,
                    `originalDayOfWeek` INTEGER NOT NULL,
                    `originalStartSection` INTEGER NOT NULL,
                    `originalSectionCount` INTEGER NOT NULL,
                    `newWeekNumber` INTEGER NOT NULL,
                    `newDayOfWeek` INTEGER NOT NULL,
                    `newStartSection` INTEGER NOT NULL,
                    `newSectionCount` INTEGER NOT NULL,
                    `reason` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            
            database.execSQL(
                """
                INSERT INTO `course_adjustments_new` (
                    `id`, `originalCourseId`, `scheduleId`,
                    `originalWeekNumber`, `originalDayOfWeek`,
                    `originalStartSection`, `originalSectionCount`,
                    `newWeekNumber`, `newDayOfWeek`,
                    `newStartSection`, `newSectionCount`,
                    `reason`, `createdAt`, `updatedAt`
                )
                SELECT 
                    `id`, `originalCourseId`, `scheduleId`,
                    `originalWeekNumber`, `originalDayOfWeek`,
                    `originalStartSection`, `originalSectionCount`,
                    `newWeekNumber`, `newDayOfWeek`,
                    `newStartSection`, `newSectionCount`,
                    `reason`, `createdAt`, `updatedAt`
                FROM `course_adjustments`
                """.trimIndent()
            )
            
            database.execSQL("DROP TABLE `course_adjustments`")
            database.execSQL("ALTER TABLE `course_adjustments_new` RENAME TO `course_adjustments`")
            
            // ========== 4. 重建索引 ==========
            // course_adjustments 索引
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_course_adjustments_course_week` ON `course_adjustments` (`originalCourseId`, `originalWeekNumber`)"
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_course_adjustments_new_time` ON `course_adjustments` (`scheduleId`, `newWeekNumber`, `newDayOfWeek`, `newStartSection`)"
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_course_adjustments_schedule` ON `course_adjustments` (`scheduleId`)"
            )
            
            // 记录迁移日志
            android.util.Log.i("Migration", "Database migrated from version 12 to 13: Merged Semester into Schedule, removed semesterId from courses and course_adjustments")
        }
    }
    
    /**
     * 获取所有迁移配置
     * 按照顺序返回所有Migration对象
     */
    fun getAllMigrations(): Array<Migration> {
        return arrayOf(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
            MIGRATION_7_8,
            MIGRATION_8_9,
            MIGRATION_9_10,
            MIGRATION_10_11,
            MIGRATION_11_12,
            MIGRATION_12_13
        )
    }
}
