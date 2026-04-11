package com.wind.ggbond.classtime.service.helper

import com.wind.ggbond.classtime.data.local.entity.ClassTime
import com.wind.ggbond.classtime.data.local.entity.Course
import com.wind.ggbond.classtime.data.local.entity.Schedule
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExportFormatter @Inject constructor() {

    fun formatIcsDateTime(dateTime: LocalDateTime): String {
        val instant = dateTime.atZone(ZoneId.systemDefault()).toInstant()
        return DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .withZone(ZoneId.of("UTC"))
            .format(instant)
    }

    fun getCurrentDateString(): String {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
    }

    fun getDayName(dayOfWeek: Int): String {
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

    fun buildIcsContent(
        courses: List<Course>,
        schedule: Schedule,
        classTimes: List<ClassTime>
    ): String {
        val sb = StringBuilder()
        val now = LocalDateTime.now()
        val exportTime = now.format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"))

        sb.appendLine("BEGIN:VCALENDAR")
        sb.appendLine("VERSION:2.0")
        sb.appendLine("PRODID:-//Course Schedule App//CN")
        sb.appendLine("CALSCALE:GREGORIAN")
        sb.appendLine("METHOD:PUBLISH")
        sb.appendLine("X-WR-CALNAME:${schedule.name}")
        sb.appendLine("X-WR-TIMEZONE:Asia/Shanghai")
        sb.appendLine("X-EXPORT-VERSION:${ExportMeta.CURRENT_EXPORT_VERSION}")
        sb.appendLine("X-APP-VERSION:${ExportMeta.CURRENT_APP_VERSION}")
        sb.appendLine("X-EXPORT-TIME:${now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}")

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
                    sb.appendLine("DTSTAMP:${formatIcsDateTime(now)}")
                    sb.appendLine("DTSTART:${formatIcsDateTime(startDateTime)}")
                    sb.appendLine("DTEND:${formatIcsDateTime(endDateTime)}")
                    sb.appendLine("SUMMARY:${course.courseName}")
                    sb.appendLine("LOCATION:${course.classroom}")

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

    fun buildHtmlContent(
        courses: List<Course>,
        schedule: Schedule?,
        classTimes: List<ClassTime>
    ): String {
        val courseColorList = listOf(
            "#FFE0B2", "#BBDEFB", "#C8E6C9", "#E1BEE7", "#FFCDD2",
            "#FFF9C4", "#B2EBF2", "#D7CCC8", "#F8BBD0", "#B3E5FC"
        )

        val courseNames = courses.map { it.courseName }.distinct()
        val courseColorMap = courseNames.mapIndexed { index, name ->
            name to courseColorList[index % courseColorList.size]
        }.toMap()

        val maxSection = courses.maxOfOrNull { it.startSection + it.sectionCount - 1 } ?: 12
        val now = LocalDateTime.now()
        val exportTimeStr = now.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm"))

        return buildString {
            appendLine("<!DOCTYPE html>")
            appendLine("<html lang='zh-CN'>")
            appendLine("<head>")
            appendLine("    <meta charset='UTF-8'>")
            appendLine("    <meta name='viewport' content='width=device-width, initial-scale=1.0'>")
            appendLine("    <meta name='generator' content='课程表 App v${ExportMeta.CURRENT_APP_VERSION}'>")
            appendLine("    <meta name='export-version' content='${ExportMeta.CURRENT_EXPORT_VERSION}'>")
            appendLine("    <meta name='export-time' content='${now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}'>")
            appendLine("    <title>${schedule?.name ?: "我的课程表"}</title>")
            appendLine("    <style>")
            appendLine(buildHtmlStyles())
            appendLine("    </style>")
            appendLine("</head>")
            appendLine("<body>")
            appendLine("    <div class='container'>")

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

            appendLine("        <section class='schedule-section'>")
            appendLine("            <h2 class='section-title'>课表视图</h2>")
            appendLine("            <div class='schedule-grid'>")

            appendLine("                <div class='grid-header corner'></div>")
            for (day in 1..7) {
                appendLine("                <div class='grid-header'>${getDayName(day)}</div>")
            }

            for (section in 1..maxSection) {
                val classTime = classTimes.find { it.sectionNumber == section }
                val timeStr = classTime?.let { "${it.startTime.toString().substring(0, 5)}" } ?: ""
                appendLine("                <div class='grid-section'>")
                appendLine("                    <span class='section-num'>$section</span>")
                if (timeStr.isNotEmpty()) {
                    appendLine("                    <span class='section-time'>$timeStr</span>")
                }
                appendLine("                </div>")

                for (day in 1..7) {
                    val coursesStartingAtSlot = courses.filter {
                        it.dayOfWeek == day &&
                        section == it.startSection
                    }

                    val isOccupiedByPrevious = courses.any {
                        it.dayOfWeek == day &&
                        section > it.startSection &&
                        section < it.startSection + it.sectionCount
                    }

                    if (coursesStartingAtSlot.isNotEmpty()) {
                        if (coursesStartingAtSlot.size == 1) {
                            val course = coursesStartingAtSlot.first()
                            val bgColor = course.color.ifEmpty { courseColorMap[course.courseName] ?: "#FFE0B2" }
                            appendLine("                <div class='grid-cell course-cell' style='background-color: $bgColor; grid-row: span ${course.sectionCount};'>")
                            appendLine("                    <div class='course-name'>${course.courseName}</div>")
                            if (course.classroom.isNotEmpty()) {
                                appendLine("                    <div class='course-room'>${course.classroom}</div>")
                            }
                            appendLine("                </div>")
                        } else {
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
                        appendLine("                <div class='grid-cell empty-cell'></div>")
                    }
                }
            }

            appendLine("            </div>")
            appendLine("        </section>")

            appendLine("        <section class='list-section'>")
            appendLine("            <h2 class='section-title'>课程详情</h2>")
            appendLine("            <div class='course-list'>")

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

            appendLine("        <footer class='footer'>")
            appendLine("            <p>导出时间：$exportTimeStr</p>")
            appendLine("            <p>应用版本：v${ExportMeta.CURRENT_APP_VERSION} | 导出格式版本：${ExportMeta.CURRENT_EXPORT_VERSION}</p>")
            appendLine("            <p class='app-name'>由 课程表 App 导出</p>")
            appendLine("        </footer>")

            appendLine("    </div>")
            appendLine("</body>")
            appendLine("</html>")
        }
    }

    private fun buildHtmlStyles(): String {
        return """
            :root {
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
            
            .section-title {
                font-size: 18px;
                font-weight: 600;
                color: var(--text-primary);
                margin-bottom: 16px;
                padding-left: 12px;
                border-left: 3px solid var(--primary);
            }
            
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

    fun buildTextContent(
        courses: List<Course>,
        schedule: Schedule?,
        classTimes: List<ClassTime>
    ): String {
        val now = LocalDateTime.now()
        val exportTimeStr = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))

        return buildString {
            appendLine("╔══════════════════════════════════════════════════════════════╗")
            appendLine("║                                                              ║")
            appendLine("║                        我 的 课 程 表                        ║")
            appendLine("║                                                              ║")
            appendLine("╚══════════════════════════════════════════════════════════════╝")
            appendLine()

            appendLine("┌──────────────────────────────────────────────────────────────┐")
            appendLine("│  基本信息                                                    │")
            appendLine("├──────────────────────────────────────────────────────────────┤")
            schedule?.let {
                appendLine("│  课表名称：${it.name.padEnd(48)}│")
                appendLine("│  学期时间：${it.startDate} ~ ${it.endDate}".padEnd(63) + "│")
                appendLine("│  总  周  数：${it.totalWeeks}周".padEnd(61) + "│")
            }
            appendLine("│  课程数量：${courses.size}门".padEnd(61) + "│")
            appendLine("│  导出时间：$exportTimeStr".padEnd(61) + "│")
            appendLine("│  应用版本：v${ExportMeta.CURRENT_APP_VERSION}".padEnd(61) + "│")
            appendLine("│  格式版本：${ExportMeta.CURRENT_EXPORT_VERSION}".padEnd(61) + "│")
            appendLine("└──────────────────────────────────────────────────────────────┘")
            appendLine()

            for (day in 1..7) {
                val dayCourses = courses.filter { it.dayOfWeek == day }
                    .sortedBy { it.startSection }

                if (dayCourses.isNotEmpty()) {
                    appendLine("┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓")
                    appendLine("┃  ${getDayName(day)}                                                        ┃")
                    appendLine("┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛")
                    appendLine()

                    dayCourses.forEach { course ->
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

            appendLine()
            appendLine("════════════════════════════════════════════════════════════════")
            appendLine("                      由 课程表 App v${ExportMeta.CURRENT_APP_VERSION} 导出")
            appendLine("════════════════════════════════════════════════════════════════")
        }
    }

    fun buildCsvContent(
        courses: List<Course>,
        schedule: Schedule?,
        classTimes: List<ClassTime>
    ): String {
        val now = LocalDateTime.now()

        return buildString {
            append("\uFEFF")

            appendLine("\"# 课程表导出数据\"")
            appendLine("\"# 应用版本\",\"v${ExportMeta.CURRENT_APP_VERSION}\"")
            appendLine("\"# 导出格式版本\",\"${ExportMeta.CURRENT_EXPORT_VERSION}\"")
            appendLine("\"# 导出时间\",\"${now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}\"")
            appendLine()

            appendLine("序号,课程名称,教师,教室,星期,节次,上课时间,周次,学分,颜色,提醒,备注")

            courses.sortedWith(compareBy({ it.dayOfWeek }, { it.startSection }))
                .forEachIndexed { index, course ->
                    val dayName = getDayName(course.dayOfWeek)
                    val sections = "第${course.startSection}-${course.startSection + course.sectionCount - 1}节"

                    val classTime = classTimes.find { it.sectionNumber == course.startSection }
                    val endClassTime = classTimes.find { it.sectionNumber == course.startSection + course.sectionCount - 1 }
                    val timeStr = if (classTime != null && endClassTime != null) {
                        "${classTime.startTime.toString().substring(0, 5)}-${endClassTime.endTime.toString().substring(0, 5)}"
                    } else {
                        ""
                    }

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
                            course.note.replace("\n", " ")
                        ).joinToString(",") { "\"${it.replace("\"", "\"\"")}\"" }
                    )
                }

            appendLine()
            appendLine("\"# 统计信息\"")
            appendLine("\"课表名称\",\"${schedule?.name ?: ""}\"")
            appendLine("\"学期时间\",\"${schedule?.startDate ?: ""} ~ ${schedule?.endDate ?: ""}\"")
            appendLine("\"总周数\",\"${schedule?.totalWeeks ?: 0}周\"")
            appendLine("\"课程总数\",\"${courses.size}门\"")
            appendLine("\"总学分\",\"${courses.sumOf { it.credit.toDouble() }}\"")
            appendLine("\"导出时间\",\"${now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}\"")
        }
    }
}
