package com.wind.ggbond.classtime.service.helper

import android.content.Context
import android.net.Uri
import com.wind.ggbond.classtime.data.model.ParsedCourse
import com.wind.ggbond.classtime.util.AppLogger
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LightweightXlsxParser @Inject constructor() {

    companion object {
        private const val TAG = "LightweightXlsxParser"
        private const val SHARED_STRINGS_PATH = "xl/sharedStrings.xml"
        private const val WORKSHEET_PREFIX = "xl/worksheets/sheet"
        private const val WORKBOOK_PATH = "xl/workbook.xml"
    }

    fun parseXlsx(inputStream: InputStream, config: ExcelImportConfig = ExcelImportConfig()): ExcelParseResult {
        return try {
            val zipData = readZipEntries(inputStream)
            val sharedStrings = parseSharedStrings(zipData[SHARED_STRINGS_PATH])
            val sheetNames = parseWorkbookSheetNames(zipData[WORKBOOK_PATH])

            val sheetKey = if (config.sheetIndex < sheetNames.size) {
                "xl/worksheets/sheet${config.sheetIndex + 1}.xml"
            } else {
                zipData.keys.firstOrNull { it.startsWith(WORKSHEET_PREFIX) }
            }

            if (sheetKey == null || !zipData.containsKey(sheetKey)) {
                return ExcelParseResult(courses = emptyList(), warnings = listOf("未找到工作表"))
            }

            val sheetData = parseSheetData(zipData[sheetKey]!!, sharedStrings)
            val mergedRegions = parseMergeCells(zipData[sheetKey]!!)

            if (config.fieldMapping.isEmpty()) {
                return ExcelParseResult(
                    courses = emptyList(),
                    warnings = listOf("未提供字段映射"),
                    headerRowIndex = 0
                )
            }

            val courses = buildCourses(sheetData, mergedRegions, config)
            ExcelParseResult(
                courses = courses.first,
                warnings = courses.second,
                headerRowIndex = config.headerRowIndex,
                fieldMapping = config.fieldMapping
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "xlsx解析失败", e)
            ExcelParseResult(courses = emptyList(), warnings = listOf("解析失败: ${e.message}"))
        }
    }

    fun parseXlsxFromUri(
        context: Context,
        uri: Uri,
        config: ExcelImportConfig = ExcelImportConfig()
    ): ExcelParseResult {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                parseXlsx(inputStream, config)
            } ?: ExcelParseResult(courses = emptyList(), warnings = listOf("无法打开文件"))
        } catch (e: Exception) {
            AppLogger.e(TAG, "xlsx解析失败", e)
            ExcelParseResult(courses = emptyList(), warnings = listOf("解析失败: ${e.message}"))
        }
    }

    fun getSheetNames(context: Context, uri: Uri): List<String> {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val zipData = readZipEntries(inputStream)
                parseWorkbookSheetNames(zipData[WORKBOOK_PATH])
            } ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    fun getSheetNames(inputStream: InputStream): List<String> {
        return try {
            val zipData = readZipEntries(inputStream)
            parseWorkbookSheetNames(zipData[WORKBOOK_PATH])
        } catch (e: Exception) { emptyList() }
    }

    fun getSheetPreview(
        context: Context,
        uri: Uri,
        sheetIndex: Int = 0,
        maxRows: Int = 10
    ): List<List<String>> {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                getSheetPreview(inputStream, null, sheetIndex, maxRows)
            } ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    fun getSheetPreview(
        inputStream: InputStream,
        fileName: String?,
        sheetIndex: Int = 0,
        maxRows: Int = 10
    ): List<List<String>> {
        return try {
            val zipData = readZipEntries(inputStream)
            val sharedStrings = parseSharedStrings(zipData[SHARED_STRINGS_PATH])
            val sheetKey = "xl/worksheets/sheet${sheetIndex + 1}.xml"
            val sheetXml = zipData[sheetKey] ?: return emptyList()
            val sheetData = parseSheetData(sheetXml, sharedStrings)
            val mergedRegions = parseMergeCells(sheetXml)

            val result = mutableListOf<List<String>>()
            val maxRow = sheetData.keys.maxOrNull() ?: 0
            val maxCol = sheetData.values.maxOfOrNull { rowMap -> rowMap.keys.maxOrNull() ?: 0 } ?: 0

            for (rowIdx in 0..minOf(maxRow, maxRows - 1)) {
                val rowData = mutableListOf<String>()
                for (colIdx in 0..maxCol) {
                    val directValue = sheetData[rowIdx]?.get(colIdx)
                    if (directValue != null && directValue != "__MERGED__") {
                        rowData.add(directValue)
                    } else {
                        val mergedValue = findMergedValue(mergedRegions, sheetData, rowIdx, colIdx)
                        rowData.add(mergedValue)
                    }
                }
                result.add(rowData)
            }
            result
        } catch (e: Exception) {
            AppLogger.e(TAG, "获取预览数据失败", e)
            emptyList()
        }
    }

    private fun readZipEntries(inputStream: InputStream): Map<String, ByteArray> {
        val entries = mutableMapOf<String, ByteArray>()
        val zis = ZipInputStream(inputStream)
        var entry = zis.nextEntry
        while (entry != null) {
            if (!entry.isDirectory) {
                val baos = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var len = zis.read(buffer)
                while (len > 0) {
                    baos.write(buffer, 0, len)
                    len = zis.read(buffer)
                }
                entries[entry.name] = baos.toByteArray()
            }
            zis.closeEntry()
            entry = zis.nextEntry
        }
        return entries
    }

    private fun parseSharedStrings(xmlData: ByteArray?): List<String> {
        if (xmlData == null) return emptyList()
        val strings = mutableListOf<String>()
        try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(xmlData.inputStream(), "UTF-8")

            var inSi = false
            var inT = false
            var currentString = StringBuilder()

            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "si" -> { inSi = true; currentString = StringBuilder() }
                            "t" -> if (inSi) inT = true
                            "r" -> if (inSi) { /* run element, just continue */ }
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (inT) currentString.append(parser.text ?: "")
                    }
                    XmlPullParser.END_TAG -> {
                        when (parser.name) {
                            "t" -> inT = false
                            "si" -> {
                                inSi = false
                                strings.add(currentString.toString())
                            }
                        }
                    }
                }
                event = parser.next()
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "解析sharedStrings失败", e)
        }
        return strings
    }

    private fun parseWorkbookSheetNames(xmlData: ByteArray?): List<String> {
        if (xmlData == null) return emptyList()
        val names = mutableListOf<String>()
        try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(xmlData.inputStream(), "UTF-8")

            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name == "sheet") {
                    val name = parser.getAttributeValue(null, "name") ?: ""
                    if (name.isNotEmpty()) names.add(name)
                }
                event = parser.next()
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "解析workbook失败", e)
        }
        return names
    }

    private fun parseSheetData(
        xmlData: ByteArray,
        sharedStrings: List<String>
    ): Map<Int, Map<Int, String>> {
        val rows = mutableMapOf<Int, MutableMap<Int, String>>()
        try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(xmlData.inputStream(), "UTF-8")

            var currentRow = -1
            var currentCol = -1
            var inV = false
            var inInlineStr = false
            var inT = false
            var cellType = ""
            var cellValue = StringBuilder()
            var currentRowNum = -1

            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "row" -> {
                                val r = parser.getAttributeValue(null, "r")
                                currentRowNum = r?.toIntOrNull()?.minus(1) ?: (currentRowNum + 1)
                                if (!rows.containsKey(currentRowNum)) {
                                    rows[currentRowNum] = mutableMapOf()
                                }
                            }
                            "c" -> {
                                val ref = parser.getAttributeValue(null, "r") ?: ""
                                cellType = parser.getAttributeValue(null, "t") ?: ""
                                val (row, col) = parseCellRef(ref)
                                currentRow = row
                                currentCol = col
                                cellValue = StringBuilder()
                            }
                            "v" -> inV = true
                            "is" -> inInlineStr = true
                            "t" -> if (inInlineStr) inT = true
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (inV || inT) {
                            cellValue.append(parser.text ?: "")
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when (parser.name) {
                            "v" -> {
                                inV = false
                                val value = cellValue.toString()
                                val displayValue = when (cellType) {
                                    "s" -> {
                                        val idx = value.toIntOrNull()
                                        if (idx != null && idx < sharedStrings.size) sharedStrings[idx] else value
                                    }
                                    "b" -> if (value == "1") "TRUE" else "FALSE"
                                    "str" -> value
                                    else -> {
                                        val d = value.toDoubleOrNull()
                                        if (d != null && d == d.toLong().toDouble()) d.toLong().toString() else value
                                    }
                                }
                                if (currentRow >= 0 && currentCol >= 0) {
                                    if (!rows.containsKey(currentRow)) rows[currentRow] = mutableMapOf()
                                    rows[currentRow]!![currentCol] = displayValue
                                }
                            }
                            "is" -> inInlineStr = false
                            "t" -> inT = false
                            "c" -> {
                                if (inInlineStr && cellValue.isNotEmpty() && currentRow >= 0 && currentCol >= 0) {
                                    if (!rows.containsKey(currentRow)) rows[currentRow] = mutableMapOf()
                                    rows[currentRow]!![currentCol] = cellValue.toString()
                                }
                            }
                        }
                    }
                }
                event = parser.next()
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "解析sheet数据失败", e)
        }
        @Suppress("UNCHECKED_CAST")
        return rows as Map<Int, Map<Int, String>>
    }

    private fun parseMergeCells(xmlData: ByteArray): List<MergedCellInfo> {
        val merged = mutableListOf<MergedCellInfo>()
        try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(xmlData.inputStream(), "UTF-8")

            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name == "mergeCell") {
                    val ref = parser.getAttributeValue(null, "ref") ?: ""
                    val parts = ref.split(":")
                    if (parts.size == 2) {
                        val (firstRow, firstCol) = parseCellRef(parts[0])
                        val (lastRow, lastCol) = parseCellRef(parts[1])
                        merged.add(MergedCellInfo(firstRow, lastRow, firstCol, lastCol, ""))
                    }
                }
                event = parser.next()
            }
        } catch (e: Exception) { AppLogger.e("Safety", "操作异常", e) }
        return merged
    }

    private fun parseCellRef(ref: String): Pair<Int, Int> {
        val colStr = ref.takeWhile { it.isLetter() }
        val rowStr = ref.takeLastWhile { it.isDigit() }
        val col = columnLetterToIndex(colStr)
        val row = rowStr.toIntOrNull()?.minus(1) ?: 0
        return Pair(row, col)
    }

    private fun columnLetterToIndex(letters: String): Int {
        var index = 0
        for (ch in letters.uppercase()) {
            index = index * 26 + (ch.code - 'A'.code + 1)
        }
        return index - 1
    }

    private fun findMergedValue(mergedRegions: List<MergedCellInfo>, sheetData: Map<Int, Map<Int, String>>, row: Int, col: Int): String {
        for (region in mergedRegions) {
            if (row in region.firstRow..region.lastRow && col in region.firstCol..region.lastCol) {
                if (row == region.firstRow && col == region.firstCol) {
                    return sheetData[row]?.get(col) ?: ""
                }
                return sheetData[region.firstRow]?.get(region.firstCol) ?: ""
            }
        }
        return sheetData[row]?.get(col) ?: ""
    }

    private fun buildCourses(
        sheetData: Map<Int, Map<Int, String>>,
        mergedRegions: List<MergedCellInfo>,
        config: ExcelImportConfig
    ): Pair<List<ParsedCourse>, List<String>> {
        val warnings = mutableListOf<String>()
        val courses = mutableListOf<ParsedCourse>()
        val fieldMapping = config.fieldMapping
        val startRow = config.dataStartRowIndex.coerceAtLeast(config.headerRowIndex + 1)

        for (rowIdx in startRow..(sheetData.keys.maxOrNull() ?: 0)) {
            val row = sheetData[rowIdx] ?: continue

            val courseName = getFieldValueWithMerge(row, fieldMapping, "courseName", mergedRegions, rowIdx, sheetData)
            if (courseName.isBlank()) continue

            val dayOfWeekStr = getFieldValueWithMerge(row, fieldMapping, "dayOfWeek", mergedRegions, rowIdx, sheetData)
            val sectionStr = getFieldValueWithMerge(row, fieldMapping, "section", mergedRegions, rowIdx, sheetData)
            val weeksStr = getFieldValueWithMerge(row, fieldMapping, "weeks", mergedRegions, rowIdx, sheetData)

            val dayOfWeek = parseDayOfWeek(dayOfWeekStr)
            val (startSection, sectionCount) = parseSectionInfo(sectionStr)
            val (weeks, weekExpression) = parseWeekInfo(weeksStr)

            if (dayOfWeek !in 1..7) {
                warnings.add("行${rowIdx + 1}: 星期无效 '$dayOfWeekStr'")
                continue
            }

            courses.add(ParsedCourse(
                courseName = courseName.trim(),
                teacher = getFieldValueWithMerge(row, fieldMapping, "teacher", mergedRegions, rowIdx, sheetData).trim(),
                classroom = getFieldValueWithMerge(row, fieldMapping, "classroom", mergedRegions, rowIdx, sheetData).trim(),
                dayOfWeek = dayOfWeek,
                startSection = startSection,
                sectionCount = sectionCount,
                weekExpression = weekExpression,
                weeks = weeks,
                credit = getFieldValueWithMerge(row, fieldMapping, "credit", mergedRegions, rowIdx, sheetData)
                    .toFloatOrNull() ?: 0f,
                courseCode = getFieldValueWithMerge(row, fieldMapping, "courseCode", mergedRegions, rowIdx, sheetData).trim(),
                className = getFieldValueWithMerge(row, fieldMapping, "className", mergedRegions, rowIdx, sheetData).trim(),
                courseNature = getFieldValueWithMerge(row, fieldMapping, "courseNature", mergedRegions, rowIdx, sheetData).trim(),
                studentCount = getFieldValueWithMerge(row, fieldMapping, "studentCount", mergedRegions, rowIdx, sheetData).trim().toIntOrNull() ?: 0,
                selectedCount = getFieldValueWithMerge(row, fieldMapping, "selectedCount", mergedRegions, rowIdx, sheetData).trim().toIntOrNull() ?: 0,
                courseHours = getFieldValueWithMerge(row, fieldMapping, "courseHours", mergedRegions, rowIdx, sheetData).trim()
            ))
        }

        if (courses.isEmpty()) warnings.add("未解析出有效课程数据")
        return Pair(courses, warnings)
    }

    private fun getFieldValueWithMerge(
        row: Map<Int, String>,
        fieldMapping: Map<String, Int>,
        fieldName: String,
        mergedRegions: List<MergedCellInfo>,
        rowIdx: Int,
        allRows: Map<Int, Map<Int, String>>
    ): String {
        val colIdx = fieldMapping[fieldName] ?: return ""
        val directValue = row[colIdx]
        if (directValue != null && directValue != "__MERGED__") return directValue

        for (region in mergedRegions) {
            if (colIdx in region.firstCol..region.lastCol && rowIdx in region.firstRow..region.lastRow) {
                val originRow = allRows[region.firstRow]
                if (originRow != null) {
                    val originValue = originRow[region.firstCol]
                    if (originValue != null) return originValue
                }
            }
        }

        return directValue ?: ""
    }

    private fun parseDayOfWeek(value: String): Int = ScheduleFieldParser.parseDayOfWeek(value)

    private fun parseSectionInfo(value: String): Pair<Int, Int> = ScheduleFieldParser.parseSectionInfo(value)

    private fun parseWeekInfo(value: String): Pair<List<Int>, String> = ScheduleFieldParser.parseWeekInfo(value)

    private fun formatWeekExpression(weeks: List<Int>): String = ScheduleFieldParser.formatWeekExpression(weeks)
}
