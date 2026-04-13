package com.wind.ggbond.classtime.service.helper

import android.content.Context
import android.net.Uri
import com.wind.ggbond.classtime.data.model.ParsedCourse
import com.wind.ggbond.classtime.util.AppLogger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImportRouter @Inject constructor(
    // TODO: [临时禁用Excel/CSV导入功能 - 后续需要恢复]
    // 以下依赖暂时不使用，保留以便后续恢复
    private val excelParser: ExcelParser,
    private val csvParser: CsvParser,
    private val smartRecognitionEngine: SmartRecognitionEngine,
    private val templateMatcher: TemplateMatcher,
    private val gridScheduleParser: GridScheduleParser
) {

    companion object {
        private const val TAG = "ImportRouter"
    }

    data class ImportRouteResult(
        val decision: ImportRouteDecision,
        val courses: List<ParsedCourse> = emptyList(),
        val preview: List<List<String>> = emptyList(),
        val sheetNames: List<String> = emptyList(),
        val warnings: List<String> = emptyList(),
        val detectedEncoding: String? = null
    )

    @Deprecated("Use routeImport instead", ReplaceWith("routeImport(context, uri, sheetIndex, forceTemplate, forceFieldMapping, forceHeaderRow)"))
    data class ExcelImportRouteResult(
        val decision: ImportRouteDecision,
        val courses: List<ParsedCourse> = emptyList(),
        val preview: List<List<String>> = emptyList(),
        val sheetNames: List<String> = emptyList(),
        val warnings: List<String> = emptyList()
    )

    // TODO: [临时禁用Excel/CSV导入功能 - 后续需要恢复]
    // 此方法暂时不使用，保留代码以便后续恢复
    fun routeImport(
        context: Context,
        uri: Uri,
        sheetIndex: Int = 0,
        forceTemplate: String? = null,
        forceFieldMapping: Map<String, Int>? = null,
        forceHeaderRow: Int? = null
    ): ImportRouteResult {
        val fileName = getFileName(context, uri)
        val extension = fileName?.substringAfterLast('.', "")?.lowercase() ?: ""

        return when (extension) {
            "csv" -> routeCsvImport(context, uri, forceTemplate, forceFieldMapping, forceHeaderRow)
            "xlsx", "xls" -> routeExcelImport(context, uri, sheetIndex, forceTemplate, forceFieldMapping, forceHeaderRow)
            else -> {
                val preview = tryReadPreview(context, uri)
                if (isCsvPreview(preview)) {
                    routeCsvImport(context, uri, forceTemplate, forceFieldMapping, forceHeaderRow)
                } else {
                    routeExcelImport(context, uri, sheetIndex, forceTemplate, forceFieldMapping, forceHeaderRow)
                }
            }
        }
    }

    // TODO: [临时禁用CSV导入功能 - 后续需要恢复]
    // 此方法暂时不使用，保留代码以便后续恢复
    fun routeCsvImport(
        context: Context,
        uri: Uri,
        forceTemplate: String? = null,
        forceFieldMapping: Map<String, Int>? = null,
        forceHeaderRow: Int? = null
    ): ImportRouteResult {
        val warnings = mutableListOf<String>()
        val preview = csvParser.getPreviewFromUri(context, uri, 20)

        if (preview.isEmpty()) {
            return ImportRouteResult(
                decision = ImportRouteDecision(
                    confidence = ImportConfidence.LOW,
                    templateName = null,
                    fieldMapping = emptyMap(),
                    headerRowIndex = 0,
                    dataStartRowIndex = 1,
                    needsConfirmation = true,
                    suggestedAction = "文件为空或无法读取"
                ),
                warnings = listOf("文件为空或无法读取")
            )
        }

        if (forceFieldMapping != null && forceHeaderRow != null) {
            return csvImportWithMapping(context, uri, forceFieldMapping, forceHeaderRow, preview)
        }

        if (forceTemplate != null) {
            return csvImportWithTemplate(context, uri, forceTemplate, preview)
        }

        val appExportResult = tryParseAppExportedCsv(context, uri, preview)
        if (appExportResult != null) {
            AppLogger.d(TAG, "检测到应用自身导出的CSV格式，直接解析")
            return appExportResult
        }

        if (gridScheduleParser.isGridSchedule(preview)) {
            AppLogger.d(TAG, "检测到CSV网格课表格式，使用GridScheduleParser解析")
            val courses = gridScheduleParser.parseGridSchedule(preview)
            if (courses.isNotEmpty()) {
                return ImportRouteResult(
                    decision = ImportRouteDecision(
                        confidence = ImportConfidence.HIGH,
                        templateName = "网格课表",
                        fieldMapping = emptyMap(),
                        headerRowIndex = 0,
                        dataStartRowIndex = 0,
                        needsConfirmation = false,
                        suggestedAction = "自动识别为网格课表格式"
                    ),
                    courses = courses,
                    preview = preview,
                    warnings = if (courses.any { it.weeks.isEmpty() }) listOf("部分课程缺少周次信息") else emptyList()
                )
            }
        }

        val keywordMatch = templateMatcher.matchByKeywords(preview)
        if (keywordMatch != null && keywordMatch.confidence >= 0.7f) {
            AppLogger.d(TAG, "CSV关键词匹配成功: ${keywordMatch.templateName}, 置信度: ${keywordMatch.confidence}")
            val config = TableImportConfig(
                headerRowIndex = keywordMatch.headerRowIndex,
                dataStartRowIndex = keywordMatch.dataStartRowIndex,
                fieldMapping = keywordMatch.fieldMapping
            )
            val parseResult = csvParser.parseFromUri(context, uri, config)
            val decision = ImportRouteDecision(
                confidence = ImportConfidence.HIGH,
                templateName = keywordMatch.templateName,
                fieldMapping = keywordMatch.fieldMapping,
                headerRowIndex = keywordMatch.headerRowIndex,
                dataStartRowIndex = keywordMatch.dataStartRowIndex,
                needsConfirmation = false,
                suggestedAction = "自动解析"
            )
            return ImportRouteResult(
                decision = decision,
                courses = parseResult.courses,
                preview = preview,
                warnings = parseResult.warnings,
                detectedEncoding = parseResult.detectedEncoding
            )
        }

        val headerMatch = templateMatcher.matchTemplate(preview.firstOrNull() ?: emptyList())
        if (headerMatch != null && headerMatch.confidence >= 0.7f) {
            AppLogger.d(TAG, "CSV表头模板匹配成功: ${headerMatch.templateName}, 置信度: ${headerMatch.confidence}")
            val config = TableImportConfig(
                headerRowIndex = headerMatch.headerRowIndex,
                dataStartRowIndex = headerMatch.dataStartRowIndex,
                fieldMapping = headerMatch.fieldMapping
            )
            val parseResult = csvParser.parseFromUri(context, uri, config)
            val confidence = smartRecognitionEngine.determineConfidence(headerMatch.confidence)
            val decision = ImportRouteDecision(
                confidence = confidence,
                templateName = headerMatch.templateName,
                fieldMapping = headerMatch.fieldMapping,
                headerRowIndex = headerMatch.headerRowIndex,
                dataStartRowIndex = headerMatch.dataStartRowIndex,
                needsConfirmation = confidence != ImportConfidence.HIGH,
                suggestedAction = if (confidence == ImportConfidence.HIGH) "自动解析" else "请确认字段映射"
            )
            return ImportRouteResult(
                decision = decision,
                courses = parseResult.courses,
                preview = preview,
                warnings = parseResult.warnings,
                detectedEncoding = parseResult.detectedEncoding
            )
        }

        val analysis = smartRecognitionEngine.analyzeSheet(preview)
        AppLogger.d(TAG, "CSV智能分析结果: 置信度=${analysis.confidence}, 建议=${analysis.suggestedAction}")

        if (analysis.confidence == ImportConfidence.HIGH || analysis.confidence == ImportConfidence.MEDIUM) {
            val config = TableImportConfig(
                headerRowIndex = analysis.headerRowIndex,
                dataStartRowIndex = analysis.dataStartRowIndex,
                fieldMapping = analysis.fieldMapping
            )
            val parseResult = csvParser.parseFromUri(context, uri, config)
            return ImportRouteResult(
                decision = analysis,
                courses = parseResult.courses,
                preview = preview,
                warnings = parseResult.warnings,
                detectedEncoding = parseResult.detectedEncoding
            )
        }

        return ImportRouteResult(
            decision = analysis,
            preview = preview,
            warnings = warnings + "需要手动确认字段映射"
        )
    }

    // TODO: [临时禁用Excel导入功能 - 后续需要恢复]
    // 此方法暂时不使用，保留代码以便后续恢复
    fun routeExcelImport(
        context: Context,
        uri: Uri,
        sheetIndex: Int = 0,
        forceTemplate: String? = null,
        forceFieldMapping: Map<String, Int>? = null,
        forceHeaderRow: Int? = null
    ): ImportRouteResult {
        val warnings = mutableListOf<String>()

        val sheetNames = excelParser.getSheetNames(context, uri)
        val preview = excelParser.getSheetPreview(context, uri, sheetIndex, 50)

        if (preview.isEmpty()) {
            return ImportRouteResult(
                decision = ImportRouteDecision(
                    confidence = ImportConfidence.LOW,
                    templateName = null,
                    fieldMapping = emptyMap(),
                    headerRowIndex = 0,
                    dataStartRowIndex = 1,
                    needsConfirmation = true,
                    suggestedAction = "文件为空或无法读取"
                ),
                sheetNames = sheetNames,
                warnings = listOf("文件为空或无法读取")
            )
        }

        if (gridScheduleParser.isGridSchedule(preview)) {
            AppLogger.d(TAG, "检测到网格课表格式，使用GridScheduleParser解析")
            val courses = gridScheduleParser.parseGridSchedule(preview)
            if (courses.isNotEmpty()) {
                return ImportRouteResult(
                    decision = ImportRouteDecision(
                        confidence = ImportConfidence.HIGH,
                        templateName = "网格课表",
                        fieldMapping = emptyMap(),
                        headerRowIndex = 0,
                        dataStartRowIndex = 0,
                        needsConfirmation = false,
                        suggestedAction = "自动识别为网格课表格式"
                    ),
                    courses = courses,
                    preview = preview,
                    sheetNames = sheetNames,
                    warnings = if (courses.any { it.weeks.isEmpty() }) listOf("部分课程缺少周次信息") else emptyList()
                )
            }
        }

        if (forceFieldMapping != null && forceHeaderRow != null) {
            return excelImportWithMapping(context, uri, sheetIndex, forceFieldMapping, forceHeaderRow, preview, sheetNames)
        }

        if (forceTemplate != null) {
            return excelImportWithTemplate(context, uri, sheetIndex, forceTemplate, preview, sheetNames)
        }

        val keywordMatch = templateMatcher.matchByKeywords(preview)
        if (keywordMatch != null && keywordMatch.confidence >= 0.7f) {
            AppLogger.d(TAG, "关键词匹配成功: ${keywordMatch.templateName}, 置信度: ${keywordMatch.confidence}")
            val config = ExcelImportConfig(
                headerRowIndex = keywordMatch.headerRowIndex,
                dataStartRowIndex = keywordMatch.dataStartRowIndex,
                fieldMapping = keywordMatch.fieldMapping
            )
            val parseResult = excelParser.parseExcelFromUri(context, uri, config)
            val decision = ImportRouteDecision(
                confidence = ImportConfidence.HIGH,
                templateName = keywordMatch.templateName,
                fieldMapping = keywordMatch.fieldMapping,
                headerRowIndex = keywordMatch.headerRowIndex,
                dataStartRowIndex = keywordMatch.dataStartRowIndex,
                needsConfirmation = false,
                suggestedAction = "自动解析"
            )
            return ImportRouteResult(
                decision = decision,
                courses = parseResult.courses,
                preview = preview,
                sheetNames = sheetNames,
                warnings = parseResult.warnings
            )
        }

        val headerMatch = templateMatcher.matchTemplate(preview.firstOrNull() ?: emptyList())
        if (headerMatch != null && headerMatch.confidence >= 0.7f) {
            AppLogger.d(TAG, "表头模板匹配成功: ${headerMatch.templateName}, 置信度: ${headerMatch.confidence}")
            val config = ExcelImportConfig(
                headerRowIndex = headerMatch.headerRowIndex,
                dataStartRowIndex = headerMatch.dataStartRowIndex,
                fieldMapping = headerMatch.fieldMapping
            )
            val parseResult = excelParser.parseExcelFromUri(context, uri, config)
            val confidence = smartRecognitionEngine.determineConfidence(headerMatch.confidence)
            val decision = ImportRouteDecision(
                confidence = confidence,
                templateName = headerMatch.templateName,
                fieldMapping = headerMatch.fieldMapping,
                headerRowIndex = headerMatch.headerRowIndex,
                dataStartRowIndex = headerMatch.dataStartRowIndex,
                needsConfirmation = confidence != ImportConfidence.HIGH,
                suggestedAction = if (confidence == ImportConfidence.HIGH) "自动解析" else "请确认字段映射"
            )
            return ImportRouteResult(
                decision = decision,
                courses = parseResult.courses,
                preview = preview,
                sheetNames = sheetNames,
                warnings = parseResult.warnings
            )
        }

        val analysis = smartRecognitionEngine.analyzeSheet(preview)
        AppLogger.d(TAG, "智能分析结果: 置信度=${analysis.confidence}, 建议=${analysis.suggestedAction}")

        if (analysis.confidence == ImportConfidence.HIGH) {
            val config = ExcelImportConfig(
                headerRowIndex = analysis.headerRowIndex,
                dataStartRowIndex = analysis.dataStartRowIndex,
                fieldMapping = analysis.fieldMapping
            )
            val parseResult = excelParser.parseExcelFromUri(context, uri, config)
            return ImportRouteResult(
                decision = analysis,
                courses = parseResult.courses,
                preview = preview,
                sheetNames = sheetNames,
                warnings = parseResult.warnings
            )
        }

        return ImportRouteResult(
            decision = analysis,
            preview = preview,
            sheetNames = sheetNames,
            warnings = warnings + "需要手动确认字段映射"
        )
    }

    private fun csvImportWithMapping(
        context: Context,
        uri: Uri,
        fieldMapping: Map<String, Int>,
        headerRow: Int,
        preview: List<List<String>>
    ): ImportRouteResult {
        val config = TableImportConfig(
            headerRowIndex = headerRow,
            dataStartRowIndex = headerRow + 1,
            fieldMapping = fieldMapping
        )
        val parseResult = csvParser.parseFromUri(context, uri, config)
        val decision = ImportRouteDecision(
            confidence = ImportConfidence.HIGH,
            templateName = null,
            fieldMapping = fieldMapping,
            headerRowIndex = headerRow,
            dataStartRowIndex = headerRow + 1,
            needsConfirmation = false,
            suggestedAction = "使用手动映射解析"
        )
        return ImportRouteResult(
            decision = decision,
            courses = parseResult.courses,
            preview = preview,
            warnings = parseResult.warnings,
            detectedEncoding = parseResult.detectedEncoding
        )
    }

    private fun csvImportWithTemplate(
        context: Context,
        uri: Uri,
        templateName: String,
        preview: List<List<String>>
    ): ImportRouteResult {
        val template = templateMatcher.getTemplateByName(templateName)
        if (template == null) {
            return ImportRouteResult(
                decision = ImportRouteDecision(
                    confidence = ImportConfidence.LOW,
                    templateName = null,
                    fieldMapping = emptyMap(),
                    headerRowIndex = 0,
                    dataStartRowIndex = 1,
                    needsConfirmation = true,
                    suggestedAction = "未找到模板: $templateName"
                ),
                preview = preview,
                warnings = listOf("未找到模板: $templateName")
            )
        }

        val headerRow = preview.getOrNull(template.defaultHeaderRow) ?: emptyList()
        val matchResult = template.matchHeaders(headerRow)
        val config = TableImportConfig(
            headerRowIndex = matchResult.headerRowIndex,
            dataStartRowIndex = matchResult.dataStartRowIndex,
            fieldMapping = matchResult.fieldMapping
        )
        val parseResult = csvParser.parseFromUri(context, uri, config)

        val confidence = smartRecognitionEngine.determineConfidence(matchResult.confidence)
        val decision = ImportRouteDecision(
            confidence = confidence,
            templateName = templateName,
            fieldMapping = matchResult.fieldMapping,
            headerRowIndex = matchResult.headerRowIndex,
            dataStartRowIndex = matchResult.dataStartRowIndex,
            needsConfirmation = confidence != ImportConfidence.HIGH,
            suggestedAction = "使用${template.displayName}模板解析"
        )

        return ImportRouteResult(
            decision = decision,
            courses = parseResult.courses,
            preview = preview,
            warnings = parseResult.warnings,
            detectedEncoding = parseResult.detectedEncoding
        )
    }

    private fun excelImportWithMapping(
        context: Context,
        uri: Uri,
        sheetIndex: Int,
        fieldMapping: Map<String, Int>,
        headerRow: Int,
        preview: List<List<String>>,
        sheetNames: List<String>
    ): ImportRouteResult {
        val config = ExcelImportConfig(
            headerRowIndex = headerRow,
            dataStartRowIndex = headerRow + 1,
            fieldMapping = fieldMapping
        )
        val parseResult = excelParser.parseExcelFromUri(context, uri, config)
        val decision = ImportRouteDecision(
            confidence = ImportConfidence.HIGH,
            templateName = null,
            fieldMapping = fieldMapping,
            headerRowIndex = headerRow,
            dataStartRowIndex = headerRow + 1,
            needsConfirmation = false,
            suggestedAction = "使用手动映射解析"
        )
        return ImportRouteResult(
            decision = decision,
            courses = parseResult.courses,
            preview = preview,
            sheetNames = sheetNames,
            warnings = parseResult.warnings
        )
    }

    private fun excelImportWithTemplate(
        context: Context,
        uri: Uri,
        sheetIndex: Int,
        templateName: String,
        preview: List<List<String>>,
        sheetNames: List<String>
    ): ImportRouteResult {
        val template = templateMatcher.getTemplateByName(templateName)
        if (template == null) {
            return ImportRouteResult(
                decision = ImportRouteDecision(
                    confidence = ImportConfidence.LOW,
                    templateName = null,
                    fieldMapping = emptyMap(),
                    headerRowIndex = 0,
                    dataStartRowIndex = 1,
                    needsConfirmation = true,
                    suggestedAction = "未找到模板: $templateName"
                ),
                preview = preview,
                sheetNames = sheetNames,
                warnings = listOf("未找到模板: $templateName")
            )
        }

        val headerRow = preview.getOrNull(template.defaultHeaderRow) ?: emptyList()
        val matchResult = template.matchHeaders(headerRow)
        val config = template.buildConfig(matchResult.headerRowIndex, matchResult.fieldMapping)
        val parseResult = excelParser.parseExcelFromUri(context, uri, config)

        val confidence = smartRecognitionEngine.determineConfidence(matchResult.confidence)
        val decision = ImportRouteDecision(
            confidence = confidence,
            templateName = templateName,
            fieldMapping = matchResult.fieldMapping,
            headerRowIndex = matchResult.headerRowIndex,
            dataStartRowIndex = matchResult.dataStartRowIndex,
            needsConfirmation = confidence != ImportConfidence.HIGH,
            suggestedAction = "使用${template.displayName}模板解析"
        )

        return ImportRouteResult(
            decision = decision,
            courses = parseResult.courses,
            preview = preview,
            sheetNames = sheetNames,
            warnings = parseResult.warnings
        )
    }

    private fun tryReadPreview(context: Context, uri: Uri): List<List<String>> {
        return try {
            excelParser.getSheetPreview(context, uri, 0, 5)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun isCsvPreview(preview: List<List<String>>): Boolean {
        if (preview.isEmpty()) return false
        val firstRow = preview.first()
        if (firstRow.isEmpty()) return false
        val avgFields = preview.map { it.size }.average()
        return avgFields >= 3
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

    private fun tryParseAppExportedCsv(
        context: Context,
        uri: Uri,
        preview: List<List<String>>
    ): ImportRouteResult? {
        return try {
            val headerRow = preview.firstOrNull() ?: return null
            val headerFields = headerRow.map { it.trim().removeSurrounding("\"").lowercase() }

            val hasCourseCode = headerFields.any { it.contains("课程代码") || it.contains("课程编号") || it.contains("课程号") }
            val hasCourseName = headerFields.any { it.contains("课程名称") || it.contains("课程名") }
            val hasTeacher = headerFields.any { it.contains("授课教师") || it.contains("任课教师") || it.contains("教师") }
            val hasWeeks = headerFields.any { it.contains("上课周次") || it.contains("周次") }
            val hasDayOfWeek = headerFields.any { it.contains("星期") }
            val hasSection = headerFields.any { it.contains("节次") }
            val hasLocation = headerFields.any { it.contains("上课地点") || it.contains("教室") || it.contains("地点") }
            val hasSequence = headerFields.any { it == "序号" }

            val isAppNewFormat = hasCourseCode && hasCourseName && hasTeacher && hasWeeks && hasDayOfWeek && hasSection && hasLocation
            val isAppOldFormat = hasSequence && hasCourseName && hasDayOfWeek && hasSection

            if (isAppNewFormat || isAppOldFormat) {
                val fieldMapping = mutableMapOf<String, Int>()
                headerFields.forEachIndexed { idx, field ->
                    when {
                        field == "序号" -> {}
                        field.contains("课程代码") || field.contains("课程编号") || field.contains("课程号") -> fieldMapping["courseCode"] = idx
                        field.contains("课程名称") || field.contains("课程名") -> fieldMapping["courseName"] = idx
                        field.contains("课程性质") || field.contains("课程类型") || field.contains("课程类别") -> fieldMapping["courseNature"] = idx
                        field.contains("学分") -> fieldMapping["credit"] = idx
                        field.contains("授课教师") || field.contains("任课教师") -> fieldMapping["teacher"] = idx
                        field.contains("教师") && !field.contains("授课") && !field.contains("任课") -> fieldMapping["teacher"] = idx
                        field.contains("上课周次") || field.contains("周次") -> fieldMapping["weeks"] = idx
                        field.contains("星期") -> fieldMapping["dayOfWeek"] = idx
                        field.contains("节次") -> fieldMapping["section"] = idx
                        field.contains("上课时间") -> {}
                        field.contains("上课地点") || (field.contains("教室") && !field.contains("上课")) -> fieldMapping["classroom"] = idx
                        field.contains("地点") && !field.contains("上课") -> fieldMapping["classroom"] = idx
                        field.contains("颜色") -> {}
                        field.contains("提醒") -> {}
                        field.contains("备注") || field.contains("说明") -> fieldMapping["note"] = idx
                    }
                }
                val headerRowIndex = preview.indexOf(headerRow)
                val config = TableImportConfig(
                    headerRowIndex = headerRowIndex,
                    dataStartRowIndex = headerRowIndex + 1,
                    fieldMapping = fieldMapping
                )
                val parseResult = csvParser.parseFromUri(context, uri, config)
                val templateName = if (isAppNewFormat) "课程表App导出" else "课程表App导出(旧版)"
                val decision = ImportRouteDecision(
                    confidence = ImportConfidence.HIGH,
                    templateName = templateName,
                    fieldMapping = fieldMapping,
                    headerRowIndex = headerRowIndex,
                    dataStartRowIndex = headerRowIndex + 1,
                    needsConfirmation = false,
                    suggestedAction = "自动识别为课程表App导出格式"
                )
                ImportRouteResult(
                    decision = decision,
                    courses = parseResult.courses,
                    preview = preview,
                    warnings = parseResult.warnings,
                    detectedEncoding = parseResult.detectedEncoding
                )
            } else {
                null
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "检测应用导出CSV格式失败", e)
            null
        }
    }
}
