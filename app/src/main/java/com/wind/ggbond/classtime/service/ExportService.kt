package com.wind.ggbond.classtime.service

import android.content.Context
import android.content.Intent
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import com.wind.ggbond.classtime.util.AppLogger
import com.wind.ggbond.classtime.data.repository.ClassTimeRepository
import com.wind.ggbond.classtime.data.repository.CourseRepository
import com.wind.ggbond.classtime.data.repository.ScheduleRepository
import com.wind.ggbond.classtime.service.contract.IDataExporter
import com.wind.ggbond.classtime.service.contract.IDataExporter.ExportFormat
import com.wind.ggbond.classtime.service.contract.IDataExporter.ExportResult
import com.wind.ggbond.classtime.service.helper.ExportDirectoryManager
import com.wind.ggbond.classtime.service.helper.ExportFormatter
import com.wind.ggbond.classtime.service.helper.ExportMeta
import com.wind.ggbond.classtime.service.helper.ExportDataModel
import com.wind.ggbond.classtime.service.helper.CourseExportItem
import com.wind.ggbond.classtime.service.helper.ScheduleExportItem
import com.wind.ggbond.classtime.service.helper.ClassTimeExportItem
import com.wind.ggbond.classtime.service.helper.StatisticsExportItem
import com.google.gson.GsonBuilder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExportService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val courseRepository: CourseRepository,
    private val scheduleRepository: ScheduleRepository,
    private val classTimeRepository: ClassTimeRepository,
    private val exportFormatter: ExportFormatter,
    private val exportDirectoryManager: ExportDirectoryManager
) : IDataExporter {

    private val gson = GsonBuilder()
        .setPrettyPrinting()
        .setDateFormat("yyyy-MM-dd HH:mm:ss")
        .create()

    override suspend fun export(scheduleId: Long, format: ExportFormat): ExportResult {
        if (!exportDirectoryManager.isExternalStorageWritable()) {
            AppLogger.w("ExportService", "外部存储不可写，尝试使用备用目录")
        }
        return when (format) {
            ExportFormat.JSON -> exportToJson(scheduleId)
            ExportFormat.ICS -> exportToIcs(scheduleId)
            ExportFormat.CSV -> exportToCsv(scheduleId)
            ExportFormat.TXT -> exportToText(scheduleId)
            ExportFormat.HTML -> exportToHtml(scheduleId)
        }
    }

    private fun buildExportMeta(): ExportMeta {
        val now = java.time.LocalDateTime.now()
        return ExportMeta(
            exportTime = now.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
            appName = "课程表",
            appVersion = ExportMeta.CURRENT_APP_VERSION,
            exportVersion = ExportMeta.CURRENT_EXPORT_VERSION,
            format = "CourseScheduleExport"
        )
    }

    private fun buildScheduleExportItem(schedule: com.wind.ggbond.classtime.data.local.entity.Schedule): ScheduleExportItem {
        return ScheduleExportItem(
            name = schedule.name,
            schoolName = schedule.schoolName,
            startDate = schedule.startDate.toString(),
            endDate = schedule.endDate.toString(),
            totalWeeks = schedule.totalWeeks,
            classTimeConfigName = schedule.classTimeConfigName
        )
    }

    private fun buildClassTimeExportItems(classTimes: List<com.wind.ggbond.classtime.data.local.entity.ClassTime>): List<ClassTimeExportItem> {
        return classTimes.map {
            ClassTimeExportItem(
                sectionNumber = it.sectionNumber,
                startTime = it.startTime.toString(),
                endTime = it.endTime.toString(),
                configName = it.configName
            )
        }
    }

    private fun buildCourseExportItems(courses: List<com.wind.ggbond.classtime.data.local.entity.Course>): List<CourseExportItem> {
        return courses.map { course ->
            CourseExportItem(
                courseName = course.courseName,
                courseCode = course.courseCode,
                teacher = course.teacher,
                classroom = course.classroom,
                dayOfWeek = course.dayOfWeek,
                dayName = exportFormatter.getDayName(course.dayOfWeek),
                startSection = course.startSection,
                sectionCount = course.sectionCount,
                weeks = course.weeks,
                weekExpression = course.weekExpression,
                credit = course.credit,
                color = course.color,
                note = course.note,
                reminderEnabled = course.reminderEnabled,
                reminderMinutes = course.reminderMinutes
            )
        }
    }

    private fun buildStatisticsExportItem(courses: List<com.wind.ggbond.classtime.data.local.entity.Course>): StatisticsExportItem {
        return StatisticsExportItem(
            totalCourses = courses.size,
            totalCredits = courses.sumOf { it.credit.toDouble() },
            coursesByDay = (1..7).associate { day ->
                exportFormatter.getDayName(day) to courses.count { it.dayOfWeek == day }
            }
        )
    }

    private fun computeChecksum(data: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(data.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }

    suspend fun exportToJson(scheduleId: Long): ExportResult {
        return try {
            val schedule = scheduleRepository.getScheduleById(scheduleId)
                ?: return ExportResult(success = false, errorMessage = "未找到课表信息")
            val courseList = courseRepository.getAllCoursesBySchedule(scheduleId).first()
            val classTimes = classTimeRepository.getClassTimesByConfigSync()

            val meta = buildExportMeta()
            val scheduleItem = buildScheduleExportItem(schedule)
            val classTimeItems = buildClassTimeExportItems(classTimes)
            val courseItems = buildCourseExportItems(courseList)
            val statistics = buildStatisticsExportItem(courseList)

            val exportData = ExportDataModel(
                meta = meta,
                schedule = scheduleItem,
                classTimes = classTimeItems,
                statistics = statistics,
                courses = courseItems
            )

            val json = gson.toJson(exportData)
            val checksum = computeChecksum(json)
            val finalExportData = exportData.copy(meta = meta.copy(checksum = checksum))
            val finalJson = gson.toJson(finalExportData)

            val fileName = "课程表_${exportFormatter.getCurrentDateString()}.json"
            val file = saveToFile(fileName, finalJson, ExportFormat.JSON)

            ExportResult(
                success = true,
                filePath = file.absolutePath,
                fileUri = getFileUri(file)
            )
        } catch (e: Exception) {
            AppLogger.e("ExportService", "JSON导出失败", e)
            ExportResult(success = false, errorMessage = "JSON导出失败: ${e.message}")
        }
    }

    suspend fun exportToIcs(scheduleId: Long): ExportResult {
        return try {
            val schedule = scheduleRepository.getScheduleById(scheduleId)
                ?: return ExportResult(success = false, errorMessage = "未找到课表信息")
            val classTimes = classTimeRepository.getClassTimesByConfigSync()
            val courseList = courseRepository.getAllCoursesBySchedule(scheduleId).first()

            val icsContent = exportFormatter.buildIcsContent(courseList, schedule, classTimes)

            val fileName = "课程表_${exportFormatter.getCurrentDateString()}.ics"
            val file = saveToFile(fileName, icsContent, ExportFormat.ICS)

            ExportResult(
                success = true,
                filePath = file.absolutePath,
                fileUri = getFileUri(file)
            )
        } catch (e: Exception) {
            AppLogger.e("ExportService", "ICS导出失败", e)
            ExportResult(success = false, errorMessage = "ICS导出失败: ${e.message}")
        }
    }

    suspend fun exportToCsv(scheduleId: Long): ExportResult {
        return try {
            val schedule = scheduleRepository.getScheduleById(scheduleId)
                ?: return ExportResult(success = false, errorMessage = "未找到课表信息")
            val courseList = courseRepository.getAllCoursesBySchedule(scheduleId).first()
            val classTimes = classTimeRepository.getClassTimesByConfigSync()

            val csv = exportFormatter.buildCsvContent(courseList, schedule, classTimes)

            val fileName = "课程表_${exportFormatter.getCurrentDateString()}.csv"
            val file = saveToFile(fileName, csv, ExportFormat.CSV)

            ExportResult(
                success = true,
                filePath = file.absolutePath,
                fileUri = getFileUri(file)
            )
        } catch (e: Exception) {
            AppLogger.e("ExportService", "CSV导出失败", e)
            ExportResult(success = false, errorMessage = "CSV导出失败: ${e.message}")
        }
    }

    suspend fun exportToText(scheduleId: Long): ExportResult {
        return try {
            val schedule = scheduleRepository.getScheduleById(scheduleId)
                ?: return ExportResult(success = false, errorMessage = "未找到课表信息")
            val courseList = courseRepository.getAllCoursesBySchedule(scheduleId).first()
            val classTimes = classTimeRepository.getClassTimesByConfigSync()

            val text = exportFormatter.buildTextContent(courseList, schedule, classTimes)

            val fileName = "课程表_${exportFormatter.getCurrentDateString()}.txt"
            val file = saveToFile(fileName, text, ExportFormat.TXT)

            ExportResult(
                success = true,
                filePath = file.absolutePath,
                fileUri = getFileUri(file)
            )
        } catch (e: Exception) {
            AppLogger.e("ExportService", "文本导出失败", e)
            ExportResult(success = false, errorMessage = "文本导出失败: ${e.message}")
        }
    }

    suspend fun exportToHtml(scheduleId: Long): ExportResult {
        return try {
            val schedule = scheduleRepository.getScheduleById(scheduleId)
                ?: return ExportResult(success = false, errorMessage = "未找到课表信息")
            val courseList = courseRepository.getAllCoursesBySchedule(scheduleId).first()
            val classTimes = classTimeRepository.getClassTimesByConfigSync()

            val html = exportFormatter.buildHtmlContent(courseList, schedule, classTimes)

            val fileName = "课程表_${exportFormatter.getCurrentDateString()}.html"
            val file = saveToFile(fileName, html, ExportFormat.HTML)

            ExportResult(
                success = true,
                filePath = file.absolutePath,
                fileUri = getFileUri(file)
            )
        } catch (e: Exception) {
            AppLogger.e("ExportService", "HTML导出失败", e)
            ExportResult(success = false, errorMessage = "HTML导出失败: ${e.message}")
        }
    }

    override fun shareFile(filePath: String, mimeType: String) {
        try {
            val file = File(filePath)
            if (!file.exists()) {
                Toast.makeText(context, "文件不存在", Toast.LENGTH_SHORT).show()
                return
            }
            val baseDir = exportDirectoryManager.getBaseDir()
            val fallbackDir = File(context.getExternalFilesDir(null), "exports")
            val canonicalPath = file.canonicalPath
            val isAllowed = canonicalPath.startsWith(baseDir.canonicalPath) ||
                    canonicalPath.startsWith(fallbackDir.canonicalPath)
            if (!isAllowed) {
                AppLogger.e("ExportService", "非法文件路径: $filePath")
                return
            }

            val uri = getFileUri(file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(Intent.createChooser(intent, "分享课程表").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(context, "分享失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveToFile(fileName: String, content: String, format: ExportFormat): File {
        val file = if (exportDirectoryManager.isExternalStorageWritable()) {
            try {
                val dir = exportDirectoryManager.getExportDir(format)
                if (dir.exists() || dir.mkdirs()) {
                    val f = File(dir, fileName)
                    f.writeText(content, Charsets.UTF_8)
                    AppLogger.d("ExportService", "文件已保存到公共目录: ${f.absolutePath}")
                    return f
                }
            } catch (e: Exception) {
                AppLogger.w("ExportService", "公共目录保存失败，使用备用目录: ${e.message}")
            }
            null
        } else null

        val fallbackDir = exportDirectoryManager.getFallbackDir(format)
        val fallbackFile = File(fallbackDir, fileName)
        fallbackFile.writeText(content, Charsets.UTF_8)
        AppLogger.d("ExportService", "文件已保存到备用目录: ${fallbackFile.absolutePath}")
        return fallbackFile
    }

    private fun getFileUri(file: File): android.net.Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }
}
