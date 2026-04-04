package com.wind.ggbond.classtime.service

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import com.wind.ggbond.classtime.data.local.entity.Course
import com.wind.ggbond.classtime.data.local.entity.Schedule
import com.wind.ggbond.classtime.data.repository.ClassTimeRepository
import com.wind.ggbond.classtime.data.repository.CourseRepository
import com.wind.ggbond.classtime.data.repository.ScheduleRepository
import com.wind.ggbond.classtime.service.contract.IDataExporter
import com.wind.ggbond.classtime.service.helper.ExportFormatter
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private typealias ExportFormat = IDataExporter.ExportFormat
private typealias ExportResult = IDataExporter.ExportResult

@Singleton
class ExportService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val courseRepository: CourseRepository,
    private val scheduleRepository: ScheduleRepository,
    private val classTimeRepository: ClassTimeRepository,
    private val exportFormatter: ExportFormatter
) : IDataExporter {

    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .setDateFormat("yyyy-MM-dd HH:mm:ss")
        .create()

    override suspend fun export(scheduleId: Long, format: IDataExporter.ExportFormat): IDataExporter.ExportResult {
        return when (format) {
            ExportFormat.JSON -> exportToJson(scheduleId)
            ExportFormat.ICS -> exportToIcs(scheduleId)
            ExportFormat.CSV -> exportToCsv(scheduleId)
            ExportFormat.TXT -> exportToText(scheduleId)
            ExportFormat.HTML -> exportToHtml(scheduleId)
        }
    }
    
    suspend fun exportToJson(scheduleId: Long): ExportResult {
        return try {
            val schedule = scheduleRepository.getScheduleById(scheduleId)
            val courseList = courseRepository.getAllCoursesBySchedule(scheduleId).first()
            val classTimes = classTimeRepository.getClassTimesByConfigSync()
            
            val exportData = mapOf(
                "meta" to mapOf(
                    "exportTime" to java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                    "appName" to "课程表",
                    "version" to "2.0",
                    "format" to "CourseScheduleExport"
                ),
                "schedule" to schedule?.let {
                    mapOf(
                        "name" to it.name,
                        "schoolName" to it.schoolName,
                        "startDate" to it.startDate.toString(),
                        "endDate" to it.endDate.toString(),
                        "totalWeeks" to it.totalWeeks
                    )
                },
                "classTimes" to classTimes.map {
                    mapOf(
                        "sectionNumber" to it.sectionNumber,
                        "startTime" to it.startTime.toString(),
                        "endTime" to it.endTime.toString()
                    )
                },
                "statistics" to mapOf(
                    "totalCourses" to courseList.size,
                    "totalCredits" to courseList.sumOf { it.credit.toDouble() },
                    "coursesByDay" to (1..7).associate { day ->
                        exportFormatter.getDayName(day) to courseList.count { it.dayOfWeek == day }
                    }
                ),
                "courses" to courseList.map { course ->
                    mapOf(
                        "courseName" to course.courseName,
                        "teacher" to course.teacher,
                        "classroom" to course.classroom,
                        "dayOfWeek" to course.dayOfWeek,
                        "dayName" to exportFormatter.getDayName(course.dayOfWeek),
                        "startSection" to course.startSection,
                        "sectionCount" to course.sectionCount,
                        "weeks" to course.weeks,
                        "weekExpression" to course.weekExpression,
                        "credit" to course.credit,
                        "color" to course.color,
                        "note" to course.note,
                        "reminderEnabled" to course.reminderEnabled,
                        "reminderMinutes" to course.reminderMinutes
                    )
                }
            )
            
            val prettyGson = com.google.gson.GsonBuilder()
                .setPrettyPrinting()
                .create()
            val json = prettyGson.toJson(exportData)
            val fileName = "课程表_${exportFormatter.getCurrentDateString()}.json"
            val file = saveToFile(fileName, json)
            
            ExportResult(
                success = true,
                filePath = file.absolutePath,
                fileUri = getFileUri(file)
            )
        } catch (e: Exception) {
            ExportResult(
                success = false,
                errorMessage = "JSON导出失败: ${e.message}"
            )
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
            val file = saveToFile(fileName, icsContent)
            
            ExportResult(
                success = true,
                filePath = file.absolutePath,
                fileUri = getFileUri(file)
            )
        } catch (e: Exception) {
            ExportResult(
                success = false,
                errorMessage = "ICS导出失败: ${e.message}"
            )
        }
    }
    
    suspend fun exportToCsv(scheduleId: Long): ExportResult {
        return try {
            val schedule = scheduleRepository.getScheduleById(scheduleId)
            val courseList = courseRepository.getAllCoursesBySchedule(scheduleId).first()
            val classTimes = classTimeRepository.getClassTimesByConfigSync()
            
            val csv = exportFormatter.buildCsvContent(courseList, schedule, classTimes)
            
            val fileName = "课程表_${exportFormatter.getCurrentDateString()}.csv"
            val file = saveToFile(fileName, csv)
            
            ExportResult(
                success = true,
                filePath = file.absolutePath,
                fileUri = getFileUri(file)
            )
        } catch (e: Exception) {
            ExportResult(
                success = false,
                errorMessage = "CSV导出失败: ${e.message}"
            )
        }
    }
    
    suspend fun exportToText(scheduleId: Long): ExportResult {
        return try {
            val schedule = scheduleRepository.getScheduleById(scheduleId)
            val courseList = courseRepository.getAllCoursesBySchedule(scheduleId).first()
            val classTimes = classTimeRepository.getClassTimesByConfigSync()
            
            val text = exportFormatter.buildTextContent(courseList, schedule, classTimes)
            
            val fileName = "课程表_${exportFormatter.getCurrentDateString()}.txt"
            val file = saveToFile(fileName, text)
            
            ExportResult(
                success = true,
                filePath = file.absolutePath,
                fileUri = getFileUri(file)
            )
        } catch (e: Exception) {
            ExportResult(
                success = false,
                errorMessage = "文本导出失败: ${e.message}"
            )
        }
    }
    
    suspend fun exportToHtml(scheduleId: Long): ExportResult {
        return try {
            val schedule = scheduleRepository.getScheduleById(scheduleId)
            val courseList = courseRepository.getAllCoursesBySchedule(scheduleId).first()
            val classTimes = classTimeRepository.getClassTimesByConfigSync()
            
            val html = exportFormatter.buildHtmlContent(courseList, schedule, classTimes)
            
            val fileName = "课程表_${exportFormatter.getCurrentDateString()}.html"
            val file = saveToFile(fileName, html)
            
            ExportResult(
                success = true,
                filePath = file.absolutePath,
                fileUri = getFileUri(file)
            )
        } catch (e: Exception) {
            ExportResult(
                success = false,
                errorMessage = "HTML导出失败: ${e.message}"
            )
        }
    }
    
    override fun shareFile(filePath: String, mimeType: String) {
        try {
            val file = File(filePath)
            if (!file.exists()) {
                Toast.makeText(context, "文件不存在", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(context, "分享失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun saveToFile(fileName: String, content: String): File {
        val exportDir = File(context.getExternalFilesDir(null), "exports").apply {
            if (!exists()) mkdirs()
        }
        val file = File(exportDir, fileName)
        file.writeText(content, Charsets.UTF_8)
        return file
    }
    
    private fun getFileUri(file: File): android.net.Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }
}
