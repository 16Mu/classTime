package com.wind.ggbond.classtime.service.helper

import android.content.Context
import android.net.Uri
import com.wind.ggbond.classtime.data.model.ParsedCourse
import com.wind.ggbond.classtime.util.AppLogger
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CsvParser @Inject constructor() : TableParser {

    companion object {
        private const val TAG = "CsvParser"
        private const val MAX_ROWS_TO_SCAN = 50
        private const val BOM_CHAR = '\uFEFF'

        private val ENCODING_CANDIDATES = listOf("UTF-8", "GBK", "GB2312", "GB18030", "Big5")

        private val DAY_MAP = mapOf(
            "周一" to 1, "星期一" to 1, "Monday" to 1, "一" to 1,
            "周二" to 2, "星期二" to 2, "Tuesday" to 2, "二" to 2,
            "周三" to 3, "星期三" to 3, "Wednesday" to 3, "三" to 3,
            "周四" to 4, "星期四" to 4, "Thursday" to 4, "四" to 4,
            "周五" to 5, "星期五" to 5, "Friday" to 5, "五" to 5,
            "周六" to 6, "星期六" to 6, "Saturday" to 6, "六" to 6,
            "周日" to 7, "星期日" to 7, "Sunday" to 7, "日" to 7,
        )
    }

    override fun parseFromUri(
        context: Context,
        uri: Uri,
        config: TableImportConfig
    ): TableParser.TableParseResult {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val fileName = getFileName(context, uri)
                parseFromStream(inputStream, fileName, config)
            } ?: TableParser.TableParseResult(
                courses = emptyList(),
                warnings = listOf("无法打开文件")
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "CSV解析失败", e)
            TableParser.TableParseResult(
                courses = emptyList(),
                warnings = listOf("解析失败: ${e.message}")
            )
        }
    }

    override fun parseFromStream(
        inputStream: InputStream,
        fileName: String?,
        config: TableImportConfig
    ): TableParser.TableParseResult {
        return try {
            val (content, detectedEncoding) = readWithEncodingDetection(inputStream, config.encoding)
            val allRows = parseCsvRows(content, config.delimiter)

            if (allRows.isEmpty()) {
                return TableParser.TableParseResult(
                    courses = emptyList(),
                    warnings = listOf("文件为空"),
                    detectedEncoding = detectedEncoding
                )
            }

            val effectiveConfig = if (config.fieldMapping.isNotEmpty()) {
                config
            } else {
                config.copy(
                    headerRowIndex = detectHeaderRow(allRows),
                    fieldMapping = autoDetectFieldMapping(allRows)
                )
            }

            val headerRowIndex = effectiveConfig.headerRowIndex
            val dataStartRowIndex = if (effectiveConfig.dataStartRowIndex > headerRowIndex) {
                effectiveConfig.dataStartRowIndex
            } else {
                headerRowIndex + 1
            }
            val fieldMapping = effectiveConfig.fieldMapping

            if (fieldMapping.isEmpty() || !fieldMapping.containsKey("courseName")) {
                return TableParser.TableParseResult(
                    courses = emptyList(),
                    warnings = listOf("未找到课程名称字段，无法解析"),
                    headerRowIndex = headerRowIndex,
                    fieldMapping = fieldMapping,
                    detectedEncoding = detectedEncoding
                )
            }

            val courses = mutableListOf<ParsedCourse>()
            val warnings = mutableListOf<String>()

            for (rowIdx in dataStartRowIndex until allRows.size) {
                val row = allRows[rowIdx]
                if (effectiveConfig.skipEmptyRows && row.all { it.isBlank() }) continue

                val courseName = getFieldValue(row, fieldMapping, "courseName", effectiveConfig.trimWhitespace)
                if (courseName.isBlank()) continue

                val dayOfWeekStr = getFieldValue(row, fieldMapping, "dayOfWeek", effectiveConfig.trimWhitespace)
                val sectionStr = getFieldValue(row, fieldMapping, "section", effectiveConfig.trimWhitespace)
                val weeksStr = getFieldValue(row, fieldMapping, "weeks", effectiveConfig.trimWhitespace)

                val dayOfWeek = parseDayOfWeek(dayOfWeekStr)
                val (startSection, sectionCount) = parseSectionInfo(sectionStr)
                val (weeks, weekExpression) = parseWeekInfo(weeksStr)

                if (dayOfWeek !in 1..7) {
                    warnings.add("行${rowIdx + 1}: 星期无效 '$dayOfWeekStr'")
                    continue
                }

                courses.add(ParsedCourse(
                    courseName = courseName.trim(),
                    teacher = getFieldValue(row, fieldMapping, "teacher", effectiveConfig.trimWhitespace).trim(),
                    classroom = getFieldValue(row, fieldMapping, "classroom", effectiveConfig.trimWhitespace).trim(),
                    dayOfWeek = dayOfWeek,
                    startSection = startSection,
                    sectionCount = sectionCount,
                    weekExpression = weekExpression,
                    weeks = weeks,
                    credit = getFieldValue(row, fieldMapping, "credit", effectiveConfig.trimWhitespace)
                        .toFloatOrNull() ?: 0f,
                    courseCode = getFieldValue(row, fieldMapping, "courseCode", effectiveConfig.trimWhitespace).trim(),
                    className = getFieldValue(row, fieldMapping, "className", effectiveConfig.trimWhitespace).trim(),
                    courseNature = getFieldValue(row, fieldMapping, "courseNature", effectiveConfig.trimWhitespace).trim(),
                    studentCount = getFieldValue(row, fieldMapping, "studentCount", effectiveConfig.trimWhitespace).trim().toIntOrNull() ?: 0,
                    selectedCount = getFieldValue(row, fieldMapping, "selectedCount", effectiveConfig.trimWhitespace).trim().toIntOrNull() ?: 0,
                    courseHours = getFieldValue(row, fieldMapping, "courseHours", effectiveConfig.trimWhitespace).trim()
                ))
            }

            if (courses.isEmpty() && dataStartRowIndex < allRows.size) {
                warnings.add("未解析出有效课程数据")
            }

            TableParser.TableParseResult(
                courses = courses,
                warnings = warnings,
                headerRowIndex = headerRowIndex,
                fieldMapping = fieldMapping,
                detectedEncoding = detectedEncoding
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "CSV解析失败", e)
            TableParser.TableParseResult(
                courses = emptyList(),
                warnings = listOf("解析失败: ${e.message}")
            )
        }
    }

    override fun getPreviewFromUri(
        context: Context,
        uri: Uri,
        maxRows: Int
    ): List<List<String>> {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val fileName = getFileName(context, uri)
                getPreviewFromStream(inputStream, fileName, maxRows)
            } ?: emptyList()
        } catch (e: Exception) {
            AppLogger.e(TAG, "获取CSV预览失败", e)
            emptyList()
        }
    }

    override fun getPreviewFromStream(
        inputStream: InputStream,
        fileName: String?,
        maxRows: Int
    ): List<List<String>> {
        return try {
            val (content, _) = readWithEncodingDetection(inputStream)
            val rows = parseCsvRows(content)
            rows.take(maxRows)
        } catch (e: Exception) {
            AppLogger.e(TAG, "获取CSV预览失败", e)
            emptyList()
        }
    }

    fun parseCsvContent(
        content: String,
        config: TableImportConfig = TableImportConfig()
    ): TableParser.TableParseResult {
        val allRows = parseCsvRows(content, config.delimiter)

        if (allRows.isEmpty()) {
            return TableParser.TableParseResult(
                courses = emptyList(),
                warnings = listOf("内容为空")
            )
        }

        val effectiveConfig = if (config.fieldMapping.isNotEmpty()) {
            config
        } else {
            config.copy(
                headerRowIndex = detectHeaderRow(allRows),
                fieldMapping = autoDetectFieldMapping(allRows)
            )
        }

        val headerRowIndex = effectiveConfig.headerRowIndex
        val dataStartRowIndex = if (effectiveConfig.dataStartRowIndex > headerRowIndex) {
            effectiveConfig.dataStartRowIndex
        } else {
            headerRowIndex + 1
        }
        val fieldMapping = effectiveConfig.fieldMapping

        if (fieldMapping.isEmpty() || !fieldMapping.containsKey("courseName")) {
            return TableParser.TableParseResult(
                courses = emptyList(),
                warnings = listOf("未找到课程名称字段，无法解析"),
                headerRowIndex = headerRowIndex,
                fieldMapping = fieldMapping
            )
        }

        val courses = mutableListOf<ParsedCourse>()
        val warnings = mutableListOf<String>()

        for (rowIdx in dataStartRowIndex until allRows.size) {
            val row = allRows[rowIdx]
            if (effectiveConfig.skipEmptyRows && row.all { it.isBlank() }) continue

            val courseName = getFieldValue(row, fieldMapping, "courseName", effectiveConfig.trimWhitespace)
            if (courseName.isBlank()) continue

            val dayOfWeekStr = getFieldValue(row, fieldMapping, "dayOfWeek", effectiveConfig.trimWhitespace)
            val sectionStr = getFieldValue(row, fieldMapping, "section", effectiveConfig.trimWhitespace)
            val weeksStr = getFieldValue(row, fieldMapping, "weeks", effectiveConfig.trimWhitespace)

            val dayOfWeek = parseDayOfWeek(dayOfWeekStr)
            val (startSection, sectionCount) = parseSectionInfo(sectionStr)
            val (weeks, weekExpression) = parseWeekInfo(weeksStr)

            if (dayOfWeek !in 1..7) {
                warnings.add("行${rowIdx + 1}: 星期无效 '$dayOfWeekStr'")
                continue
            }

            courses.add(ParsedCourse(
                courseName = courseName.trim(),
                teacher = getFieldValue(row, fieldMapping, "teacher", effectiveConfig.trimWhitespace).trim(),
                classroom = getFieldValue(row, fieldMapping, "classroom", effectiveConfig.trimWhitespace).trim(),
                dayOfWeek = dayOfWeek,
                startSection = startSection,
                sectionCount = sectionCount,
                weekExpression = weekExpression,
                weeks = weeks,
                credit = getFieldValue(row, fieldMapping, "credit", effectiveConfig.trimWhitespace)
                    .toFloatOrNull() ?: 0f,
                courseCode = getFieldValue(row, fieldMapping, "courseCode", effectiveConfig.trimWhitespace).trim(),
                className = getFieldValue(row, fieldMapping, "className", effectiveConfig.trimWhitespace).trim(),
                courseNature = getFieldValue(row, fieldMapping, "courseNature", effectiveConfig.trimWhitespace).trim(),
                studentCount = getFieldValue(row, fieldMapping, "studentCount", effectiveConfig.trimWhitespace).trim().toIntOrNull() ?: 0,
                selectedCount = getFieldValue(row, fieldMapping, "selectedCount", effectiveConfig.trimWhitespace).trim().toIntOrNull() ?: 0,
                courseHours = getFieldValue(row, fieldMapping, "courseHours", effectiveConfig.trimWhitespace).trim()
            ))
        }

        return TableParser.TableParseResult(
            courses = courses,
            warnings = warnings,
            headerRowIndex = headerRowIndex,
            fieldMapping = fieldMapping
        )
    }

    internal fun parseCsvRows(content: String, delimiter: Char = ','): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val lines = content.lines()

        for (line in lines) {
            if (line.isBlank()) continue
            val trimmed = line.trimStart()
            if (trimmed.startsWith("#")) continue
            if (trimmed.startsWith("\"#")) continue
            val parsedRow = parseCsvLine(line, delimiter)
            if (parsedRow.isEmpty()) continue
            val firstField = parsedRow[0].trim().removeSurrounding("\"")
            if (firstField.startsWith("#")) continue
            rows.add(parsedRow)
        }

        return rows
    }

    internal fun parseCsvLine(line: String, delimiter: Char = ','): List<String> {
        val fields = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false
        var i = 0

        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"')
                        i += 2
                        continue
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                c == delimiter && !inQuotes -> {
                    fields.add(current.toString())
                    current = StringBuilder()
                }
                else -> {
                    current.append(c)
                }
            }
            i++
        }
        fields.add(current.toString())
        return fields
    }

    internal fun readWithEncodingDetection(
        inputStream: InputStream,
        preferredEncoding: String = "UTF-8"
    ): Pair<String, String> {
        val bytes = inputStream.readBytes()

        val hasBom = bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()

        if (hasBom) {
            val content = String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
            return Pair(content, "UTF-8")
        }

        val utf8Content = try {
            val decoded = String(bytes, Charsets.UTF_8)
            if (isValidUtf8(decoded)) decoded else null
        } catch (_: Exception) { null }

        if (utf8Content != null && preferredEncoding == "UTF-8") {
            return Pair(utf8Content, "UTF-8")
        }

        for (encoding in ENCODING_CANDIDATES) {
            if (encoding == "UTF-8" && utf8Content != null) {
                return Pair(utf8Content, "UTF-8")
            }
            try {
                val charset = charset(encoding)
                val content = String(bytes, charset)
                if (looksLikeChineseText(content)) {
                    AppLogger.d(TAG, "检测到编码: $encoding")
                    return Pair(content, encoding)
                }
            } catch (_: Exception) { continue }
        }

        val fallback = String(bytes, Charsets.UTF_8)
        return Pair(fallback, "UTF-8")
    }

    internal fun detectHeaderRow(rows: List<List<String>>): Int {
        val rowsToScan = minOf(rows.size, MAX_ROWS_TO_SCAN)

        for (rowIdx in 0 until rowsToScan) {
            val row = rows[rowIdx]
            if (row.isEmpty() || row.all { it.isBlank() }) continue

            val headerKeywords = listOf(
                "课程", "教师", "教室", "星期", "节次", "周次", "学分",
                "course", "teacher", "room", "day", "section", "week", "credit",
                "序号", "名称", "地点", "科目"
            )

            val matchCount = row.count { cell ->
                val normalized = cell.trim().lowercase().removeSurrounding("\"")
                headerKeywords.any { keyword -> normalized.contains(keyword.lowercase()) }
            }

            if (matchCount >= 2) {
                return rowIdx
            }
        }

        return 0
    }

    internal fun autoDetectFieldMapping(rows: List<List<String>>): Map<String, Int> {
        val headerRowIndex = detectHeaderRow(rows)
        if (headerRowIndex >= rows.size) return emptyMap()

        val headerRow = rows[headerRowIndex].map { it.trim().removeSurrounding("\"").lowercase() }

        val fieldPatterns = mapOf(
            "courseName" to listOf("课程名称", "课程名", "课程", "科目", "科目名称", "科目名", "name", "subject", "course"),
            "teacher" to listOf("授课教师", "任课教师", "教师", "任课老师", "授课老师", "老师", "teacher", "instructor"),
            "classroom" to listOf("上课地点", "教室", "地点", "教学班", "上课教室", "room", "location", "classroom"),
            "dayOfWeek" to listOf("星期", "星 期", "上课星期", "day", "weekday"),
            "section" to listOf("节次", "节 次", "上课节次", "上课时间", "节", "section", "period"),
            "weeks" to listOf("上课周次", "周次", "起止周", "上课周", "week", "weeks"),
            "credit" to listOf("学分", "credit", "credits"),
            "courseCode" to listOf("课程代码", "课程编号", "课程号", "课序号", "course_code", "course_id"),
            "className" to listOf("教学班名称", "班级", "教学班", "班名", "class_name"),
            "courseNature" to listOf("课程性质", "课程类型", "课程类别", "修读类型", "课程属性", "course_nature"),
            "studentCount" to listOf("教学班人数", "人数", "班级人数", "容量", "student_count"),
            "selectedCount" to listOf("选课人数", "已选人数", "已选", "selected_count"),
            "courseHours" to listOf("学时组成", "课程学时", "学时", "总学时", "课时", "course_hours"),
            "note" to listOf("备注", "说明", "note", "remark", "memo")
        )

        val mapping = mutableMapOf<String, Int>()

        for ((fieldName, patterns) in fieldPatterns) {
            var bestIdx = -1
            var bestScore = 0f

            headerRow.forEachIndexed { idx, header ->
                for (pattern in patterns) {
                    val score = when {
                        header == pattern.lowercase() -> 1.0f
                        header.contains(pattern.lowercase()) -> 0.8f
                        pattern.lowercase().contains(header) -> 0.6f
                        else -> 0f
                    }
                    if (score > bestScore) {
                        bestScore = score
                        bestIdx = idx
                    }
                }
            }

            if (bestIdx >= 0 && bestScore > 0.5f) {
                if (!mapping.containsValue(bestIdx)) {
                    mapping[fieldName] = bestIdx
                } else {
                    val existingKey = mapping.entries.first { it.value == bestIdx }.key
                    if (bestScore > 0.8f && fieldName == "courseName") {
                        mapping.remove(existingKey)
                        mapping[fieldName] = bestIdx
                    }
                }
            }
        }

        return mapping
    }

    private fun getFieldValue(
        row: List<String>,
        fieldMapping: Map<String, Int>,
        fieldName: String,
        trim: Boolean
    ): String {
        val idx = fieldMapping[fieldName] ?: return ""
        if (idx >= row.size) return ""
        val value = row[idx].removeSurrounding("\"")
        return if (trim) value.trim() else value
    }

    private fun parseDayOfWeek(value: String): Int {
        val trimmed = value.trim()
        DAY_MAP[trimmed]?.let { return it }
        val numMatch = Regex("\\d+").find(trimmed)
        val num = numMatch?.value?.toIntOrNull() ?: return 0
        return if (num in 1..7) num else 0
    }

    private fun parseSectionInfo(value: String): Pair<Int, Int> {
        val trimmed = value.trim()
        val rangeMatch = Regex("(\\d+)\\s*[-–—]\\s*(\\d+)").find(trimmed)
        if (rangeMatch != null) {
            val start = rangeMatch.groupValues[1].toIntOrNull() ?: 1
            val end = rangeMatch.groupValues[2].toIntOrNull() ?: start
            return Pair(start, end - start + 1)
        }
        val singleMatch = Regex("(\\d+)").find(trimmed)
        if (singleMatch != null) {
            val start = singleMatch.groupValues[1].toIntOrNull() ?: 1
            return Pair(start, 1)
        }
        return Pair(1, 1)
    }

    private fun parseWeekInfo(value: String): Pair<List<Int>, String> {
        if (value.isBlank()) return Pair(emptyList(), "")
        val trimmed = value.trim()
        val weeks = mutableSetOf<Int>()

        val rangePattern = Regex("(\\d+)\\s*[-–—]\\s*(\\d+)\\s*周?\\s*(?:\\(([单双])\\))?")
        rangePattern.findAll(trimmed).forEach { match ->
            val start = match.groupValues[1].toIntOrNull() ?: return@forEach
            val end = match.groupValues[2].toIntOrNull() ?: return@forEach
            val oddEven = match.groupValues.getOrNull(3)
            (start..end).filter { w ->
                when (oddEven) {
                    "单" -> w % 2 == 1
                    "双" -> w % 2 == 0
                    else -> true
                }
            }.forEach { weeks.add(it) }
        }

        if (weeks.isEmpty()) {
            val rangeNoWeek = Regex("(\\d+)\\s*[-–—]\\s*(\\d+)")
            rangeNoWeek.find(trimmed)?.let { match ->
                val start = match.groupValues[1].toIntOrNull() ?: return@let
                val end = match.groupValues[2].toIntOrNull() ?: return@let
                if (start in 1..30 && end in 1..30 && end >= start) {
                    (start..end).forEach { weeks.add(it) }
                }
            }
        }

        if (weeks.isEmpty()) {
            Regex("(\\d+)").findAll(trimmed).mapNotNull { it.groupValues[1].toIntOrNull() }
                .filter { it in 1..30 }.forEach { weeks.add(it) }
        }

        val sortedWeeks = weeks.sorted()
        val weekExpr = if (sortedWeeks.isNotEmpty()) formatWeekExpression(sortedWeeks) else trimmed
        return Pair(sortedWeeks, weekExpr)
    }

    private fun formatWeekExpression(weeks: List<Int>): String {
        if (weeks.isEmpty()) return ""
        val ranges = mutableListOf<String>()
        var rs = weeks[0]
        var re = weeks[0]
        for (i in 1 until weeks.size) {
            if (weeks[i] == re + 1) {
                re = weeks[i]
            } else {
                ranges.add(if (rs == re) "$rs" else "$rs-$re")
                rs = weeks[i]
                re = weeks[i]
            }
        }
        ranges.add(if (rs == re) "$rs" else "$rs-$re")
        return ranges.joinToString(",") + "周"
    }

    private fun isValidUtf8(text: String): Boolean {
        var replacementCount = 0
        for (c in text) {
            if (c == '\uFFFD') replacementCount++
        }
        return replacementCount < text.length * 0.01
    }

    private fun looksLikeChineseText(text: String): Boolean {
        val chineseChars = text.count { it.code in 0x4E00..0x9FFF }
        val totalChars = text.filter { !it.isWhitespace() }.length
        return if (totalChars > 0) chineseChars.toFloat() / totalChars > 0.1f else false
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        var fileName: String? = null
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        fileName = cursor.getString(nameIndex)
                    }
                }
            }
        } catch (e: Exception) { AppLogger.e("Safety", "操作异常", e) }
        return fileName ?: uri.lastPathSegment
    }
}
