package com.wind.ggbond.classtime.service.helper

import android.content.Context
import android.net.Uri
import com.wind.ggbond.classtime.data.model.ParsedCourse
import com.wind.ggbond.classtime.util.AppLogger
import jxl.Workbook
import jxl.CellType
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LightweightXlsParser @Inject constructor() {

    companion object {
        private const val TAG = "LightweightXlsParser"
    }

    fun parseXls(inputStream: InputStream, config: ExcelImportConfig = ExcelImportConfig()): ExcelParseResult {
        var workbook: Workbook? = null
        return try {
            workbook = Workbook.getWorkbook(inputStream)
            val sheetIndex = config.sheetIndex.coerceAtMost(workbook.numberOfSheets - 1)
            val sheet = workbook.getSheet(sheetIndex) ?: return ExcelParseResult(
                courses = emptyList(),
                warnings = listOf("工作表不存在")
            )
            parseSheet(sheet, config)
        } catch (e: Exception) {
            AppLogger.e(TAG, "xls解析失败", e)
            ExcelParseResult(courses = emptyList(), warnings = listOf("解析失败: ${e.message}"))
        } finally {
            workbook?.close()
        }
    }

    fun parseXlsFromUri(
        context: Context,
        uri: Uri,
        config: ExcelImportConfig = ExcelImportConfig()
    ): ExcelParseResult {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                parseXls(inputStream, config)
            } ?: ExcelParseResult(courses = emptyList(), warnings = listOf("无法打开文件"))
        } catch (e: Exception) {
            AppLogger.e(TAG, "xls解析失败", e)
            ExcelParseResult(courses = emptyList(), warnings = listOf("解析失败: ${e.message}"))
        }
    }

    fun getSheetNames(context: Context, uri: Uri): List<String> {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                getSheetNames(inputStream)
            } ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    fun getSheetNames(inputStream: InputStream): List<String> {
        var workbook: Workbook? = null
        return try {
            workbook = Workbook.getWorkbook(inputStream)
            (0 until workbook.numberOfSheets).map { workbook.getSheet(it).name }
        } catch (e: Exception) { emptyList() }
        finally { workbook?.close() }
    }

    fun getSheetPreview(
        context: Context,
        uri: Uri,
        sheetIndex: Int = 0,
        maxRows: Int = 10
    ): List<List<String>> {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                getSheetPreview(inputStream, sheetIndex, maxRows)
            } ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    fun getSheetPreview(
        inputStream: InputStream,
        sheetIndex: Int = 0,
        maxRows: Int = 10
    ): List<List<String>> {
        var workbook: Workbook? = null
        return try {
            workbook = Workbook.getWorkbook(inputStream)
            if (sheetIndex >= workbook.numberOfSheets) return emptyList()
            val sheet = workbook.getSheet(sheetIndex)
            val rows = sheet.rows
            val cols = sheet.columns
            val result = mutableListOf<List<String>>()
            for (rowIdx in 0 until minOf(rows, maxRows)) {
                val rowData = mutableListOf<String>()
                for (colIdx in 0 until cols) {
                    rowData.add(getCellStringValue(sheet.getCell(colIdx, rowIdx)))
                }
                result.add(rowData)
            }
            result
        } catch (e: Exception) { emptyList() }
        finally { workbook?.close() }
    }

    private fun parseSheet(sheet: jxl.Sheet, config: ExcelImportConfig): ExcelParseResult {
        val warnings = mutableListOf<String>()
        val fieldMapping = config.fieldMapping

        if (fieldMapping.isEmpty()) {
            return ExcelParseResult(
                courses = emptyList(),
                warnings = listOf("未提供字段映射"),
                headerRowIndex = config.headerRowIndex
            )
        }

        val courses = mutableListOf<ParsedCourse>()
        val startRow = config.dataStartRowIndex.coerceAtLeast(config.headerRowIndex + 1)
        val mergedRegions = extractMergedRegions(sheet)

        for (rowIdx in startRow until sheet.rows) {
            val courseName = getFieldValue(sheet, fieldMapping, "courseName", rowIdx, mergedRegions)
            if (courseName.isBlank()) continue

            val dayOfWeekStr = getFieldValue(sheet, fieldMapping, "dayOfWeek", rowIdx, mergedRegions)
            val sectionStr = getFieldValue(sheet, fieldMapping, "section", rowIdx, mergedRegions)
            val weeksStr = getFieldValue(sheet, fieldMapping, "weeks", rowIdx, mergedRegions)

            val dayOfWeek = parseDayOfWeek(dayOfWeekStr)
            val (startSection, sectionCount) = parseSectionInfo(sectionStr)
            val (weeks, weekExpression) = parseWeekInfo(weeksStr)

            if (dayOfWeek !in 1..7) {
                warnings.add("行${rowIdx + 1}: 星期无效 '$dayOfWeekStr'")
                continue
            }

            courses.add(ParsedCourse(
                courseName = courseName.trim(),
                teacher = getFieldValue(sheet, fieldMapping, "teacher", rowIdx, mergedRegions).trim(),
                classroom = getFieldValue(sheet, fieldMapping, "classroom", rowIdx, mergedRegions).trim(),
                dayOfWeek = dayOfWeek,
                startSection = startSection,
                sectionCount = sectionCount,
                weekExpression = weekExpression,
                weeks = weeks,
                credit = getFieldValue(sheet, fieldMapping, "credit", rowIdx, mergedRegions)
                    .toFloatOrNull() ?: 0f,
                courseCode = getFieldValue(sheet, fieldMapping, "courseCode", rowIdx, mergedRegions).trim(),
                className = getFieldValue(sheet, fieldMapping, "className", rowIdx, mergedRegions).trim(),
                courseNature = getFieldValue(sheet, fieldMapping, "courseNature", rowIdx, mergedRegions).trim(),
                studentCount = getFieldValue(sheet, fieldMapping, "studentCount", rowIdx, mergedRegions).trim().toIntOrNull() ?: 0,
                selectedCount = getFieldValue(sheet, fieldMapping, "selectedCount", rowIdx, mergedRegions).trim().toIntOrNull() ?: 0,
                courseHours = getFieldValue(sheet, fieldMapping, "courseHours", rowIdx, mergedRegions).trim()
            ))
        }

        if (courses.isEmpty() && startRow < sheet.rows) {
            warnings.add("未解析出有效课程数据")
        }

        return ExcelParseResult(
            courses = courses,
            warnings = warnings,
            headerRowIndex = config.headerRowIndex,
            fieldMapping = fieldMapping
        )
    }

    private fun getCellStringValue(cell: jxl.Cell?): String {
        if (cell == null) return ""
        return when (cell.type) {
            CellType.LABEL -> (cell as jxl.LabelCell).string ?: ""
            CellType.NUMBER -> {
                val d = (cell as jxl.NumberCell).value
                if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()
            }
            CellType.BOOLEAN -> (cell as jxl.BooleanCell).value.toString()
            CellType.DATE -> (cell as jxl.DateCell).date?.toString() ?: ""
            else -> cell.contents?.trim() ?: ""
        }
    }

    private fun getFieldValue(
        sheet: jxl.Sheet,
        fieldMapping: Map<String, Int>,
        fieldName: String,
        rowIdx: Int,
        mergedRegions: List<MergedCellInfo>
    ): String {
        val colIdx = fieldMapping[fieldName] ?: return ""
        if (colIdx >= sheet.columns) return ""

        for (region in mergedRegions) {
            if (colIdx in region.firstCol..region.lastCol && rowIdx in region.firstRow..region.lastRow) {
                if (rowIdx != region.firstRow || colIdx != region.firstCol) {
                    val originCell = sheet.getCell(region.firstCol, region.firstRow)
                    return getCellStringValue(originCell)
                }
            }
        }

        val cell = sheet.getCell(colIdx, rowIdx)
        return getCellStringValue(cell)
    }

    private fun extractMergedRegions(sheet: jxl.Sheet): List<MergedCellInfo> {
        val merged = mutableListOf<MergedCellInfo>()
        val mergedCells = sheet.mergedCells ?: return merged
        for (range in mergedCells) {
            merged.add(MergedCellInfo(
                firstRow = range.topLeft.row,
                lastRow = range.bottomRight.row,
                firstCol = range.topLeft.column,
                lastCol = range.bottomRight.column,
                value = ""
            ))
        }
        return merged
    }

    private fun parseDayOfWeek(value: String): Int = ScheduleFieldParser.parseDayOfWeek(value)

    private fun parseSectionInfo(value: String): Pair<Int, Int> = ScheduleFieldParser.parseSectionInfo(value)

    private fun parseWeekInfo(value: String): Pair<List<Int>, String> = ScheduleFieldParser.parseWeekInfo(value)

    private fun formatWeekExpression(weeks: List<Int>): String = ScheduleFieldParser.formatWeekExpression(weeks)
}
