package com.wind.ggbond.classtime.service

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import com.wind.ggbond.classtime.data.local.entity.ClassTime
import com.wind.ggbond.classtime.data.local.entity.Course
import com.wind.ggbond.classtime.data.local.entity.Schedule
import com.wind.ggbond.classtime.data.repository.ClassTimeRepository
import com.wind.ggbond.classtime.data.repository.CourseRepository
import com.wind.ggbond.classtime.data.repository.ScheduleRepository
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 导出服务 - 增强版
 * 支持多种格式导出和分享功能
 */
@Singleton
class ExportService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val courseRepository: CourseRepository,
    private val scheduleRepository: ScheduleRepository,
    private val classTimeRepository: ClassTimeRepository
) {
    
    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .setDateFormat("yyyy-MM-dd HH:mm:ss")
        .create()
    
    /**
     * 导出格式枚举
     */
    enum class ExportFormat {
        JSON,    // JSON格式
        ICS,     // iCalendar格式
        CSV,     // CSV表格格式
        TXT,     // 纯文本格式
        HTML     // HTML网页格式
    }
    
    /**
     * 导出结果
     */
    data class ExportResult(
        val success: Boolean,
        val filePath: String? = null,
        val errorMessage: String? = null,
        val fileUri: android.net.Uri? = null
    )
    
    /**
     * 导出为指定格式
     */
    suspend fun export(scheduleId: Long, format: ExportFormat): ExportResult {
        return when (format) {
            ExportFormat.JSON -> exportToJson(scheduleId)
            ExportFormat.ICS -> exportToIcs(scheduleId)
            ExportFormat.CSV -> exportToCsv(scheduleId)
            ExportFormat.TXT -> exportToText(scheduleId)
            ExportFormat.HTML -> exportToHtml(scheduleId)
        }
    }
    
    /**
     * 导出为 JSON (增强版)
     */
    suspend fun exportToJson(scheduleId: Long): ExportResult {
        return try {
            val schedule = scheduleRepository.getScheduleById(scheduleId)
            val courseList = courseRepository.getAllCoursesBySchedule(scheduleId).first()
            
            val exportData = mapOf(
                "exportTime" to LocalDateTime.now().toString(),
                "version" to "1.0",
                "schedule" to schedule,
                "totalCourses" to courseList.size,
                "courses" to courseList
            )
            
            val json = gson.toJson(exportData)
            val fileName = "课程表_${getCurrentDateString()}.json"
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
    
    /**
     * 导出为 ICS (增强版 - 支持提醒)
     */
    suspend fun exportToIcs(scheduleId: Long): ExportResult {
        return try {
            val schedule = scheduleRepository.getScheduleById(scheduleId)
                ?: return ExportResult(success = false, errorMessage = "未找到课表信息")
            val classTimes = classTimeRepository.getClassTimesByConfigSync()
            val courseList = courseRepository.getAllCoursesBySchedule(scheduleId).first()
            
            val icsContent = buildIcsContent(courseList, schedule, classTimes)
            
            val fileName = "课程表_${getCurrentDateString()}.ics"
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
    
    /**
     * 导出为 CSV (新增)
     */
    suspend fun exportToCsv(scheduleId: Long): ExportResult {
        return try {
            val courseList = courseRepository.getAllCoursesBySchedule(scheduleId).first()
            
            val csv = buildString {
                // CSV 表头
                appendLine("课程名称,教师,教室,星期,节次,周次,学分,备注")
                
                // 课程数据
                courseList.forEach { course ->
                    val dayName = getDayName(course.dayOfWeek)
                    val sections = "${course.startSection}-${course.startSection + course.sectionCount - 1}节"
                    
                    appendLine(
                        listOf(
                            course.courseName,
                            course.teacher,
                            course.classroom,
                            dayName,
                            sections,
                            course.weekExpression,
                            course.credit.toString(),
                            course.note
                        ).joinToString(",") { "\"$it\"" }
                    )
                }
            }
            
            val fileName = "课程表_${getCurrentDateString()}.csv"
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
    
    /**
     * 导出为纯文本 (新增)
     */
    suspend fun exportToText(scheduleId: Long): ExportResult {
        return try {
            val schedule = scheduleRepository.getScheduleById(scheduleId)
            val courseList = courseRepository.getAllCoursesBySchedule(scheduleId).first()
            
            val text = buildString {
                appendLine("=".repeat(50))
                appendLine("课程表")
                appendLine("=".repeat(50))
                schedule?.let {
                    appendLine("课表: ${it.name}")
                    appendLine("起止: ${it.startDate} ~ ${it.endDate}")
                }
                appendLine("导出时间: ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}")
                appendLine("课程总数: ${courseList.size}")
                appendLine("=".repeat(50))
                appendLine()
                
                // 按星期分组
                courseList.groupBy { it.dayOfWeek }
                    .toSortedMap()
                    .forEach { (day, courses) ->
                        appendLine("【${getDayName(day)}】")
                        appendLine("-".repeat(50))
                        
                        courses.sortedBy { it.startSection }.forEach { course ->
                            appendLine("${course.courseName}")
                            appendLine("   教师: ${course.teacher}")
                            appendLine("   教室: ${course.classroom}")
                            appendLine("   节次: 第${course.startSection}-${course.startSection + course.sectionCount - 1}节")
                            appendLine("   周次: ${course.weekExpression}")
                            if (course.note.isNotEmpty()) {
                                appendLine("   备注: ${course.note}")
                            }
                            appendLine()
                        }
                    }
            }
            
            val fileName = "课程表_${getCurrentDateString()}.txt"
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
    
    /**
     * 导出为 HTML (新增)
     */
    suspend fun exportToHtml(scheduleId: Long): ExportResult {
        return try {
            val schedule = scheduleRepository.getScheduleById(scheduleId)
            val courseList = courseRepository.getAllCoursesBySchedule(scheduleId).first()
            val classTimes = classTimeRepository.getClassTimesByConfigSync()
            
            val html = buildHtmlContent(courseList, schedule, classTimes)
            
            val fileName = "课程表_${getCurrentDateString()}.html"
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
    
    /**
     * 构建 ICS 内容 (增强版 - 支持提醒和详细信息)
     */
    private fun buildIcsContent(
        courses: List<Course>,
        schedule: Schedule,
        classTimes: List<ClassTime>
    ): String {
        val sb = StringBuilder()
        
        // ICS 文件头
        sb.appendLine("BEGIN:VCALENDAR")
        sb.appendLine("VERSION:2.0")
        sb.appendLine("PRODID:-//Course Schedule App//CN")
        sb.appendLine("CALSCALE:GREGORIAN")
        sb.appendLine("METHOD:PUBLISH")
        sb.appendLine("X-WR-CALNAME:${schedule.name}")
        sb.appendLine("X-WR-TIMEZONE:Asia/Shanghai")
        
        // 为每门课程的每个上课周创建事件
        courses.forEach { course ->
            val classTime = classTimes.find { it.sectionNumber == course.startSection }
            if (classTime != null) {
                val endClassTime = classTimes.find { 
                    it.sectionNumber == course.startSection + course.sectionCount - 1 
                }
                
                course.weeks.forEach { weekNumber ->
                    val monday = schedule.startDate.plusWeeks((weekNumber - 1).toLong())
                    val courseDate = monday.plusDays((course.dayOfWeek - 1).toLong())
                    
                    val startDateTime = LocalDateTime.of(courseDate, classTime.startTime)
                    val endDateTime = LocalDateTime.of(
                        courseDate, 
                        endClassTime?.endTime ?: classTime.endTime
                    )
                    
                    sb.appendLine("BEGIN:VEVENT")
                    sb.appendLine("UID:${course.id}-$weekNumber@courseschedule.app")
                    sb.appendLine("DTSTAMP:${formatIcsDateTime(LocalDateTime.now())}")
                    sb.appendLine("DTSTART:${formatIcsDateTime(startDateTime)}")
                    sb.appendLine("DTEND:${formatIcsDateTime(endDateTime)}")
                    sb.appendLine("SUMMARY:${course.courseName}")
                    sb.appendLine("LOCATION:${course.classroom}")
                    
                    // 详细描述
                    val description = buildString {
                        append("教师：${course.teacher}\\n")
                        append("教室：${course.classroom}\\n")
                        append("节次：第${course.startSection}-${course.startSection + course.sectionCount - 1}节\\n")
                        append("学分：${course.credit}\\n")
                        if (course.note.isNotEmpty()) {
                            append("备注：${course.note}")
                        }
                    }
                    sb.appendLine("DESCRIPTION:$description")
                    
                    // 添加提醒
                    if (course.reminderEnabled) {
                        sb.appendLine("BEGIN:VALARM")
                        sb.appendLine("ACTION:DISPLAY")
                        sb.appendLine("DESCRIPTION:${course.courseName} - ${course.classroom}")
                        sb.appendLine("TRIGGER:-PT${course.reminderMinutes}M")
                        sb.appendLine("END:VALARM")
                    }
                    
                    sb.appendLine("STATUS:CONFIRMED")
                    sb.appendLine("TRANSP:OPAQUE")
                    sb.appendLine("END:VEVENT")
                }
            }
        }
        
        sb.appendLine("END:VCALENDAR")
        
        return sb.toString()
    }
    
    /**
     * 构建 HTML 内容
     */
    private fun buildHtmlContent(
        courses: List<Course>,
        schedule: Schedule?,
        classTimes: List<ClassTime>
    ): String {
        return buildString {
            appendLine("<!DOCTYPE html>")
            appendLine("<html lang='zh-CN'>")
            appendLine("<head>")
            appendLine("    <meta charset='UTF-8'>")
            appendLine("    <meta name='viewport' content='width=device-width, initial-scale=1.0'>")
            appendLine("    <title>我的课程表</title>")
            appendLine("    <style>")
            appendLine("""
                body { 
                    font-family: 'Microsoft YaHei', Arial, sans-serif; 
                    margin: 20px; 
                    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                    min-height: 100vh;
                }
                .container {
                    max-width: 1200px;
                    margin: 0 auto;
                    background: white;
                    padding: 30px;
                    border-radius: 15px;
                    box-shadow: 0 10px 30px rgba(0,0,0,0.2);
                }
                h1 { 
                    color: #667eea; 
                    text-align: center; 
                    border-bottom: 3px solid #667eea;
                    padding-bottom: 15px;
                }
                .info {
                    background: #f8f9fa;
                    padding: 15px;
                    border-radius: 8px;
                    margin: 20px 0;
                }
                table { 
                    width: 100%; 
                    border-collapse: collapse; 
                    margin-top: 20px;
                    box-shadow: 0 2px 10px rgba(0,0,0,0.1);
                }
                th, td { 
                    border: 1px solid #ddd; 
                    padding: 12px; 
                    text-align: left; 
                }
                th { 
                    background: linear-gradient(135deg, #667eea, #764ba2); 
                    color: white;
                    font-weight: bold;
                }
                tr:nth-child(even) { background-color: #f8f9fa; }
                tr:hover { background-color: #e9ecef; }
                .course-name { font-weight: bold; color: #667eea; }
                .time-info { color: #6c757d; font-size: 0.9em; }
                @media print {
                    body { background: white; }
                    .container { box-shadow: none; }
                }
            """.trimIndent())
            appendLine("    </style>")
            appendLine("</head>")
            appendLine("<body>")
            appendLine("    <div class='container'>")
            appendLine("        <h1>📚 我的课程表</h1>")
            
            // 课表信息
            schedule?.let {
                appendLine("        <div class='info'>")
                appendLine("            <p><strong>课表：</strong>${it.name}</p>")
                appendLine("            <p><strong>时间：</strong>${it.startDate} ~ ${it.endDate}</p>")
                appendLine("            <p><strong>总周数：</strong>${it.totalWeeks}周</p>")
                appendLine("            <p><strong>课程数：</strong>${courses.size}门</p>")
                appendLine("        </div>")
            }
            
            appendLine("        <table>")
            appendLine("            <thead>")
            appendLine("                <tr>")
            appendLine("                    <th>星期</th>")
            appendLine("                    <th>课程名称</th>")
            appendLine("                    <th>教师</th>")
            appendLine("                    <th>教室</th>")
            appendLine("                    <th>时间</th>")
            appendLine("                    <th>周次</th>")
            appendLine("                </tr>")
            appendLine("            </thead>")
            appendLine("            <tbody>")
            
            // 课程数据
            courses.sortedWith(compareBy({ it.dayOfWeek }, { it.startSection }))
                .forEach { course ->
                    val classTime = classTimes.find { it.sectionNumber == course.startSection }
                    val timeInfo = classTime?.let { 
                        "${it.startTime} - ${it.endTime}" 
                    } ?: "第${course.startSection}节"
                    
                    appendLine("                <tr>")
                    appendLine("                    <td>${getDayName(course.dayOfWeek)}</td>")
                    appendLine("                    <td class='course-name'>${course.courseName}</td>")
                    appendLine("                    <td>${course.teacher}</td>")
                    appendLine("                    <td>${course.classroom}</td>")
                    appendLine("                    <td class='time-info'>$timeInfo</td>")
                    appendLine("                    <td>${course.weekExpression}</td>")
                    appendLine("                </tr>")
                }
            
            appendLine("            </tbody>")
            appendLine("        </table>")
            appendLine("        <div class='info' style='margin-top: 20px; text-align: center;'>")
            appendLine("            <p>导出时间：${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}</p>")
            appendLine("        </div>")
            appendLine("    </div>")
            appendLine("</body>")
            appendLine("</html>")
        }
    }
    
    /**
     * 分享文件
     */
    fun shareFile(filePath: String, mimeType: String = "*/*") {
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
    
    /**
     * 保存文件到应用目录
     */
    private fun saveToFile(fileName: String, content: String): File {
        val exportDir = File(context.getExternalFilesDir(null), "exports").apply {
            if (!exists()) mkdirs()
        }
        val file = File(exportDir, fileName)
        file.writeText(content, Charsets.UTF_8)
        return file
    }
    
    /**
     * 获取文件 URI (用于分享)
     */
    private fun getFileUri(file: File): android.net.Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }
    
    /**
     * 格式化 ICS 日期时间
     */
    private fun formatIcsDateTime(dateTime: LocalDateTime): String {
        val instant = dateTime.atZone(ZoneId.systemDefault()).toInstant()
        return DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .withZone(ZoneId.of("UTC"))
            .format(instant)
    }
    
    /**
     * 获取当前日期字符串
     */
    private fun getCurrentDateString(): String {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
    }
    
    /**
     * 获取星期名称
     */
    private fun getDayName(dayOfWeek: Int): String {
        return when (dayOfWeek) {
            1 -> "周一"
            2 -> "周二"
            3 -> "周三"
            4 -> "周四"
            5 -> "周五"
            6 -> "周六"
            7 -> "周日"
            else -> "未知"
        }
    }
}



