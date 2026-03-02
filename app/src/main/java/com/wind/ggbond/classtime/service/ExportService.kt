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
     * 导出为 JSON
     * 采用结构化格式，包含完整的课表元数据和课程信息
     * 支持导入时完整还原课表数据
     */
    suspend fun exportToJson(scheduleId: Long): ExportResult {
        return try {
            val schedule = scheduleRepository.getScheduleById(scheduleId)
            val courseList = courseRepository.getAllCoursesBySchedule(scheduleId).first()
            val classTimes = classTimeRepository.getClassTimesByConfigSync()
            
            // 构建结构化的导出数据
            val exportData = mapOf(
                // 元数据
                "meta" to mapOf(
                    "exportTime" to LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                    "appName" to "课程表",
                    "version" to "2.0",
                    "format" to "CourseScheduleExport"
                ),
                // 课表信息
                "schedule" to schedule?.let {
                    mapOf(
                        "name" to it.name,
                        "schoolName" to it.schoolName,
                        "startDate" to it.startDate.toString(),
                        "endDate" to it.endDate.toString(),
                        "totalWeeks" to it.totalWeeks
                    )
                },
                // 时间配置
                "classTimes" to classTimes.map {
                    mapOf(
                        "sectionNumber" to it.sectionNumber,
                        "startTime" to it.startTime.toString(),
                        "endTime" to it.endTime.toString()
                    )
                },
                // 统计信息
                "statistics" to mapOf(
                    "totalCourses" to courseList.size,
                    "totalCredits" to courseList.sumOf { it.credit.toDouble() },
                    "coursesByDay" to (1..7).associate { day ->
                        getDayName(day) to courseList.count { it.dayOfWeek == day }
                    }
                ),
                // 课程列表
                "courses" to courseList.map { course ->
                    mapOf(
                        "courseName" to course.courseName,
                        "teacher" to course.teacher,
                        "classroom" to course.classroom,
                        "dayOfWeek" to course.dayOfWeek,
                        "dayName" to getDayName(course.dayOfWeek),
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
            
            // 使用格式化的JSON输出（便于阅读）
            val prettyGson = com.google.gson.GsonBuilder()
                .setPrettyPrinting()
                .create()
            val json = prettyGson.toJson(exportData)
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
     * 导出为 CSV
     * 采用标准CSV格式，包含完整的课程信息
     * 支持Excel、WPS等表格软件打开
     */
    suspend fun exportToCsv(scheduleId: Long): ExportResult {
        return try {
            val schedule = scheduleRepository.getScheduleById(scheduleId)
            val courseList = courseRepository.getAllCoursesBySchedule(scheduleId).first()
            val classTimes = classTimeRepository.getClassTimesByConfigSync()
            
            val csv = buildString {
                // 添加BOM头，确保Excel正确识别UTF-8编码
                append("\uFEFF")
                
                // CSV 表头（包含更多字段）
                appendLine("序号,课程名称,教师,教室,星期,节次,上课时间,周次,学分,颜色,提醒,备注")
                
                // 课程数据（按星期和节次排序）
                courseList.sortedWith(compareBy({ it.dayOfWeek }, { it.startSection }))
                    .forEachIndexed { index, course ->
                        val dayName = getDayName(course.dayOfWeek)
                        val sections = "第${course.startSection}-${course.startSection + course.sectionCount - 1}节"
                        
                        // 获取具体上课时间
                        val classTime = classTimes.find { it.sectionNumber == course.startSection }
                        val endClassTime = classTimes.find { it.sectionNumber == course.startSection + course.sectionCount - 1 }
                        val timeStr = if (classTime != null && endClassTime != null) {
                            "${classTime.startTime.toString().substring(0, 5)}-${endClassTime.endTime.toString().substring(0, 5)}"
                        } else {
                            ""
                        }
                        
                        // 提醒状态
                        val reminderStr = if (course.reminderEnabled) "提前${course.reminderMinutes}分钟" else "关闭"
                        
                        appendLine(
                            listOf(
                                (index + 1).toString(),
                                course.courseName,
                                course.teacher,
                                course.classroom,
                                dayName,
                                sections,
                                timeStr,
                                course.weekExpression,
                                course.credit.toString(),
                                course.color,
                                reminderStr,
                                course.note.replace("\n", " ") // 移除换行符
                            ).joinToString(",") { "\"${it.replace("\"", "\"\"")}\"" } // 转义双引号
                        )
                    }
                
                // 添加统计信息行
                appendLine()
                appendLine("\"# 统计信息\"")
                appendLine("\"课表名称\",\"${schedule?.name ?: ""}\"")
                appendLine("\"学期时间\",\"${schedule?.startDate ?: ""} ~ ${schedule?.endDate ?: ""}\"")
                appendLine("\"总周数\",\"${schedule?.totalWeeks ?: 0}周\"")
                appendLine("\"课程总数\",\"${courseList.size}门\"")
                appendLine("\"总学分\",\"${courseList.sumOf { it.credit.toDouble() }}\"")
                appendLine("\"导出时间\",\"${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}\"")
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
     * 导出为纯文本
     * 采用清晰的排版格式，便于阅读和打印
     */
    suspend fun exportToText(scheduleId: Long): ExportResult {
        return try {
            val schedule = scheduleRepository.getScheduleById(scheduleId)
            val courseList = courseRepository.getAllCoursesBySchedule(scheduleId).first()
            val classTimes = classTimeRepository.getClassTimesByConfigSync()
            
            val text = buildString {
                // 标题区域
                appendLine("╔══════════════════════════════════════════════════════════════╗")
                appendLine("║                                                              ║")
                appendLine("║                        我 的 课 程 表                        ║")
                appendLine("║                                                              ║")
                appendLine("╚══════════════════════════════════════════════════════════════╝")
                appendLine()
                
                // 课表信息
                appendLine("┌──────────────────────────────────────────────────────────────┐")
                appendLine("│  基本信息                                                    │")
                appendLine("├──────────────────────────────────────────────────────────────┤")
                schedule?.let {
                    appendLine("│  课表名称：${it.name.padEnd(48)}│")
                    appendLine("│  学期时间：${it.startDate} ~ ${it.endDate}".padEnd(63) + "│")
                    appendLine("│  总  周  数：${it.totalWeeks}周".padEnd(61) + "│")
                }
                appendLine("│  课程数量：${courseList.size}门".padEnd(61) + "│")
                appendLine("│  导出时间：${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))}".padEnd(61) + "│")
                appendLine("└──────────────────────────────────────────────────────────────┘")
                appendLine()
                
                // 按星期分组显示课程
                for (day in 1..7) {
                    val dayCourses = courseList.filter { it.dayOfWeek == day }
                        .sortedBy { it.startSection }
                    
                    if (dayCourses.isNotEmpty()) {
                        // 星期标题
                        appendLine("┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓")
                        appendLine("┃  ${getDayName(day)}                                                        ┃")
                        appendLine("┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛")
                        appendLine()
                        
                        dayCourses.forEach { course ->
                            // 获取时间信息
                            val classTime = classTimes.find { it.sectionNumber == course.startSection }
                            val endClassTime = classTimes.find { it.sectionNumber == course.startSection + course.sectionCount - 1 }
                            val timeStr = if (classTime != null && endClassTime != null) {
                                "${classTime.startTime.toString().substring(0, 5)} - ${endClassTime.endTime.toString().substring(0, 5)}"
                            } else {
                                "第${course.startSection}-${course.startSection + course.sectionCount - 1}节"
                            }
                            
                            appendLine("    ┌────────────────────────────────────────────────────────┐")
                            appendLine("    │  ${course.courseName.take(50).padEnd(54)}│")
                            appendLine("    ├────────────────────────────────────────────────────────┤")
                            if (course.teacher.isNotEmpty()) {
                                appendLine("    │    教师：${course.teacher.take(44).padEnd(46)}│")
                            }
                            if (course.classroom.isNotEmpty()) {
                                appendLine("    │    教室：${course.classroom.take(44).padEnd(46)}│")
                            }
                            appendLine("    │    时间：${timeStr.padEnd(46)}│")
                            if (course.weekExpression.isNotEmpty()) {
                                appendLine("    │    周次：${course.weekExpression.take(44).padEnd(46)}│")
                            }
                            if (course.credit > 0) {
                                appendLine("    │    学分：${course.credit}".padEnd(59) + "│")
                            }
                            if (course.note.isNotEmpty()) {
                                appendLine("    │    备注：${course.note.take(44).padEnd(46)}│")
                            }
                            appendLine("    └────────────────────────────────────────────────────────┘")
                            appendLine()
                        }
                    }
                }
                
                // 页脚
                appendLine()
                appendLine("════════════════════════════════════════════════════════════════")
                appendLine("                      由 课程表 App 导出")
                appendLine("════════════════════════════════════════════════════════════════")
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
     * 构建HTML内容
     * 采用温暖米白色系设计风格，与应用UI保持一致
     * 支持课表视图和列表视图两种展示方式
     */
    private fun buildHtmlContent(
        courses: List<Course>,
        schedule: Schedule?,
        classTimes: List<ClassTime>
    ): String {
        // 课程颜色映射（与应用内CourseColors保持一致）
        val courseColorList = listOf(
            "#FFE0B2", "#BBDEFB", "#C8E6C9", "#E1BEE7", "#FFCDD2",
            "#FFF9C4", "#B2EBF2", "#D7CCC8", "#F8BBD0", "#B3E5FC"
        )
        
        // 为每门课程分配颜色
        val courseNames = courses.map { it.courseName }.distinct()
        val courseColorMap = courseNames.mapIndexed { index, name -> 
            name to courseColorList[index % courseColorList.size] 
        }.toMap()
        
        // 计算最大节次
        val maxSection = courses.maxOfOrNull { it.startSection + it.sectionCount - 1 } ?: 12
        
        return buildString {
            appendLine("<!DOCTYPE html>")
            appendLine("<html lang='zh-CN'>")
            appendLine("<head>")
            appendLine("    <meta charset='UTF-8'>")
            appendLine("    <meta name='viewport' content='width=device-width, initial-scale=1.0'>")
            appendLine("    <title>${schedule?.name ?: "我的课程表"}</title>")
            appendLine("    <style>")
            appendLine(buildHtmlStyles())
            appendLine("    </style>")
            appendLine("</head>")
            appendLine("<body>")
            appendLine("    <div class='container'>")
            
            // 头部信息卡片
            appendLine("        <header class='header-card'>")
            appendLine("            <h1 class='title'>${schedule?.name ?: "我的课程表"}</h1>")
            schedule?.let {
                appendLine("            <div class='meta-info'>")
                appendLine("                <span class='meta-item'>")
                appendLine("                    <svg class='icon' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2'>")
                appendLine("                        <rect x='3' y='4' width='18' height='18' rx='2' ry='2'></rect>")
                appendLine("                        <line x1='16' y1='2' x2='16' y2='6'></line>")
                appendLine("                        <line x1='8' y1='2' x2='8' y2='6'></line>")
                appendLine("                        <line x1='3' y1='10' x2='21' y2='10'></line>")
                appendLine("                    </svg>")
                appendLine("                    ${it.startDate} ~ ${it.endDate}")
                appendLine("                </span>")
                appendLine("                <span class='meta-item'>")
                appendLine("                    <svg class='icon' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2'>")
                appendLine("                        <circle cx='12' cy='12' r='10'></circle>")
                appendLine("                        <polyline points='12 6 12 12 16 14'></polyline>")
                appendLine("                    </svg>")
                appendLine("                    共${it.totalWeeks}周")
                appendLine("                </span>")
                appendLine("                <span class='meta-item'>")
                appendLine("                    <svg class='icon' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2'>")
                appendLine("                        <path d='M4 19.5A2.5 2.5 0 0 1 6.5 17H20'></path>")
                appendLine("                        <path d='M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z'></path>")
                appendLine("                    </svg>")
                appendLine("                    ${courses.size}门课程")
                appendLine("                </span>")
                appendLine("            </div>")
            }
            appendLine("        </header>")
            
            // 课表网格视图
            appendLine("        <section class='schedule-section'>")
            appendLine("            <h2 class='section-title'>课表视图</h2>")
            appendLine("            <div class='schedule-grid'>")
            
            // 表头 - 星期
            appendLine("                <div class='grid-header corner'></div>")
            for (day in 1..7) {
                appendLine("                <div class='grid-header'>${getDayName(day)}</div>")
            }
            
            // 课程网格
            for (section in 1..maxSection) {
                // 节次列
                val classTime = classTimes.find { it.sectionNumber == section }
                val timeStr = classTime?.let { "${it.startTime.toString().substring(0, 5)}" } ?: ""
                appendLine("                <div class='grid-section'>")
                appendLine("                    <span class='section-num'>$section</span>")
                if (timeStr.isNotEmpty()) {
                    appendLine("                    <span class='section-time'>$timeStr</span>")
                }
                appendLine("                </div>")
                
                // 每天的课程
                for (day in 1..7) {
                    // 筛选在当前节次开始的课程（支持多课程）
                    val coursesStartingAtSlot = courses.filter { 
                        it.dayOfWeek == day && 
                        section == it.startSection 
                    }
                    
                    // 检查是否被上方课程占用（用于判断是否需要渲染空单元格）
                    val isOccupiedByPrevious = courses.any { 
                        it.dayOfWeek == day && 
                        section > it.startSection && 
                        section < it.startSection + it.sectionCount 
                    }
                    
                    if (coursesStartingAtSlot.isNotEmpty()) {
                        // 当前节次有课程开始，渲染所有课程
                        if (coursesStartingAtSlot.size == 1) {
                            // 单个课程：使用grid-row span
                            val course = coursesStartingAtSlot.first()
                            val bgColor = course.color.ifEmpty { courseColorMap[course.courseName] ?: "#FFE0B2" }
                            appendLine("                <div class='grid-cell course-cell' style='background-color: $bgColor; grid-row: span ${course.sectionCount};'>")
                            appendLine("                    <div class='course-name'>${course.courseName}</div>")
                            if (course.classroom.isNotEmpty()) {
                                appendLine("                    <div class='course-room'>${course.classroom}</div>")
                            }
                            appendLine("                </div>")
                        } else {
                            // 多个课程：在同一格子内显示所有课程
                            // 计算最大跨行数
                            val maxSpan = coursesStartingAtSlot.maxOf { it.sectionCount }
                            appendLine("                <div class='grid-cell multi-course-cell' style='grid-row: span $maxSpan;'>")
                            coursesStartingAtSlot.forEach { course ->
                                val bgColor = course.color.ifEmpty { courseColorMap[course.courseName] ?: "#FFE0B2" }
                                appendLine("                    <div class='course-item' style='background-color: $bgColor;'>")
                                appendLine("                        <div class='course-name'>${course.courseName}</div>")
                                if (course.classroom.isNotEmpty()) {
                                    appendLine("                        <div class='course-room'>${course.classroom}</div>")
                                }
                                if (course.weekExpression.isNotEmpty()) {
                                    appendLine("                        <div class='course-weeks-mini'>${course.weekExpression}</div>")
                                }
                                appendLine("                    </div>")
                            }
                            appendLine("                </div>")
                        }
                    } else if (!isOccupiedByPrevious) {
                        // 当前格子为空且未被上方课程占用
                        appendLine("                <div class='grid-cell empty-cell'></div>")
                    }
                }
            }
            
            appendLine("            </div>")
            appendLine("        </section>")
            
            // 课程详情列表
            appendLine("        <section class='list-section'>")
            appendLine("            <h2 class='section-title'>课程详情</h2>")
            appendLine("            <div class='course-list'>")
            
            // 按星期分组显示
            for (day in 1..7) {
                val dayCourses = courses.filter { it.dayOfWeek == day }
                    .sortedBy { it.startSection }
                
                if (dayCourses.isNotEmpty()) {
                    appendLine("                <div class='day-group'>")
                    appendLine("                    <div class='day-header'>${getDayName(day)}</div>")
                    appendLine("                    <div class='day-courses'>")
                    
                    dayCourses.forEach { course ->
                        val bgColor = course.color.ifEmpty { courseColorMap[course.courseName] ?: "#FFE0B2" }
                        val classTime = classTimes.find { it.sectionNumber == course.startSection }
                        val endClassTime = classTimes.find { it.sectionNumber == course.startSection + course.sectionCount - 1 }
                        val timeStr = if (classTime != null && endClassTime != null) {
                            "${classTime.startTime.toString().substring(0, 5)} - ${endClassTime.endTime.toString().substring(0, 5)}"
                        } else {
                            "第${course.startSection}-${course.startSection + course.sectionCount - 1}节"
                        }
                        
                        appendLine("                        <div class='course-card'>")
                        appendLine("                            <div class='course-color-bar' style='background-color: $bgColor;'></div>")
                        appendLine("                            <div class='course-content'>")
                        appendLine("                                <div class='course-title'>${course.courseName}</div>")
                        appendLine("                                <div class='course-details'>")
                        if (course.teacher.isNotEmpty()) {
                            appendLine("                                    <span class='detail-item'>")
                            appendLine("                                        <svg class='detail-icon' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2'>")
                            appendLine("                                            <path d='M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2'></path>")
                            appendLine("                                            <circle cx='12' cy='7' r='4'></circle>")
                            appendLine("                                        </svg>")
                            appendLine("                                        ${course.teacher}")
                            appendLine("                                    </span>")
                        }
                        if (course.classroom.isNotEmpty()) {
                            appendLine("                                    <span class='detail-item'>")
                            appendLine("                                        <svg class='detail-icon' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2'>")
                            appendLine("                                            <path d='M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z'></path>")
                            appendLine("                                            <circle cx='12' cy='10' r='3'></circle>")
                            appendLine("                                        </svg>")
                            appendLine("                                        ${course.classroom}")
                            appendLine("                                    </span>")
                        }
                        appendLine("                                    <span class='detail-item'>")
                        appendLine("                                        <svg class='detail-icon' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2'>")
                        appendLine("                                            <circle cx='12' cy='12' r='10'></circle>")
                        appendLine("                                            <polyline points='12 6 12 12 16 14'></polyline>")
                        appendLine("                                        </svg>")
                        appendLine("                                        $timeStr")
                        appendLine("                                    </span>")
                        appendLine("                                </div>")
                        if (course.weekExpression.isNotEmpty()) {
                            appendLine("                                <div class='course-weeks'>${course.weekExpression}</div>")
                        }
                        appendLine("                            </div>")
                        appendLine("                        </div>")
                    }
                    
                    appendLine("                    </div>")
                    appendLine("                </div>")
                }
            }
            
            appendLine("            </div>")
            appendLine("        </section>")
            
            // 页脚
            appendLine("        <footer class='footer'>")
            appendLine("            <p>导出时间：${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm"))}</p>")
            appendLine("            <p class='app-name'>由 课程表 App 导出</p>")
            appendLine("        </footer>")
            
            appendLine("    </div>")
            appendLine("</body>")
            appendLine("</html>")
        }
    }
    
    /**
     * 构建HTML样式
     * 采用温暖米白色系，与应用UI风格一致
     */
    private fun buildHtmlStyles(): String {
        return """
            :root {
                /* 温暖米白色系 - 与应用主题一致 */
                --primary: #D4A574;
                --primary-variant: #B8956A;
                --secondary: #E8D5C4;
                --background: #FFFBF5;
                --surface: #FFF9F0;
                --surface-variant: #FFF3E6;
                --text-primary: #3E2723;
                --text-secondary: #5D4037;
                --text-tertiary: #8D6E63;
                --outline: #D7C3B0;
                --outline-variant: #E8DDD0;
                --shadow: rgba(62, 39, 35, 0.08);
                --shadow-strong: rgba(62, 39, 35, 0.15);
            }
            
            * {
                margin: 0;
                padding: 0;
                box-sizing: border-box;
            }
            
            body {
                font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', sans-serif;
                background: var(--background);
                color: var(--text-primary);
                line-height: 1.6;
                min-height: 100vh;
                padding: 24px;
            }
            
            .container {
                max-width: 1000px;
                margin: 0 auto;
            }
            
            /* 头部卡片 */
            .header-card {
                background: var(--surface);
                border-radius: 16px;
                padding: 24px 32px;
                margin-bottom: 24px;
                box-shadow: 0 2px 12px var(--shadow);
                border: 1px solid var(--outline-variant);
            }
            
            .title {
                font-size: 28px;
                font-weight: 600;
                color: var(--text-primary);
                margin-bottom: 16px;
            }
            
            .meta-info {
                display: flex;
                flex-wrap: wrap;
                gap: 24px;
            }
            
            .meta-item {
                display: flex;
                align-items: center;
                gap: 8px;
                color: var(--text-secondary);
                font-size: 14px;
            }
            
            .icon {
                width: 18px;
                height: 18px;
                color: var(--primary);
            }
            
            /* 区块标题 */
            .section-title {
                font-size: 18px;
                font-weight: 600;
                color: var(--text-primary);
                margin-bottom: 16px;
                padding-left: 12px;
                border-left: 3px solid var(--primary);
            }
            
            /* 课表网格 */
            .schedule-section {
                background: var(--surface);
                border-radius: 16px;
                padding: 24px;
                margin-bottom: 24px;
                box-shadow: 0 2px 12px var(--shadow);
                border: 1px solid var(--outline-variant);
                overflow-x: auto;
            }
            
            .schedule-grid {
                display: grid;
                grid-template-columns: 60px repeat(7, 1fr);
                gap: 2px;
                background: var(--outline-variant);
                border-radius: 12px;
                overflow: hidden;
                min-width: 700px;
            }
            
            .grid-header {
                background: var(--primary);
                color: white;
                padding: 12px 8px;
                text-align: center;
                font-weight: 500;
                font-size: 14px;
            }
            
            .grid-header.corner {
                background: var(--primary-variant);
            }
            
            .grid-section {
                background: var(--surface-variant);
                padding: 8px 4px;
                text-align: center;
                display: flex;
                flex-direction: column;
                justify-content: center;
                align-items: center;
                min-height: 60px;
            }
            
            .section-num {
                font-weight: 600;
                color: var(--text-primary);
                font-size: 14px;
            }
            
            .section-time {
                font-size: 10px;
                color: var(--text-tertiary);
                margin-top: 2px;
            }
            
            .grid-cell {
                background: var(--surface);
                min-height: 60px;
            }
            
            .empty-cell {
                background: var(--surface);
            }
            
            .course-cell {
                padding: 8px;
                display: flex;
                flex-direction: column;
                justify-content: center;
                align-items: center;
                text-align: center;
                border-radius: 8px;
                margin: 2px;
            }
            
            .course-cell .course-name {
                font-size: 12px;
                font-weight: 600;
                color: var(--text-primary);
                line-height: 1.3;
                word-break: break-all;
            }
            
            .course-cell .course-room {
                font-size: 10px;
                color: var(--text-secondary);
                margin-top: 4px;
            }
            
            /* 多课程单元格样式 */
            .multi-course-cell {
                padding: 4px;
                display: flex;
                flex-direction: column;
                gap: 4px;
                background: var(--surface);
            }
            
            .multi-course-cell .course-item {
                padding: 6px;
                border-radius: 6px;
                text-align: center;
                flex: 1;
                display: flex;
                flex-direction: column;
                justify-content: center;
                min-height: 0;
            }
            
            .multi-course-cell .course-name {
                font-size: 11px;
                font-weight: 600;
                color: var(--text-primary);
                line-height: 1.2;
                word-break: break-all;
            }
            
            .multi-course-cell .course-room {
                font-size: 9px;
                color: var(--text-secondary);
                margin-top: 2px;
            }
            
            .multi-course-cell .course-weeks-mini {
                font-size: 8px;
                color: var(--text-tertiary);
                margin-top: 2px;
            }
            
            /* 课程列表 */
            .list-section {
                background: var(--surface);
                border-radius: 16px;
                padding: 24px;
                margin-bottom: 24px;
                box-shadow: 0 2px 12px var(--shadow);
                border: 1px solid var(--outline-variant);
            }
            
            .course-list {
                display: flex;
                flex-direction: column;
                gap: 20px;
            }
            
            .day-group {
                display: flex;
                flex-direction: column;
                gap: 12px;
            }
            
            .day-header {
                font-size: 15px;
                font-weight: 600;
                color: var(--primary-variant);
                padding: 8px 12px;
                background: var(--surface-variant);
                border-radius: 8px;
                display: inline-block;
                width: fit-content;
            }
            
            .day-courses {
                display: flex;
                flex-direction: column;
                gap: 12px;
                padding-left: 12px;
            }
            
            .course-card {
                display: flex;
                background: var(--background);
                border-radius: 12px;
                overflow: hidden;
                box-shadow: 0 1px 4px var(--shadow);
                border: 1px solid var(--outline-variant);
                transition: transform 0.2s, box-shadow 0.2s;
            }
            
            .course-card:hover {
                transform: translateY(-2px);
                box-shadow: 0 4px 12px var(--shadow-strong);
            }
            
            .course-color-bar {
                width: 4px;
                flex-shrink: 0;
            }
            
            .course-content {
                padding: 14px 16px;
                flex: 1;
            }
            
            .course-title {
                font-size: 15px;
                font-weight: 600;
                color: var(--text-primary);
                margin-bottom: 8px;
            }
            
            .course-details {
                display: flex;
                flex-wrap: wrap;
                gap: 16px;
                margin-bottom: 8px;
            }
            
            .detail-item {
                display: flex;
                align-items: center;
                gap: 6px;
                font-size: 13px;
                color: var(--text-secondary);
            }
            
            .detail-icon {
                width: 14px;
                height: 14px;
                color: var(--text-tertiary);
            }
            
            .course-weeks {
                font-size: 12px;
                color: var(--text-tertiary);
                background: var(--surface-variant);
                padding: 4px 10px;
                border-radius: 12px;
                display: inline-block;
            }
            
            /* 页脚 */
            .footer {
                text-align: center;
                padding: 24px;
                color: var(--text-tertiary);
                font-size: 13px;
            }
            
            .footer p {
                margin: 4px 0;
            }
            
            .app-name {
                color: var(--primary);
                font-weight: 500;
            }
            
            /* 打印样式 */
            @media print {
                body {
                    background: white;
                    padding: 0;
                }
                
                .header-card,
                .schedule-section,
                .list-section {
                    box-shadow: none;
                    border: 1px solid #ddd;
                    break-inside: avoid;
                }
                
                .course-card:hover {
                    transform: none;
                    box-shadow: 0 1px 4px var(--shadow);
                }
            }
            
            /* 响应式 */
            @media (max-width: 768px) {
                body {
                    padding: 16px;
                }
                
                .header-card,
                .schedule-section,
                .list-section {
                    padding: 16px;
                    border-radius: 12px;
                }
                
                .title {
                    font-size: 22px;
                }
                
                .meta-info {
                    gap: 12px;
                }
                
                .schedule-grid {
                    font-size: 12px;
                }
                
                .grid-section {
                    min-height: 50px;
                }
                
                .course-cell {
                    min-height: 50px;
                }
            }
        """.trimIndent()
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



