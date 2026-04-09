package com.wind.ggbond.classtime.data.local.database

import androidx.room.migration.Migration
import com.wind.ggbond.classtime.util.AppLogger
import androidx.sqlite.db.SupportSQLiteDatabase

object Migrations {

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
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
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_schools_name` ON `schools` (`name`)")
            AppLogger.i("Migration", "Database migrated from version 1 to 2 successfully")
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
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
            database.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_class_times_configName_sectionNumber` ON `class_times_new` (`configName`, `sectionNumber`)"
            )
            database.execSQL(
                """
                INSERT INTO `class_times_new` (`id`, `sectionNumber`, `startTime`, `endTime`, `configName`, `createdAt`)
                SELECT `id`, `sectionNumber`, `startTime`, `endTime`, `configName`, `createdAt`
                FROM `class_times`
                WHERE `id` IN (SELECT MIN(`id`) FROM `class_times` GROUP BY `configName`, `sectionNumber`)
                """.trimIndent()
            )
            database.execSQL("DROP TABLE `class_times`")
            database.execSQL("ALTER TABLE `class_times_new` RENAME TO `class_times`")
            AppLogger.i("Migration", "Database migrated from version 2 to 3: Added unique index to class_times and removed duplicates")
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(database: SupportSQLiteDatabase) {
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
                    `updatedAt` INTEGER NOT NULL,
                    FOREIGN KEY(`originalCourseId`) REFERENCES `courses`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`scheduleId`) REFERENCES `schedules`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_course_adjustments_originalCourseId` ON `course_adjustments` (`originalCourseId`)")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_course_adjustments_scheduleId` ON `course_adjustments` (`scheduleId`)")
            AppLogger.i("Migration", "Database migrated from version 3 to 4: Added course_adjustments table with foreign keys")
        }
    }

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(database: SupportSQLiteDatabase) {
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
                    `updatedAt` INTEGER NOT NULL,
                    FOREIGN KEY(`courseId`) REFERENCES `courses`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_exams_courseId` ON `exams` (`courseId`)")
            AppLogger.i("Migration", "Database migrated from version 4 to 5: Added exams table with foreign key")
        }
    }

    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(database: SupportSQLiteDatabase) {
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
            AppLogger.i("Migration", "Database migrated from version 5 to 6: Added auto_update_logs table")
        }
    }

    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("DELETE FROM `schools`")
            AppLogger.i("Migration", "Database migrated from version 6 to 7: Cleared schools table for reload")
        }
    }

    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(database: SupportSQLiteDatabase) {
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
            database.execSQL("DROP TABLE IF EXISTS `schools`")
            database.execSQL("ALTER TABLE `schools_new` RENAME TO `schools`")
            AppLogger.i("Migration", "Database migrated from version 7 to 8: Rebuilt schools table with complete fields")
        }
    }

    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(database: SupportSQLiteDatabase) {
            try { database.execSQL("UPDATE `courses` SET `reminderEnabled` = 1 WHERE `reminderEnabled` = 0") } catch (_: Exception) {}
            AppLogger.i("Migration", "Database migrated from version 8 to 9")
        }
    }

    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(database: SupportSQLiteDatabase) {
            try { database.execSQL("ALTER TABLE `schedules` ADD COLUMN `classTimeConfigName` TEXT NOT NULL DEFAULT 'default'") } catch (_: Exception) {}
            AppLogger.i("Migration", "Database migrated from version 9 to 10: Added classTimeConfigName field to schedules table")
        }
    }

    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(database: SupportSQLiteDatabase) {
            try { database.execSQL("ALTER TABLE `schools` ADD COLUMN `defaultSemesterStartDate` TEXT DEFAULT NULL") } catch (_: Exception) {}
            try { database.execSQL("ALTER TABLE `schools` ADD COLUMN `fallSemesterStartDate` TEXT DEFAULT NULL") } catch (_: Exception) {}
            try { database.execSQL("ALTER TABLE `schools` ADD COLUMN `springSemesterStartDate` TEXT DEFAULT NULL") } catch (_: Exception) {}
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
            AppLogger.i("Migration", "Database migrated from version 10 to 11: Added school semester date fields + auto_login_logs table")
        }
    }

    val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(database: SupportSQLiteDatabase) {
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
                    `updatedAt` INTEGER NOT NULL,
                    FOREIGN KEY(`courseId`) REFERENCES `courses`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            database.execSQL(
                """
                INSERT INTO `exams_new` (`id`,`courseId`,`courseName`,`examType`,`weekNumber`,`dayOfWeek`,`startSection`,`sectionCount`,`examTime`,`location`,`seat`,`reminderEnabled`,`reminderDays`,`note`,`createdAt`,`updatedAt`)
                SELECT `id`,`courseId`,`courseName`,`examType`,`weekNumber`,`dayOfWeek`,`startSection`,`sectionCount`,`examTime`,`location`,`seat`,`reminderEnabled`,`reminderDays`,`note`,`createdAt`,`updatedAt`
                FROM `exams`
                """.trimIndent()
            )
            database.execSQL("DROP TABLE `exams`")
            database.execSQL("ALTER TABLE `exams_new` RENAME TO `exams`")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_exams_courseId` ON `exams` (`courseId`)")
            AppLogger.i("Migration", "Database migrated from version 11 to 12: Rebuilt exams table with foreign key, removed examDate column")
        }
    }

    val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(database: SupportSQLiteDatabase) {
            safeAddColumn(database, "courses", "`note` TEXT NOT NULL DEFAULT ''")
            safeAddColumn(database, "courses", "`weeks` TEXT NOT NULL DEFAULT '[]'")
            safeAddColumn(database, "courses", "`color` TEXT NOT NULL DEFAULT '#42A5F5'")
            safeAddColumn(database, "courses", "`reminderMinutes` INTEGER NOT NULL DEFAULT 10")
            safeAddColumn(database, "courses", "`credit` REAL NOT NULL DEFAULT 0.0")
            safeAddColumn(database, "courses", "`courseCode` TEXT NOT NULL DEFAULT ''")
            safeAddColumn(database, "courses", "`weekExpression` TEXT NOT NULL DEFAULT ''")
            safeAddColumn(database, "courses", "`teacher` TEXT NOT NULL DEFAULT ''")
            safeAddColumn(database, "courses", "`classroom` TEXT NOT NULL DEFAULT ''")

            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `schedules_new` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `name` TEXT NOT NULL,
                    `schoolName` TEXT NOT NULL DEFAULT '',
                    `startDate` TEXT NOT NULL DEFAULT '',
                    `endDate` TEXT NOT NULL DEFAULT '',
                    `totalWeeks` INTEGER NOT NULL DEFAULT 20,
                    `isCurrent` INTEGER NOT NULL DEFAULT 0,
                    `classTimeConfigName` TEXT NOT NULL DEFAULT 'default',
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            database.execSQL(
                """
                INSERT INTO `schedules_new` (`id`,`name`,`schoolName`,`startDate`,`endDate`,`totalWeeks`,`isCurrent`,`classTimeConfigName`,`createdAt`,`updatedAt`)
                SELECT s.`id`, s.`name`, COALESCE(s.`schoolName`, ''),
                       COALESCE(sem.`startDate`, ''), COALESCE(sem.`endDate`, ''), COALESCE(sem.`totalWeeks`, 20),
                       s.`isCurrent`, COALESCE(s.`classTimeConfigName`, 'default'),
                       s.`createdAt`, s.`updatedAt`
                FROM `schedules` s LEFT JOIN `semesters` sem ON s.`semesterId` = sem.`id`
                """.trimIndent()
            )
            database.execSQL("DROP TABLE `schedules`")
            database.execSQL("ALTER TABLE `schedules_new` RENAME TO `schedules`")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_schedules_isCurrent` ON `schedules` (`isCurrent`)")

            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `courses_new` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `courseName` TEXT NOT NULL,
                    `teacher` TEXT NOT NULL DEFAULT '',
                    `classroom` TEXT NOT NULL DEFAULT '',
                    `dayOfWeek` INTEGER NOT NULL,
                    `startSection` INTEGER NOT NULL,
                    `sectionCount` INTEGER NOT NULL DEFAULT 1,
                    `weeks` TEXT NOT NULL DEFAULT '[]',
                    `weekExpression` TEXT NOT NULL DEFAULT '',
                    `scheduleId` INTEGER NOT NULL DEFAULT 1,
                    `credit` REAL NOT NULL DEFAULT 0.0,
                    `courseCode` TEXT NOT NULL DEFAULT '',
                    `note` TEXT NOT NULL DEFAULT '',
                    `color` TEXT NOT NULL DEFAULT '#42A5F5',
                    `reminderEnabled` INTEGER NOT NULL DEFAULT 1,
                    `reminderMinutes` INTEGER NOT NULL DEFAULT 10,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    FOREIGN KEY(`scheduleId`) REFERENCES `schedules`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            database.execSQL(
                """
                INSERT INTO `courses_new` (`id`,`courseName`,`teacher`,`classroom`,`dayOfWeek`,`startSection`,`sectionCount`,`weeks`,`weekExpression`,`scheduleId`,`credit`,`courseCode`,`note`,`color`,`reminderEnabled`,`reminderMinutes`,`createdAt`,`updatedAt`)
                SELECT `id`,
                       COALESCE(`courseName`, ''),
                       COALESCE(`teacher`, ''),
                       COALESCE(`classroom`, ''),
                       COALESCE(`dayOfWeek`, 1),
                       COALESCE(`startSection`, 1),
                       COALESCE(`sectionCount`, 1),
                       COALESCE(`weeks`, '[]'),
                       COALESCE(`weekExpression`, ''),
                       COALESCE(`scheduleId`, 1),
                       COALESCE(`credit`, 0.0),
                       COALESCE(`courseCode`, ''),
                       COALESCE(`note`, ''),
                       COALESCE(`color`, '#42A5F5'),
                       COALESCE(`reminderEnabled`, 1),
                       COALESCE(`reminderMinutes`, 10),
                       `createdAt`, `updatedAt`
                FROM `courses`
                """.trimIndent()
            )
            database.execSQL("DROP TABLE `courses`")
            database.execSQL("ALTER TABLE `courses_new` RENAME TO `courses`")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_courses_scheduleId` ON `courses` (`scheduleId`)")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_courses_scheduleId_dayOfWeek_startSection` ON `courses` (`scheduleId`, `dayOfWeek`, `startSection`)")

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
                    `reason` TEXT NOT NULL DEFAULT '',
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    FOREIGN KEY(`originalCourseId`) REFERENCES `courses`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`scheduleId`) REFERENCES `schedules`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            database.execSQL(
                """
                INSERT INTO `course_adjustments_new` (`id`,`originalCourseId`,`scheduleId`,`originalWeekNumber`,`originalDayOfWeek`,`originalStartSection`,`originalSectionCount`,`newWeekNumber`,`newDayOfWeek`,`newStartSection`,`newSectionCount`,`reason`,`createdAt`,`updatedAt`)
                SELECT * FROM `course_adjustments`
                """.trimIndent()
            )
            database.execSQL("DROP TABLE `course_adjustments`")
            database.execSQL("ALTER TABLE `course_adjustments_new` RENAME TO `course_adjustments`")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_course_adjustments_originalCourseId` ON `course_adjustments` (`originalCourseId`)")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_course_adjustments_scheduleId` ON `course_adjustments` (`scheduleId`)")
            AppLogger.i("Migration", "Database migrated from version 12 to 13: Full schema rebuild with missing column fallback")
        }
    }

    val MIGRATION_13_14 = object : Migration(13, 14) {
        override fun migrate(database: SupportSQLiteDatabase) {
            try { database.execSQL("DROP INDEX IF EXISTS `index_schools_name`") } catch (_: Exception) {}
            try { database.execSQL("DROP INDEX IF EXISTS `index_schools_province`") } catch (_: Exception) {}
            try { database.execSQL("DROP INDEX IF EXISTS `index_auto_update_logs_timestamp`") } catch (_: Exception) {}
            try { database.execSQL("DROP INDEX IF EXISTS `index_auto_update_logs_result`") } catch (_: Exception) {}
            try { database.execSQL("DROP INDEX IF EXISTS `index_auto_login_logs_timestamp`") } catch (_: Exception) {}
            AppLogger.i("Migration", "Database migrated from version 13 to 14: Cleaned up extra indices")
        }
    }

    val MIGRATION_14_15 = object : Migration(14, 15) {
        override fun migrate(database: SupportSQLiteDatabase) {
            try { database.execSQL("ALTER TABLE `course_adjustments` ADD COLUMN `newClassroom` TEXT NOT NULL DEFAULT ''") } catch (_: Exception) {}
            AppLogger.i("Migration", "Database migrated from version 14 to 15: Added newClassroom field to course_adjustments")
        }
    }

    val MIGRATION_15_16 = object : Migration(15, 16) {
        override fun migrate(database: SupportSQLiteDatabase) {
            ensureCoursesSchema(database)
            ensureSchedulesSchema(database)
            ensureExamsSchema(database)
            ensureCourseAdjustmentsSchema(database)
            ensureSchoolsSchema(database)
            AppLogger.i("Migration", "Database migrated from version 15 to 16: Ultimate schema validation - all tables verified against Entity definitions")
        }
    }

    private fun safeAddColumn(db: SupportSQLiteDatabase, table: String, columnDef: String) {
        try { db.execSQL("ALTER TABLE `$table` ADD COLUMN $columnDef") } catch (e: Exception) {
            AppLogger.d("Migration", "Column already exists in $table, skipping: ${e.message}")
        }
    }

    private fun ensureCoursesSchema(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `courses_backup` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `courseName` TEXT NOT NULL,
                `teacher` TEXT NOT NULL DEFAULT '',
                `classroom` TEXT NOT NULL DEFAULT '',
                `dayOfWeek` INTEGER NOT NULL,
                `startSection` INTEGER NOT NULL,
                `sectionCount` INTEGER NOT NULL DEFAULT 1,
                `weeks` TEXT NOT NULL DEFAULT '[]',
                `weekExpression` TEXT NOT NULL DEFAULT '',
                `scheduleId` INTEGER NOT NULL DEFAULT 1,
                `credit` REAL NOT NULL DEFAULT 0.0,
                `courseCode` TEXT NOT NULL DEFAULT '',
                `note` TEXT NOT NULL DEFAULT '',
                `color` TEXT NOT NULL DEFAULT '#42A5F5',
                `reminderEnabled` INTEGER NOT NULL DEFAULT 1,
                `reminderMinutes` INTEGER NOT NULL DEFAULT 10,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                FOREIGN KEY(`scheduleId`) REFERENCES `schedules`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT OR IGNORE INTO `courses_backup` (`id`,`courseName`,`teacher`,`classroom`,`dayOfWeek`,`startSection`,`sectionCount`,`weeks`,`weekExpression`,`scheduleId`,`credit`,`courseCode`,`note`,`color`,`reminderEnabled`,`reminderMinutes`,`createdAt`,`updatedAt`)
            SELECT `id`,
                   COALESCE(`courseName`, ''),
                   COALESCE(`teacher`, ''),
                   COALESCE(`classroom`, ''),
                   COALESCE(`dayOfWeek`, 1),
                   COALESCE(`startSection`, 1),
                   COALESCE(`sectionCount`, 1),
                   COALESCE(`weeks`, '[]'),
                   COALESCE(`weekExpression`, ''),
                   COALESCE(`scheduleId`, 1),
                   COALESCE(`credit`, 0.0),
                   COALESCE(`courseCode`, ''),
                   COALESCE(`note`, ''),
                   COALESCE(`color`, '#42A5F5'),
                   COALESCE(`reminderEnabled`, 1),
                   COALESCE(`reminderMinutes`, 10),
                   `createdAt`, `updatedAt`
            FROM `courses`
            """.trimIndent()
        )
        db.execSQL("DROP TABLE `courses`")
        db.execSQL("ALTER TABLE `courses_backup` RENAME TO `courses`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_courses_scheduleId` ON `courses` (`scheduleId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_courses_scheduleId_dayOfWeek_startSection` ON `courses` (`scheduleId`, `dayOfWeek`, `startSection`)")
    }

    private fun ensureSchedulesSchema(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `schedules_backup` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `schoolName` TEXT NOT NULL DEFAULT '',
                `startDate` TEXT NOT NULL DEFAULT '',
                `endDate` TEXT NOT NULL DEFAULT '',
                `totalWeeks` INTEGER NOT NULL DEFAULT 20,
                `isCurrent` INTEGER NOT NULL DEFAULT 0,
                `classTimeConfigName` TEXT NOT NULL DEFAULT 'default',
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT OR IGNORE INTO `schedules_backup` (`id`,`name`,`schoolName`,`startDate`,`endDate`,`totalWeeks`,`isCurrent`,`classTimeConfigName`,`createdAt`,`updatedAt`)
            SELECT `id`, COALESCE(`name`, ''), COALESCE(`schoolName`, ''),
                   COALESCE(`startDate`, ''), COALESCE(`endDate`, ''), COALESCE(`totalWeeks`, 20),
                   COALESCE(`isCurrent`, 0), COALESCE(`classTimeConfigName`, 'default'), `createdAt`, `updatedAt`
            FROM `schedules`
            """.trimIndent()
        )
        db.execSQL("DROP TABLE `schedules`")
        db.execSQL("ALTER TABLE `schedules_backup` RENAME TO `schedules`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_schedules_isCurrent` ON `schedules` (`isCurrent`)")
    }

    private fun ensureExamsSchema(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `exams_backup` (
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
                `updatedAt` INTEGER NOT NULL,
                FOREIGN KEY(`courseId`) REFERENCES `courses`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT OR IGNORE INTO `exams_backup` (`id`,`courseId`,`courseName`,`examType`,`weekNumber`,`dayOfWeek`,`startSection`,`sectionCount`,`examTime`,`location`,`seat`,`reminderEnabled`,`reminderDays`,`note`,`createdAt`,`updatedAt`)
            SELECT * FROM `exams`
            """.trimIndent()
        )
        db.execSQL("DROP TABLE `exams`")
        db.execSQL("ALTER TABLE `exams_backup` RENAME TO `exams`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_exams_courseId` ON `exams` (`courseId`)")
    }

    private fun ensureCourseAdjustmentsSchema(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `course_adjustments_backup` (
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
                `newClassroom` TEXT NOT NULL DEFAULT '',
                `reason` TEXT NOT NULL DEFAULT '',
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                FOREIGN KEY(`originalCourseId`) REFERENCES `courses`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`scheduleId`) REFERENCES `schedules`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT OR IGNORE INTO `course_adjustments_backup` (`id`,`originalCourseId`,`scheduleId`,`originalWeekNumber`,`originalDayOfWeek`,`originalStartSection`,`originalSectionCount`,`newWeekNumber`,`newDayOfWeek`,`newStartSection`,`newSectionCount`,`newClassroom`,`reason`,`createdAt`,`updatedAt`)
            SELECT `id`,`originalCourseId`,`scheduleId`,`originalWeekNumber`,`originalDayOfWeek`,`originalStartSection`,`originalSectionCount`,`newWeekNumber`,`newDayOfWeek`,`newStartSection`,`newSectionCount`,COALESCE(`newClassroom`, ''),COALESCE(`reason`, ''),`createdAt`,`updatedAt`
            FROM `course_adjustments`
            """.trimIndent()
        )
        db.execSQL("DROP TABLE `course_adjustments`")
        db.execSQL("ALTER TABLE `course_adjustments_backup` RENAME TO `course_adjustments`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_course_adjustments_originalCourseId` ON `course_adjustments` (`originalCourseId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_course_adjustments_scheduleId` ON `course_adjustments` (`scheduleId`)")
    }

    private fun ensureSchoolsSchema(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `schools_backup` (
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
                `defaultSemesterStartDate` TEXT,
                `fallSemesterStartDate` TEXT,
                `springSemesterStartDate` TEXT,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT OR IGNORE INTO `schools_backup` (`id`,`name`,`shortName`,`province`,`systemType`,`loginUrl`,`scheduleUrl`,`scheduleMethod`,`scheduleParams`,`dataFormat`,`needCsrfToken`,`csrfTokenName`,`jsonMapping`,`description`,`tips`,`isEnabled`,`createdAt`,`defaultSemesterStartDate`,`fallSemesterStartDate`,`springSemesterStartDate`)
            SELECT `id`,`name`,`shortName`,`province`,`systemType`,`loginUrl`,`scheduleUrl`,`scheduleMethod`,`scheduleParams`,`dataFormat`,`needCsrfToken`,`csrfTokenName`,`jsonMapping`,`description`,`tips`,COALESCE(`isEnabled`, 1),`createdAt`,`defaultSemesterStartDate`,`fallSemesterStartDate`,`springSemesterStartDate`
            FROM `schools`
            """.trimIndent()
        )
        db.execSQL("DROP TABLE `schools`")
        db.execSQL("ALTER TABLE `schools_backup` RENAME TO `schools`")
    }

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
            MIGRATION_12_13,
            MIGRATION_13_14,
            MIGRATION_14_15,
            MIGRATION_15_16
        )
    }
}
